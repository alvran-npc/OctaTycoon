package id.octanexus.octatycoon.mining;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mining rules. Ore level bands are loaded from data/ores.yml by the runtime adapter. */
public final class MiningEngine {
    private MiningEngine() {}

    public record OreBand(String key, int minLevel, int maxLevel, int baseXp, long baseToken) {
        public OreBand {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Ore key is required");
            if (minLevel < 1 || maxLevel < minLevel) throw new IllegalArgumentException("Invalid ore level band");
            baseXp = Math.max(0, baseXp);
            baseToken = Math.max(0, baseToken);
        }
    }
    public record MineResult(boolean allowed, int xp, long token, double xpMultiplier, String reason) {}

    public static MineResult mine(OreBand ore, int level, boolean permitted, double rewardMultiplier) {
        if (ore == null) return new MineResult(false, 0, 0, 0, "unknown_ore");
        if (!permitted) return new MineResult(false, 0, 0, 0, "permit_required");
        if (level < ore.minLevel()) return new MineResult(false, 0, 0, 0, "level_required");
        double xpMul = level > ore.maxLevel() ? 0.5 : 1.0;
        double rewardMul = Double.isFinite(rewardMultiplier) ? Math.max(0, Math.min(100, rewardMultiplier)) : 1.0;
        return new MineResult(true,
                (int) Math.min(Integer.MAX_VALUE, Math.floor(ore.baseXp() * xpMul * rewardMul)),
                (long) Math.floor(ore.baseToken() * rewardMul), xpMul, "ok");
    }

    public static final class HeatTracker {
        private final Map<String, Long> heatByPlayer = new ConcurrentHashMap<>();
        private final Map<String, Long> lastActionByPlayer = new ConcurrentHashMap<>();
        public int addAction(String playerKey, long nowMillis) {
            long previous = lastActionByPlayer.getOrDefault(playerKey, nowMillis);
            long current = heatByPlayer.getOrDefault(playerKey, 0L);
            long decay = Math.max(0, nowMillis - previous) / 2_000L;
            long heat = Math.max(0, current - decay);
            heat = Math.min(100, heat + 1);
            heatByPlayer.put(playerKey, heat);
            lastActionByPlayer.put(playerKey, nowMillis);
            return (int) heat;
        }
        public int current(String playerKey, long nowMillis) {
            long previous = lastActionByPlayer.getOrDefault(playerKey, nowMillis);
            long current = heatByPlayer.getOrDefault(playerKey, 0L);
            long decay = Math.max(0, nowMillis - previous) / 2_000L;
            return (int) Math.max(0, current - decay);
        }
        public boolean startRush(String playerKey, long nowMillis) {
            if (current(playerKey, nowMillis) < 100) return false;
            heatByPlayer.put(playerKey, 0L);
            lastActionByPlayer.put(playerKey, nowMillis);
            return true;
        }
    }
}
