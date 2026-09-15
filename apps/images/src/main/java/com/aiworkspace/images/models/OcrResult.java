package com.aiworkspace.images.models;

import java.util.List;

public record OcrResult(List<OcrRegion> regions) {
    public OcrResult {
        regions = List.copyOf(regions);
    }

    public String text() {
        return regions.stream().map(OcrRegion::text).collect(java.util.stream.Collectors.joining("\n\n"));
    }
}
