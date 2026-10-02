package com.aiworkspace.images.models;

/** Optional confidence and bounds are provider estimates, not calibrated guarantees. */
public record OcrRegion(String text, Double confidence, Bounds bounds) {
    /** Coordinates normalized to the range 0..1, relative to the input image. */
    public record Bounds(double x, double y, double width, double height) {}
}
