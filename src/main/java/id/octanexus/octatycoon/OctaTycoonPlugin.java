package id.octanexus.octatycoon;

import id.octanexus.octatycoon.admin.KuyCommand;
import id.octanexus.octatycoon.admin.TestAdminCommand;
import id.octanexus.octatycoon.farming.FarmingListener;
import id.octanexus.octatycoon.mining.MiningListener;
import id.octanexus.octatycoon.progression.ProgressionCommand;
import id.octanexus.octatycoon.anti.AntiDupeListener;
import id.octanexus.octatycoon.anti.AuditLog;
import id.octanexus.octatycoon.anti.BoundItemService;
import id.octanexus.octatycoon.anti.ProtectedBlockListener;
import id.octanexus.octatycoon.config.Language;
import id.octanexus.octatycoon.db.DatabaseManager;
import id.octanexus.octatycoon.db.PlayerDataCache;
import id.octanexus.octatycoon.db.PlayerModuleData;
import id.octanexus.octatycoon.progression.ProgressionService;
import id.octanexus.octatycoon.progression.ProgressionEngine;
import id.octanexus.octatycoon.economy.PayService;
import id.octanexus.octatycoon.gui.GuiService;
import id.octanexus.octatycoon.notify.NotificationService;
import id.octanexus.octatycoon.profile.JobProfiler;
import id.octanexus.octatycoon.task.EntityLimitJob;
import id.octanexus.octatycoon.task.FreezeManager;
import id.octanexus.octatycoon.task.Priority;
import id.octanexus.octatycoon.task.TPSGuard;
import id.octanexus.octatycoon.task.TickBus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public final class OctaTycoonPlugin extends JavaPlugin {
    private Language language;
    private DatabaseManager database;
    private PlayerDataCache playerCache;
    private PlayerModuleData moduleData;
    private ProgressionService progression;
    private PayService payService;
    private FreezeManager freezeManager;
    private JobProfiler profiler;
    private TPSGuard tpsGuard;
    private TickBus tickBus;
    private AuditLog audit;
    private NotificationService notifications;
    private BoundItemService boundItems;
    private GuiService guiService;
    private volatile boolean ready;
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> bedrockOverrides = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDataResource("data/ranks.yml");
        saveDataResource("data/crops.yml");
        saveDataResource("data/ores.yml");
        saveDataResource("data/gens.yml");
        saveDataResource("data/fish.yml");
        saveDataResource("data/events.yml");
        language = new Language(this);
        language.load();

        String databasePath = getConfig().getString("settings.database.sqlite-file", "data/octatycoon.db");
        database = new DatabaseManager(new File(getDataFolder(), databasePath));
        Map<String, Double> statCaps = getConfig().getConfigurationSection("settings.stats.caps") == null
                ? Map.of("efisiensi", 0.6, "hoki", 0.4, "ceri", 0.3, "jual", 0.30, "lumbung", 1.0, "regen", 0.5, "combo", 0.5, "turnamen", 0.5, "offline", 0.5)
                : getConfig().getConfigurationSection("settings.stats.caps").getValues(false).entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> ((Number) entry.getValue()).doubleValue()));
        playerCache = new PlayerDataCache(database, statCaps, getConfig().getDouble("settings.stats.global-cap", 1.5));
        moduleData = new PlayerModuleData(database);
        progression = new ProgressionService(moduleData,
                level -> ProgressionEngine.xpRequired((int) level,
                        getConfig().getDouble("gameplay.progression.base-xp", 100),
                        getConfig().getDouble("gameplay.progression.xp-growth", 25),
                        getConfig().getDouble("gameplay.progression.xp-exponent", 1.15)),
                prestige -> {
                    java.util.List<Integer> caps = getConfig().getIntegerList("gameplay.progression.prestige-level-caps");
                    if (caps.isEmpty()) caps = java.util.List.of(200, 400, 700, 1000, 1500, 2000, 3000, 4000, 6000, 8000, 10000);
                    int configuredMaximum = getConfig().getInt("gameplay.progression.level-cap", 10000);
                    return Math.min(configuredMaximum, ProgressionEngine.prestigeLevelCap(prestige, caps));
                });
        payService = new PayService(this, database);
        freezeManager = new FreezeManager(Set.copyOf(getConfig().getStringList("modules.frozen")));
        profiler = new JobProfiler();
        tpsGuard = new TPSGuard(this);
        tickBus = new TickBus(this, tpsGuard, profiler);
        audit = new AuditLog(this);
        notifications = new NotificationService(this);
        boundItems = new BoundItemService(this);
        guiService = new GuiService(this);

        Bukkit.getPluginManager().registerEvents(new AntiDupeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectedBlockListener(this), this);
        Bukkit.getPluginManager().registerEvents(guiService, this);
        Bukkit.getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MiningListener(this), this);

        if (getCommand("kuy") != null) getCommand("kuy").setExecutor(new KuyCommand(this));
        else getLogger().severe("Command 'kuy' is missing from plugin.yml");
        if (getCommand("testadmin") != null) getCommand("testadmin").setExecutor(new TestAdminCommand(this));
        else getLogger().severe("Command 'testadmin' is missing from plugin.yml");
        ProgressionCommand progressionCommand = new ProgressionCommand(this);
        if (getCommand("progress") != null) getCommand("progress").setExecutor(progressionCommand);
        if (getCommand("mastery") != null) getCommand("mastery").setExecutor(progressionCommand);

        getLogger().info("Loading OctaTycoon database asynchronously...");
        database.openAsync().whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(this, () -> {
            if (error != null) {
                getLogger().severe("OctaTycoon failed to initialize database: " + error.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            ready = true;
            registerJobs();
            for (Player player : Bukkit.getOnlinePlayers()) {
                markOnline(player.getUniqueId());
                CompletableFuture.allOf(playerCache.load(player.getUniqueId()), moduleData.load(player.getUniqueId()))
                        .thenRun(() -> Bukkit.getScheduler().runTask(this, () -> {
                            if (player.isOnline()) payService.deliverPending(player.getUniqueId());
                        }));
            }
            getLogger().info("OctaTycoon " + getDescription().getVersion() + " is ready.");
            getLogger().info("Optional hooks detected: " + detectedHooks());
            Bukkit.getConsoleSender().sendMessage(language.prefix() + language.get("plugin-ready"));
        }));
    }

    private void registerJobs() {
        EntityLimitJob entityLimitJob = new EntityLimitJob(this);
        tickBus.register("entity-limit", Duration.ofSeconds(getConfig().getLong("settings.entity-limit.interval-seconds", 10)), Priority.P3, entityLimitJob::run);
        tickBus.register("player-cache-flush", Duration.ofMinutes(5), Priority.P2, context -> CompletableFuture.allOf(playerCache.flushAll(), moduleData.flushAll()).exceptionally(error -> {
            getLogger().warning("Player data flush failed: " + error.getMessage()); return null;
        }));
        tickBus.register("player-cache-gc", Duration.ofMinutes(getConfig().getLong("settings.scheduler.offline-cache-gc-minutes", 30)), Priority.P3, context -> {
            Set<UUID> online = Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            int removed = playerCache.collectOffline(online);
            int moduleRemoved = moduleData.collectOffline(online);
            if (removed + moduleRemoved > 0) getLogger().fine("Collected " + (removed + moduleRemoved) + " offline player cache entries.");
        });
        tickBus.register("sqlite-checkpoint", Duration.ofMinutes(10), Priority.P2, context -> database.checkpoint().exceptionally(error -> {
            getLogger().warning("SQLite checkpoint failed: " + error.getMessage()); return null;
        }));
        tickBus.start();
    }

    @Override
    public void onDisable() {
        ready = false;
        if (tickBus != null) tickBus.stop();
        if (audit != null) audit.shutdown();
        if (database != null) {
            if (playerCache != null && moduleData != null) CompletableFuture.allOf(playerCache.flushAll(), moduleData.flushAll()).thenCompose(ignored -> database.checkpoint()).thenCompose(ignored -> database.closeAsync())
                    .exceptionally(error -> { getLogger().warning("Shutdown flush failed: " + error.getMessage()); database.closeAsync(); return null; });
            else database.closeAsync();
        }
        if (language != null) Bukkit.getConsoleSender().sendMessage(language.prefix() + language.get("plugin-disabled"));
    }

    private void saveDataResource(String resourcePath) {
        File target = new File(getDataFolder(), resourcePath);
        if (!target.exists()) saveResource(resourcePath, false);
    }

    private String detectedHooks() {
        String[] names = {"Vault", "ExcellentEconomy", "PlaceholderAPI", "LuckPerms", "Citizens", "SuperiorSkyblock2", "floodgate", "Geyser-Spigot", "TAB"};
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            if (Bukkit.getPluginManager().getPlugin(name) != null) {
                if (!result.isEmpty()) result.append(", ");
                result.append(name);
            }
        }
        return result.isEmpty() ? "none (standalone mode)" : result.toString();
    }

    public boolean isBedrock(Player player) {
        Boolean override = bedrockOverrides.get(player.getUniqueId());
        if (override != null) return override;
        String uuid = player.getUniqueId().toString();
        return uuid.startsWith("00000000-0000-0000-") || player.getName().startsWith(".");
    }
    public void setBedrockOverride(UUID uuid, Boolean bedrock) {
        if (bedrock == null) bedrockOverrides.remove(uuid); else bedrockOverrides.put(uuid, bedrock);
    }
    public void markOnline(UUID uuid) { onlinePlayers.add(uuid); }
    public void markOffline(UUID uuid) { onlinePlayers.remove(uuid); }
    public boolean isPlayerOnline(UUID uuid) { return onlinePlayers.contains(uuid); }
    public boolean isReady() { return ready; }

    public Language lang() { return language; }
    public DatabaseManager database() { return database; }
    public PlayerDataCache playerCache() { return playerCache; }
    public PlayerModuleData moduleData() { return moduleData; }
    public ProgressionService progression() { return progression; }
    public PayService payService() { return payService; }
    public FreezeManager freezeManager() { return freezeManager; }
    public JobProfiler profiler() { return profiler; }
    public TPSGuard tpsGuard() { return tpsGuard; }
    public TickBus tickBus() { return tickBus; }
    public AuditLog audit() { return audit; }
    public NotificationService notifications() { return notifications; }
    public BoundItemService boundItems() { return boundItems; }
    public GuiService guiService() { return guiService; }
}
