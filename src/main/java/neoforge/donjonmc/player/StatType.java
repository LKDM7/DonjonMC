package neoforge.donjonmc.player;

public enum StatType {
    STRENGTH    ("Force"),
    AGILITY     ("Agilité"),
    VITALITY    ("Vitalité"),
    INTELLIGENCE("Intelligence"),
    PERCEPTION  ("Perception");

    public final String displayName;

    StatType(String displayName) {
        this.displayName = displayName;
    }

    public String langKey() {
        return "donjonmc.stat." + name().toLowerCase();
    }
}
