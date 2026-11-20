package id.octanexus.octatycoon.gens;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratorEngineTest {
    @Test void productionUsesElapsedTimeAndStorageCap() {
        var state = new GeneratorEngine.GeneratorState(1, 0, 100, 0, 1_000);
        var result = GeneratorEngine.calculate(state, 3_601_000, 120, 50, false, 1);
        assertEquals(50, result.produced());
        assertEquals(50, result.newStored());
    }
    @Test void highTierAndStarsIncreaseOutputMultiplier() {
        assertTrue(GeneratorEngine.tierMultiplier(40) > GeneratorEngine.tierMultiplier(30));
    }
}
