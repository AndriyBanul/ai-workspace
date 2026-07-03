package com.aiworkspace.documents.models;

import lombok.Builder;

@Builder
public record ExtractedWebPage(String url, String title, String content) {
}
