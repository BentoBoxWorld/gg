package world.bentobox.gg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(text);
        return config;
    }

    @Test
    void shippedConfigLoadsWithSensibleDefaults() {
        Settings s = Settings.from(YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")),
                k -> null);
        assertEquals(60, s.pollInterval());
        assertEquals(100, s.windowSize());
        assertEquals(2, s.minPlayersOnline());
        assertEquals(5, s.maxRewardsPerDay());
        assertFalse(s.dryRun());
        assertTrue(s.trackJoins());
        assertTrue(s.trackAdvancements());
        assertEquals(5, s.challenges().size());
        Challenge helper = s.challenges().stream().filter(c -> c.id().equals("helper")).findFirst().orElseThrow();
        assertEquals(3, helper.dailyLimit());
        assertEquals(2, helper.commands().size());
    }

    @Test
    void shippedConfigNeedsAModelBeforeItWillRun() {
        Settings s = Settings.from(YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml")),
                k -> null);
        assertEquals(2, s.problems().size());
    }

    @Test
    void aLocalModelNeedsNoKey() throws Exception {
        Settings s = Settings.from(yaml("""
                llm:
                  base-url: http://localhost:11434/v1
                  model: some-model
                challenges:
                  gg:
                    prompt: says gg
                """), k -> null);
        assertTrue(s.problems().isEmpty());
        assertEquals("", s.llm().apiKey());
        assertEquals(1, s.challenges().getFirst().dailyLimit());
    }

    @Test
    void apiKeyFallsBackToEnvironmentThenLegacyKey() throws Exception {
        Map<String, String> env = Map.of(Settings.API_KEY_ENV, "from-env");
        assertEquals("from-config", Settings.from(yaml("llm:\n  api-key: from-config"), env::get).llm().apiKey());
        assertEquals("from-env", Settings.from(yaml("openai-api-key: legacy"), env::get).llm().apiKey());
        assertEquals("legacy", Settings.from(yaml("openai-api-key: legacy"), k -> null).llm().apiKey());
        assertEquals("", Settings.from(yaml("openai-api-key: your-secret-key-here"), k -> null).llm().apiKey());
    }

    @Test
    void curlyQuotesAroundPastedValuesAreRemoved() throws Exception {
        assertEquals("sk-123", Settings.from(yaml("llm:\n  api-key: “sk-123”"), k -> null).llm().apiKey());
    }

    @Test
    void outOfRangeNumbersAreClamped() throws Exception {
        Settings s = Settings.from(yaml("""
                poll-interval: 1
                window-size: 0
                min-players-online: 0
                max-rewards-per-day: -3
                challenges:
                  gg:
                    prompt: says gg
                    daily-limit: 0
                  empty:
                    commands: [say hi]
                """), k -> null);
        assertEquals(10, s.pollInterval());
        assertEquals(1, s.windowSize());
        assertEquals(1, s.minPlayersOnline());
        assertEquals(0, s.maxRewardsPerDay());
        assertEquals(1, s.challenges().size());
        assertEquals(1, s.challenges().getFirst().dailyLimit());
    }

    @Test
    void theKeyIsNeverPrinted() throws Exception {
        Settings s = Settings.from(yaml("llm:\n  api-key: sk-secret"), k -> null);
        assertFalse(s.toString().contains("sk-secret"));
    }
}
