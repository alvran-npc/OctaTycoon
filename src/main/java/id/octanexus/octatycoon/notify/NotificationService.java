package id.octanexus.octatycoon.notify;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.config.Language;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationService {
    private final OctaTycoonPlugin plugin;
    private final Map<UUID, NotificationMode> modes = new ConcurrentHashMap<>();
    private final Map<UUID, RecentMessage> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Window> compactWindows = new ConcurrentHashMap<>();

    public NotificationService(OctaTycoonPlugin plugin) { this.plugin = plugin; }

    public NotificationMode mode(UUID uuid) { return modes.getOrDefault(uuid, NotificationMode.NORMAL); }
    public void mode(UUID uuid, NotificationMode mode) { modes.put(uuid, mode); }

    public void send(Player player, String message) {
        boolean error = isError(message);
        NotificationMode mode = mode(player.getUniqueId());
        if (mode == NotificationMode.QUIET && !error) return;
        long now = System.currentTimeMillis();
        if (mode == NotificationMode.NORMAL) {
            long window = plugin.getConfig().getLong("settings.notifications.duplicate-window-seconds", 3) * 1000L;
            RecentMessage previous = lastMessages.get(player.getUniqueId());
            if (previous != null && previous.message.equals(message) && now - previous.time <= window) {
                previous.count++;
                player.sendMessage(message + " §7(x" + previous.count + ")");
                return;
            }
            lastMessages.put(player.getUniqueId(), new RecentMessage(message, now));
        } else if (mode == NotificationMode.COMPACT && !error) {
            long window = plugin.getConfig().getLong("settings.notifications.compact-window-seconds", 3) * 1000L;
            int max = plugin.getConfig().getInt("settings.notifications.compact-max-messages", 6);
            Window state = compactWindows.compute(player.getUniqueId(), (uuid, current) -> {
                if (current == null || now - current.started > window) return new Window(now, 0);
                return current;
            });
            if (state.sent >= max) return;
            state.sent++;
        }
        player.sendMessage(message);
        if (plugin.getConfig().getBoolean("settings.notifications.sound-enabled", true)) {
            try { player.playSound(player.getLocation(), error ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, error ? 0.7f : 1.4f); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    public void info(Player player, String message) { send(player, plugin.lang().prefix() + Language.color(message)); }

    private boolean isError(String message) {
        return message.startsWith("§c") || message.toLowerCase().contains("error") || message.toLowerCase().contains("gagal");
    }

    private static final class RecentMessage {
        private final String message;
        private final long time;
        private int count = 1;
        private RecentMessage(String message, long time) { this.message = message; this.time = time; }
    }
    private static final class Window {
        private final long started;
        private int sent;
        private Window(long started, int sent) { this.started = started; this.sent = sent; }
    }
}
