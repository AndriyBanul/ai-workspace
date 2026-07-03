package com.aiworkspace.documents.models;

import lombok.Builder;

@Builder
public record FetchedWebPage(String url, String html) {
}
