package id.octanexus.octatycoon.fishing;

import java.util.List;

/** Data-driven fishing algorithms shared by Java and Bedrock UI adapters. */
public final class FishingEngine {
    private FishingEngine() {}
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, ARTIFACT }
    public record FishDefinition(String id, Rarity rarity, double minWeight, double maxWeight, long basePrice) {
        public FishDefinition {
            if (id == null || id.isBlank() || rarity == null) throw new IllegalArgumentException("Invalid fish definition");
            minWeight = Math.max(0.01, minWeight);
            maxWeight = Math.max(minWeight, maxWeight);
            basePrice = Math.max(0, basePrice);
        }
    }
    public record MinigameResult(boolean success, int score, int zonesHit, int totalZones) {}

    public static FishDefinition roll(List<FishDefinition> fish, double rarityRoll, double weightRoll) {
        if (fish == null || fish.isEmpty()) throw new IllegalArgumentException("Fish table is empty");
        double rr = Math.max(0, Math.min(0.999999, rarityRoll));
        int index = Math.min(fish.size() - 1, (int) (rr * fish.size()));
        FishDefinition chosen = fish.get(index);
        double wr = Math.max(0, Math.min(1, weightRoll));
        double weight = chosen.minWeight() + (chosen.maxWeight() - chosen.minWeight()) * wr;
        return new FishDefinition(chosen.id(), chosen.rarity(), weight, weight, chosen.basePrice());
    }

    public static FishDefinition rollWeighted(List<FishDefinition> fish, java.util.Map<Rarity, Integer> rarityWeights,
                                              double rarityRoll, double fishRoll, double weightRoll) {
        if (fish == null || fish.isEmpty()) throw new IllegalArgumentException("Fish table is empty");
        if (rarityWeights == null || rarityWeights.values().stream().mapToInt(v -> Math.max(0, v == null ? 0 : v)).sum() <= 0)
            throw new IllegalArgumentException("Rarity weights are empty");
        int totalWeight = rarityWeights.values().stream().mapToInt(v -> Math.max(0, v == null ? 0 : v)).sum();
        int target = Math.min(totalWeight - 1, (int) (Math.max(0, Math.min(0.999999, rarityRoll)) * totalWeight));
        Rarity selected = Rarity.COMMON;
        for (Rarity rarity : Rarity.values()) {
            target -= Math.max(0, rarityWeights.getOrDefault(rarity, 0));
            if (target < 0) { selected = rarity; break; }
        }
        final Rarity selectedRarity = selected;
        List<FishDefinition> candidates = fish.stream().filter(def -> def.rarity() == selectedRarity).toList();
        if (candidates.isEmpty()) candidates = fish;
        int fishIndex = Math.min(candidates.size() - 1, (int) (Math.max(0, Math.min(0.999999, fishRoll)) * candidates.size()));
        FishDefinition chosen = candidates.get(fishIndex);
        double wr = Math.max(0, Math.min(1, weightRoll));
        double weight = chosen.minWeight() + (chosen.maxWeight() - chosen.minWeight()) * wr;
        return new FishDefinition(chosen.id(), chosen.rarity(), weight, weight, chosen.basePrice());
    }

    public static MinigameResult evaluate(int[] targetPositions, int[] inputPositions, int tolerance) {
        int total = targetPositions == null ? 0 : targetPositions.length;
        if (total == 0 || inputPositions == null) return new MinigameResult(false, 0, 0, total);
        int hits = 0;
        for (int i = 0; i < Math.min(total, inputPositions.length); i++) {
            if (Math.abs(targetPositions[i] - inputPositions[i]) <= Math.max(0, tolerance)) hits++;
        }
        int score = (int) Math.round(100.0 * hits / total);
        return new MinigameResult(hits == total, score, hits, total);
    }

    public static boolean consumeBaitOnSuccessfulCatch(boolean minigameSuccess) { return minigameSuccess; }
}
