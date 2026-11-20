package id.octanexus.octatycoon.boss;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** BossBar-only encounter state and deterministic contribution/reward split. */
public final class BossEngine {
    private BossEngine() {}
    public record RewardShare(double participantPool, double contributionPool, double lastHitBonus, double topBonus) {}

    public static Map<UUID, Double> distribute(Map<UUID, Long> damage, UUID lastHit, double baseReward, boolean fled) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        if (damage == null || damage.isEmpty() || baseReward <= 0) return result;
        long totalDamage = damage.values().stream().mapToLong(v -> Math.max(0, v == null ? 0 : v)).sum();
        if (totalDamage <= 0) return result;
        UUID top = damage.entrySet().stream().max(Comparator.comparingLong(e -> Math.max(0, e.getValue()))).map(Map.Entry::getKey).orElse(null);
        double participantPool = baseReward * 0.40 * (fled ? 0.25 : 1.0);
        double contributionPool = baseReward * 0.60;
        damage.forEach((uuid, raw) -> {
            long dealt = Math.max(0, raw == null ? 0 : raw);
            if (dealt == 0) return;
            double share = participantPool / damage.size() + contributionPool * ((double) dealt / totalDamage);
            result.put(uuid, share);
        });
        if (!fled && lastHit != null && result.containsKey(lastHit)) result.merge(lastHit, baseReward * 0.10, Double::sum);
        if (!fled && top != null) result.merge(top, baseReward * 0.15, Double::sum);
        return result;
    }

    public static final class Encounter {
        private final String id;
        private final long maxHealth;
        private long health;
        private final Map<UUID, Long> damage = new LinkedHashMap<>();
        private UUID lastHit;
        private final long expiresAt;
        public Encounter(String id, long health, long expiresAt) {
            if (id == null || id.isBlank() || health <= 0) throw new IllegalArgumentException("Invalid boss encounter");
            this.id = id; this.maxHealth = health; this.health = health; this.expiresAt = expiresAt;
        }
        public synchronized long damage(UUID player, long amount) {
            if (player == null || amount <= 0 || health <= 0) return 0;
            long applied = Math.min(health, amount);
            health -= applied;
            damage.merge(player, applied, Encounter::safeAdd);
            lastHit = player;
            return applied;
        }
        public String id() { return id; }
        public long maxHealth() { return maxHealth; }
        public synchronized long health() { return health; }
        public long expiresAt() { return expiresAt; }
        public synchronized Map<UUID, Long> damageSnapshot() { return Map.copyOf(damage); }
        public synchronized UUID lastHit() { return lastHit; }
        private static long safeAdd(long a, long b) { return b > 0 && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    }
}
