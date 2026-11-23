package world.bentobox.gg;

import java.util.UUID;

/**
 * One line of the transcript: either something a player said, or something that happened on the server.
 *
 * @param seq  ever-increasing sequence number assigned by the {@link Transcript}
 * @param uuid speaker, or null for a server event
 * @param name speaker's name, or null for a server event
 * @param text sanitised single-line text
 */
public record ChatLine(long seq, UUID uuid, String name, String text) {

    /**
     * @return true if a player said this, false if it is a server event
     */
    public boolean isChat() {
        return uuid != null;
    }
}
