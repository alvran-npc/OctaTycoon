package id.octanexus.octatycoon.db;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Generic per-player module cache for progression/meta systems, persisted asynchronously in SQLite. */
public final class PlayerModuleData {
    private final DatabaseManager database;
    private final Map<UUID, Profile> cache = new ConcurrentHashMap<>();

    public PlayerModuleData(DatabaseManager database) { this.database = database; }

    public CompletableFuture<Profile> load(UUID uuid) {
        Profile loaded = cache.get(uuid);
        if (loaded != null) return CompletableFuture.completedFuture(loaded);
        return database.loadPlayerData(uuid).thenApply(values -> cache.computeIfAbsent(uuid, ignored -> new Profile(values)));
    }

    public Profile get(UUID uuid) { return cache.get(uuid); }
    public int size() { return cache.size(); }

    public CompletableFuture<Void> flush(UUID uuid) {
        Profile profile = cache.get(uuid);
        if (profile == null) return CompletableFuture.completedFuture(null);
        Map<String, String> snapshot = profile.dirtySnapshot();
        if (snapshot.isEmpty()) return CompletableFuture.completedFuture(null);
        return database.savePlayerData(uuid, snapshot).thenRun(() -> profile.clearPersisted(snapshot));
    }

    public CompletableFuture<Void> flushAll() {
        return CompletableFuture.allOf(cache.keySet().stream().map(this::flush).toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> flushAndRemove(UUID uuid) {
        return flush(uuid).thenRun(() -> {
            Profile profile = cache.get(uuid);
            if (profile != null && !profile.hasDirty()) cache.remove(uuid, profile);
        });
    }

    public int collectOffline(Set<UUID> online) {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> !online.contains(entry.getKey()) && !entry.getValue().hasDirty());
        return before - cache.size();
    }

    public static final class Profile {
        private final Map<String, String> values = new HashMap<>();
        private final Set<String> dirty = new HashSet<>();
        private Profile(Map<String, String> initial) { values.putAll(initial); }
        public synchronized String get(String key, String fallback) { return values.getOrDefault(key, fallback); }
        public synchronized long getLong(String key, long fallback) {
            try { return Long.parseLong(values.getOrDefault(key, Long.toString(fallback))); }
            catch (NumberFormatException ignored) { return fallback; }
        }
        public synchronized int getInt(String key, int fallback) {
            long value = getLong(key, fallback);
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) value;
        }
        public synchronized void set(String key, String value) {
            if (key == null || key.isBlank() || value == null) throw new IllegalArgumentException("Invalid module data entry");
            if (!value.equals(values.put(key, value))) dirty.add(key);
        }
        public synchronized void setLong(String key, long value) { set(key, Long.toString(value)); }
        private synchronized Map<String, String> dirtySnapshot() {
            Map<String, String> snapshot = new HashMap<>();
            for (String key : dirty) snapshot.put(key, values.get(key));
            return snapshot;
        }
        private synchronized void clearPersisted(Map<String, String> written) {
            written.forEach((key, value) -> { if (java.util.Objects.equals(values.get(key), value)) dirty.remove(key); });
        }
        private synchronized boolean hasDirty() { return !dirty.isEmpty(); }
    }
}
