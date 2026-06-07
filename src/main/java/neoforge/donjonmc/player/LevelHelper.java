package neoforge.donjonmc.player;

public final class LevelHelper {

    private LevelHelper() {}

    /** Niveau maximum atteignable. National = 80+ ; on laisse une marge jusqu'à 100. */
    public static final int MAX_LEVEL = 100;

    /**
     * XP requis pour passer du niveau {@code currentLevel} au suivant.
     * Formule : 100 * (currentLevel + 1)^1.8
     *
     * Exemples :
     *   0 → 1 :   100 XP
     *   1 → 2 :   287 XP
     *   9 → 10 : 3 981 XP
     *  49 → 50 : 89 443 XP
     */
    public static long xpRequired(int currentLevel) {
        return (long) (100.0 * Math.pow(currentLevel + 1, 1.8));
    }

    /**
     * Modificateur de PV à appliquer en ADD_VALUE sur l'attribut MAX_HEALTH (base = 20).
     *
     * Résultat :
     *   niveau  0 → 20 + (-10) = 10 PV (5 cœurs)
     *   niveau  4 → 20 + (-8)  = 12 PV (6 cœurs)
     *   niveau 20 → 20 +   0   = 20 PV (10 cœurs)
     *   niveau 100 → 20 + 40   = 60 PV (30 cœurs)
     */
    public static String rankForLevel(int level) {
        if (level >= 80) return "National";
        if (level >= 60) return "S";
        if (level >= 40) return "A";
        if (level >= 30) return "B";
        if (level >= 20) return "C";
        if (level >= 10) return "D";
        return "E";
    }

    public static int rankColor(int level) {
        if (level >= 80) return 0xFFFFFF;
        if (level >= 60) return 0xFF5555;
        if (level >= 40) return 0xFFAA00;
        if (level >= 30) return 0xAA00AA;
        if (level >= 20) return 0x5555FF;
        if (level >= 10) return 0x55FF55;
        return 0xAAAAAA;
    }

    public static double healthModifier(int level) {
        return -10.0 + level * 0.5;
    }

    /** Returns 0 (E) through 6 (National) — used for carry-bonus comparison. */
    public static int rankTier(int level) {
        if (level >= 80) return 6;
        if (level >= 60) return 5;
        if (level >= 40) return 4;
        if (level >= 30) return 3;
        if (level >= 20) return 2;
        if (level >= 10) return 1;
        return 0;
    }
}
