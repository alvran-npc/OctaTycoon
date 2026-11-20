package id.octanexus.octatycoon.event;

import id.octanexus.octatycoon.task.GuardLevel;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class EventRegistryTest {
    @Test void selectsOnlyEventsThatPassOnlineAndGuardGates() {
        var registry = new EventRegistry();
        registry.register(new EventRegistry.Definition("storm", "gens", 1, 1200, 20, 5, GuardLevel.YELLOW, 2));
        assertNull(registry.choose("gens", 2, GuardLevel.GREEN, new Random(1)));
        assertNull(registry.choose("gens", 10, GuardLevel.ORANGE, new Random(1)));
        assertNotNull(registry.choose("gens", 10, GuardLevel.YELLOW, new Random(1)));
    }
}
