package id.octanexus.octatycoon.admin;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class TestAdminCommand implements CommandExecutor {
    private final OctaTycoonPlugin plugin;

    public TestAdminCommand(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
        plugin.guiService().registerRouter("admin", this::route);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("octatycoon.admin.core")) {
            sender.sendMessage(plugin.lang().prefix() + plugin.lang().get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.lang().get("player-only"));
            return true;
        }
        if (!plugin.isReady()) {
            player.sendMessage("§eOctaTycoon masih menyiapkan database.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Inventory inventory = plugin.guiService().open(player, 3, "§8OctaTycoon §7» §bCore Test", "admin-test");
        plugin.guiService().frame(inventory, Material.GRAY_STAINED_GLASS_PANE);
        set(inventory, 10, Material.COMPASS, "§bStatus Core", "§7Lihat TPS Guard dan job", "admin:status");
        set(inventory, 11, Material.GREEN_WOOL, "§aGuard Otomatis", "§7Kembali ke mode otomatis", "admin:guard:auto");
        set(inventory, 12, Material.ORANGE_WOOL, "§6Paksa Guard Oranye", "§7Uji pembagian batch", "admin:guard:orange");
        set(inventory, 13, Material.RED_WOOL, "§cPaksa Guard Merah", "§7Uji penonaktifan P2/P3", "admin:guard:red");
        set(inventory, 14, Material.ANVIL, "§eFreeze Gens", "§7Bekukan modul gens", "admin:freeze:gens");
        set(inventory, 15, Material.BARRIER, "§aUnfreeze Semua", "§7Aktifkan modul kembali", "admin:unfreeze:all");
        set(inventory, 16, Material.BOOK, "§bAudit Test", "§7Tulis satu entri audit", "admin:audit");
        set(inventory, 22, Material.CHEST, "§dBackup SQLite", "§7Flush cache dan checkpoint", "admin:backup");
    }

    private void set(Inventory inventory, int slot, Material material, String name, String lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        plugin.guiService().set(inventory, slot, item, action);
    }

    private void route(Player player, String payload) {
        String[] args = payload.split(":", 2);
        String action = args[0];
        String value = args.length > 1 ? args[1] : "";
        switch (action) {
            case "status" -> {
                player.closeInventory();
                player.performCommand("kuy status");
            }
            case "guard" -> {
                if (value.equalsIgnoreCase("auto")) plugin.tpsGuard().auto();
                else plugin.tpsGuard().force(id.octanexus.octatycoon.task.GuardLevel.from(value));
                player.sendMessage("§aTPS Guard: " + plugin.tpsGuard().level().id().toUpperCase());
                plugin.audit().log(player.getName(), "TESTADMIN_GUARD", value);
            }
            case "freeze" -> {
                plugin.freezeManager().freeze(value);
                player.sendMessage("§eModul " + value + " dibekukan.");
                plugin.audit().log(player.getName(), "TESTADMIN_FREEZE", value);
            }
            case "unfreeze" -> {
                plugin.freezeManager().unfreeze(value);
                player.sendMessage("§aModul diaktifkan kembali: " + value);
                plugin.audit().log(player.getName(), "TESTADMIN_UNFREEZE", value);
            }
            case "audit" -> {
                plugin.audit().log(player.getName(), "TESTADMIN", "GUI audit action test");
                player.sendMessage("§aEntri audit berhasil ditulis.");
            }
            case "backup" -> {
                player.closeInventory();
                player.performCommand("kuy backup");
            }
            default -> player.sendMessage("§cAksi test admin tidak dikenal.");
        }
    }
}
