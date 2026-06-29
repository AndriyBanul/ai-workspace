package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.ParsedTextDocument;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

@Service
public class TextDocumentParserService {

    public ParsedTextDocument parse(String filename, byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);

        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        return new ParsedTextDocument(filename, content);
    }
}
