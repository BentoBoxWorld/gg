package world.bentobox.gg;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Reads the LLM's reply. Models wrap JSON in prose or code fences often enough that this looks for the
 * outermost JSON value rather than demanding a clean reply, and it drops any entry that is not exactly the
 * expected shape.
 */
public final class VerdictParser {

    /**
     * What the model claimed: 1-based line number and challenge id. Nothing here is trusted yet.
     */
    public record Verdict(int line, String challengeId) {
    }

    private VerdictParser() {
    }

    /**
     * @param reply raw model output
     * @return the verdicts found, possibly empty
     * @throws JsonParseException if no JSON could be found in the reply at all
     */
    public static List<Verdict> parse(String reply) {
        JsonElement root = JsonParser.parseString(extractJson(reply));
        JsonArray array;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject() && root.getAsJsonObject().get("awards") instanceof JsonArray a) {
            array = a;
        } else {
            return List.of();
        }
        List<Verdict> verdicts = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject o = element.getAsJsonObject();
            JsonElement line = o.get("line");
            JsonElement challenge = o.get("challenge");
            if (isNumber(line) && isString(challenge)) {
                try {
                    verdicts.add(new Verdict(line.getAsBigDecimal().intValueExact(), challenge.getAsString()));
                } catch (ArithmeticException | NumberFormatException e) {
                    // Not a whole number; ignore the entry rather than round it onto someone else's line.
                }
            }
        }
        return verdicts;
    }

    private static boolean isNumber(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber();
    }

    private static boolean isString(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    private static String extractJson(String reply) {
        if (reply == null) {
            throw new JsonParseException("Empty reply");
        }
        int objStart = reply.indexOf('{');
        int arrStart = reply.indexOf('[');
        boolean object = objStart >= 0 && (arrStart < 0 || objStart < arrStart);
        int start = object ? objStart : arrStart;
        int end = reply.lastIndexOf(object ? '}' : ']');
        if (start < 0 || end < start) {
            throw new JsonParseException("No JSON in reply");
        }
        return reply.substring(start, end + 1);
    }
}
