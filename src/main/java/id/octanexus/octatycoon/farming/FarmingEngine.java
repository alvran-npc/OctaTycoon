package id.octanexus.octatycoon.farming;

import java.util.EnumMap;
import java.util.Map;

/** Deterministic farming rules, separated from Bukkit block/inventory handling. */
public final class FarmingEngine {
    private FarmingEngine() {}

    public enum Crop {
        WHEAT(1, 7), CARROT(700, 7), POTATO(1_400, 7), BEETROOT(2_100, 3);
        private final int requiredLevel;
        private final int maxAge;
        Crop(int requiredLevel, int maxAge) { this.requiredLevel = requiredLevel; this.maxAge = maxAge; }
        public int requiredLevel() { return requiredLevel; }
        public int maxAge() { return maxAge; }
    }

    public record HarvestResult(boolean allowed, int xp, long token, int cropCount, String reason) {}

    public static HarvestResult harvest(Crop crop, int playerLevel, int age, int baseXp, long baseToken, double multiplier) {
        if (crop == null) return new HarvestResult(false, 0, 0, 0, "unknown_crop");
        return harvest(playerLevel, crop.requiredLevel(), crop.maxAge(), age, baseXp, baseToken, multiplier);
    }

    public static HarvestResult harvest(int playerLevel, int requiredLevel, int maxAge, int age,
                                        int baseXp, long baseToken, double multiplier) {
        if (playerLevel < Math.max(1, requiredLevel)) return new HarvestResult(false, 0, 0, 0, "level_required");
        if (age < Math.max(1, maxAge)) return new HarvestResult(false, 0, 0, 0, "not_mature");
        double safeMultiplier = Double.isFinite(multiplier) ? Math.max(0, Math.min(100, multiplier)) : 1;
        long token = (long) Math.floor(Math.max(0, baseToken) * safeMultiplier);
        int xp = (int) Math.min(Integer.MAX_VALUE, Math.floor(Math.max(0, baseXp) * safeMultiplier));
        return new HarvestResult(true, xp, token, 1, "ok");
    }

    public static final class WheatMarket {
        private double multiplier = 1.0;
        public synchronized double multiplier() { return multiplier; }
        public synchronized double step(double randomDelta) {
            double delta = Double.isFinite(randomDelta) ? Math.max(-0.03, Math.min(0.03, randomDelta)) : 0;
            multiplier = Math.max(0.85, Math.min(1.15, multiplier + delta));
            return multiplier;
        }
        public synchronized void reset() { multiplier = 1.0; }
    }

    public static final class Barn {
        private long capacity;
        private long stored;
        public Barn(long capacity) { this.capacity = Math.max(0, capacity); }
        public synchronized long capacity() { return capacity; }
        public synchronized long stored() { return stored; }
        public synchronized long add(long amount) {
            long accepted = Math.min(Math.max(0, amount), Math.max(0, capacity - stored));
            stored += accepted;
            return accepted;
        }
        public synchronized long take(long amount) {
            long removed = Math.min(stored, Math.max(0, amount));
            stored -= removed;
            return removed;
        }
        public synchronized long takeAll() { long result = stored; stored = 0; return result; }
        public synchronized void upgradeCapacity(long newCapacity) { capacity = Math.max(capacity, newCapacity); }
    }
}
