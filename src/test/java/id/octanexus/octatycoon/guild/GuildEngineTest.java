package id.octanexus.octatycoon.guild;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildEngineTest {
    @Test void memberCanDepositButCannotWithdraw() {
        assertTrue(GuildEngine.can(GuildEngine.Role.MEMBER, GuildEngine.Action.DEPOSIT_BANK));
        assertFalse(GuildEngine.can(GuildEngine.Role.MEMBER, GuildEngine.Action.WITHDRAW_BANK));
        assertTrue(GuildEngine.can(GuildEngine.Role.LEADER, GuildEngine.Action.DISBAND));
    }
}
