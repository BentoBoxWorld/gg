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
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.util.Util;

public class GgAddon extends Addon implements Listener {
    private Deque<ChatLine> chatBuffer;
    private List<Challenge> challenges;
    private ChatGPTService gpt;
    private BukkitTask pollingTask;
    private Map<UUID, Map<String, LocalDate>> completedToday;
    private boolean checking;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        chatBuffer = new ConcurrentLinkedDeque<>();
        completedToday = new HashMap<>();

        // Load config
        int interval = getConfig().getInt("poll-interval", 60);
        String key  = getConfig().getString("openai-api-key");
        if (key.equals("your-secret-key-here")) {
            this.logError("Set the ChatGPT key in config.yml and then restart the server!");
            this.setState(State.DISABLED);
            return;
        }
        gpt = new ChatGPTService(this, key);

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
        // Check if there are any players online that the player can see
        User user = User.getInstance(e.getPlayer());
        if (Util.getOnlinePlayerList(user).isEmpty()) {
            return;
        }
        // keep only last N
        chatBuffer.addLast(new ChatLine(e.getPlayer().getUniqueId(), e.getPlayer().getName(), e.getMessage()));
        while (chatBuffer.size() > getConfig().getInt("window-size", 100)) {
            chatBuffer.pollFirst();
        }
    }

    private void checkChallenges() {
        if (chatBuffer.isEmpty() || checking)
            return; // no new chat

        checking = true;

        // snapshot current buffer
        List<ChatLine> window = new ArrayList<>(chatBuffer);

        // build payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat", window);
        payload.put("challenges", challenges);

        // ask ChatGPT
        Map<String, List<String>> results = gpt.evaluateChallenges(payload);
        // results: map<challengeId, list of player names>
        ConsoleCommandSender console = getServer().getConsoleSender();
        LocalDate today = LocalDate.now();
        
        results.forEach((id, winners) -> {
            Challenge c = challenges.stream()
                    .filter(ch -> ch.id().equals(id)).findFirst().orElse(null);
            if (c == null) return;
        
            for (String playerName : winners) {
                UUID uuid = this.getPlayers().getUUID(playerName);
                if (uuid == null) {
                    this.logError("Player not found: " + playerName);
                    continue;
                }
                completedToday.putIfAbsent(uuid, new HashMap<>());
                Map<String, LocalDate> doneMap = completedToday.get(uuid);
                if (doneMap.getOrDefault(id, LocalDate.MIN).isEqual(today)) continue;
        
                // run reward commands sync
                Bukkit.getScheduler().runTask(getPlugin(), () ->
                c.commands().forEach(cmd -> Bukkit.dispatchCommand(console, cmd.replace("%player%", playerName))));

                doneMap.put(id, today);
            }
        });
        // clear buffer so we don't double-count
        chatBuffer.clear();
        checking = false;
    }
}
