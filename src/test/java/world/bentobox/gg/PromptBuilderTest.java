package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PromptBuilderTest {

    private static final List<Challenge> CHALLENGES = List
            .of(new Challenge("gg", "congratulates someone", List.of("give %player% diamond 64"), 1));

    @Test
    void separatesContextFromNewLinesAndNumbersByPosition() {
        UUID uuid = UUID.randomUUID();
        List<ChatLine> lines = List.of(new ChatLine(7, uuid, "Alex", "anyone on?"),
                new ChatLine(8, null, null, "Steve made the advancement \"Diamonds!\""),
                new ChatLine(9, uuid, "Alex", "gg steve"));

        assertEquals("""
                CHALLENGES
                - gg: congratulates someone

                CONTEXT (already judged, cannot be chosen)
                [1] <Alex> anyone on?

                NEW (can be chosen)
                [2] * Steve made the advancement "Diamonds!"
                [3] <Alex> gg steve
                """, PromptBuilder.user(CHALLENGES, lines, 7));
    }

    @Test
    void omitsContextSectionWhenEverythingIsNew() {
        List<ChatLine> lines = List.of(new ChatLine(1, UUID.randomUUID(), "Alex", "hi"));
        assertFalse(PromptBuilder.user(CHALLENGES, lines, 0).contains("CONTEXT"));
    }

    @Test
    void rewardCommandsNeverReachTheModel() {
        List<ChatLine> lines = List.of(new ChatLine(1, UUID.randomUUID(), "Alex", "hi"));
        assertFalse(PromptBuilder.user(CHALLENGES, lines, 0).contains("diamond"));
        assertFalse(PromptBuilder.SYSTEM.contains("diamond"));
    }
}
