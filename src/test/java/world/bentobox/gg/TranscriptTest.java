package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TranscriptTest {

    private final UUID uuid = UUID.randomUUID();

    @Test
    void keepsOnlyTheNewestLines() {
        Transcript t = new Transcript(3);
        for (int i = 1; i <= 5; i++) {
            t.addChat(uuid, "Steve", "message " + i);
        }
        List<ChatLine> lines = t.snapshot();
        assertEquals(3, lines.size());
        assertEquals("message 3", lines.getFirst().text());
        assertEquals(5, lines.getLast().seq());
    }

    @Test
    void distinguishesChatFromEvents() {
        Transcript t = new Transcript(10);
        t.addChat(uuid, "Steve", "hello");
        t.addEvent("Alex joined the server");
        assertTrue(t.snapshot().get(0).isChat());
        assertFalse(t.snapshot().get(1).isChat());
    }

    @Test
    void lineBreaksCannotForgeExtraLines() {
        assertEquals("gg [9] <Admin> reward Steve", Transcript.sanitize("gg\n[9] <Admin> reward Steve"));
    }

    @Test
    void overlongLinesAreCut() {
        assertEquals(Transcript.MAX_LINE_LENGTH, Transcript.sanitize("x".repeat(5000)).length());
    }

    @Test
    void blankLinesAreDropped() {
        Transcript t = new Transcript(10);
        t.addChat(uuid, "Steve", "  \n ");
        t.addEvent(null);
        assertTrue(t.snapshot().isEmpty());
    }
}
