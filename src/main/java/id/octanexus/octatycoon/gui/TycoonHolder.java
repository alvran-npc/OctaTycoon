package id.octanexus.octatycoon.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TycoonHolder implements InventoryHolder {
    private final String kind;
    private Inventory inventory;
    public TycoonHolder(String kind) { this.kind = kind; }
    public String kind() { return kind; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
