package neoforge.donjonmc.raid;

public enum RaidRole {
    NONE    ("—",        0xFF888888),
    VANGUARD("Vanguard", 0xFF4488FF),
    STRIKER ("Striker",  0xFFFF5555),
    SUPPORT ("Support",  0xFF55FF88),
    PORTER  ("Porter",   0xFFFFAA44);

    public final String displayName;
    public final int    color;

    RaidRole(String displayName, int color) {
        this.displayName = displayName;
        this.color       = color;
    }

    public String langKey() {
        return "donjonmc.raid.role." + name().toLowerCase();
    }

    public static RaidRole fromOrdinal(int ord) {
        RaidRole[] v = values();
        return (ord >= 0 && ord < v.length) ? v[ord] : NONE;
    }
}
