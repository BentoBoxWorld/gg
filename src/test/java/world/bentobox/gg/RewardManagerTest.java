package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RewardManagerTest {

    private static final UUID ALEX = UUID.randomUUID();
    private static final Challenge GG = new Challenge("gg", "p", List.of("give %player% diamond 1", "msg %player% ty"),
            1);
    private static final Challenge HELP = new Challenge("help", "p", List.of("give %player% emerald 1"), 3);

    private final List<String> commands = new ArrayList<>();
    private final List<String> logs = new ArrayList<>();
    private final Set<UUID> online = new HashSet<>();
    private LocalDate today = LocalDate.of(2026, 1, 1);
    private long seq;

    @BeforeEach
    void setUp() {
        online.add(ALEX);
    }

    private RewardManager manager(int maxPerDay, boolean dryRun) {
        return new RewardManager(maxPerDay, dryRun, online::contains, commands::add, logs::add, () -> today);
    }

    private Award award(Challenge challenge) {
        return new Award(new ChatLine(++seq, ALEX, "Alex", "gg"), challenge);
    }

    @Test
    void runsCommandsWithThePlayersName() {
        assertTrue(manager(5, false).grant(award(GG)));
        assertEquals(List.of("give Alex diamond 1", "msg Alex ty"), commands);
    }

    @Test
    void enforcesThePerChallengeDailyLimit() {
        RewardManager rm = manager(0, false);
        assertTrue(rm.grant(award(GG)));
        assertFalse(rm.grant(award(GG)));
        assertTrue(rm.grant(award(HELP)));
        assertTrue(rm.grant(award(HELP)));
        assertTrue(rm.grant(award(HELP)));
        assertFalse(rm.grant(award(HELP)));
    }

    @Test
    void enforcesTheOverallDailyCap() {
        RewardManager rm = manager(2, false);
        assertTrue(rm.grant(award(GG)));
        assertTrue(rm.grant(award(HELP)));
        assertFalse(rm.grant(award(HELP)));
        assertEquals(3, commands.size());
    }

    @Test
    void limitsResetOnANewDay() {
        RewardManager rm = manager(5, false);
        assertTrue(rm.grant(award(GG)));
        assertFalse(rm.grant(award(GG)));
        today = today.plusDays(1);
        assertTrue(rm.grant(award(GG)));
    }

    @Test
    void offlinePlayersAreSkippedWithoutUsingUpTheirLimit() {
        RewardManager rm = manager(5, false);
        online.clear();
        assertFalse(rm.grant(award(GG)));
        online.add(ALEX);
        assertTrue(rm.grant(award(GG)));
    }

    @Test
    void dryRunLogsButRunsNothing() {
        assertTrue(manager(5, true).grant(award(GG)));
        assertTrue(commands.isEmpty());
        assertTrue(logs.getFirst().startsWith("[dry run] Alex earned 'gg'"));
    }
}
