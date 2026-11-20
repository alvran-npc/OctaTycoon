package id.octanexus.octatycoon.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** RAM cache for player stats. Dirty stat values are persisted asynchronously. */
public final class PlayerDataCache {
    private final DatabaseManager database;
    private final Map<String, Double> statCaps;
    private final double globalCap;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public PlayerDataCache(DatabaseManager database, Map<String, Double> statCaps, double globalCap) {
        this.database = database;
        Map<String, Double> safeCaps = new HashMap<>();
        statCaps.forEach((key, value) -> {
            if (key != null && value != null && Double.isFinite(value) && value >= 0.0) {
                safeCaps.put(key.toLowerCase(java.util.Locale.ROOT), value);
            }
        });
        this.statCaps = Map.copyOf(safeCaps);
        this.globalCap = Double.isFinite(globalCap) && globalCap >= 0.0 ? globalCap : 1.5;
    }

    public CompletableFuture<PlayerProfile> load(UUID uuid) {
        PlayerProfile existing = cache.get(uuid);
        if (existing != null) return CompletableFuture.completedFuture(existing);
        CompletableFuture<Long> token = database.getTokenBalance(uuid);
        CompletableFuture<Map<String, Double>> stats = database.loadStats(uuid);
        return token.thenCombine(stats, (balance, values) -> cache.computeIfAbsent(uuid, ignored -> new PlayerProfile(balance, values)));
    }

    public void statSet(UUID uuid, String source, String stat, double value) {
        if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException("Stat value must be finite and non-negative");
        PlayerProfile profile = cache.computeIfAbsent(uuid, ignored -> new PlayerProfile(0L, Map.of()));
        profile.set(source, stat, value, statCaps.getOrDefault(stat.toLowerCase(java.util.Locale.ROOT), globalCap), globalCap);
    }

    public double statGet(UUID uuid, String source, String stat) {
        PlayerProfile profile = cache.get(uuid);
        return profile == null ? 0.0 : profile.get(source, stat);
    }

    public long tokenBalance(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        return profile == null ? 0L : profile.tokenBalance();
    }

    public void refreshToken(UUID uuid) {
        database.getTokenBalance(uuid).thenAccept(balance -> {
            PlayerProfile profile = cache.get(uuid);
            if (profile != null) profile.tokenBalance(balance);
        });
    }

    public CompletableFuture<Void> flush(UUID uuid) {
        PlayerProfile profile = cache.get(uuid);
        if (profile == null) return CompletableFuture.completedFuture(null);
        Map<String, Double> snapshot = profile.dirtySnapshot();
        if (snapshot.isEmpty()) return CompletableFuture.completedFuture(null);
        return database.saveStats(uuid, snapshot).thenRun(() -> profile.clearPersisted(snapshot));
    }

    public CompletableFuture<Void> flushAll() {
        return CompletableFuture.allOf(cache.keySet().stream().map(this::flush).toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> flushAndRemove(UUID uuid) {
        return flush(uuid).thenRun(() -> {
            PlayerProfile profile = cache.get(uuid);
            if (profile != null && !profile.hasDirty()) cache.remove(uuid, profile);
        });
    }

    public int collectOffline(Set<UUID> online) {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> !online.contains(entry.getKey()) && !entry.getValue().hasDirty());
        return before - cache.size();
    }

    public int size() { return cache.size(); }

    public static final class PlayerProfile {
        private long tokenBalance;
        private final Map<String, Double> stats = new HashMap<>();
        private final Set<String> dirty = new HashSet<>();
        private PlayerProfile(long tokenBalance, Map<String, Double> values) {
            this.tokenBalance = tokenBalance;
            this.stats.putAll(values);
        }
        public synchronized long tokenBalance() { return tokenBalance; }
        public synchronized void tokenBalance(long value) { tokenBalance = value; }
        private synchronized void set(String source, String stat, double value, double statCap, double globalCap) {
            String key = source + "|" + stat;
            double otherSameStat = stats.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(key) && entry.getKey().endsWith("|" + stat))
                    .mapToDouble(Map.Entry::getValue).sum();
            double otherTotal = stats.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(key))
                    .mapToDouble(Map.Entry::getValue).sum();
            double capped = Math.min(value, Math.max(0.0, statCap - otherSameStat));
            capped = Math.min(capped, Math.max(0.0, globalCap - otherTotal));
            stats.put(key, capped);
            dirty.add(key);
        }
        private synchronized double get(String source, String stat) { return stats.getOrDefault(source + "|" + stat, 0.0); }
        private synchronized Map<String, Double> dirtySnapshot() {
            Map<String, Double> copy = new HashMap<>();
            for (String key : dirty) copy.put(key, stats.get(key));
            return copy;
        }
        private synchronized void clearPersisted(Map<String, Double> written) {
            written.forEach((key, value) -> {
                if (java.util.Objects.equals(stats.get(key), value)) dirty.remove(key);
            });
        }
        private synchronized boolean hasDirty() { return !dirty.isEmpty(); }
    }
}
