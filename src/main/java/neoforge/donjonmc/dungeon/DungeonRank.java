package neoforge.donjonmc.dungeon;

import net.minecraft.util.RandomSource;

public enum DungeonRank {

    //        minPl  bossHP  xpReward  r     g     b      timeLimit(s)
    D(1,  200,   800,   0.20f, 0.33f, 1.00f, 2700),
    C(1,  500,   2000,  0.20f, 1.00f, 0.20f, 1800),
    B(1,  1000,  5000,  0.67f, 0.00f, 0.67f, 1500),
    A(1,  2000,  12000, 1.00f, 0.67f, 0.00f, 1200),
    S(4,  4000,  30000, 1.00f, 0.20f, 0.20f, 900);

    public final int   minPlayers;
    public final int   bossHealth;
    public final long  xpReward;
    /** Particle color components in [0,1] */
    public final float pr, pg, pb;
    /** Maximum time to complete the dungeon, in seconds. */
    public final int   timeLimitSeconds;

    DungeonRank(int minPlayers, int bossHealth, long xpReward, float r, float g, float b, int timeLimitSeconds) {
        this.minPlayers       = minPlayers;
        this.bossHealth       = bossHealth;
        this.xpReward         = xpReward;
        this.pr = r; this.pg = g; this.pb = b;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    public String langKey()    { return "donjonmc.dungeon.rank." + name(); }
    public String keyItemId()  { return "key_" + name().toLowerCase(); }

    /** Weighted random rank — higher ranks rarer. */
    public static DungeonRank random(RandomSource rng) {
        int roll = rng.nextInt(100);
        if (roll < 2)  return S;
        if (roll < 10) return A;
        if (roll < 25) return B;
        if (roll < 55) return C;
        return D;
    }

    public static DungeonRank fromOrdinal(int ord) {
        DungeonRank[] v = values();
        return (ord >= 0 && ord < v.length) ? v[ord] : D;
    }
}
