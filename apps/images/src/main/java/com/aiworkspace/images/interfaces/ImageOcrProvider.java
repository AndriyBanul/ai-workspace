package com.aiworkspace.images.interfaces;

import com.aiworkspace.images.models.OcrResult;

public interface ImageOcrProvider {
    OcrResult recognize(byte[] image, String mimeType);
}
