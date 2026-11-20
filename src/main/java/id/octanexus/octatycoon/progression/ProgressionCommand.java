package id.octanexus.octatycoon.progression;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.db.PlayerModuleData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ProgressionCommand implements CommandExecutor {
    private final OctaTycoonPlugin plugin;
    public ProgressionCommand(OctaTycoonPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Command ini hanya bisa digunakan pemain.");
            return true;
        }
        PlayerModuleData.Profile profile = plugin.moduleData().get(player.getUniqueId());
        if (profile == null) {
            plugin.notifications().send(player, "§eData progres sedang dimuat. Coba lagi sebentar.");
            return true;
        }
        int level = plugin.progression().level(player.getUniqueId());
        long xp = plugin.progression().xp(player.getUniqueId());
        long required = plugin.progression().xpRequired(level);
        int prestige = plugin.progression().prestige(player.getUniqueId());
        int mastery = plugin.progression().masteryPoints(player.getUniqueId());
        plugin.notifications().send(player, "§b§lOCTATYCOON §8» §f" + (command.getName().equalsIgnoreCase("mastery") ? "Mastery" : "Progres kamu"));
        plugin.notifications().send(player, "§7Level: §a" + level + "§7/§a" + ProgressionEngine.MAX_LEVEL + " §8| §7Prestige: §dP" + prestige);
        plugin.notifications().send(player, "§7XP: §e" + xp + "§7/§e" + required + " §8| §7Mastery tersedia: §b" + mastery);
        plugin.notifications().send(player, "§7Hoe Lv. §a" + plugin.progression().hoeLevel(player.getUniqueId())
                + " §8| §7Pickaxe Lv. §a" + plugin.progression().pickaxeLevel(player.getUniqueId()));
        if (command.getName().equalsIgnoreCase("mastery")) plugin.notifications().send(player, "§7Mastery: §b" + mastery + " §7poin tersedia untuk dialokasikan (allocation GUI menyusul).");
        return true;
    }
}
