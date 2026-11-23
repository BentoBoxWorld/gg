package world.bentobox.gg;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Decides whether an {@link Award} is paid, and pays it. Limits live here, in plain code, because they are
 * the part that must hold even when the LLM is fooled. Main server thread only.
 */
public class RewardManager {

    private final int maxRewardsPerDay;
    private final boolean dryRun;
    private final Predicate<UUID> isOnline;
    private final Consumer<String> commandRunner;
    private final Consumer<String> log;
    private final Supplier<LocalDate> today;

    private final Map<UUID, Map<String, Integer>> earned = new HashMap<>();
    private LocalDate day;

    /**
     * @param maxRewardsPerDay most rewards one player can earn per day across all challenges; 0 = no cap
     * @param dryRun           log what would be paid instead of running commands
     * @param isOnline         whether a player is online right now
     * @param commandRunner    runs a console command
     * @param log              info logger
     * @param today            the current date; a new date resets everyone's counts
     */
    public RewardManager(int maxRewardsPerDay, boolean dryRun, Predicate<UUID> isOnline,
            Consumer<String> commandRunner, Consumer<String> log, Supplier<LocalDate> today) {
        this.maxRewardsPerDay = maxRewardsPerDay;
        this.dryRun = dryRun;
        this.isOnline = isOnline;
        this.commandRunner = commandRunner;
        this.log = log;
        this.today = today;
    }

    public void grant(List<Award> awards) {
        awards.forEach(this::grant);
    }

    /**
     * @return true if the player was rewarded (or would have been, in dry-run mode)
     */
    public boolean grant(Award award) {
        LocalDate now = today.get();
        if (!now.equals(day)) {
            day = now;
            earned.clear();
        }
        ChatLine line = award.line();
        Challenge challenge = award.challenge();
        if (!isOnline.test(line.uuid())) {
            return false;
        }
        Map<String, Integer> counts = earned.computeIfAbsent(line.uuid(), k -> new HashMap<>());
        if (counts.getOrDefault(challenge.id(), 0) >= challenge.dailyLimit()) {
            return false;
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (maxRewardsPerDay > 0 && total >= maxRewardsPerDay) {
            return false;
        }
        counts.merge(challenge.id(), 1, Integer::sum);

        log.accept((dryRun ? "[dry run] " : "") + line.name() + " earned '" + challenge.id() + "' for: "
                + line.text());
        if (!dryRun) {
            // The name comes from the server's own record of who spoke, never from the LLM.
            challenge.commands().forEach(cmd -> commandRunner.accept(cmd.replace("%player%", line.name())));
        }
        return true;
    }
}
