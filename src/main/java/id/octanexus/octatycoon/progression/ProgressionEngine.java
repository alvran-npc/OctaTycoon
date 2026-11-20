package id.octanexus.octatycoon.progression;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Pure progression rules. Numeric curves should be overridden from config in the runtime service. */
public final class ProgressionEngine {
    private ProgressionEngine() {}

    public static final int MAX_LEVEL = 10_000;
    public static final int MAX_PRESTIGE = 10;

    public enum FreeRank {
        COAL(0), IRON(1), GOLD(2), DIAMOND(3), NETHERITE(4);
        private final int ordinal;
        FreeRank(int ordinal) { this.ordinal = ordinal; }
        public int ordinalValue() { return ordinal; }
        public FreeRank next() { return values()[Math.min(values().length - 1, ordinal + 1)]; }
    }

    public enum PremiumRank {
        NONE(0), NOBLE(1), SAINT(2), HERO(3), DIVINE(4), TRANSCENDANT(5), CUSTOM(6);
        private final int ordinal;
        PremiumRank(int ordinal) { this.ordinal = ordinal; }
        public int ordinalValue() { return ordinal; }
    }

    public enum MasteryStat { XP, TOKEN, DROP }

    public record XpResult(int oldLevel, int newLevel, long remainingXp, int levelsGained) {}
    public record RankBonus(double xp, double token, double sell, int generatorSlots, int ringSlots) {
        public static RankBonus none() { return new RankBonus(0, 0, 0, 0, 0); }
    }

    @FunctionalInterface
    public interface XpCurve { long required(int currentLevel); }

    /** Example curve only. Production server should override this with config values. */
    public static long defaultXpRequired(int level) {
        return xpRequired(level, 100.0, 25.0, 1.15);
    }

    public static long xpRequired(int level, double baseXp, double growth, double exponent) {
        int safeLevel = Math.max(1, Math.min(MAX_LEVEL - 1, level));
        double safeBase = Double.isFinite(baseXp) ? Math.max(1, baseXp) : 100;
        double safeGrowth = Double.isFinite(growth) ? Math.max(0, growth) : 25;
        double safeExponent = Double.isFinite(exponent) ? Math.max(0.1, Math.min(3.0, exponent)) : 1.15;
        double required = safeBase + safeGrowth * Math.pow(safeLevel, safeExponent);
        return required >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1L, Math.round(required));
    }

    public static XpResult addXp(int level, long currentXp, long gainedXp, int levelCap, XpCurve curve) {
        int safeLevel = Math.max(1, Math.min(MAX_LEVEL, level));
        int cap = Math.max(1, Math.min(MAX_LEVEL, levelCap));
        long xp = saturatedAdd(Math.max(0, currentXp), Math.max(0, gainedXp));
        int old = safeLevel;
        while (safeLevel < cap) {
            long requirement = Math.max(1, curve.required(safeLevel));
            if (xp < requirement) break;
            xp -= requirement;
            safeLevel++;
        }
        if (safeLevel == cap) xp = 0;
        return new XpResult(old, safeLevel, xp, safeLevel - old);
    }

    public static int masteryPoints(int hoeLevel, int pickaxeLevel) {
        return Math.max(0, hoeLevel) / 100 + Math.max(0, pickaxeLevel) / 100;
    }

    public static RankBonus combine(RankBonus free, RankBonus premium) {
        RankBonus a = free == null ? RankBonus.none() : free;
        RankBonus b = premium == null ? RankBonus.none() : premium;
        return new RankBonus(
                combineStat(a.xp, b.xp), combineStat(a.token, b.token), combineStat(a.sell, b.sell),
                a.generatorSlots + b.generatorSlots,
                Math.max(a.ringSlots, b.ringSlots));
    }

    private static double combineStat(double first, double second) {
        double high = Math.max(first, second);
        double low = Math.min(first, second);
        return high + (low * 0.5);
    }

    public static String permissionNode(String rank) {
        String safe = rank == null ? "" : rank.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return "octatycoon.rank." + safe;
    }

    public static int prestigeLevelCap(int prestige, java.util.List<Integer> configuredCaps) {
        int p = Math.max(0, Math.min(MAX_PRESTIGE, prestige));
        if (configuredCaps == null || configuredCaps.isEmpty()) return 200;
        int index = Math.min(p, configuredCaps.size() - 1);
        return Math.max(1, Math.min(MAX_LEVEL, configuredCaps.get(index)));
    }

    public static long saturatedAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
}
