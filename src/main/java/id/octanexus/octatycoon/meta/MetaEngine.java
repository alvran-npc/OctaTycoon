package id.octanexus.octatycoon.meta;

/** Battle Pass, class page gates, pity counters and shared meta progression formulas. */
public final class MetaEngine {
    private MetaEngine() {}
    public enum ClassType { FARM, MINING, FISHING, GENS }

    public static long battlePassXpRequiredForLevel(int level) {
        int safe = Math.max(0, level);
        double required = 300.0 * safe + 2.5 * safe * (safe + 1.0);
        return required >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.ceil(required);
    }
    public static int battlePassLevel(long totalXp, int maxLevel) {
        long xp = Math.max(0, totalXp);
        int cap = Math.max(1, maxLevel);
        int low = 0, high = cap;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (battlePassXpRequiredForLevel(mid) <= xp) low = mid;
            else high = mid - 1;
        }
        return low;
    }
    public static int classPageRequiredLevel(int page) {
        return switch (page) { case 1 -> 0; case 2 -> 6; case 3 -> 14; case 4 -> 22; case 5 -> 32; case 6 -> 42; default -> Integer.MAX_VALUE; };
    }
    public static int guildCapacity(int guildLevel) { return 10 + Math.max(0, Math.min(10, guildLevel) - 1) * 4; }
    public static double guildMemberBonus(int guildLevel) { return Math.max(0, Math.min(10, guildLevel)) * 0.01; }

    public static final class PityCounter {
        private int misses;
        private final int guaranteeAfter;
        public PityCounter(int guaranteeAfter) { this.guaranteeAfter = Math.max(1, guaranteeAfter); }
        public synchronized boolean roll(double probability, double random) {
            double chance = Double.isFinite(probability) ? Math.max(0, Math.min(1, probability)) : 0;
            boolean success = misses + 1 >= guaranteeAfter || Math.max(0, Math.min(1, random)) < chance;
            misses = success ? 0 : misses + 1;
            return success;
        }
        public synchronized int misses() { return misses; }
        public synchronized void reset() { misses = 0; }
    }
}
