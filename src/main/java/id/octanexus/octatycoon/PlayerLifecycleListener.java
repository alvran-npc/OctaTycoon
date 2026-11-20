package id.octanexus.octatycoon;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public final class PlayerLifecycleListener implements Listener {
    private final OctaTycoonPlugin plugin;
    public PlayerLifecycleListener(OctaTycoonPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.markOnline(player.getUniqueId());
        if (!plugin.isReady()) return;
        java.util.concurrent.CompletableFuture.allOf(
                plugin.playerCache().load(player.getUniqueId()),
                plugin.moduleData().load(player.getUniqueId())
        ).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.payService().deliverPending(player.getUniqueId());
        })).exceptionally(error -> {
            plugin.getLogger().warning("Could not load player cache for " + player.getName() + ": " + error.getMessage());
            return null;
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.markOffline(uuid);
        if (plugin.isReady()) {
            java.util.concurrent.CompletableFuture.allOf(
                    plugin.playerCache().flushAndRemove(uuid),
                    plugin.moduleData().flushAndRemove(uuid)
            ).exceptionally(error -> {
                plugin.getLogger().warning("Could not flush cache for " + event.getPlayer().getName() + ": " + error.getMessage());
                return null;
            });
        }
    }
}
