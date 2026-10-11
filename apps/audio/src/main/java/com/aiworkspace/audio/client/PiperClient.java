package com.aiworkspace.audio.client;

import com.aiworkspace.audio.interfaces.TextToSpeechProvider;
import com.aiworkspace.audio.models.PcmAudioFormat;
import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.aiworkspace.audio.models.WyomingErrorResponse;
import com.aiworkspace.audio.models.WyomingEvent;
import com.aiworkspace.audio.models.WyomingEventHeader;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PiperClient implements TextToSpeechProvider {

    private static final String WYOMING_VERSION = "1.8.0";
    private static final int SOCKET_TIMEOUT_MILLIS = 30_000;
    private static final long MAX_WAV_BYTE_RATE = 0xffff_ffffL;

    @Value("${ai-workspace.piper.host:localhost}")
    private final String host;
    @Value("${ai-workspace.piper.port:10200}")
    private final int port;
    private final ObjectMapper objectMapper;

    @Override
    public SynthesizedSpeech synthesize(String text) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MILLIS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);

            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            writeEvent(outputStream, "synthesize", Map.of("text", text));

            PcmAudioFormat audioFormat = null;
            ByteArrayOutputStream pcmAudio = new ByteArrayOutputStream();

            while (true) {
                WyomingEvent event = readEvent(inputStream);

                if ("audio-start".equals(event.type())) {
                    audioFormat = event.audioFormat();
                } else if ("audio-chunk".equals(event.type())) {
                    pcmAudio.writeBytes(event.payload());
                } else if ("audio-stop".equals(event.type())) {
                    break;
                } else if ("error".equals(event.type())) {
                    String errorText = event.error() == null ? null : event.error().text();
                    throw new IOException(errorText == null || errorText.isBlank()
                            ? "Piper returned an error" : "Piper returned an error: " + errorText);
                }
            }

            if (audioFormat == null) {
                throw new IOException("Piper did not return audio format metadata");
            }

            return new SynthesizedSpeech("speech.wav", encodeWav(audioFormat, pcmAudio.toByteArray()));
        } catch (IOException exception) {
            throw new UpstreamServiceException("Piper", resolvePiperFailureMessage(exception), exception);
        }
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
        String headerLine = readHeaderLine(inputStream);
        WyomingEventHeader header = objectMapper.readValue(headerLine, WyomingEventHeader.class);
        if (header == null || header.type() == null || header.type().isBlank()) {
            throw new IOException("Piper returned an invalid event header");
        }
        byte[] data = readEventBytes(inputStream, header.dataLength());
        byte[] payload = readEventBytes(inputStream, header.payloadLength());

        PcmAudioFormat audioFormat = null;
        WyomingErrorResponse error = null;
        if ("audio-start".equals(header.type())) {
            audioFormat = objectMapper.readerFor(PcmAudioFormat.class)
                    .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .readValue(data);
            validateAudioFormat(audioFormat);
        } else if ("error".equals(header.type()) && data.length > 0) {
            error = objectMapper.readValue(data, WyomingErrorResponse.class);
        }
        return new WyomingEvent(header.type(), audioFormat, error, payload);
    }

    private byte[] readEventBytes(InputStream inputStream, int length) throws IOException {
        if (length < 0) {
            throw new IOException("Piper returned a negative event length");
        }
        byte[] bytes = inputStream.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("Piper closed the connection before sending a complete event");
        }
        return bytes;
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
