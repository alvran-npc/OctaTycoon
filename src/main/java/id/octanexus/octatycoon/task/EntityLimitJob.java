package id.octanexus.octatycoon.task;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.util.List;

public final class EntityLimitJob {
    private final OctaTycoonPlugin plugin;
    public EntityLimitJob(OctaTycoonPlugin plugin) { this.plugin = plugin; }

    public void run(JobContext context) {
        if (!plugin.getConfig().getBoolean("settings.entity-limit.enabled", true)) return;
        int radius = plugin.getConfig().getInt("settings.entity-limit.radius", 16);
        int limit = plugin.getConfig().getInt("settings.entity-limit.max-dropped-items", 120);
        int removals = plugin.getConfig().getInt("settings.entity-limit.max-removals-per-pass", 60);
        var online = plugin.getServer().getOnlinePlayers();
        int baseBatch = Math.max(4, (online.size() + 5) / 6);
        List<Player> selected = plugin.tickBus().slicePlayers("entity-limit", online, baseBatch, context.batchDivisor());
        for (Player player : selected) {
            if (!player.isOnline()) continue;
            List<Item> drops = player.getNearbyEntities(radius, radius, radius).stream()
                    .filter(entity -> entity instanceof Item)
                    .map(entity -> (Item) entity)
                    .toList();
            if (drops.size() <= limit) continue;
            int removed = 0;
            for (Item item : drops) {
                if (removed >= removals) break;
                if (item.isValid() && !plugin.boundItems().isBound(item.getItemStack())) {
                    item.remove();
                    removed++;
                }
            }
        }
    }
}
