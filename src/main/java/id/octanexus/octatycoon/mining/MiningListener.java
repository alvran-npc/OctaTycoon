package id.octanexus.octatycoon.mining;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.economy.RewardType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** First runtime mining slice: supported ore XP/token, permits, and heat tracking. */
public final class MiningListener implements Listener {
    private static final Map<Material, String> ORES = Map.ofEntries(
            Map.entry(Material.COAL_ORE, "coal"), Map.entry(Material.DEEPSLATE_COAL_ORE, "coal"),
            Map.entry(Material.COPPER_ORE, "copper"), Map.entry(Material.DEEPSLATE_COPPER_ORE, "copper"),
            Map.entry(Material.IRON_ORE, "iron"), Map.entry(Material.DEEPSLATE_IRON_ORE, "iron"),
            Map.entry(Material.GOLD_ORE, "gold"), Map.entry(Material.DEEPSLATE_GOLD_ORE, "gold"), Map.entry(Material.NETHER_GOLD_ORE, "gold"),
            Map.entry(Material.LAPIS_ORE, "lapis"), Map.entry(Material.DEEPSLATE_LAPIS_ORE, "lapis"),
            Map.entry(Material.REDSTONE_ORE, "redstone"), Map.entry(Material.DEEPSLATE_REDSTONE_ORE, "redstone"),
            Map.entry(Material.EMERALD_ORE, "emerald"), Map.entry(Material.DEEPSLATE_EMERALD_ORE, "emerald"),
            Map.entry(Material.DIAMOND_ORE, "diamond"), Map.entry(Material.DEEPSLATE_DIAMOND_ORE, "diamond"),
            Map.entry(Material.ANCIENT_DEBRIS, "ancient_debris"));

    private final OctaTycoonPlugin plugin;
    private final MiningEngine.HeatTracker heat = new MiningEngine.HeatTracker();
    private final Map<String, MiningEngine.OreBand> bands = new ConcurrentHashMap<>();

    public MiningListener(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
        loadBands();
    }

    private void loadBands() {
        var file = new java.io.File(plugin.getDataFolder(), "data/ores.yml");
        if (!file.exists()) plugin.saveResource("data/ores.yml", false);
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("ores");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            var ore = section.getConfigurationSection(key);
            if (ore == null) continue;
            bands.put(key.toLowerCase(), new MiningEngine.OreBand(key,
                    ore.getInt("min-level", 3000), ore.getInt("max-level", 10000),
                    ore.getInt("base-xp", 1), ore.getLong("base-token", 1)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isReady() || plugin.moduleData().get(player.getUniqueId()) == null) return;
        if (plugin.freezeManager().isFrozen("pickaxe")) return;
        Block block = event.getBlock();
        String oreKey = ORES.get(block.getType());
        if (oreKey == null) return;
        MiningEngine.OreBand band = bands.get(oreKey);
        if (band == null) return;

        UUID uuid = player.getUniqueId();
        var profile = plugin.moduleData().get(uuid);
        boolean permitted = player.isOp() || player.hasPermission("octatycoon.mine." + oreKey)
                || profile != null && Boolean.parseBoolean(profile.get("mine.permit." + oreKey, "false"));
        MiningEngine.MineResult result = MiningEngine.mine(band, plugin.progression().level(uuid), permitted,
                plugin.getConfig().getDouble("gameplay.mining.reward-multiplier", 1.0));
        if (!result.allowed()) {
            if ("permit_required".equals(result.reason()) || "level_required".equals(result.reason())) {
                // MONITOR events must not be modified; permission/level checks are enforced in the LOWEST handler below.
                return;
            }
            return;
        }
        long now = System.currentTimeMillis();
        int currentHeat = heat.addAction(uuid.toString(), now);
        if (heat.startRush(uuid.toString(), now)) {
            plugin.notifications().send(player, "§6Mining Rush threshold tercapai! §7Timer dan bonus Rush akan dihubungkan pada polishing F3.");
        }
        var xpResult = plugin.progression().addXp(uuid, result.xp());
        if (result.token() > 0) plugin.payService().pay(uuid, RewardType.TOKEN, BigDecimal.valueOf(result.token()), "mine:" + oreKey);
        if (currentHeat % 20 == 0) plugin.notifications().send(player, "§7Mining Heat: §e" + currentHeat + "§7/100");
        if (xpResult.levelsGained() > 0) plugin.notifications().send(player, "§aLevel naik menjadi " + xpResult.newLevel() + "!");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void enforceOreGate(BlockBreakEvent event) {
        if (!plugin.isReady() || plugin.moduleData().get(event.getPlayer().getUniqueId()) == null) return;
        String oreKey = ORES.get(event.getBlock().getType());
        if (oreKey == null) return;
        if (plugin.freezeManager().isFrozen("pickaxe")) {
            event.setCancelled(true);
            plugin.notifications().send(event.getPlayer(), "§cModul mining sedang dibekukan admin.");
            return;
        }
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!player.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) {
            event.setCancelled(true);
            plugin.notifications().send(player, "§cGunakan Pickaxe untuk menambang ore ini.");
            return;
        }
        var profile = plugin.moduleData().get(uuid);
        boolean permitted = player.isOp() || player.hasPermission("octatycoon.mine." + oreKey)
                || profile != null && Boolean.parseBoolean(profile.get("mine.permit." + oreKey, "false"));
        MiningEngine.OreBand band = bands.get(oreKey);
        if (band != null && (!permitted || plugin.progression().level(uuid) < band.minLevel())) {
            event.setCancelled(true);
            plugin.notifications().send(player, !permitted ? "§cKamu belum memiliki izin menambang ore ini." : "§cMining terbuka mulai level " + band.minLevel() + ".");
        }
    }
}
