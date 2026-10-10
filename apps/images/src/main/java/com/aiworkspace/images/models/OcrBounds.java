package com.aiworkspace.images.models;

/** Coordinates normalized to the range 0..1, relative to the input image. */
public record OcrBounds(double x, double y, double width, double height) {
}
