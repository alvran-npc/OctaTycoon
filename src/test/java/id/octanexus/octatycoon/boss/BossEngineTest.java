package id.octanexus.octatycoon.boss;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BossEngineTest {
    @Test void distributesParticipantAndContributionPoolsWithBonuses() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var shares = BossEngine.distribute(Map.of(a, 100L, b, 50L), a, 1000, false);
        assertEquals(850, shares.get(a), 0.001);
        assertEquals(400, shares.get(b), 0.001);
    }
}
