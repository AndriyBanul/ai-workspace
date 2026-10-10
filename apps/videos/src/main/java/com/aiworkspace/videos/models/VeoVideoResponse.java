package com.aiworkspace.videos.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VeoVideoResponse(
        String bytesBase64Encoded,
        @JsonProperty("bytes_base64_encoded") String snakeCaseBytesBase64Encoded,
        String data,
        String mimeType,
        @JsonProperty("mime_type") String snakeCaseMimeType,
        String uri,
        String url
) {
}
