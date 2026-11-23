package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import world.bentobox.bentobox.api.addons.Addon.State;
import world.bentobox.bentobox.api.addons.AddonDescription;
import world.bentobox.bentobox.managers.AddonsManager;

class GgAddonTest extends CommonTestSetup {

    private static final String CONFIGURED = """
            poll-interval: 30
            llm:
              base-url: http://localhost:1/v1
              model: test-model
            challenges:
              goodgame:
                prompt: congratulates another player
                commands:
                  - give %player% diamond 1
            """;

    @Mock
    private AddonsManager am;
    @Mock
    private BukkitTask task;

    private GgAddon addon;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        when(plugin.getAddonsManager()).thenReturn(am);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(sch.runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        addon = new GgAddon();
        File jar = new File("addon.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar))) {
            jos.putNextEntry(new JarEntry("config.yml"));
            jos.write(Files.readAllBytes(Path.of("src/main/resources/config.yml")));
            jos.closeEntry();
        }
        addon.setDataFolder(new File("addons/Gg"));
        addon.setFile(jar);
        addon.setDescription(
                new AddonDescription.Builder("bentobox", "Gg", "1.0.0").description("test").authors("tastybento").build());
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        Files.deleteIfExists(Path.of("addon.jar"));
        deleteAll(new File("addons"));
    }

    /** Overwrites the saved config, as a server owner would after first start. */
    private void configure() throws Exception {
        addon.onLoad();
        Files.writeString(Path.of("addons/Gg/config.yml"), CONFIGURED, StandardCharsets.UTF_8);
        addon.onReload();
    }

    @Test
    void settingsAreNullBeforeLoad() {
        assertNull(addon.getSettings());
    }

    @Test
    void onLoadReadsTheDefaultConfig() {
        addon.onLoad();
        assertNotNull(addon.getSettings());
        assertEquals(60, addon.getSettings().pollInterval());
        assertEquals(5, addon.getSettings().challenges().size());
    }

    @Test
    void anUnconfiguredAddonDisablesItselfInsteadOfPolling() {
        addon.onLoad();
        addon.setState(State.LOADED);
        addon.onEnable();

        assertEquals(State.DISABLED, addon.getState());
        verify(sch, never()).runTaskTimerAsynchronously(any(), any(Runnable.class), anyLong(), anyLong());
    }

    @Test
    void aConfiguredAddonSchedulesPollingAtTheConfiguredInterval() throws Exception {
        configure();
        assertEquals("test-model", addon.getSettings().llm().model());
        verify(sch).runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(600L), eq(600L));
    }

    @Test
    void disableCancelsPolling() throws Exception {
        configure();
        addon.onDisable();
        verify(task).cancel();
        assertDoesNotThrow(addon::onDisable);
    }

    @Test
    void disableBeforeEnableIsHarmless() {
        assertDoesNotThrow(addon::onDisable);
        assertDoesNotThrow(() -> addon.recordEvent("ignored"));
    }

    @Test
    void chatIsJudgedAndTheWinnerRewardedOnTheMainThread() throws Exception {
        configure();
        // Swap in a model that always picks line 2.
        addon.onDisable();
        addon.start((system, user) -> "{\"awards\":[{\"line\":2,\"challenge\":\"goodgame\"}]}");

        ArgumentCaptor<org.bukkit.event.Listener> listeners = ArgumentCaptor.forClass(org.bukkit.event.Listener.class);
        verify(am, org.mockito.Mockito.atLeastOnce()).registerListener(eq(addon), listeners.capture());
        ChatListener listener = (ChatListener) listeners.getValue();

        mockedBukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(mockPlayer, mock(Player.class)));
        mockedBukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(mockPlayer);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        mockedBukkit.when(Bukkit::getConsoleSender).thenReturn(console);

        addon.recordEvent("Alex made the advancement \"Diamonds!\"");
        Component message = Component.text("gg alex");
        listener.onChat(new AsyncChatEvent(true, mockPlayer, java.util.Set.of(), mock(ChatRenderer.class), message,
                message, mock(SignedMessage.class)));

        addon.poll();

        // Nothing is paid from the async thread; the grant is queued for the main thread.
        mockedBukkit.verify(() -> Bukkit.dispatchCommand(any(), any()), never());
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        verify(sch).runTask(eq(plugin), queued.capture());
        queued.getValue().run();
        mockedBukkit.verify(() -> Bukkit.dispatchCommand(console, "give tastybento diamond 1"));
    }

    @Test
    void theSameAddonInstanceIsReturnedByThePladdon() {
        GgPladdon pladdon = mock(GgPladdon.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        assertSame(pladdon.getAddon(), pladdon.getAddon());
    }
}
