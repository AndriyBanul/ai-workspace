package com.aiworkspace.audio.client;

import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import com.aiworkspace.shared.media.TranscriptSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhisperClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WhisperClient client = new WhisperClient(
            "http://localhost",
            RestClient.builder().build(),
            objectMapper
    );

    @Test
    void parsesTimedSegmentsFromWhisperJson() {
        var response = client.parseTranscription("""
                {
                  "text": "Hello world",
                  "language": "en",
                  "segments": [
                    {"start": 0.25, "end": 1.5, "text": " Hello"},
                    {"timestamps": {"from": "00:01.500", "to": "00:02.750"},
                     "transcript": "world", "speaker": "SPEAKER_01"}
                  ]
                }
                """);

        assertEquals("Hello world", response.text());
        assertEquals("en", response.language());
        assertEquals(2, response.segments().size());
        assertEquals(250, response.segments().get(0).startMilliseconds());
        assertEquals(1_500, response.segments().get(0).endMilliseconds());
        assertNull(response.segments().get(0).speaker());
        assertEquals(1_500, response.segments().get(1).startMilliseconds());
        assertEquals(2_750, response.segments().get(1).endMilliseconds());
        assertEquals("SPEAKER_01", response.segments().get(1).speaker());
    }

    @Test
    void ignoresAdditionalProviderFields() {
        var response = client.parseTranscription("""
                {
                  "text": "Hello", "language": "en", "duration": 1.5,
                  "segments": [{
                    "id": 0, "text": " Hello ", "words": [],
                    "timestamps": {"from": 0.25, "to": 1.5, "extra": true}
                  }]
                }
                """);

        assertEquals(List.of(new TranscriptSegment(250, 1_500, null, "Hello")), response.segments());
    }

    @Test
    void defaultsMissingAndNullOptionalFields() {
        for (String body : List.of("{}", "{\"text\":null,\"language\":null,\"segments\":null}")) {
            var response = client.parseTranscription(body);

            assertEquals("", response.text());
            assertEquals("", response.language());
            assertEquals(List.of(), response.segments());
        }
    }

    @Test
    void preservesTimestampFallbacksAndSegmentNormalization() {
        var response = client.parseTranscription("""
                {
                  "segments": [
                    {"text": " ", "transcript": " hello ", "start": "1.25", "end": 0.5,
                     "timestamps": {"from": "00:09", "to": "00:10"}, "speaker": " S1 "},
                    {"text": "world", "start": null, "end": null,
                     "timestamps": {"from": "00:01:02.500", "to": "00:01:03"}, "speaker": " "},
                    {"text": "invalid timestamps", "start": "invalid", "end": -1},
                    {"text": "missing timestamps"},
                    {"text": " ", "transcript": " "},
                    null
                  ]
                }
                """);

        assertEquals(List.of(
                new TranscriptSegment(1_250, 1_250, "S1", "hello"),
                new TranscriptSegment(62_500, 63_000, null, "world"),
                new TranscriptSegment(0, 0, null, "invalid timestamps"),
                new TranscriptSegment(0, 0, null, "missing timestamps")
        ), response.segments());
    }

    @Test
    void wrapsInvalidResponsesAsUpstreamFailures() {
        for (String body : List.of("", "null", "{invalid", "{\"segments\":{}}")) {
            var exception = assertThrows(UpstreamServiceException.class, () -> client.parseTranscription(body));

            assertEquals("Whisper", exception.serviceName());
            assertEquals("Whisper returned an invalid response", exception.getMessage());
        }
        assertThrows(UpstreamServiceException.class, () -> client.parseTranscription(null));
    }

    @Test
    void transcribesThroughInjectedClientWithItsDefaultHeadersAndMultipartAudio() throws Exception {
        var builder = RestClient.builder().defaultHeader(HttpHeaders.USER_AGENT, "Configured-Agent");
        var server = MockRestServiceServer.bindTo(builder).build();
        byte[] audio = {0, 1, (byte) 255};
        server.expect(requestTo("http://localhost/asr?task=transcribe&output=json&word_timestamps=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, "Configured-Agent"))
                .andExpect(request -> {
                    MediaType contentType = request.getHeaders().getContentType();
                    assertNotNull(contentType);
                    assertEquals("multipart", contentType.getType());
                    assertEquals("form-data", contentType.getSubtype());
                    String boundary = contentType.getParameter("boundary");
                    assertNotNull(boundary);
                    var expectedBody = new ByteArrayOutputStream();
                    expectedBody.write(("--" + boundary + "\r\n"
                            + "Content-Disposition: form-data; name=\"audio_file\"; filename=\"meeting.wav\"\r\n"
                            + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    expectedBody.write(audio);
                    expectedBody.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    assertArrayEquals(expectedBody.toByteArray(), ((MockClientHttpRequest) request).getBodyAsBytes());
                })
                .andRespond(withSuccess("""
                        {"text":"Hello","language":"en","segments":[{"start":0.25,"end":1.5,"text":"Hello"}]}
                        """, MediaType.APPLICATION_JSON));
        var injectedClient = new WhisperClient("http://localhost/", builder.build(), objectMapper);

        var result = injectedClient.transcribe("meeting.wav", audio);

        assertEquals("Hello", result.text());
        assertEquals(List.of(new TranscriptSegment(250, 1_500, null, "Hello")), result.segments());
        server.verify();
    }

    @Test
    void rejectsUnsafeFilenamesBeforeSendingRequests() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var injectedClient = new WhisperClient("http://localhost", builder.build(), objectMapper);
        for (String filename : List.of(
                "meeting.wav\r\nX-Injected: true",
                "meeting\r.wav", "meeting\n.wav", "meeting\t.wav",
                "meeting\u0000.wav", "meeting\u001b.wav", "meeting\u007f.wav", "meeting\u0085.wav",
                "\r\n", "\t"
        )) {
            var exception = assertThrows(IllegalArgumentException.class,
                    () -> injectedClient.transcribe(filename, new byte[]{1}));

            assertEquals("Audio filename must not contain control characters", exception.getMessage());
        }
        server.verify();
    }

    @Test
    void escapesQuotedFilenamesAndPreservesUnicode() throws Exception {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost/asr?task=transcribe&output=json&word_timestamps=true"))
                .andExpect(request -> {
                    String body = new String(((MockClientHttpRequest) request).getBodyAsBytes(), StandardCharsets.UTF_8);
                    assertEquals("Content-Disposition: form-data; name=\"audio_file\"; "
                            + "filename=\"Résumé \\\"notes\\\"\\\\take.wav\"", body.split("\r\n")[1]);
                })
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var injectedClient = new WhisperClient("http://localhost", builder.build(), objectMapper);

        injectedClient.transcribe("Résumé \"notes\"\\take.wav", new byte[]{1});

        server.verify();
    }

    @Test
    void usesFallbackFilenameForMissingOrBlankNames() throws Exception {
        for (String filename : Arrays.asList(null, "", "   ")) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost/asr?task=transcribe&output=json&word_timestamps=true"))
                    .andExpect(request -> {
                        String body = new String(((MockClientHttpRequest) request).getBodyAsBytes(), StandardCharsets.UTF_8);
                        assertEquals("Content-Disposition: form-data; name=\"audio_file\"; filename=\"audio\"",
                                body.split("\r\n")[1]);
                    })
                    .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
            var injectedClient = new WhisperClient("http://localhost", builder.build(), objectMapper);

            injectedClient.transcribe(filename, new byte[]{1});

            server.verify();
        }
    }

    @Test
    void wrapsHttpFailuresFromInjectedClient() {
        for (HttpStatus status : List.of(HttpStatus.BAD_REQUEST, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.FOUND)) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost/asr?task=transcribe&output=json&word_timestamps=true"))
                    .andRespond(withStatus(status));
            var injectedClient = new WhisperClient("http://localhost", builder.build(), objectMapper);

            var exception = assertThrows(UpstreamServiceException.class,
                    () -> injectedClient.transcribe("meeting.wav", new byte[]{1}));

            assertEquals("Whisper", exception.serviceName());
            assertEquals("Whisper returned HTTP " + status.value(), exception.getMessage());
            server.verify();
        }
    }

    @Test
    void wrapsTransportFailuresFromInjectedClient() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://localhost/asr?task=transcribe&output=json&word_timestamps=true"))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });
        var injectedClient = new WhisperClient("http://localhost", builder.build(), objectMapper);

        var exception = assertThrows(UpstreamServiceException.class,
                () -> injectedClient.transcribe("meeting.wav", new byte[]{1}));

        assertEquals("Whisper", exception.serviceName());
        assertEquals("Whisper is unavailable", exception.getMessage());
        assertNotNull(exception.getCause());
        server.verify();
    }
}
