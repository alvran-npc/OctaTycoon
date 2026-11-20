package id.octanexus.octatycoon.gui;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.config.Language;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class GuiService implements Listener {
    private final OctaTycoonPlugin plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, ClickState> clickStates = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<Player, String>> routers = new ConcurrentHashMap<>();

    public GuiService(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
        actionKey = new NamespacedKey(plugin, "gui_action");
    }

    public Inventory open(Player player, int rows, String title, String kind) {
        int safeRows = Math.max(1, Math.min(6, rows));
        TycoonHolder holder = new TycoonHolder(kind);
        String prefix = Language.color(plugin.getConfig().getString("settings.gui.title-prefix", ""));
        String prefixedTitle = prefix + Language.color(title);
        Inventory inventory = Bukkit.createInventory(holder, safeRows * 9, prefixedTitle);
        holder.inventory(inventory);
        player.openInventory(inventory);
        return inventory;
    }

    public void set(Inventory inventory, int slot, ItemStack item, String action) {
        if (slot < 0 || slot >= inventory.getSize()) throw new IndexOutOfBoundsException("GUI slot " + slot);
        ItemStack copy = item == null ? null : item.clone();
        if (copy != null && action != null && !action.isBlank()) {
            ItemMeta meta = copy.getItemMeta();
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            copy.setItemMeta(meta);
        }
        inventory.setItem(slot, copy);
    }

    public void frame(Inventory inventory, Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName("§r");
        pane.setItemMeta(meta);
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (slot < 9 || slot >= size - 9 || slot % 9 == 0 || slot % 9 == 8) {
                if (inventory.getItem(slot) == null) inventory.setItem(slot, pane);
            }
        }
    }

    public void registerRouter(String module, BiConsumer<Player, String> router) {
        routers.put(module.toLowerCase(), router);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof TycoonHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getClick().isShiftClick() || event.getClick().isKeyboardClick() || event.getClick().isCreativeAction()) return;
        if (event.getSlot() < 0 || event.getSlot() >= event.getView().getTopInventory().getSize()) return;
        if (!allowClick(player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.isBlank()) return;
        String[] parts = action.split(":", 3);
        if (parts.length < 2) {
            plugin.getLogger().warning("Invalid GUI action: " + action);
            return;
        }
        BiConsumer<Player, String> router = routers.get(parts[0].toLowerCase());
        if (router == null) {
            plugin.getLogger().warning("No GUI action router registered for module " + parts[0]);
            return;
        }
        String payload = parts[1] + (parts.length == 3 ? ":" + parts[2] : "");
        try { router.accept(player, payload); }
        catch (Throwable throwable) { plugin.getLogger().severe("GUI action failed: " + action + " - " + throwable.getMessage()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TycoonHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        clickStates.remove(event.getPlayer().getUniqueId());
    }

    private boolean allowClick(Player player) {
        long now = System.nanoTime();
        boolean bedrock = plugin.isBedrock(player);
        int defaultTicks = plugin.getConfig().getInt(bedrock ? "settings.gui.bedrock-click-cooldown-ticks" : "settings.gui.default-click-cooldown-ticks", 4);
        ClickState state = clickStates.computeIfAbsent(player.getUniqueId(), ignored -> new ClickState());
        long elapsed = now - state.lastClick;
        int cooldownTicks = Math.max(3, defaultTicks);
        if (state.lastClick != 0 && elapsed < 800_000_000L) {
            if (elapsed < 200_000_000L) state.tier = 2;
            else if (elapsed < 400_000_000L) state.tier = Math.max(state.tier, 1);
            else state.tier = 0;
            cooldownTicks = switch (state.tier) { case 2 -> 20; case 1 -> 8; default -> cooldownTicks; };
        } else state.tier = 0;
        if (state.lastClick != 0 && elapsed < cooldownTicks * 50_000_000L) return false;
        state.lastClick = now;
        return true;
    }

    private static final class ClickState { private long lastClick; private int tier; }
}
