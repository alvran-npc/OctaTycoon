package id.octanexus.octatycoon.gens;

/** Timestamp-based generator calculations; no per-generator repeating task is required. */
public final class GeneratorEngine {
    private GeneratorEngine() {}

    public record GeneratorState(int tier, int stars, double durability, long stored, long lastProducedAt) {
        public GeneratorState {
            tier = Math.max(1, Math.min(100, tier));
            stars = Math.max(0, Math.min(10, stars));
            durability = Math.max(0, Math.min(100, durability));
            stored = Math.max(0, stored);
            lastProducedAt = Math.max(0, lastProducedAt);
        }
    }
    public record Production(long produced, long newStored, long newLastProducedAt, double effectiveMultiplier) {}

    public static double tierMultiplier(int tier) {
        int safeTier = Math.max(1, Math.min(100, tier));
        if (safeTier <= 30) return 1.0 + (safeTier - 1) * 0.05;
        return (1.0 + 29 * 0.05) * (1.0 + (safeTier - 30) * 0.15);
    }

    public static Production calculate(GeneratorState state, long nowMillis, double baseItemsPerHour,
                                       long storageCapacity, boolean overclock, double externalMultiplier) {
        if (state == null) throw new IllegalArgumentException("Generator state is required");
        long elapsed = Math.max(0, nowMillis - state.lastProducedAt());
        double hours = elapsed / 3_600_000.0;
        double starMultiplier = 1.0 + state.stars() * 0.15;
        double durabilityMultiplier = state.durability() <= 0 ? 0 : state.durability() / 100.0;
        double ext = Double.isFinite(externalMultiplier) ? Math.max(0, Math.min(1.6, externalMultiplier)) : 1.0;
        double multiplier = tierMultiplier(state.tier()) * starMultiplier * (overclock ? 2.0 : 1.0) * ext * durabilityMultiplier;
        double amount = Math.max(0, baseItemsPerHour) * hours * multiplier;
        long capacity = Math.max(0, storageCapacity);
        long room = Math.max(0, capacity - state.stored());
        long produced = Math.min(room, amount >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(amount));
        return new Production(produced, state.stored() + produced, Math.max(state.lastProducedAt(), nowMillis), multiplier);
    }

    public static int repairCostPercent(double durability) {
        double safe = Math.max(0, Math.min(100, durability));
        return (int) Math.ceil(100 - safe);
    }
}
