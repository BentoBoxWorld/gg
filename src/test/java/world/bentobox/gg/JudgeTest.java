package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.bentobox.gg.llm.LlmClient;
import world.bentobox.gg.llm.LlmException;

class JudgeTest {

    private static final UUID STEVE = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    private final List<String> prompts = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private Transcript transcript;
    private String reply;
    private boolean fail;
    private Judge judge;

    @BeforeEach
    void setUp() {
        transcript = new Transcript(100);
        LlmClient client = (system, user) -> {
            prompts.add(user);
            if (fail) {
                throw new LlmException("down");
            }
            return reply;
        };
        judge = new Judge(transcript,
                List.of(new Challenge("gg", "congratulates", List.of(), 1),
                        new Challenge("thanks", "says thanks", List.of(), 1)),
                client, warnings::add);
    }

    @Test
    void doesNotCallTheModelWhenNobodyHasSpoken() throws LlmException {
        transcript.addEvent("Steve joined the server");
        assertTrue(judge.run().isEmpty());
        assertTrue(prompts.isEmpty());
    }

    @Test
    void awardsGoToWhoeverWroteTheLine() throws LlmException {
        transcript.addEvent("Steve made the advancement \"Diamonds!\"");
        transcript.addChat(ALEX, "Alex", "gg steve");
        reply = "{\"awards\":[{\"line\":2,\"challenge\":\"gg\"}]}";

        List<Award> awards = judge.run();

        assertEquals(1, awards.size());
        assertEquals(ALEX, awards.getFirst().line().uuid());
        assertEquals("gg", awards.getFirst().challenge().id());
    }

    @Test
    void rejectsVerdictsThatDoNotMatchReality() throws LlmException {
        transcript.addEvent("Steve joined the server");
        transcript.addChat(ALEX, "Alex", "welcome");
        // A server event, an out of range line, line zero, an unknown challenge, and a duplicate.
        reply = "{\"awards\":[{\"line\":1,\"challenge\":\"gg\"},{\"line\":99,\"challenge\":\"gg\"},"
                + "{\"line\":0,\"challenge\":\"gg\"},{\"line\":2,\"challenge\":\"jackpot\"},"
                + "{\"line\":2,\"challenge\":\"gg\"},{\"line\":2,\"challenge\":\"gg\"}]}";

        assertEquals(1, judge.run().size());
    }

    @Test
    void oldLinesAreContextAndCannotBeAwardedAgain() throws LlmException {
        transcript.addChat(ALEX, "Alex", "gg steve");
        reply = "{\"awards\":[{\"line\":1,\"challenge\":\"gg\"}]}";
        assertEquals(1, judge.run().size());

        transcript.addChat(STEVE, "Steve", "thanks");
        // The model wrongly picks the already-judged line 1 again.
        reply = "{\"awards\":[{\"line\":1,\"challenge\":\"gg\"},{\"line\":2,\"challenge\":\"thanks\"}]}";
        List<Award> awards = judge.run();

        assertEquals(1, awards.size());
        assertEquals(STEVE, awards.getFirst().line().uuid());
        assertTrue(prompts.getLast().contains("CONTEXT (already judged, cannot be chosen)\n[1] <Alex> gg steve"));
    }

    @Test
    void aFailedRequestIsRetriedWithTheSameLines() {
        transcript.addChat(ALEX, "Alex", "gg steve");
        fail = true;
        assertThrows(LlmException.class, judge::run);

        fail = false;
        reply = "{\"awards\":[{\"line\":1,\"challenge\":\"gg\"}]}";
        assertEquals(1, assertDoesNotThrowAndGet().size());
    }

    @Test
    void anUnreadableReplyAwardsNothingAndIsNotRetried() throws LlmException {
        transcript.addChat(ALEX, "Alex", "gg steve");
        reply = "I'm sorry, I can't do that.";
        assertTrue(judge.run().isEmpty());
        assertFalse(warnings.isEmpty());

        assertTrue(judge.run().isEmpty());
        assertEquals(1, prompts.size());
    }

    private List<Award> assertDoesNotThrowAndGet() {
        try {
            return judge.run();
        } catch (LlmException e) {
            throw new AssertionError(e);
        }
    }
}
