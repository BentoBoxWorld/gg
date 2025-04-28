package world.bentobox.gg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitTask;

import world.bentobox.bentobox.api.addons.Addon;

public class GgAddon extends Addon implements Listener {
    private Deque<ChatLine> chatBuffer;
    private List<Challenge> challenges;
    private ChatGPTService gpt;
    private BukkitTask pollingTask;
    private Map<UUID, Map<String, LocalDate>> completedToday;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chatBuffer = new ConcurrentLinkedDeque<>();
        completedToday = new HashMap<>();

        // Load config
        int interval = getConfig().getInt("poll-interval", 60);
        int window  = getConfig().getInt("window-size", 100);
        String key  = getConfig().getString("openai-api-key");
        gpt = new ChatGPTService(key);

        // Load challenges
        challenges = new ArrayList<>();
        getConfig().getConfigurationSection("challenges").getKeys(false)
            .forEach(id -> {
                String prompt = getConfig().getString("challenges." + id + ".prompt");
                List<String> cmds = getConfig().getStringList("challenges." + id + ".commands");
                challenges.add(new Challenge(id, prompt, cmds));
            });

        // Register chat listener
        this.registerListener(this);

        // Schedule polling task
        pollingTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                getPlugin(),
            this::checkChallenges,
            interval * 20L,  // delay
            interval * 20L   // period
        );
    }

    @Override
    public void onDisable() {
        if (pollingTask != null) pollingTask.cancel();
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent e) {
        // keep only last N
        chatBuffer.addLast(new ChatLine(e.getPlayer().getUniqueId(), e.getPlayer().getName(), e.getMessage()));
        while (chatBuffer.size() > getConfig().getInt("window-size", 100)) {
            chatBuffer.pollFirst();
        }
    }

    private void checkChallenges() {
        if (chatBuffer.isEmpty()) return;  // no new chat

        // snapshot current buffer
        List<ChatLine> window = new ArrayList<>(chatBuffer);

        // build payload
        Map<String,Object> payload = new HashMap<>();
        payload.put("chat", window);
        payload.put("challenges", challenges);

        // ask ChatGPT
        Map<String,List<String>> results = gpt.evaluateChallenges(payload);
        // results: map<challengeId, list of player names>

        ConsoleCommandSender console = getServer().getConsoleSender();
        LocalDate today = LocalDate.now();

        results.forEach((id, winners) -> {
            Challenge c = challenges.stream()
                                    .filter(ch -> ch.getId().equals(id))
                                    .findFirst().orElse(null);
            if (c == null) return;

            for (String playerName : winners) {
                Bukkit.getPlayerExact(playerName).getUniqueId(); // lookup UUID if needed
                // only once per day
                UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
                completedToday.putIfAbsent(uuid, new HashMap<>());
                Map<String, LocalDate> doneMap = completedToday.get(uuid);
                if (doneMap.getOrDefault(id, LocalDate.MIN).isEqual(today)) continue;

                // run reward commands
                c.getCommands().forEach(cmd ->
                    Bukkit.dispatchCommand(console, cmd.replace("%player%", playerName))
                );
                doneMap.put(id, today);
            }
        });

        // clear buffer so we don't double-count
        chatBuffer.clear();
    }
}
