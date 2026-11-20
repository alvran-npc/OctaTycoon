package id.octanexus.octatycoon.anti;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.gui.TycoonHolder;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiDupeListener implements Listener {
    private final OctaTycoonPlugin plugin;
    private final Map<java.util.UUID, List<ItemStack>> respawnItems = new ConcurrentHashMap<>();
    public AntiDupeListener(OctaTycoonPlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TycoonHolder) return;
        Inventory top = event.getView().getTopInventory();
        boolean boundCurrent = plugin.boundItems().isBound(event.getCurrentItem());
        boolean boundCursor = plugin.boundItems().isBound(event.getCursor());
        boolean wrongCurrentOwner = boundCurrent && !plugin.boundItems().isOwner(event.getCurrentItem(), event.getWhoClicked().getUniqueId());
        boolean wrongCursorOwner = boundCursor && !plugin.boundItems().isOwner(event.getCursor(), event.getWhoClicked().getUniqueId());
        boolean containerOpen = top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER;
        boolean shiftTransfer = event.isShiftClick() && boundCurrent;
        boolean clickingTop = event.getClickedInventory() == top;
        boolean puttingIntoContainer = clickingTop && boundCursor;
        boolean takingBoundFromContainer = containerOpen && clickingTop && boundCurrent;
        // Number-key and offhand swaps move an item without using the cursor.
        boolean hotbarSwapIntoContainer = clickingTop
                && event.getClick().name().equals("NUMBER_KEY")
                && event.getHotbarButton() >= 0
                && plugin.boundItems().isBound(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        boolean offhandSwapIntoContainer = clickingTop
                && event.getClick().name().equals("SWAP_OFFHAND")
                && plugin.boundItems().isBound(event.getWhoClicked().getInventory().getItemInOffHand());
        boolean creativeClone = boundCurrent
                && (event.getAction().name().equals("CLONE_STACK") || event.getClick().isCreativeAction());
        boolean interactingWithBoundInContainer = containerOpen && boundCurrent && event.getClickedInventory() == event.getView().getBottomInventory()
                && (event.isShiftClick() || event.getAction().name().contains("COLLECT"));
        boolean crafting = top.getType() == InventoryType.CRAFTING && event.getClickedInventory() == top && boundCursor;
        boolean nonPlayerGui = top.getType() == InventoryType.ANVIL || top.getType() == InventoryType.SMITHING
                || top.getType() == InventoryType.MERCHANT || top.getType() == InventoryType.WORKBENCH
                || top.getType() == InventoryType.FURNACE || top.getType() == InventoryType.BLAST_FURNACE
                || top.getType() == InventoryType.SMOKER;
        if (wrongCurrentOwner || wrongCursorOwner || shiftTransfer || puttingIntoContainer || takingBoundFromContainer
                || hotbarSwapIntoContainer || offhandSwapIntoContainer || creativeClone
                || interactingWithBoundInContainer || crafting || (nonPlayerGui && (boundCurrent || boundCursor))) {
            event.setCancelled(true);
            audit(event.getWhoClicked().getName(), "BLOCK_INVENTORY_MOVE", "bound=" + plugin.boundItems().itemId(boundCurrent ? event.getCurrentItem() : event.getCursor()) + ", inventory=" + top.getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TycoonHolder) return;
        if (!plugin.boundItems().isBound(event.getOldCursor())) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
            audit(event.getWhoClicked().getName(), "BLOCK_DRAG", "bound item into top inventory");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.boundItems().isBound(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        audit(event.getPlayer().getName(), "BLOCK_DROP", plugin.boundItems().itemId(event.getItemDrop().getItemStack()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) return;
        List<ItemStack> bound = event.getDrops().stream().filter(plugin.boundItems()::isBound).map(ItemStack::clone).toList();
        event.getDrops().removeIf(plugin.boundItems()::isBound);
        if (!bound.isEmpty()) {
            respawnItems.put(event.getEntity().getUniqueId(), bound);
            audit(event.getEntity().getName(), "PRESERVE_BOUND_DEATH_ITEMS", "count=" + bound.size());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        List<ItemStack> bound = respawnItems.remove(event.getPlayer().getUniqueId());
        if (bound == null || bound.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                respawnItems.put(event.getPlayer().getUniqueId(), bound);
                return;
            }
            Map<Integer, ItemStack> leftovers = event.getPlayer().getInventory().addItem(bound.toArray(ItemStack[]::new));
            if (!leftovers.isEmpty()) {
                respawnItems.put(event.getPlayer().getUniqueId(), leftovers.values().stream().map(ItemStack::clone).toList());
                event.getPlayer().sendMessage("§eOctaTycoon: sebagian item terikat ditahan sampai ruang inventori tersedia.");
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (plugin.boundItems().isBound(event.getItem())) {
            event.setCancelled(true);
            audit("SYSTEM", "BLOCK_HOPPER_MOVE", plugin.boundItems().itemId(event.getItem()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        if (plugin.boundItems().isBound(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (plugin.boundItems().isBound(item) && (!(event.getEntity() instanceof Player player) || !plugin.boundItems().isOwner(item, player.getUniqueId()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getInventory().getMatrix().length > 0) {
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (plugin.boundItems().isBound(item)) {
                    event.setCancelled(true);
                    audit(event.getWhoClicked().getName(), "BLOCK_BOUND_CRAFT", "bound item in crafting matrix");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if ((plugin.boundItems().isBound(main) && !plugin.boundItems().isOwner(main, player.getUniqueId()))
                || (plugin.boundItems().isBound(off) && !plugin.boundItems().isOwner(off, player.getUniqueId()))) {
            event.setCancelled(true);
            audit(player.getName(), "BLOCK_WRONG_OWNER_USE", "bound item used by non-owner");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand) && !(event.getRightClicked() instanceof ItemFrame)) return;
        if (plugin.boundItems().isBound(event.getPlayer().getInventory().getItemInMainHand())
                || plugin.boundItems().isBound(event.getPlayer().getInventory().getItemInOffHand())) {
            event.setCancelled(true);
            audit(event.getPlayer().getName(), "BLOCK_DECORATION_INSERT", "bound item interaction");
        }
    }

    private void audit(String actor, String action, String detail) {
        plugin.audit().log(actor, action, detail);
    }
}
