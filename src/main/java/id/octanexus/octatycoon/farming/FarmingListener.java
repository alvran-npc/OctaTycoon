package id.octanexus.octatycoon.farming;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.economy.RewardType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Map;

/** First runtime farming slice: gated mature vanilla crop harvests with immediate replant. */
public final class FarmingListener implements Listener {
    private static final Map<Material, FarmingEngine.Crop> CROPS = Map.of(
            Material.WHEAT, FarmingEngine.Crop.WHEAT,
            Material.CARROTS, FarmingEngine.Crop.CARROT,
            Material.POTATOES, FarmingEngine.Crop.POTATO,
            Material.BEETROOTS, FarmingEngine.Crop.BEETROOT);
    private final OctaTycoonPlugin plugin;
    private final Map<FarmingEngine.Crop, CropSettings> settings = new java.util.EnumMap<>(FarmingEngine.Crop.class);

    public FarmingListener(OctaTycoonPlugin plugin) {
        this.plugin = plugin;
        loadCropSettings();
    }

    private void loadCropSettings() {
        var file = new java.io.File(plugin.getDataFolder(), "data/crops.yml");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (FarmingEngine.Crop crop : FarmingEngine.Crop.values()) {
            String key = crop.name().toLowerCase(java.util.Locale.ROOT);
            var section = yaml.getConfigurationSection("crops." + key);
            settings.put(crop, new CropSettings(
                    section == null ? crop.requiredLevel() : section.getInt("min-level", crop.requiredLevel()),
                    section == null ? crop.maxAge() : section.getInt("max-age", crop.maxAge()),
                    section == null ? 1 : section.getInt("base-xp", 1),
                    section == null ? 1 : section.getLong("base-token", 1)));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.isReady() || plugin.moduleData().get(player.getUniqueId()) == null) return;
        Block block = event.getBlock();
        FarmingEngine.Crop crop = CROPS.get(block.getType());
        if (crop == null || !(block.getBlockData() instanceof Ageable ageable)) return;
        CropSettings cropSettings = settings.get(crop);
        if (cropSettings == null) return;
        if (plugin.freezeManager().isFrozen("hoe")) {
            if (ageable.getAge() >= cropSettings.maxAge()) event.setCancelled(true);
            return;
        }
        if (ageable.getAge() < cropSettings.maxAge()) return;
        int level = plugin.progression().level(player.getUniqueId());
        if (level < cropSettings.requiredLevel()) {
            event.setCancelled(true);
            plugin.notifications().send(player, "§cTanaman ini membutuhkan level " + cropSettings.requiredLevel() + ".");
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!tool.getType().name().endsWith("_HOE")) {
            event.setCancelled(true);
            plugin.notifications().send(player, "§cGunakan Hoe untuk memanen tanaman ini.");
            return;
        }

        FarmingEngine.HarvestResult result = FarmingEngine.harvest(level, cropSettings.requiredLevel(), cropSettings.maxAge(),
                ageable.getAge(), cropSettings.baseXp(), cropSettings.baseToken(),
                plugin.getConfig().getDouble("gameplay.farming.reward-multiplier", 1.0));
        if (!result.allowed()) {
            if ("level_required".equals(result.reason())) {
                event.setCancelled(true);
                plugin.notifications().send(player, "§cTanaman ini membutuhkan level " + cropSettings.requiredLevel() + ".");
            }
            return;
        }

        event.setCancelled(true);
        for (ItemStack drop : block.getDrops(tool, player)) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
            leftovers.values().forEach(item -> block.getWorld().dropItemNaturally(block.getLocation(), item));
        }
        ageable.setAge(0);
        block.setBlockData(ageable, false);
        var xpResult = plugin.progression().addXp(player.getUniqueId(), result.xp());
        if (result.token() > 0) plugin.payService().pay(player.getUniqueId(), RewardType.TOKEN, BigDecimal.valueOf(result.token()), "farm:" + crop.name().toLowerCase());
        if (xpResult.levelsGained() > 0) plugin.notifications().send(player, "§aLevel naik menjadi " + xpResult.newLevel() + "!");
    }

    private record CropSettings(int requiredLevel, int maxAge, int baseXp, long baseToken) {}
}
