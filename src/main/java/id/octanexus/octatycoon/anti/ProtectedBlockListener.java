package id.octanexus.octatycoon.anti;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.persistence.PersistentDataType;

/** Protects future generator blocks tagged by GensService using PDC on their tile state. */
public final class ProtectedBlockListener implements Listener {
    private final NamespacedKey ownerKey;
    public ProtectedBlockListener(OctaTycoonPlugin plugin) { ownerKey = new NamespacedKey(plugin, "gens_owner"); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(this::isProtected)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock())) event.setCancelled(true);
    }

    private boolean isProtected(Block block) {
        if (!(block.getState() instanceof TileState state)) return false;
        return state.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }
}
