package id.octanexus.octatycoon.guild;

import java.util.Locale;

/** Guild permission and capacity rules. Persistence/invitation transport is handled by the repository layer. */
public final class GuildEngine {
    private GuildEngine() {}
    public enum Role { MEMBER, OFFICER, LEADER }
    public enum Action { INVITE, KICK, PROMOTE, DEMOTE, WITHDRAW_BANK, DEPOSIT_BANK, TRANSFER_LEADERSHIP, DISBAND, EDIT_MOTD }

    public static boolean can(Role role, Action action) {
        if (role == null || action == null) return false;
        return switch (action) {
            case DEPOSIT_BANK -> true;
            case INVITE, KICK, PROMOTE, DEMOTE, WITHDRAW_BANK, EDIT_MOTD -> role.ordinal() >= Role.OFFICER.ordinal();
            case TRANSFER_LEADERSHIP, DISBAND -> role == Role.LEADER;
        };
    }
    public static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }
    public static boolean validName(String name) { return name != null && name.matches("[A-Za-z0-9_-]{3,16}"); }
}
