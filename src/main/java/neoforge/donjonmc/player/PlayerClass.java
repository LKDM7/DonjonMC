package neoforge.donjonmc.player;

public enum PlayerClass {
    NONE      ("Aucune",     "Atteignez le niveau 50 pour choisir votre classe.",   0xFF888888),
    TANK      ("Tank",       "Maître de la résistance et du bouclier de l'équipe.", 0xFFFF4500),
    ASSASSIN  ("Assassin",   "Ombre et vitesse — frappe avant d'être vu.",          0xFFAA00AA),
    MAGE      ("Mage",       "Maître de l'arcane, réserve de mana colossale.",      0xFF4169E1),
    HEALER    ("Guérisseur", "Gardien de la vie et de la régénération.",            0xFF00CC44);

    public final String displayName;
    public final String description;
    public final int    color;

    PlayerClass(String displayName, String description, int color) {
        this.displayName = displayName;
        this.description = description;
        this.color       = color;
    }

    public String nameLangKey() {
        return "donjonmc.class." + name().toLowerCase() + ".name";
    }

    public String descLangKey() {
        return "donjonmc.class." + name().toLowerCase() + ".desc";
    }

    public static PlayerClass fromOrdinal(int ord) {
        PlayerClass[] v = values();
        return (ord >= 0 && ord < v.length) ? v[ord] : NONE;
    }
}
