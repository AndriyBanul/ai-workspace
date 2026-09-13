package com.aiworkspace.knowledge.services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenWindowSplitterTest {
    @Test
    void coversEveryCodePointWithoutTruncationOrBreakingSurrogatePairs() {
        var splitter = new TokenWindowSplitter(s -> s.codePointCount(0, s.length()), 12);
        String text = "A😀B ".repeat(20);
        var parts = splitter.split("query: ", text);
        assertTrue(parts.size() > 1);
        assertTrue(parts.stream().allMatch(s -> s.codePointCount(0, s.length()) <= 12));
        assertEquals(text, parts.stream().map(s -> s.substring(7)).reduce("", String::concat));
    }
}
