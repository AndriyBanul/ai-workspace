package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    public static List<KnowledgeItem> fuse(List<List<KnowledgeItem>> rankings, int limit, int rankConstant) {
        if (limit <= 0 || rankConstant <= 0) {
            throw new IllegalArgumentException("Fusion limit and rank constant must be positive");
        }
        Map<String, KnowledgeItem> items = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();
        for (List<KnowledgeItem> ranking : rankings) {
            var seen = new HashSet<String>();
            for (int index = 0; index < ranking.size(); index++) {
                KnowledgeItem item = ranking.get(index);
                if (seen.add(item.id())) {
                    items.putIfAbsent(item.id(), item);
                    scores.merge(item.id(), 1.0 / (rankConstant + (double) index + 1), Double::sum);
                }
            }
        }
        // Stable sorting preserves first encounter order when fused scores tie.
        return items.values().stream()
                .sorted((left, right) -> Double.compare(scores.get(right.id()), scores.get(left.id())))
                .limit(limit)
                .toList();
    }
}
