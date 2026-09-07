package com.aiworkspace.documents.models;

import lombok.Builder;

@Builder
public record FetchedWebPage(
        String url,
        String contentType,
        String charset,
        byte[] body
) {

    public FetchedWebPage {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
