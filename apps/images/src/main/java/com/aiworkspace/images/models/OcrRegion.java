package com.aiworkspace.images.models;

/** Optional confidence and bounds are provider estimates, not calibrated guarantees. */
public record OcrRegion(String text, Double confidence, OcrBounds bounds) {
}
