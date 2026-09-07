package com.aiworkspace.documents.services;

import com.aiworkspace.documents.models.ExtractedWebPage;
import com.aiworkspace.documents.models.FetchedWebPage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class WebPageContentExtractor {

    private static final String PARSER_VERSION = parserVersion();

    ExtractedWebPage extract(FetchedWebPage page) throws IOException {
        if (!isHtml(page)) {
            return new ExtractedWebPage(
                    page.url(),
                    page.contentType(),
                    null,
                    decodePlainText(page),
                    Instant.now(),
                    PARSER_VERSION
            );
        }

        Document document = Jsoup.parse(
                new ByteArrayInputStream(page.body()),
                page.charset(),
                page.url()
        );
        return new ExtractedWebPage(
                page.url(),
                page.contentType(),
                document.title(),
                extractMainHtmlContent(document),
                Instant.now(),
                PARSER_VERSION
        );
    }

    private boolean isHtml(FetchedWebPage page) {
        if ("text/html".equalsIgnoreCase(page.contentType())
                || "application/xhtml+xml".equalsIgnoreCase(page.contentType())) {
            return true;
        }
        if (page.contentType() != null) {
            return false;
        }

        byte[] body = page.body();
        String prefix = new String(
                body,
                0,
                Math.min(body.length, 512),
                StandardCharsets.ISO_8859_1
        ).stripLeading().toLowerCase(Locale.ROOT);
        return prefix.contains("<!doctype html")
                || prefix.contains("<html")
                || prefix.contains("<head")
                || prefix.contains("<body")
                || prefix.contains("<article")
                || prefix.contains("<main");
    }

    private String decodePlainText(FetchedWebPage page) {
        byte[] body = page.body();
        Charset charset = page.charset() == null ? charsetFromBom(body) : Charset.forName(page.charset());
        int bomLength = bomLength(body, charset);
        return normalizePlainText(new String(body, bomLength, body.length - bomLength, charset));
    }

    private Charset charsetFromBom(byte[] body) {
        if (body.length >= 2 && body[0] == (byte) 0xff && body[1] == (byte) 0xfe) {
            return StandardCharsets.UTF_16LE;
        }
        if (body.length >= 2 && body[0] == (byte) 0xfe && body[1] == (byte) 0xff) {
            return StandardCharsets.UTF_16BE;
        }
        return StandardCharsets.UTF_8;
    }

    private int bomLength(byte[] body, Charset charset) {
        if (StandardCharsets.UTF_8.equals(charset)
                && body.length >= 3
                && body[0] == (byte) 0xef
                && body[1] == (byte) 0xbb
                && body[2] == (byte) 0xbf) {
            return 3;
        }
        if ((StandardCharsets.UTF_16LE.equals(charset) || StandardCharsets.UTF_16BE.equals(charset))
                && body.length >= 2
                && ((body[0] == (byte) 0xff && body[1] == (byte) 0xfe)
                || (body[0] == (byte) 0xfe && body[1] == (byte) 0xff))) {
            return 2;
        }
        return 0;
    }

    private String normalizePlainText(String content) {
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private String extractMainHtmlContent(Document document) {
        Element contentRoot = mainContentRoot(document);
        if (contentRoot == null) {
            return document.text().trim();
        }

        Element cleanRoot = contentRoot.clone();
        cleanRoot.select("script, style, noscript, template, nav, aside, form").remove();
        if ("body".equals(contentRoot.tagName())) {
            cleanRoot.select("header, footer").remove();
        }

        List<String> blocks = new ArrayList<>();
        for (Element element : cleanRoot.select("h1, h2, h3, h4, h5, h6, p, li, pre, tr")) {
            if ((inside(element, "table") && !"tr".equals(element.tagName()))
                    || ("p".equals(element.tagName()) && inside(element, "li"))) {
                continue;
            }
            String text = htmlBlockText(element);
            if (!text.isBlank() && (blocks.isEmpty() || !text.equals(blocks.getLast()))) {
                blocks.add(text);
            }
        }
        return blocks.isEmpty() ? cleanRoot.text().trim() : String.join("\n\n", blocks);
    }

    private Element mainContentRoot(Document document) {
        for (String selector : List.of("main article", "[role=main] article", "article", "main", "[role=main]")) {
            Element root = document.selectFirst(selector);
            if (root != null) {
                return root;
            }
        }
        return document.body();
    }

    private boolean inside(Element element, String ancestorTag) {
        return element.parents().stream().anyMatch(parent -> ancestorTag.equals(parent.tagName()));
    }

    private String htmlBlockText(Element element) {
        String text;
        if ("tr".equals(element.tagName())) {
            text = element.children().stream()
                    .filter(child -> "th".equals(child.tagName()) || "td".equals(child.tagName()))
                    .map(Element::text)
                    .reduce((left, right) -> left + " | " + right)
                    .orElse("");
        } else if ("li".equals(element.tagName())) {
            Element listItem = element.clone();
            listItem.select("ul, ol").remove();
            text = listItem.text();
        } else {
            text = element.text();
        }
        text = text.replaceAll("[\\p{Z}\\s]+", " ").trim();
        if (element.tagName().matches("h[1-6]")) {
            return "# " + text;
        }
        if ("li".equals(element.tagName())) {
            return "- " + text;
        }
        return text;
    }

    private static String parserVersion() {
        String jsoupVersion = Jsoup.class.getPackage().getImplementationVersion();
        return "ai-workspace-web-extractor-v2/jsoup-" + (jsoupVersion == null ? "unknown" : jsoupVersion);
    }
}
