package id.octanexus.octatycoon.anti;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public final class BoundItemService {
    private final NamespacedKey itemIdKey;
    private final NamespacedKey ownerKey;

    public BoundItemService(OctaTycoonPlugin plugin) {
        itemIdKey = new NamespacedKey(plugin, "item_id");
        ownerKey = new NamespacedKey(plugin, "owner_uuid");
    }

    public ItemStack bind(ItemStack item, String itemId, UUID owner) {
        if (item == null || item.getType().isAir()) throw new IllegalArgumentException("Cannot bind an empty item");
        if (owner == null) throw new IllegalArgumentException("Bound items must have an owner UUID");
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
        if (owner != null) meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        copy.setItemMeta(meta);
        return copy;
    }

    public boolean isBound(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(itemIdKey, PersistentDataType.STRING);
    }

    public String itemId(ItemStack item) {
        if (!isBound(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    public UUID owner(ItemStack item) {
        if (!isBound(item)) return null;
        String value = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public boolean isOwner(ItemStack item, UUID player) {
        if (!isBound(item)) return true;
        var container = item.getItemMeta().getPersistentDataContainer();
        if (!container.has(ownerKey, PersistentDataType.STRING)) return false;
        String value = container.get(ownerKey, PersistentDataType.STRING);
        if (value == null) return false;
        try { return UUID.fromString(value).equals(player); }
        catch (IllegalArgumentException ignored) { return false; }
    }
}
