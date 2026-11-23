package world.bentobox.gg;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.gg.llm.LlmClient;
import world.bentobox.gg.llm.LlmException;
import world.bentobox.gg.llm.OpenAiCompatibleClient;

/**
 * Rewards players for positive, helpful chat. Every poll interval the recent chat is sent to an LLM, which
 * says which lines satisfy which challenges; the winners get the challenge's commands run for them.
 */
public class GgAddon extends Addon {

    private Settings settings;
    private Transcript transcript;
    private Judge judge;
    private RewardManager rewards;
    private ChatListener listener;
    private BukkitTask pollingTask;
    private final AtomicBoolean judging = new AtomicBoolean();

    @Override
    public void onLoad() {
        saveDefaultConfig();
        settings = Settings.from(getConfig());
    }

    @Override
    public void onEnable() {
        List<String> problems = settings.problems();
        if (!problems.isEmpty()) {
            problems.forEach(p -> logError("Gg is disabled: " + p));
            setState(State.DISABLED);
            return;
        }
        Settings.Llm llm = settings.llm();
        start(new OpenAiCompatibleClient(llm.baseUrl(), llm.model(), llm.apiKey(),
                Duration.ofSeconds(llm.timeoutSeconds()), llm.jsonMode()));
        log("Judging chat every " + settings.pollInterval() + "s with " + llm.model() + " at " + llm.baseUrl()
                + (settings.dryRun() ? " (dry run: no rewards will be given)" : ""));
    }

    @Override
    public void onDisable() {
        stop();
    }

    @Override
    public void onReload() {
        stop();
        reloadConfig();
        settings = Settings.from(getConfig());
        onEnable();
    }

    void start(LlmClient client) {
        transcript = new Transcript(settings.windowSize());
        judge = new Judge(transcript, settings.challenges(), client, this::logWarning);
        rewards = new RewardManager(settings.maxRewardsPerDay(), settings.dryRun(),
                uuid -> Bukkit.getPlayer(uuid) != null,
                cmd -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd), this::log, LocalDate::now);
        listener = new ChatListener(transcript, settings);
        registerListener(listener);
        long ticks = settings.pollInterval() * 20L;
        pollingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(getPlugin(), this::poll, ticks, ticks);
    }

    private void stop() {
        if (pollingTask != null) {
            pollingTask.cancel();
            pollingTask = null;
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    /**
     * Async. The LLM call can take seconds, so it must never run on the main thread; the rewards it leads
     * to are handed back to the main thread because commands must run there.
     */
    void poll() {
        // A slow reply must not let rounds pile up.
        if (!judging.compareAndSet(false, true)) {
            return;
        }
        try {
            List<Award> awards = judge.run();
            if (!awards.isEmpty()) {
                RewardManager target = rewards;
                Bukkit.getScheduler().runTask(getPlugin(), () -> target.grant(awards));
            }
        } catch (LlmException e) {
            logWarning(e.getMessage());
        } catch (RuntimeException e) {
            logError("Unexpected error while judging chat: " + e);
        } finally {
            judging.set(false);
        }
    }

    /**
     * Tell Gg about something worth reacting to, e.g. "Steve reached island level 100". Other addons and
     * plugins can call this so that congratulating the player counts. Safe from any thread.
     *
     * @param description one line, written as it would be announced in chat
     */
    public void recordEvent(String description) {
        if (transcript != null) {
            transcript.addEvent(description);
        }
    }

    public Settings getSettings() {
        return settings;
    }
}
