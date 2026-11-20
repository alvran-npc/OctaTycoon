package id.octanexus.octatycoon.progression;

import id.octanexus.octatycoon.db.PlayerModuleData;

import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

/** Per-player progression backed by PlayerModuleData. Profiles must be loaded on join first. */
public final class ProgressionService {
    private final PlayerModuleData data;
    private final LongUnaryOperator xpCurve;
    private final IntUnaryOperator levelCapCurve;
    public ProgressionService(PlayerModuleData data) {
        this(data, level -> ProgressionEngine.defaultXpRequired((int) level), prestige -> (int) ProgressionEngine.prestigeLevelCap(prestige,
                java.util.List.of(200, 400, 700, 1000, 1500, 2000, 3000, 4000, 6000, 8000, 10000)));
    }
    public ProgressionService(PlayerModuleData data, LongUnaryOperator xpCurve, IntUnaryOperator levelCapCurve) {
        this.data = data;
        this.xpCurve = xpCurve;
        this.levelCapCurve = levelCapCurve;
    }
    public long xpRequired(int level) { return Math.max(1, xpCurve.applyAsLong(level)); }
    public int levelCap(int prestige) { return Math.max(1, Math.min(ProgressionEngine.MAX_LEVEL, levelCapCurve.applyAsInt(prestige))); }

    public int level(UUID uuid) { return profile(uuid).getInt("progress.level", 1); }
    public long xp(UUID uuid) { return profile(uuid).getLong("progress.xp", 0); }
    public int prestige(UUID uuid) { return profile(uuid).getInt("progress.prestige", 0); }
    public int hoeLevel(UUID uuid) { return profile(uuid).getInt("tool.hoe.level", 1); }
    public int pickaxeLevel(UUID uuid) { return profile(uuid).getInt("tool.pickaxe.level", 1); }
    public int masteryPoints(UUID uuid) { return ProgressionEngine.masteryPoints(hoeLevel(uuid), pickaxeLevel(uuid)); }

    public synchronized ProgressionEngine.XpResult addXp(UUID uuid, long amount) {
        PlayerModuleData.Profile profile = profile(uuid);
        int currentLevel = Math.max(1, profile.getInt("progress.level", 1));
        long currentXp = Math.max(0, profile.getLong("progress.xp", 0));
        int cap = levelCap(prestige(uuid));
        ProgressionEngine.XpResult result = ProgressionEngine.addXp(currentLevel, currentXp, amount, cap, xpCurve::applyAsLong);
        profile.setLong("progress.level", result.newLevel());
        profile.setLong("progress.xp", result.remainingXp());
        return result;
    }

    public synchronized void advancePrestige(UUID uuid) {
        PlayerModuleData.Profile profile = profile(uuid);
        int next = Math.min(ProgressionEngine.MAX_PRESTIGE, prestige(uuid) + 1);
        profile.setLong("progress.prestige", next);
    }

    private PlayerModuleData.Profile profile(UUID uuid) {
        PlayerModuleData.Profile profile = data.get(uuid);
        if (profile == null) throw new IllegalStateException("Player progression is not loaded for " + uuid);
        return profile;
    }
}
