package com.aiworkspace.documents.services;

import com.aiworkspace.documents.config.DocumentExtractionProperties;
import com.aiworkspace.documents.config.PdfOcrProperties;
import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentFailureCode;
import com.aiworkspace.documents.models.DocumentTextBlock;
import com.aiworkspace.images.interfaces.ImageOcrProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

@Component
public class PdfOcrFallback {
    private final ImageOcrProvider ocr;
    private final PdfOcrProperties properties;
    private final DocumentExtractionProperties limits;

    public PdfOcrFallback(ImageOcrProvider ocr, PdfOcrProperties properties, DocumentExtractionProperties limits) {
        this.ocr = ocr;
        this.properties = properties;
        this.limits = limits;
    }

    List<DocumentTextBlock> apply(byte[] bytes, List<DocumentTextBlock> original) throws IOException {
        if (!properties.enabled()) return original;
        try (var input = new RandomAccessReadBuffer(bytes);
                var pdf = Loader.loadPDF(input, IOUtils.createTempFileOnlyStreamCache())) {
            var missing = new ArrayList<Integer>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                final int number = page;
                boolean readable = original.stream().filter(block -> Integer.valueOf(number).equals(block.pageNumber()))
                        .anyMatch(block -> block.text().codePoints().anyMatch(Character::isLetterOrDigit));
                if (!readable) missing.add(page);
            }
            if (missing.isEmpty()) return original;
            if (missing.size() > properties.maxPages()) {
                throw new DocumentProcessingException(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED);
            }
            var renderer = new PDFRenderer(pdf);
            renderer.setSubsamplingAllowed(true);
            var blocks = new ArrayList<DocumentTextBlock>();
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                if (missing.contains(page)) {
                    var box = pdf.getPage(page - 1).getCropBox();
                    double area = (double) box.getWidth() * box.getHeight();
                    if (!Double.isFinite(area) || area <= 0) {
                        throw new DocumentProcessingException(DocumentFailureCode.CORRUPT_DOCUMENT);
                    }
                    float scale = (float) Math.min(properties.dpi() / 72.0, Math.sqrt(properties.maxPixels() / area));
                    // Bound extreme aspect ratios as well as the total pixel count.
                    scale = Math.min(scale, 4096f / Math.max(box.getWidth(), box.getHeight()));
                    var image = renderer.renderImage(page - 1, scale);
                    try (var output = new ByteArrayOutputStream()) {
                        ImageIO.write(image, "png", output);
                        var result = ocr.recognize(output.toByteArray(), "image/png");
                        for (var region : result.regions()) {
                            blocks.add(new DocumentTextBlock(blocks.size() + 1, DocumentBlockType.PARAGRAPH,
                                    region.text(), page, null, null));
                        }
                    } finally {
                        image.flush();
                    }
                } else {
                    for (var block : original) {
                        if (Integer.valueOf(page).equals(block.pageNumber())) {
                            blocks.add(new DocumentTextBlock(blocks.size() + 1, block.type(), block.text(), page,
                                    block.slideNumber(), block.sheetName()));
                        }
                    }
                }
                if (blocks.size() > limits.maxExtractedBlocks()) {
                    throw new DocumentProcessingException(DocumentFailureCode.EXTRACTION_LIMIT_EXCEEDED);
                }
                DocumentStructuredTextRenderer.render(blocks, limits.maxExtractedCharacters());
            }
            return List.copyOf(blocks);
        }
    }
}
