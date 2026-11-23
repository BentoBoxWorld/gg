package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParseException;

import world.bentobox.gg.VerdictParser.Verdict;

class VerdictParserTest {

    @Test
    void parsesTheRequestedShape() {
        assertEquals(List.of(new Verdict(3, "gg"), new Verdict(5, "thanks")), VerdictParser
                .parse("{\"awards\":[{\"line\":3,\"challenge\":\"gg\"},{\"line\":5,\"challenge\":\"thanks\"}]}"));
    }

    @Test
    void parsesEmptyAwards() {
        assertTrue(VerdictParser.parse("{\"awards\":[]}").isEmpty());
    }

    @Test
    void toleratesCodeFencesAndChatter() {
        String reply = "Sure! Here you go:\n```json\n{\"awards\":[{\"line\":2,\"challenge\":\"gg\"}]}\n```\nHope that helps.";
        assertEquals(List.of(new Verdict(2, "gg")), VerdictParser.parse(reply));
    }

    @Test
    void toleratesABareArray() {
        assertEquals(List.of(new Verdict(2, "gg")), VerdictParser.parse("[{\"line\":2,\"challenge\":\"gg\"}]"));
    }

    @Test
    void skipsEntriesOfTheWrongShape() {
        String reply = "{\"awards\":[{\"line\":\"3\",\"challenge\":\"gg\"},{\"line\":1.5,\"challenge\":\"gg\"},"
                + "{\"challenge\":\"gg\"},{\"line\":4,\"challenge\":7},\"gg\",null,{\"line\":2,\"challenge\":\"gg\"}]}";
        assertEquals(List.of(new Verdict(2, "gg")), VerdictParser.parse(reply));
    }

    @Test
    void unrelatedJsonMeansNoAwards() {
        assertTrue(VerdictParser.parse("{\"error\":\"nope\"}").isEmpty());
        assertTrue(VerdictParser.parse("{\"awards\":\"all of them\"}").isEmpty());
    }

    @Test
    void noJsonAtAllIsAnError() {
        assertThrows(JsonParseException.class, () -> VerdictParser.parse("I cannot help with that."));
        assertThrows(JsonParseException.class, () -> VerdictParser.parse(null));
        assertThrows(JsonParseException.class, () -> VerdictParser.parse("{\"awards\":[{\"line\":"));
    }
}
