package world.bentobox.gg;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Rolling window of recent chat and server events. Chat arrives on async threads and is read by the polling
 * task, so every method is synchronized.
 */
public class Transcript {

    /** Minecraft caps chat at 256 characters; anything longer did not come from a vanilla client. */
    static final int MAX_LINE_LENGTH = 256;

    private final Deque<ChatLine> lines = new ArrayDeque<>();
    private final int windowSize;
    private long nextSeq = 1;

    public Transcript(int windowSize) {
        this.windowSize = Math.max(1, windowSize);
    }

    /**
     * Record something a player said.
     */
    public synchronized void addChat(UUID uuid, String name, String message) {
        add(new ChatLine(nextSeq++, uuid, name, sanitize(message)));
    }

    /**
     * Record something that happened, e.g. "Steve joined the server for the first time". Events give the
     * LLM the context it needs to tell a deserved "gg" from a random one. They can never earn a reward.
     */
    public synchronized void addEvent(String description) {
        add(new ChatLine(nextSeq++, null, null, sanitize(description)));
    }

    /**
     * @return an immutable copy of the window, oldest first
     */
    public synchronized List<ChatLine> snapshot() {
        return List.copyOf(lines);
    }

    private void add(ChatLine line) {
        if (line.text().isEmpty()) {
            return;
        }
        lines.addLast(line);
        while (lines.size() > windowSize) {
            lines.pollFirst();
        }
    }

    /**
     * Forces text onto one bounded line. Without this a player could paste line breaks and forge extra
     * transcript lines that appear to come from someone else.
     */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").strip();
        return oneLine.length() > MAX_LINE_LENGTH ? oneLine.substring(0, MAX_LINE_LENGTH) : oneLine;
    }
}
