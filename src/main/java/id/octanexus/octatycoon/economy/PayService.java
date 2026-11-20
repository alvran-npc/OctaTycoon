package id.octanexus.octatycoon.economy;

import id.octanexus.octatycoon.OctaTycoonPlugin;
import id.octanexus.octatycoon.db.DatabaseManager;
import id.octanexus.octatycoon.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/** The single reward entry point. Other modules should never modify balances directly. */
public final class PayService {
    private final OctaTycoonPlugin plugin;
    private final DatabaseManager database;
    private final Map<RewardType, RewardBackend> backends = new EnumMap<>(RewardType.class);
    private final List<BiFunction<UUID, RewardType, Double>> multipliers = new CopyOnWriteArrayList<>();

    public PayService(OctaTycoonPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        multipliers.add((uuid, type) -> type == RewardType.TOKEN
                ? plugin.getConfig().getDouble("settings.economy.default-token-multiplier", 1.0) : 1.0);
    }

    public void registerBackend(RewardBackend backend) { backends.put(backend.type(), backend); }
    public void addMultiplier(BiFunction<UUID, RewardType, Double> provider) { multipliers.add(provider); }

    public CompletableFuture<PayResult> pay(UUID uuid, RewardType type, BigDecimal baseAmount, String source) {
        if (uuid == null || type == null || source == null || source.isBlank()
                || baseAmount == null || baseAmount.signum() <= 0 || !Double.isFinite(baseAmount.doubleValue())) {
            return CompletableFuture.completedFuture(new PayResult(PayResult.Status.REJECTED, 0, "invalid amount"));
        }
        double multiplier = 1.0;
        for (BiFunction<UUID, RewardType, Double> provider : multipliers) {
            try {
                double value = provider.apply(uuid, type);
                if (Double.isFinite(value) && value >= 0) multiplier *= value;
            } catch (Throwable error) {
                plugin.getLogger().warning("Reward multiplier failed: " + error.getMessage());
            }
        }
        if (!Double.isFinite(multiplier) || multiplier < 0) multiplier = 0;
        double maxMultiplier = plugin.getConfig().getDouble("settings.economy.max-multiplier", 100.0);
        if (!Double.isFinite(maxMultiplier) || maxMultiplier < 0.0) maxMultiplier = 100.0;
        multiplier = Math.min(multiplier, maxMultiplier);
        long cap = Math.max(0L, plugin.getConfig().getLong("settings.economy.max-balance", 9_000_000_000_000_000L));
        long amount;
        try {
            amount = baseAmount.multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.DOWN)
                    .min(BigDecimal.valueOf(cap)).longValueExact();
        } catch (ArithmeticException exception) {
            amount = cap;
        }
        if (amount <= 0) return CompletableFuture.completedFuture(new PayResult(PayResult.Status.REJECTED, 0, "amount after multiplier is zero"));
        final long rewardAmount = amount;

