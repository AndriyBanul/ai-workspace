package com.aiworkspace.audio.client;

import com.aiworkspace.audio.models.SynthesizedSpeech;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PiperClient {

    private static final String WYOMING_VERSION = "1.8.0";
    private static final int SOCKET_TIMEOUT_MILLIS = 30_000;

    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;

    @Autowired
    public PiperClient(
            @Value("${ai-workspace.piper.host:localhost}") String host,
            @Value("${ai-workspace.piper.port:10200}") int port
    ) {
        this(host, port, new ObjectMapper());
    }

    PiperClient(String host, int port, ObjectMapper objectMapper) {
        this.host = host;
        this.port = port;
        this.objectMapper = objectMapper;
    }

    public SynthesizedSpeech synthesize(String text) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), SOCKET_TIMEOUT_MILLIS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);

            OutputStream outputStream = socket.getOutputStream();
            InputStream inputStream = socket.getInputStream();

            writeEvent(outputStream, "synthesize", Map.of("text", text));

            AudioFormat audioFormat = null;
            ByteArrayOutputStream pcmAudio = new ByteArrayOutputStream();

            while (true) {
                WyomingEvent event = readEvent(inputStream);

                if ("audio-start".equals(event.type())) {
                    audioFormat = AudioFormat.from(event.data());
                } else if ("audio-chunk".equals(event.type())) {
                    pcmAudio.writeBytes(event.payload());
                } else if ("audio-stop".equals(event.type())) {
                    break;
                } else if ("error".equals(event.type())) {
                    throw new IOException("Piper returned an error: " + event.data());
                }
            }

            if (audioFormat == null) {
                throw new IOException("Piper did not return audio format metadata");
            }

            return new SynthesizedSpeech("speech.wav", wav(audioFormat, pcmAudio.toByteArray()));
        }
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

    private WyomingEvent readEvent(InputStream inputStream) throws IOException {
        String headerLine = readHeaderLine(inputStream);
        JsonNode header = objectMapper.readTree(headerLine);

        int dataLength = header.path("data_length").asInt(0);
        JsonNode data = objectMapper.createObjectNode();
        if (dataLength > 0) {
            data = objectMapper.readTree(inputStream.readNBytes(dataLength));
        }

        int payloadLength = header.path("payload_length").asInt(0);
        byte[] payload = payloadLength > 0 ? inputStream.readNBytes(payloadLength) : new byte[0];

        return new WyomingEvent(header.path("type").asText(), data, payload);
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

    private byte[] wav(AudioFormat audioFormat, byte[] pcmAudio) throws IOException {
        int channels = audioFormat.channels();
        int sampleRate = audioFormat.rate();
        int bytesPerSample = audioFormat.width();
        int bitsPerSample = bytesPerSample * 8;
        int byteRate = sampleRate * channels * bytesPerSample;
        int blockAlign = channels * bytesPerSample;

        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        writeAscii(wav, "RIFF");
        writeIntLittleEndian(wav, 36 + pcmAudio.length);
        writeAscii(wav, "WAVE");
        writeAscii(wav, "fmt ");
        writeIntLittleEndian(wav, 16);
        writeShortLittleEndian(wav, 1);
        writeShortLittleEndian(wav, channels);
        writeIntLittleEndian(wav, sampleRate);
        writeIntLittleEndian(wav, byteRate);
        writeShortLittleEndian(wav, blockAlign);
        writeShortLittleEndian(wav, bitsPerSample);
        writeAscii(wav, "data");
        writeIntLittleEndian(wav, pcmAudio.length);
        wav.writeBytes(pcmAudio);

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

    private record WyomingEvent(String type, JsonNode data, byte[] payload) {
    }

    private record AudioFormat(int rate, int width, int channels) {

        static AudioFormat from(JsonNode data) {
            return new AudioFormat(
                    data.path("rate").asInt(),
                    data.path("width").asInt(),
                    data.path("channels").asInt()
            );
        }
    }
}
