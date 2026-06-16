package neoforge.donjonmc.player;

public enum StatType {
    STRENGTH    ("Force"),
    AGILITY     ("Agilité"),
    VITALITY    ("Vitalité"),
    INTELLIGENCE("Intelligence"),
    PERCEPTION  ("Perception");

    /** Valeur maximale qu'une stat peut atteindre (alignée sur la jauge du HunterScreen). */
    public static final int MAX = 60;

    public final String displayName;

    StatType(String displayName) {
        this.displayName = displayName;
    }

    public String langKey() {
        return "donjonmc.stat." + name().toLowerCase();
    }
}
