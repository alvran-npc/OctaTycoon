package id.octanexus.octatycoon.meta;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetaEngineTest {
    @Test void battlePassFormulaAndLevelAreMonotonic() {
        assertEquals(0, MetaEngine.battlePassXpRequiredForLevel(0));
        assertTrue(MetaEngine.battlePassXpRequiredForLevel(10) > MetaEngine.battlePassXpRequiredForLevel(9));
        assertEquals(10, MetaEngine.battlePassLevel(MetaEngine.battlePassXpRequiredForLevel(10), 100));
    }
    @Test void unlocksSixSkillTreePagesAtConfiguredLevels() {
        assertEquals(6, MetaEngine.classPageRequiredLevel(2));
        assertEquals(42, MetaEngine.classPageRequiredLevel(6));
    }
}
