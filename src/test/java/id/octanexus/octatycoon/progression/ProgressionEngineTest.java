package id.octanexus.octatycoon.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionEngineTest {
    @Test void addsXpAndLevelsWithoutExceedingCap() {
        var result = ProgressionEngine.addXp(1, 0, 250, 2, level -> 100);
        assertEquals(2, result.newLevel());
        assertEquals(0, result.remainingXp());
        assertEquals(1, result.levelsGained());
    }
    @Test void masteryUsesHoeAndPickaxeMilestones() { assertEquals(5, ProgressionEngine.masteryPoints(250, 300)); }
    @Test void combinesLargestBenefitAndHalfOfSmaller() {
        var result = ProgressionEngine.combine(new ProgressionEngine.RankBonus(.10,.10,.10,2,2), new ProgressionEngine.RankBonus(.20,.04,.06,5,3));
        assertEquals(.25, result.xp(), 0.00001);
        assertEquals(.12, result.token(), 0.00001);
        assertEquals(7, result.generatorSlots());
        assertEquals(3, result.ringSlots());
    }
}
