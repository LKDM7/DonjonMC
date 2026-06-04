package neoforge.donjonmc.dungeon;

import net.minecraft.util.RandomSource;

public enum DungeonTheme {
    GOBLINS,
    GOBLIN,
    FOREST,
    HOBGOBLIN,
    GOLEMS,
    DEEP,
    NETHER,
    CASTLE,
    ICE,
    ANT,
    HIGH_ORC,
    ORC,
    SPIDER,
    URBAN,
    TEMPLE;

    public String langKey() { return "donjonmc.dungeon.theme." + name().toLowerCase(); }

    public static DungeonTheme random(RandomSource rng) {
        DungeonTheme[] v = values();
        return v[rng.nextInt(v.length)];
    }

    public static DungeonTheme forRank(DungeonRank rank, RandomSource rng) {
        DungeonTheme[] pool = switch (rank) {
            case D -> new DungeonTheme[]{GOBLINS, GOBLIN, FOREST, SPIDER};
            case C -> new DungeonTheme[]{HOBGOBLIN, GOBLIN, ORC, FOREST};
            case B -> new DungeonTheme[]{GOLEMS, DEEP, HIGH_ORC, ORC, HOBGOBLIN};
            case A -> new DungeonTheme[]{CASTLE, NETHER, ICE, URBAN};
            case S -> new DungeonTheme[]{ANT, CASTLE, ICE, TEMPLE};
        };
        return pool[rng.nextInt(pool.length)];
    }

    /** Tente de parser depuis un nom (insensible à la casse). Retourne null si inconnu. */
    public static DungeonTheme fromName(String name) {
        for (DungeonTheme t : values()) {
            if (t.name().equalsIgnoreCase(name)) return t;
        }
        return null;
    }
}
