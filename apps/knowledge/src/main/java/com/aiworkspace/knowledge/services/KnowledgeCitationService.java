package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSource;
import com.aiworkspace.knowledge.models.WorkspaceKnowledgeSourceFile;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCitationService {

    private static final int SOURCE_SNIPPET_LENGTH = 500;

    public String answerContext(String workspaceId, List<KnowledgeItem> items) {
        StringBuilder context = new StringBuilder("Workspace ID: ")
                .append(workspaceId)
                .append("\n\nRelevant source files and evidence:\n");

        for (int index = 0; index < items.size(); index++) {
            KnowledgeItem item = items.get(index);
            context.append("\n[")
                    .append(index + 1)
                    .append("] Source file: ")
                    .append(sourceDisplayName(item))
                    .append("\nSource file key: ")
                    .append(sourceFileKey(item))
                    .append("\nType: ")
                    .append(item.sourceType().apiName())
                    .append("\nJob ID: ")
                    .append(valueOrEmpty(item.jobId()))
                    .append("\nSource ID: ")
                    .append(valueOrEmpty(item.sourceId()))
                    .append("\nSource URL: ")
                    .append(valueOrEmpty(item.sourceUrl()))
                    .append("\nExtracted at: ")
                    .append(item.extractedAt() == null ? "(empty)" : item.extractedAt())
                    .append("\nParser version: ")
                    .append(valueOrEmpty(item.parserVersion()))
                    .append("\nChunk ID: ")
                    .append(valueOrEmpty(item.chunkId()))
                    .append("\nChunk sequence: ")
                    .append(item.chunkSequence() == null ? "(empty)" : item.chunkSequence())
                    .append("\nHeading: ")
                    .append(valueOrEmpty(item.heading()))
                    .append("\nLocation: ")
                    .append(location(item))
                    .append("\nSpeaker: ")
                    .append(valueOrEmpty(item.speaker()))
                    .append("\nContent:\n")
                    .append(item.content())
                    .append("\n");
        }

        return context.toString();
    }

    public List<WorkspaceKnowledgeSource> sources(List<KnowledgeItem> items) {
        return items.stream().map(this::source).toList();
    }

    public List<WorkspaceKnowledgeSourceFile> sourceFiles(List<KnowledgeItem> items) {
        Map<String, SourceFileAccumulator> sourceFiles = new LinkedHashMap<>();
        for (KnowledgeItem item : items) {
            String sourceFileKey = sourceFileKey(item);
            sourceFiles.computeIfAbsent(
                    sourceFileKey,
                    key -> new SourceFileAccumulator(
                            key,
                            sourceDisplayName(item),
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

    private WorkspaceKnowledgeSource source(KnowledgeItem item) {
        return new WorkspaceKnowledgeSource(
                item.id(), item.sourceType().apiName(), item.sourceName(), item.jobId(), item.sourceId(),
                item.sourceUrl(), item.extractedAt(), item.parserVersion(), item.chunkId(), item.chunkSequence(),
                item.heading(), item.pageNumber(), item.slideNumber(), item.sheetName(), item.startMilliseconds(),
                item.endMilliseconds(), item.speaker(), snippet(item.content()), sourceFileKey(item)
        );
    }

    private String sourceFileKey(KnowledgeItem item) {
        if (item.sourceId() != null && !item.sourceId().isBlank()) {
            return item.sourceType().apiName() + ":" + item.sourceId();
        }
        return item.sourceType().apiName() + ":" + nullToEmpty(item.sourceName()) + ":" + nullToEmpty(item.jobId());
    }

    private String sourceDisplayName(KnowledgeItem item) {
        return item.sourceName() == null || item.sourceName().isBlank()
                ? item.sourceType().apiName()
                : item.sourceName();
    }

    private String location(KnowledgeItem item) {
        if (item.startMilliseconds() != null) {
            String start = timestamp(item.startMilliseconds());
            return item.endMilliseconds() == null ? start : start + " - " + timestamp(item.endMilliseconds());
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

    private String timestamp(long milliseconds) {
        long totalSeconds = milliseconds / 1000;
        long hours = totalSeconds / 3600;
        long minutes = totalSeconds % 3600 / 60;
        long seconds = totalSeconds % 60;
        long remainder = milliseconds % 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d", hours, minutes, seconds, remainder);
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= SOURCE_SNIPPET_LENGTH ? value : value.substring(0, SOURCE_SNIPPET_LENGTH);
    }

    private String valueOrEmpty(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

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

        private SourceFileAccumulator(String key, String name, String type, String jobId, String sourceId,
                String sourceUrl, Instant extractedAt, String parserVersion) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.jobId = jobId;
            this.sourceId = sourceId;
            this.sourceUrl = sourceUrl;
            this.extractedAt = extractedAt;
            this.parserVersion = parserVersion;
        }

        private void increment() {
            sourceCount++;
        }

        private WorkspaceKnowledgeSourceFile toSourceFile() {
            return new WorkspaceKnowledgeSourceFile(
                    key, name, type, jobId, sourceId, sourceUrl, extractedAt, parserVersion, sourceCount);
        }
    }
}
