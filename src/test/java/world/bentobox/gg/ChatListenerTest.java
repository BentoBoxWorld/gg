package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.papermc.paper.advancement.AdvancementDisplay;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

class ChatListenerTest extends CommonTestSetup {

    private Transcript transcript;
    private Player other;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        transcript = new Transcript(10);
        other = mock(Player.class);
        online(mockPlayer, other);
    }

    private void online(Player... players) {
        mockedBukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(players));
    }

    private ChatListener listener(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return new ChatListener(transcript, Settings.from(config, k -> null));
    }

    private AsyncChatEvent chat(Component message) {
        return new AsyncChatEvent(true, mockPlayer, Set.of(), mock(ChatRenderer.class), message, message,
                mock(SignedMessage.class));
    }

    @Test
    void recordsChatAsPlainText() throws Exception {
        listener("").onChat(chat(Component.text("gg ", NamedTextColor.GOLD).append(Component.text("steve"))));

        ChatLine line = transcript.snapshot().getFirst();
        assertEquals(uuid, line.uuid());
        assertEquals("tastybento", line.name());
        assertEquals("gg steve", line.text());
    }

    @Test
    void ignoresChatWhenTooFewPlayersAreOnline() throws Exception {
        online(mockPlayer);
        listener("").onChat(chat(Component.text("gg")));
        assertTrue(transcript.snapshot().isEmpty());
    }

    @Test
    void recordsFirstJoinsDifferently() throws Exception {
        ChatListener listener = listener("");
        when(mockPlayer.hasPlayedBefore()).thenReturn(false);
        listener.onJoin(new PlayerJoinEvent(mockPlayer, Component.empty()));
        when(mockPlayer.hasPlayedBefore()).thenReturn(true);
        listener.onJoin(new PlayerJoinEvent(mockPlayer, Component.empty()));

        List<ChatLine> lines = transcript.snapshot();
        assertEquals("tastybento joined the server for the first time", lines.get(0).text());
        assertEquals("tastybento joined the server", lines.get(1).text());
        assertFalse(lines.get(0).isChat());
    }

    @Test
    void recordsAnnouncedAdvancementsOnly() throws Exception {
        ChatListener listener = listener("");
        Advancement advancement = mock(Advancement.class);
        // A recipe unlock has no display.
        listener.onAdvancement(new PlayerAdvancementDoneEvent(mockPlayer, advancement, Component.empty()));
        assertTrue(transcript.snapshot().isEmpty());

        AdvancementDisplay display = mock(AdvancementDisplay.class);
        when(advancement.getDisplay()).thenReturn(display);
        when(display.title()).thenReturn(Component.text("Diamonds!"));
        listener.onAdvancement(new PlayerAdvancementDoneEvent(mockPlayer, advancement, Component.empty()));
        assertTrue(transcript.snapshot().isEmpty());

        when(display.doesAnnounceToChat()).thenReturn(true);
        listener.onAdvancement(new PlayerAdvancementDoneEvent(mockPlayer, advancement, Component.empty()));
        assertEquals("tastybento made the advancement \"Diamonds!\"", transcript.snapshot().getFirst().text());
    }

    @Test
    void contextEventsCanBeTurnedOff() throws Exception {
        ChatListener listener = listener("context:\n  joins: false\n  advancements: false");
        listener.onJoin(new PlayerJoinEvent(mockPlayer, Component.empty()));
        Advancement advancement = mock(Advancement.class);
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        when(advancement.getDisplay()).thenReturn(display);
        when(display.doesAnnounceToChat()).thenReturn(true);
        listener.onAdvancement(new PlayerAdvancementDoneEvent(mockPlayer, advancement, Component.empty()));
        assertTrue(transcript.snapshot().isEmpty());
    }
}
