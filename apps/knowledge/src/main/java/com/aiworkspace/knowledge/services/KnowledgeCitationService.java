package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSource;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSourceFile;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCitationService {

    private static final int SOURCE_SNIPPET_LENGTH = 500;

    public String buildAnswerContext(String workspaceId, List<KnowledgeItem> items) {
        StringBuilder context = new StringBuilder("Workspace ID: ")
                .append(workspaceId)
                .append("\n\nRelevant source files and evidence:\n");

        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            context.append("\n[")
                    .append(index + 1)
                    .append("] Source file: ")
                    .append(resolveSourceDisplayName(item))
                    .append("\nSource file key: ")
                    .append(buildSourceFileKey(item))
                    .append("\nType: ")
                    .append(item.sourceType().apiName())
                    .append("\nJob ID: ")
                    .append(formatValueOrEmpty(item.jobId()))
                    .append("\nSource ID: ")
                    .append(formatValueOrEmpty(item.sourceId()))
                    .append("\nSource URL: ")
                    .append(formatValueOrEmpty(item.sourceUrl()))
                    .append("\nExtracted at: ")
                    .append(item.extractedAt() == null ? "(empty)" : item.extractedAt())
                    .append("\nParser version: ")
                    .append(formatValueOrEmpty(item.parserVersion()))
                    .append("\nChunk ID: ")
                    .append(formatValueOrEmpty(item.chunkId()))
                    .append("\nChunk sequence: ")
                    .append(item.chunkSequence() == null ? "(empty)" : item.chunkSequence())
                    .append("\nHeading: ")
                    .append(formatValueOrEmpty(item.heading()))
                    .append("\nLocation: ")
                    .append(formatLocation(item))
                    .append("\nSpeaker: ")
                    .append(formatValueOrEmpty(item.speaker()))
                    .append("\nContent:\n")
                    .append(item.content())
                    .append("\n");
        }

        return context.toString();
    }

    public List<WorkspaceKnowledgeSource> mapSources(List<KnowledgeItem> items) {
        return items.stream().map(this::toSource).toList();
    }

    public List<WorkspaceKnowledgeSourceFile> collectSourceFiles(List<KnowledgeItem> items) {
        Map<String, SourceFileAccumulator> sourceFiles = new LinkedHashMap<>();
        for (KnowledgeItem item : items) {
            String sourceFileKey = buildSourceFileKey(item);
            sourceFiles.computeIfAbsent(
                    sourceFileKey,
                    key -> new SourceFileAccumulator(
                            key,
                            resolveSourceDisplayName(item),
                            item.sourceType().apiName(),
                            item.jobId(),
                            item.sourceId(),
                            item.sourceUrl(),
                            item.extractedAt(),
                            item.parserVersion()
                    )
            ).increment();
        }

        return sourceFiles.values().stream().map(SourceFileAccumulator::toSourceFile).toList();
    }

    private WorkspaceKnowledgeSource toSource(KnowledgeItem item) {
        return new WorkspaceKnowledgeSource(
                item.id(), item.sourceType().apiName(), item.sourceName(), item.jobId(), item.sourceId(),
                item.sourceUrl(), item.extractedAt(), item.parserVersion(), item.chunkId(), item.chunkSequence(),
                item.heading(), item.pageNumber(), item.slideNumber(), item.sheetName(), item.startMilliseconds(),
                item.endMilliseconds(), item.speaker(), createSnippet(item.content()), buildSourceFileKey(item)
        );
    }

    private String buildSourceFileKey(KnowledgeItem item) {
        if (item.sourceId() != null && !item.sourceId().isBlank()) {
            return item.sourceType().apiName() + ":" + item.sourceId();
        }
        return item.sourceType().apiName() + ":" + replaceNullWithEmpty(item.sourceName()) + ":" + replaceNullWithEmpty(item.jobId());
    }

    private String resolveSourceDisplayName(KnowledgeItem item) {
        return item.sourceName() == null || item.sourceName().isBlank()
                ? item.sourceType().apiName()
                : item.sourceName();
    }

    private String formatLocation(KnowledgeItem item) {
        if (item.startMilliseconds() != null) {
            String start = formatTimestamp(item.startMilliseconds());
            return item.endMilliseconds() == null ? start : start + " - " + formatTimestamp(item.endMilliseconds());
        }
        if (item.pageNumber() != null) {
            return "page " + item.pageNumber();
        }
        if (item.slideNumber() != null) {
            return "slide " + item.slideNumber();
        }
        if (item.sheetName() != null && !item.sheetName().isBlank()) {
            return "sheet " + item.sheetName();
        }
        return "(empty)";
    }

    private String formatTimestamp(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        long remainder = milliseconds % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, remainder);
    }

    private String createSnippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= SOURCE_SNIPPET_LENGTH ? value : value.substring(0, SOURCE_SNIPPET_LENGTH);
    }

    private String formatValueOrEmpty(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }

    private String replaceNullWithEmpty(String value) {
        return value == null ? "" : value;
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class SourceFileAccumulator {

        private final String key;
        private final String name;
        private final String type;
        private final String jobId;
        private final String sourceId;
        private final String sourceUrl;
        private final Instant extractedAt;
        private final String parserVersion;
        private int sourceCount;

        private void increment() {
            sourceCount++;
        }

        private WorkspaceKnowledgeSourceFile toSourceFile() {
            return new WorkspaceKnowledgeSourceFile(
                    key, name, type, jobId, sourceId, sourceUrl, extractedAt, parserVersion, sourceCount);
        }
    }
}
