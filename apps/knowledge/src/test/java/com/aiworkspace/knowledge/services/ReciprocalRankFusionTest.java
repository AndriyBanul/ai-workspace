package com.aiworkspace.knowledge.services;

import com.aiworkspace.knowledge.models.KnowledgeItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReciprocalRankFusionTest {

    @Test
    void promotesEvidenceSupportedBySeveralListsBeforeApplyingLimit() {
        var evidence = item("evidence");
        var fused = ReciprocalRankFusion.fuse(List.of(
                List.of(item("lexical-first"), evidence),
                List.of(item("vector-first"), evidence),
                List.of(item("rewrite-first"), evidence)
        ), 1, 60);

        assertEquals(List.of(evidence), fused);
    }

    @Test
    void preservesFirstEncounterOrderForTiedScores() {
        var first = item("first");
        var second = item("second");

        assertEquals(List.of(first, second), ReciprocalRankFusion.fuse(
                List.of(List.of(first, second), List.of(second, first)), 12, 60));
    }

    @Test
    void countsEachDocumentOnlyOncePerList() {
        var repeated = item("repeated");
        var supported = item("supported");

        assertEquals(List.of(supported, repeated), ReciprocalRankFusion.fuse(
                List.of(List.of(repeated, repeated, repeated), List.of(supported), List.of(supported)), 12, 60));
    }

    @Test
    void handlesEmptyListsAndRejectsInvalidBudgets() {
        assertEquals(List.of(), ReciprocalRankFusion.fuse(List.of(List.of()), 12, 60));
        assertThrows(IllegalArgumentException.class, () -> ReciprocalRankFusion.fuse(List.of(), 0, 60));
        assertThrows(IllegalArgumentException.class, () -> ReciprocalRankFusion.fuse(List.of(), 12, 0));
    }

    @Test
    void smallerRankConstantPreservesStrongMatchesAgainstRepeatedModerateMatches() {
        var evidence = item("evidence");
        var rankings = new ArrayList<List<KnowledgeItem>>();
        for (int list = 0; list < 6; list++) {
            var ranking = new ArrayList<KnowledgeItem>();
            for (int rank = 0; rank < 35; rank++) {
                ranking.add(list < 3 && rank == 2 ? evidence : item("list-" + list + "-" + rank));
            }
            for (int rank = 0; rank < 20; rank++) {
                ranking.add(item("moderate-" + rank));
            }
            rankings.add(ranking);
        }

        assertTrue(ReciprocalRankFusion.fuse(rankings, 12, 30).contains(evidence));
        assertFalse(ReciprocalRankFusion.fuse(rankings, 12, 60).contains(evidence));
    }

    private KnowledgeItem item(String id) {
        return KnowledgeItem.builder().id(id).build();
    }
}
