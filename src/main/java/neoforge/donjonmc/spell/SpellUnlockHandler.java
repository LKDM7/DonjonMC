package neoforge.donjonmc.spell;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import neoforge.donjonmc.player.PlayerClass;

/**
 * Donne au joueur un parchemin de sort lorsqu'il atteint un rang ou complète l'épreuve de classe.
 */
public final class SpellUnlockHandler {

    private SpellUnlockHandler() {}

    /**
     * Appelé à chaque level-up. Donne le sort de rang lors du franchissement d'un seuil.
     */
    public static void onLevelUp(ServerPlayer player, int newLevel) {
        switch (newLevel) {
            case 10 -> giveScroll(player, DonjonSpellRegistry.RANK_D_SHOCKWAVE.get(), 1,
                "donjonmc.spell.rank_unlock", "D");
            case 20 -> giveScroll(player, DonjonSpellRegistry.RANK_C_FLAME.get(), 1,
                "donjonmc.spell.rank_unlock", "C");
            case 30 -> giveScroll(player, DonjonSpellRegistry.RANK_B_FROST.get(), 1,
                "donjonmc.spell.rank_unlock", "B");
            case 40 -> giveScroll(player, DonjonSpellRegistry.RANK_A_THUNDER.get(), 1,
                "donjonmc.spell.rank_unlock", "A");
            case 60 -> giveScroll(player, DonjonSpellRegistry.RANK_S_SHADOW.get(), 1,
                "donjonmc.spell.rank_unlock", "S");
        }
    }

    /**
     * Appelé quand l'épreuve de classe est complétée. Donne le sort de la classe attribuée.
     */
    public static void onClassUnlocked(ServerPlayer player, PlayerClass cls) {
        AbstractSpell spell = switch (cls) {
            case TANK     -> DonjonSpellRegistry.TANK_SHIELD.get();
            case ASSASSIN -> DonjonSpellRegistry.ASSASSIN_BLINK.get();
            case MAGE     -> DonjonSpellRegistry.MAGE_DELUGE.get();
            case HEALER   -> DonjonSpellRegistry.HEALER_AURA.get();
            default       -> null;
        };
        if (spell == null) return;
        giveScroll(player, spell, 1, "donjonmc.spell.class_unlock", cls.name());
    }

    private static void giveScroll(ServerPlayer player, AbstractSpell spell,
                                   int level, String langKey, String arg) {
        ItemStack scroll = createScroll(spell, level);
        if (!player.addItem(scroll)) {
            // Inventaire plein → drop au sol
            player.drop(scroll, false);
        }
        player.sendSystemMessage(Component.translatable(langKey, arg,
            Component.translatable(spell.getComponentId())));
    }

    /**
     * Crée un parchemin ISB contenant le sort donné au niveau indiqué.
     */
    public static ItemStack createScroll(AbstractSpell spell, int level) {
        ItemStack stack = new ItemStack(ItemRegistry.SCROLL.get());
        ISpellContainer.createScrollContainer(spell, level, stack);
        return stack;
    }
}
