package id.octanexus.octatycoon.fishing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FishingEngineTest {
    @Test void minigameRequiresEveryZoneForSuccess() {
        assertTrue(FishingEngine.evaluate(new int[]{5, 10}, new int[]{5, 10}, 1).success());
        assertFalse(FishingEngine.evaluate(new int[]{5, 10}, new int[]{5, 20}, 1).success());
    }
    @Test void weightedRollSelectsFishInsideRolledRarity() {
        var fish = List.of(
                new FishingEngine.FishDefinition("common", FishingEngine.Rarity.COMMON, 1, 2, 10),
                new FishingEngine.FishDefinition("artifact", FishingEngine.Rarity.ARTIFACT, 5, 10, 1000));
        var weights = java.util.Map.of(FishingEngine.Rarity.COMMON, 100, FishingEngine.Rarity.ARTIFACT, 1);
        var caught = FishingEngine.rollWeighted(fish, weights, 0.999, 0.1, 0.5);
        assertEquals(FishingEngine.Rarity.ARTIFACT, caught.rarity());
    }
    @Test void baitIsUsedOnlyOnSuccessfulCatch() {
        assertFalse(FishingEngine.consumeBaitOnSuccessfulCatch(false));
        assertTrue(FishingEngine.consumeBaitOnSuccessfulCatch(true));
    }
}
