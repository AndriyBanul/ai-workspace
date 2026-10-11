package com.aiworkspace.audio.client;

import com.aiworkspace.audio.config.AudioSynthesisProperties;
import com.aiworkspace.audio.exceptions.AudioSynthesisBusyException;
import com.aiworkspace.audio.interfaces.TextToSpeechProvider;
import com.aiworkspace.audio.models.PcmAudioFormat;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.WyomingErrorResponse;
import com.aiworkspace.audio.models.WyomingEvent;
import com.aiworkspace.audio.models.WyomingEventHeader;
import com.aiworkspace.audio.services.AudioValidator;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PiperClient implements TextToSpeechProvider {

    private static final String WYOMING_VERSION = "1.8.0";
    private static final long MAX_WAV_BYTE_RATE = 0xffff_ffffL;
    private static final TypeReference<Map<String, Object>> EVENT_DATA_TYPE = new TypeReference<>() { };

    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final AudioSynthesisProperties properties;
    private final AudioValidator audioValidator;
    private final ScheduledExecutorService deadlineScheduler;
    private final Semaphore synthesisSlots;

    public PiperClient(
            @Value("${ai-workspace.piper.host:localhost}") String host,
            @Value("${ai-workspace.piper.port:10200}") int port,
            ObjectMapper objectMapper,
            AudioSynthesisProperties properties,
            AudioValidator audioValidator,
            @Qualifier("piperDeadlineScheduler") ScheduledExecutorService deadlineScheduler
    ) {
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.audioValidator = audioValidator;
        this.deadlineScheduler = deadlineScheduler;
        this.synthesisSlots = new Semaphore(properties.maxConcurrentRequests());
    }

    @Override
    public SynthesizedSpeech synthesize(String text) throws IOException {
        audioValidator.validateText(text);
        if (!synthesisSlots.tryAcquire()) {
            throw new AudioSynthesisBusyException();
        }
        try (Socket socket = new Socket()) {
            return synthesize(socket, text);
        } catch (IOException exception) {
            throw new UpstreamServiceException("Piper", resolvePiperFailureMessage(exception), exception);
        } finally {
            synthesisSlots.release();
        }
    }

    private SynthesizedSpeech synthesize(Socket socket, String text) throws IOException {
        try (var deadline = new PiperSynthesisDeadline(socket, properties.requestTimeout(), deadlineScheduler)) {
            try {
                socket.connect(new InetSocketAddress(host, port), (int) properties.connectTimeout().toMillis());
                socket.setSoTimeout((int) properties.readTimeout().toMillis());
                writeEvent(socket.getOutputStream(), "synthesize", Map.of("text", text));
                SynthesizedSpeech speech = readSynthesizedSpeech(socket.getInputStream(), deadline);
                deadline.check();
                return speech;
            } catch (IOException exception) {
                if (deadline.hasExpired()) {
                    throw new IOException("Piper synthesis exceeded the total request deadline", exception);
                }
                throw exception;
            }
        }
    }

    private SynthesizedSpeech readSynthesizedSpeech(InputStream inputStream, PiperSynthesisDeadline deadline)
            throws IOException {
        PcmAudioFormat audioFormat = null;
        long maxAudioBytes = properties.maxAudioSize().toBytes();
        ByteArrayOutputStream pcmAudio = new ByteArrayOutputStream();
        for (int eventCount = 0; eventCount < properties.maxEvents(); eventCount++) {
            deadline.check();
            WyomingEvent event = readEvent(inputStream, maxAudioBytes - pcmAudio.size());
            if ("audio-start".equals(event.type())) {
                if (audioFormat != null) {
                    throw new IOException("Piper returned multiple audio-start events");
                }
                audioFormat = event.audioFormat();
                maxAudioBytes = Math.min(maxAudioBytes, calculateDurationLimitBytes(audioFormat));
            } else if ("audio-chunk".equals(event.type())) {
                if (audioFormat == null) {
                    throw new IOException("Piper returned audio before audio format metadata");
                }
                pcmAudio.writeBytes(event.payload());
            } else if ("audio-stop".equals(event.type())) {
                if (audioFormat == null) {
                    throw new IOException("Piper did not return audio format metadata");
                }
                deadline.check();
                return new SynthesizedSpeech("speech.wav", encodeWav(audioFormat, pcmAudio.toByteArray()));
            } else if ("error".equals(event.type())) {
                String errorText = event.error() == null ? null : event.error().text();
                throw new IOException(errorText == null || errorText.isBlank()
                        ? "Piper returned an error" : "Piper returned an error: " + errorText);
            }
        }
        throw new IOException("Piper exceeded the synthesis event limit");
    }

    private long calculateDurationLimitBytes(PcmAudioFormat format) {
        long frames = (long) format.rate() * properties.maxAudioDuration().getSeconds()
                + (long) format.rate() * properties.maxAudioDuration().getNano() / 1_000_000_000;
        return frames * format.width() * format.channels();
    }

    private String resolvePiperFailureMessage(IOException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Piper is unavailable";
        }

        return message;
    }

    private void writeEvent(OutputStream outputStream, String type, Map<String, Object> data) throws IOException {
        byte[] dataBytes = objectMapper.writeValueAsBytes(data);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("type", type);
        header.put("version", WYOMING_VERSION);
        header.put("data_length", dataBytes.length);

        outputStream.write(objectMapper.writeValueAsBytes(header));
        outputStream.write('\n');
        outputStream.write(dataBytes);
        outputStream.flush();
    }

    WyomingEvent readEvent(InputStream inputStream) throws IOException {
        return readEvent(inputStream, properties.maxAudioSize().toBytes());
    }

    private WyomingEvent readEvent(InputStream inputStream, long remainingAudioBytes) throws IOException {
        String headerLine = readHeaderLine(inputStream);
        WyomingEventHeader header = objectMapper.readValue(headerLine, WyomingEventHeader.class);
        if (header == null || header.type() == null || header.type().isBlank()) {
            throw new IOException("Piper returned an invalid event header");
        }
        validateEventLength(header.dataLength(), properties.maxEventDataSize().toBytes(), "metadata");
        validateEventLength(header.payloadLength(), properties.maxEventPayloadSize().toBytes(), "payload");
        if ("audio-chunk".equals(header.type()) && header.payloadLength() > remainingAudioBytes) {
            throw new IOException("Piper exceeded the synthesized audio size or duration limit");
        }
        byte[] data = readEventBytes(inputStream, header.dataLength());
        byte[] payload = readEventBytes(inputStream, header.payloadLength());
        Map<String, Object> metadata = mergeEventMetadata(header.data(), data);

        PcmAudioFormat audioFormat = null;
        WyomingErrorResponse error = null;
        if ("audio-start".equals(header.type())) {
            audioFormat = readEventMetadata(metadata, PcmAudioFormat.class);
            validateAudioFormat(audioFormat);
        } else if ("error".equals(header.type()) && !metadata.isEmpty()) {
            error = readEventMetadata(metadata, WyomingErrorResponse.class);
        }
        return new WyomingEvent(header.type(), audioFormat, error, payload);
    }

    private Map<String, Object> mergeEventMetadata(Map<String, Object> inlineData, byte[] additionalData) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (inlineData != null) {
            metadata.putAll(inlineData);
        }
        if (additionalData.length > 0) {
            Map<String, Object> additionalMetadata = objectMapper.readValue(additionalData, EVENT_DATA_TYPE);
            if (additionalMetadata == null) {
                throw new IOException("Piper returned invalid event metadata");
            }
            metadata.putAll(additionalMetadata);
        }
        return metadata;
    }

    private <T> T readEventMetadata(Map<String, Object> metadata, Class<T> responseType) throws IOException {
        try (var buffer = new TokenBuffer(objectMapper, false)) {
            objectMapper.writeValue(buffer, metadata);
            try (var parser = buffer.asParser(objectMapper)) {
                return objectMapper.readerFor(responseType)
                        .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                        .readValue(parser);
            }
        }
    }

    private byte[] readEventBytes(InputStream inputStream, int length) throws IOException {
        byte[] bytes = inputStream.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Piper closed the connection before sending a complete event");
        }
        return bytes;
    }

    private void validateEventLength(int length, long maxBytes, String section) throws IOException {
        if (length < 0) {
            throw new IOException("Piper returned a negative event length");
        }
        if (length > maxBytes) {
            throw new IOException("Piper exceeded the event " + section + " size limit");
        }
    }

    private String readHeaderLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        while (true) {
            int nextByte = inputStream.read();
            if (nextByte < 0) {
                throw new IOException("Piper closed the connection before sending a complete event");
            }

            if (nextByte == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }

            if (line.size() >= properties.maxHeaderSize().toBytes()) {
                throw new IOException("Piper exceeded the event header size limit");
            }
            line.write(nextByte);
        }
    }

    private void validateAudioFormat(PcmAudioFormat audioFormat) throws IOException {
        if (audioFormat == null) {
            throw new IOException("Piper returned invalid audio format metadata");
        }
        if (audioFormat.rate() <= 0) {
            throw new IOException("Piper returned an invalid PCM sample rate");
        }
        if (audioFormat.width() != 1 && audioFormat.width() != 2) {
            throw new IOException("Piper returned an unsupported PCM sample width; expected 1 or 2 bytes");
        }
        if (audioFormat.channels() != 1 && audioFormat.channels() != 2) {
            throw new IOException("Piper returned an unsupported PCM channel count; expected mono or stereo");
        }
        long byteRate = (long) audioFormat.rate() * audioFormat.channels() * audioFormat.width();
        if (byteRate > MAX_WAV_BYTE_RATE) {
            throw new IOException("Piper returned a PCM byte rate that exceeds the WAV format limit");
        }
    }

    byte[] encodeWav(PcmAudioFormat audioFormat, byte[] pcmAudio) throws IOException {
        validateAudioFormat(audioFormat);
        int channels = audioFormat.channels();
        int sampleRate = audioFormat.rate();
        int bytesPerSample = audioFormat.width();
        int bitsPerSample = bytesPerSample * 8;
        long byteRate = (long) sampleRate * channels * bytesPerSample;
        int blockAlign = channels * bytesPerSample;
        if (pcmAudio.length % blockAlign != 0) {
            throw new IOException("Piper returned incomplete PCM audio frames");
        }
        int padding = pcmAudio.length % 2;

        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        writeAscii(wav, "RIFF");
        writeIntLittleEndian(wav, 36 + pcmAudio.length + padding);
        writeAscii(wav, "WAVE");
        writeAscii(wav, "fmt ");
        writeIntLittleEndian(wav, 16);
        writeShortLittleEndian(wav, 1);
        writeShortLittleEndian(wav, channels);
        writeIntLittleEndian(wav, sampleRate);
        writeIntLittleEndian(wav, (int) byteRate);
        writeShortLittleEndian(wav, blockAlign);
        writeShortLittleEndian(wav, bitsPerSample);
        writeAscii(wav, "data");
        writeIntLittleEndian(wav, pcmAudio.length);
        wav.writeBytes(pcmAudio);
        if (padding != 0) {
            wav.write(0);
        }

        return wav.toByteArray();
    }

    private void writeAscii(OutputStream outputStream, String value) throws IOException {
        outputStream.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private void writeIntLittleEndian(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xff);
        outputStream.write((value >> 8) & 0xff);
        outputStream.write((value >> 16) & 0xff);
        outputStream.write((value >> 24) & 0xff);
    }

    private void writeShortLittleEndian(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xff);
        outputStream.write((value >> 8) & 0xff);
    }
}
