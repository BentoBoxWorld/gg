package world.bentobox.gg;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Feeds the {@link Transcript}: what players say, plus the server events that give those words meaning.
 */
public class ChatListener implements Listener {

    private final Transcript transcript;
    private final Settings settings;

    public ChatListener(Transcript transcript, Settings settings) {
        this.transcript = transcript;
        this.settings = settings;
    }

    /**
     * MONITOR + ignoreCancelled so that messages blocked by a mute or chat filter never count.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        // Talking to an empty server earns nothing.
        if (Bukkit.getOnlinePlayers().size() < settings.minPlayersOnline()) {
            return;
        }
        Player player = e.getPlayer();
        transcript.addChat(player.getUniqueId(), player.getName(),
                PlainTextComponentSerializer.plainText().serialize(e.message()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (settings.trackJoins()) {
            Player player = e.getPlayer();
            transcript.addEvent(player.getName()
                    + (player.hasPlayedBefore() ? " joined the server" : " joined the server for the first time"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        AdvancementDisplay display = e.getAdvancement().getDisplay();
        // No display, or not announced, means a recipe unlock or other hidden bookkeeping advancement.
        if (settings.trackAdvancements() && display != null && display.doesAnnounceToChat()) {
            transcript.addEvent(e.getPlayer().getName() + " made the advancement \""
                    + PlainTextComponentSerializer.plainText().serialize(display.title()) + "\"");
        }
    }
}
