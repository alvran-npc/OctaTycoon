package id.octanexus.octatycoon.mining;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningEngineTest {
    @Test void overBandOreGivesHalfXp() {
        var ore = new MiningEngine.OreBand("coal", 10, 20, 10, 5);
        assertEquals(10, MiningEngine.mine(ore, 20, true, 1).xp());
        assertEquals(5, MiningEngine.mine(ore, 21, true, 1).xp());
    }
    @Test void heatCanTriggerRush() {
        var heat = new MiningEngine.HeatTracker();
        long now = 10_000;
        for (int i = 0; i < 100; i++) heat.addAction("player", now);
        assertTrue(heat.startRush("player", now));
        assertEquals(0, heat.current("player", now));
    }
}
