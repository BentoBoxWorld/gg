package world.bentobox.gg;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Everything read from config.yml.
 */
public record Settings(int pollInterval, int windowSize, int minPlayersOnline, int maxRewardsPerDay,
        boolean dryRun, boolean trackJoins, boolean trackAdvancements, Llm llm, List<Challenge> challenges) {

    /** Environment variable used for the API key when config.yml leaves it blank. */
    public static final String API_KEY_ENV = "GG_API_KEY";

    /**
     * How to reach the language model.
     */
    public record Llm(String baseUrl, String model, String apiKey, int timeoutSeconds, boolean jsonMode) {

        /** Never print the key. */
        @Override
        public String toString() {
            return "Llm[baseUrl=" + baseUrl + ", model=" + model + ", apiKey=" + (apiKey.isEmpty() ? "none" : "set")
                    + "]";
        }
    }

    public static Settings from(ConfigurationSection config) {
        return from(config, System::getenv);
    }

    static Settings from(ConfigurationSection config, UnaryOperator<String> env) {
        String apiKey = clean(config.getString("llm.api-key"));
        if (apiKey.isEmpty()) {
            apiKey = clean(env.apply(API_KEY_ENV));
        }
        if (apiKey.isEmpty()) {
            // Key name used before the plugin was made provider-neutral.
            apiKey = clean(config.getString("openai-api-key"));
        }
        Llm llm = new Llm(clean(config.getString("llm.base-url")), clean(config.getString("llm.model")), apiKey,
                Math.max(1, config.getInt("llm.timeout-seconds", 30)), config.getBoolean("llm.json-mode", false));

        List<Challenge> challenges = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("challenges");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String prompt = clean(section.getString(id + ".prompt"));
                if (!prompt.isEmpty()) {
                    challenges.add(new Challenge(id, prompt, section.getStringList(id + ".commands"),
                            Math.max(1, section.getInt(id + ".daily-limit", 1))));
                }
            }
        }

        return new Settings(Math.max(10, config.getInt("poll-interval", 60)),
                Math.max(1, config.getInt("window-size", 100)), Math.max(1, config.getInt("min-players-online", 2)),
                Math.max(0, config.getInt("max-rewards-per-day", 5)), config.getBoolean("dry-run", false),
                config.getBoolean("context.joins", true), config.getBoolean("context.advancements", true), llm,
                List.copyOf(challenges));
    }

    /**
     * @return reasons the addon cannot run with these settings; empty if it can
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (llm.baseUrl().isEmpty()) {
            problems.add("llm.base-url is not set in config.yml");
        }
        if (llm.model().isEmpty()) {
            problems.add("llm.model is not set in config.yml");
        }
        if (challenges.isEmpty()) {
            problems.add("no challenges are defined in config.yml");
        }
        return problems;
    }

    /** Trims, strips the curly quotes that word processors add, and treats the old placeholder as blank. */
    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("“", "").replace("”", "").strip();
        return v.equals("your-secret-key-here") ? "" : v;
    }
}
