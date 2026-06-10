package neoforge.donjonmc.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public class PlayerData {

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("level").forGetter(d -> d.level),
            Codec.LONG.fieldOf("xp").forGetter(d -> d.xp),
            Codec.INT.fieldOf("skillPoints").forGetter(d -> d.skillPoints),
            Codec.INT.fieldOf("strength").forGetter(d -> d.strength),
            Codec.INT.fieldOf("agility").forGetter(d -> d.agility),
            Codec.INT.fieldOf("vitality").forGetter(d -> d.vitality),
            Codec.INT.fieldOf("intelligence").forGetter(d -> d.intelligence),
            Codec.INT.fieldOf("perception").forGetter(d -> d.perception),
            Codec.BOOL.fieldOf("initialized").forGetter(d -> d.initialized),
            Codec.FLOAT.optionalFieldOf("mana", 100f).forGetter(d -> 100f),
            Codec.INT.optionalFieldOf("playerClassOrdinal", 0).forGetter(d -> d.playerClassOrdinal),
            Codec.BOOL.optionalFieldOf("speedEnabled", true).forGetter(d -> d.speedEnabled),
            Codec.LONG.optionalFieldOf("lastRespecMs", 0L).forGetter(d -> d.lastRespecMs),
            Codec.LONG.optionalFieldOf("lastTrialFailMs", 0L).forGetter(d -> d.lastTrialFailMs),
            Codec.BOOL.optionalFieldOf("perceptionEnabled", true).forGetter(d -> d.perceptionEnabled),
            Codec.INT.optionalFieldOf("dungeonsCleared", 0).forGetter(d -> d.dungeonsCleared)
        ).apply(instance, PlayerData::new)
    );

    private int level;
    private long xp;
    private int skillPoints;
    private int strength;
    private int agility;
    private int vitality;
    private int intelligence;
    private int perception;
    private boolean initialized;
    private int playerClassOrdinal;
    private boolean speedEnabled;
    private long lastRespecMs; // timestamp IRL (ms) du dernier respec joueur ; 0 = jamais
    private long lastTrialFailMs; // timestamp IRL (ms) du dernier échec d'épreuve de classe ; 0 = jamais
    private boolean perceptionEnabled; // toggle de l'effet Glowing de Perception
    private int dungeonsCleared; // compteur carrière : donjons terminés (boss tué)

    public PlayerData() {
        this(0, 0L, 0, 0, 0, 0, 0, 0, false, 100f, 0, true, 0L, 0L, true, 0);
    }

    public PlayerData(int level, long xp, int skillPoints, int strength, int agility,
                      int vitality, int intelligence, int perception, boolean initialized,
                      float mana, int playerClassOrdinal, boolean speedEnabled, long lastRespecMs,
                      long lastTrialFailMs, boolean perceptionEnabled, int dungeonsCleared) {
        this.level              = level;
        this.xp                 = xp;
        this.skillPoints        = skillPoints;
        this.strength           = strength;
        this.agility            = agility;
        this.vitality           = vitality;
        this.intelligence       = intelligence;
        this.perception         = perception;
        this.initialized        = initialized;
        // mana intentionally discarded — managed by Iron's Spells 'n Spellbooks (MagicData)
        this.playerClassOrdinal = playerClassOrdinal;
        this.speedEnabled       = speedEnabled;
        this.lastRespecMs       = lastRespecMs;
        this.lastTrialFailMs    = lastTrialFailMs;
        this.perceptionEnabled  = perceptionEnabled;
        this.dungeonsCleared    = dungeonsCleared;
    }

    public int     getLevel()        { return level; }
    public long    getXp()           { return xp; }
    public int     getSkillPoints()  { return skillPoints; }
    public int     getStrength()     { return strength; }
    public int     getAgility()      { return agility; }
    public int     getVitality()     { return vitality; }
    public int     getIntelligence() { return intelligence; }
    public int     getPerception()   { return perception; }
    public boolean isInitialized()   { return initialized; }
    public int     getPlayerClassOrdinal() { return playerClassOrdinal; }
    public PlayerClass getPlayerClass()    { return PlayerClass.fromOrdinal(playerClassOrdinal); }
    public boolean isSpeedEnabled()        { return speedEnabled; }
    public long    getLastRespecMs()       { return lastRespecMs; }
    public void    setLastRespecMs(long v) { this.lastRespecMs = v; }
    public long    getLastTrialFailMs()       { return lastTrialFailMs; }
    public void    setLastTrialFailMs(long v) { this.lastTrialFailMs = v; }
    public boolean isPerceptionEnabled()         { return perceptionEnabled; }
    public void    setPerceptionEnabled(boolean v) { this.perceptionEnabled = v; }
    public int     getDungeonsCleared()          { return dungeonsCleared; }
    public void    incrementDungeonsCleared()    { this.dungeonsCleared++; }

    public void setLevel(int level)        { this.level = level; }
    public void setXp(long xp)            { this.xp = xp; }
    public void addSkillPoints(int amount) { this.skillPoints += amount; }
    public void setInitialized(boolean v)  { this.initialized = v; }
    public void setPlayerClass(PlayerClass cls)  { this.playerClassOrdinal = cls.ordinal(); }
    public void setSpeedEnabled(boolean v)       { this.speedEnabled = v; }

    public void setStrength(int v)     { this.strength = v; }
    public void setAgility(int v)      { this.agility = v; }
    public void setVitality(int v)     { this.vitality = v; }
    public void setIntelligence(int v) { this.intelligence = v; }
    public void setPerception(int v)   { this.perception = v; }

    public void spendSkillPoint() {
        if (this.skillPoints > 0) this.skillPoints--;
    }

    public long xpForNextLevel() { return LevelHelper.xpRequired(level); }
}
