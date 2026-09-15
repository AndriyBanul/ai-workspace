package com.aiworkspace.documents.services;

import com.aiworkspace.documents.config.DocumentExtractionProperties;
import com.aiworkspace.documents.config.PdfOcrProperties;
import com.aiworkspace.documents.exceptions.DocumentProcessingException;
import com.aiworkspace.documents.models.DocumentBlockType;
import com.aiworkspace.documents.models.DocumentTextBlock;
import com.aiworkspace.images.models.OcrRegion;
import com.aiworkspace.images.models.OcrResult;
import com.aiworkspace.shared.exceptions.UpstreamServiceException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdfOcrFallbackTest {
    private final DocumentExtractionProperties limits = new DocumentExtractionProperties(null, null, null, null, null);

    @Test
    void combinesScannedPagesAndExistingTextWithoutDuplicatingText() throws Exception {
        var calls = new AtomicInteger();
        var fallback = new PdfOcrFallback((image, mime) -> {
            assertEquals("image/png", mime);
            assertTrue(image.length > 0);
            return new OcrResult(List.of(new OcrRegion("Scanned page " + calls.incrementAndGet(), null, null)));
        }, new PdfOcrProperties(true, 5, 72, 1_000_000L), limits);
        var original = List.of(new DocumentTextBlock(1, DocumentBlockType.PARAGRAPH, "Existing text", 2, null, null));
        var result = fallback.apply(pdf(3), original);
        assertEquals(2, calls.get());
        assertEquals(List.of("Scanned page 1", "Existing text", "Scanned page 2"), result.stream().map(DocumentTextBlock::text).toList());
        assertEquals(List.of(1, 2, 3), result.stream().map(DocumentTextBlock::pageNumber).toList());
        assertEquals(List.of(1, 2, 3), result.stream().map(DocumentTextBlock::sequence).toList());
    }

    @Test
    void skipsProviderForTextOnlyOrDisabledOcr() throws Exception {
        var original = List.of(new DocumentTextBlock(1, DocumentBlockType.PARAGRAPH, "Text", 1, null, null));
        var fallback = new PdfOcrFallback((image, mime) -> { fail("Must not call OCR"); return null; },
                new PdfOcrProperties(true, null, null, null), limits);
        assertSame(original, fallback.apply(pdf(1), original));
        var disabled = new PdfOcrFallback((image, mime) -> { fail("Must not call OCR"); return null; },
                new PdfOcrProperties(false, null, null, null), limits);
        assertSame(original, disabled.apply(pdf(2), original));
    }

    @Test
    void rejectsPageLimitBeforeCallingProvider() throws Exception {
        var fallback = new PdfOcrFallback((image, mime) -> { fail("Must not call OCR"); return null; },
                new PdfOcrProperties(true, 1, null, null), limits);
        var bytes = pdf(2);
        assertThrows(DocumentProcessingException.class, () -> fallback.apply(bytes, List.of()));
    }

    @Test
    void rejectsOversizedTextAndPropagatesProviderFailure() throws Exception {
        var bytes = pdf(1);
        var fallback = new PdfOcrFallback((image, mime) -> new OcrResult(List.of(new OcrRegion("X".repeat(100), null, null))),
                new PdfOcrProperties(true, null, null, null), new DocumentExtractionProperties(32, null, null, null, null));
        assertThrows(DocumentProcessingException.class, () -> fallback.apply(bytes, List.of()));
        var failed = new PdfOcrFallback((image, mime) -> { throw new UpstreamServiceException("OCR", "Unavailable"); },
                new PdfOcrProperties(true, null, null, null), limits);
        assertThrows(UpstreamServiceException.class, () -> failed.apply(bytes, List.of()));
    }

    private byte[] pdf(int pages) throws Exception {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) pdf.addPage(new PDPage());
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
