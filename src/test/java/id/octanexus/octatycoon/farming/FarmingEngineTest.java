package id.octanexus.octatycoon.farming;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarmingEngineTest {
    @Test void cropGatesAndMaturityAreEnforced() {
        assertFalse(FarmingEngine.harvest(FarmingEngine.Crop.CARROT, 699, 7, 3, 2, 1).allowed());
        assertFalse(FarmingEngine.harvest(FarmingEngine.Crop.WHEAT, 1, 6, 1, 1, 1).allowed());
        assertTrue(FarmingEngine.harvest(FarmingEngine.Crop.WHEAT, 1, 7, 1, 1, 1).allowed());
    }
    @Test void barnNeverOverflows() {
        var barn = new FarmingEngine.Barn(64);
        assertEquals(64, barn.add(100));
        assertEquals(64, barn.stored());
        assertEquals(20, barn.take(20));
        assertEquals(44, barn.takeAll());
    }
}
