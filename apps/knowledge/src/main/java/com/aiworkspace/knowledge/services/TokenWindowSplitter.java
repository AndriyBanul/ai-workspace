package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.interfaces.EmbeddingTextSplitter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Partitions inference inputs only. Stored sentence chunks are never split or truncated. */
public class TokenWindowSplitter implements EmbeddingTextSplitter {
    private final ToIntFunction<String> tokenCount;
    private final int limit;

    public TokenWindowSplitter(ToIntFunction<String> tokenCount, int limit) {
        this.tokenCount = tokenCount;
        this.limit = limit;
    }

    @Override
    public List<String> split(String prefix, String text) {
        if (tokenCount.applyAsInt(prefix + text) <= limit) return List.of(prefix + text);
        var windows = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            // Work in code points so surrogate pairs are never cut.
            int low = 1;
            int probeEnd = Math.min(text.length(), start + limit * 4);
            if (probeEnd < text.length() && Character.isLowSurrogate(text.charAt(probeEnd))) probeEnd--;
            int high = text.codePointCount(start, probeEnd);
            int accepted = 0;
            while (low <= high) {
                int middle = low + (high - low) / 2;
                int end = text.offsetByCodePoints(start, middle);
                if (tokenCount.applyAsInt(prefix + text.substring(start, end)) <= limit) {
                    accepted = end;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            if (accepted == 0) throw new IllegalStateException("Embedding prefix and one character exceed model capacity");
            windows.add(prefix + text.substring(start, accepted));
            start = accepted;
        }
        return List.copyOf(windows);
    }
}
