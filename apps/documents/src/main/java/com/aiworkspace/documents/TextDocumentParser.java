package com.aiworkspace.documents;

import java.nio.charset.StandardCharsets;

public class TextDocumentParser {

    public ParsedTextDocument parse(String filename, byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);

        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        return new ParsedTextDocument(filename, content);
    }
}