        boolean online = plugin.isPlayerOnline(uuid);
        if (type == RewardType.TOKEN && online) {
            return database.creditToken(uuid, rewardAmount, cap).thenApply(paid -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(uuid);
                    if (current != null) plugin.notifications().send(current, "§a+" + Numbers.shortFormat(paid) + " Token §7(dari " + source + ")");
                });
                plugin.audit().log(uuid.toString(), "PAY_TOKEN", rewardAmount + " from " + source + "; applied=" + paid);
                recordFaucet(type, paid);
                plugin.playerCache().refreshToken(uuid);
                return new PayResult(PayResult.Status.PAID, paid, "token credited");
            });
        }

        RewardBackend backend = backends.get(type);
        if (online && backend != null) {
            return deliverBackend(backend, uuid, rewardAmount, source).thenCompose(success -> {
                if (success) {
                    plugin.audit().log(uuid.toString(), "PAY_" + type, rewardAmount + " from " + source);
                    recordFaucet(type, rewardAmount);
                    return CompletableFuture.completedFuture(new PayResult(PayResult.Status.PAID, rewardAmount, "backend delivered"));
                }
                return queue(uuid, type, rewardAmount, source);
            });
        }
        return queue(uuid, type, rewardAmount, source);
    }

    private CompletableFuture<PayResult> queue(UUID uuid, RewardType type, long amount, String source) {
        int limit = Math.max(0, plugin.getConfig().getInt("settings.economy.pending-reward-limit", 100));
        return database.queuePendingReward(uuid, type.name(), Long.toString(amount), source, limit).thenApply(saved -> {
            if (!saved) return new PayResult(PayResult.Status.REJECTED, 0, "pending reward limit reached");
            plugin.audit().log(uuid.toString(), "QUEUE_" + type, amount + " from " + source);
            return new PayResult(PayResult.Status.QUEUED, amount, "pending reward stored");
        });
    }

    public void deliverPending(UUID uuid) {
        database.getPendingRewards(uuid, Math.max(0, plugin.getConfig().getInt("settings.economy.pending-reward-limit", 100)))
                .thenAccept(rewards -> deliverNext(uuid, rewards, 0))
                .exceptionally(error -> { plugin.getLogger().warning("Pending reward load failed: " + error.getMessage()); return null; });
    }


    private CompletableFuture<Boolean> deliverBackend(RewardBackend backend, UUID uuid, long amount, String source) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    backend.deliver(uuid, amount, source).whenComplete((success, error) -> {
                        if (error != null) result.complete(false);
                        else result.complete(Boolean.TRUE.equals(success));
                    });
                } catch (Throwable error) {
                    plugin.getLogger().warning("Reward backend " + backend.type() + " failed: " + error.getMessage());
                    result.complete(false);
                }
            });
        } catch (Throwable error) {
            result.complete(false);
        }
        return result;
    }

    public CompletableFuture<Void> recordSink(RewardType type, long amount, String source) {
        if (amount <= 0) return CompletableFuture.completedFuture(null);
        plugin.audit().log("SYSTEM", "SINK_" + type, amount + " from " + source);
        return database.recordEconomyFlow(today(), type.name(), amount, false);
    }

    private void recordFaucet(RewardType type, long amount) {
        if (amount > 0) database.recordEconomyFlow(today(), type.name(), amount, true);
    }

    private String today() {
        java.time.ZoneId zone;
        try { zone = java.time.ZoneId.of(plugin.getConfig().getString("settings.timezone", "Asia/Jakarta")); }
        catch (java.time.DateTimeException exception) { zone = java.time.ZoneId.of("Asia/Jakarta"); }
        return java.time.LocalDate.now(zone).toString();
    }

    private void deliverNext(UUID uuid, List<DatabaseManager.PendingReward> rewards, int index) {
        if (index >= rewards.size()) return;
        DatabaseManager.PendingReward reward = rewards.get(index);
        RewardType type;
        try { type = RewardType.valueOf(reward.type()); }
        catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown pending reward type " + reward.type() + " for " + uuid);
            deliverNext(uuid, rewards, index + 1);
            return;
        }
        if (type == RewardType.TOKEN) {
            long cap = Math.max(0L, plugin.getConfig().getLong("settings.economy.max-balance", 9_000_000_000_000_000L));
            database.deliverPendingToken(reward, uuid, cap).thenAccept(paid -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && paid > 0) plugin.notifications().send(player, "§a+" + Numbers.shortFormat(paid) + " Token §7(dikirim dari antrean hadiah)");
                    plugin.playerCache().refreshToken(uuid);
                    recordFaucet(type, paid);
                    deliverNext(uuid, rewards, index + 1);
                });
            });
            return;
        }
        RewardBackend backend = backends.get(type);
        if (backend == null) { deliverNext(uuid, rewards, index + 1); return; }
        long amount;
        try { amount = Long.parseLong(reward.amount()); }
        catch (NumberFormatException exception) { deliverNext(uuid, rewards, index + 1); return; }
        deliverBackend(backend, uuid, amount, reward.source()).thenAccept(success -> {
            if (success) database.deletePendingReward(reward.id()).thenRun(() -> {
                recordFaucet(type, amount);
                deliverNext(uuid, rewards, index + 1);
            });
            else deliverNext(uuid, rewards, index + 1);
        });
    }
}
