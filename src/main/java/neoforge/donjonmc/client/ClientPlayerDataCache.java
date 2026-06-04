package neoforge.donjonmc.client;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import neoforge.donjonmc.network.SyncPlayerDataPacket;
import neoforge.donjonmc.player.PlayerClass;
import neoforge.donjonmc.player.StatType;

public final class ClientPlayerDataCache {

    private ClientPlayerDataCache() {}

    public static int   level              = 0;
    public static long  xp                 = 0L;
    public static long  xpRequired         = 100L;
    public static int   skillPoints        = 0;
    public static int   strength           = 0;
    public static int   agility            = 0;
    public static int   vitality           = 0;
    public static int   intelligence       = 0;
    public static int   perception         = 0;
    public static int   playerClassOrdinal = 0;
    public static boolean speedEnabled     = true;

    public static PlayerClass playerClass() { return PlayerClass.fromOrdinal(playerClassOrdinal); }

    /** Mana actuel du joueur (source : Iron's Spells 'n Spellbooks). */
    public static float getMana() {
        return ClientMagicData.getPlayerMana();
    }

    /** Mana maximum du joueur via l'attribut ISB MAX_MANA. */
    public static float getMaxMana() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 100f;
        var attr = mc.player.getAttribute(AttributeRegistry.MAX_MANA);
        return attr != null ? (float) attr.getValue() : 100f;
    }

    private static int prevLevel = -1;

    public static void update(SyncPlayerDataPacket p) {
        int newLevel = p.level();
        if (prevLevel >= 0 && newLevel > prevLevel) {
            neoforge.donjonmc.client.hud.LevelUpPopupHud.trigger(newLevel);
        }
        prevLevel = newLevel;
        level              = p.level();
        xp                 = p.xp();
        xpRequired         = p.xpRequired();
        skillPoints        = p.skillPoints();
        strength           = p.strength();
        agility            = p.agility();
        vitality           = p.vitality();
        intelligence       = p.intelligence();
        perception         = p.perception();
        playerClassOrdinal = p.playerClassOrdinal();
        speedEnabled       = p.speedEnabled();
    }

    public static int getStat(StatType stat) {
        return switch (stat) {
            case STRENGTH     -> strength;
            case AGILITY      -> agility;
            case VITALITY     -> vitality;
            case INTELLIGENCE -> intelligence;
            case PERCEPTION   -> perception;
        };
    }
}
