package neoforge.donjonmc.spell;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.spell.class_spell.AssassinBlinkSpell;
import neoforge.donjonmc.spell.class_spell.HealerAuraSpell;
import neoforge.donjonmc.spell.class_spell.MageArcaneDelugeSpell;
import neoforge.donjonmc.spell.class_spell.TankShieldSpell;
import neoforge.donjonmc.spell.rank_spell.RankAThunderSpell;
import neoforge.donjonmc.spell.rank_spell.RankBFrostSpell;
import neoforge.donjonmc.spell.rank_spell.RankCFlameSpell;
import neoforge.donjonmc.spell.rank_spell.RankDShockwaveSpell;
import neoforge.donjonmc.spell.rank_spell.RankSShadowVeilSpell;

import java.util.function.Supplier;

public final class DonjonSpellRegistry {

    private DonjonSpellRegistry() {}

    public static final DeferredRegister<AbstractSpell> SPELLS =
        DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, Donjonmc.MODID);

    // ── Sorts de classe ───────────────────────────────────────────────────────
    public static final Supplier<AbstractSpell> TANK_SHIELD      = register(new TankShieldSpell());
    public static final Supplier<AbstractSpell> ASSASSIN_BLINK   = register(new AssassinBlinkSpell());
    public static final Supplier<AbstractSpell> MAGE_DELUGE      = register(new MageArcaneDelugeSpell());
    public static final Supplier<AbstractSpell> HEALER_AURA      = register(new HealerAuraSpell());

    // ── Sorts de rang ─────────────────────────────────────────────────────────
    public static final Supplier<AbstractSpell> RANK_D_SHOCKWAVE  = register(new RankDShockwaveSpell());
    public static final Supplier<AbstractSpell> RANK_C_FLAME      = register(new RankCFlameSpell());
    public static final Supplier<AbstractSpell> RANK_B_FROST      = register(new RankBFrostSpell());
    public static final Supplier<AbstractSpell> RANK_A_THUNDER    = register(new RankAThunderSpell());
    public static final Supplier<AbstractSpell> RANK_S_SHADOW     = register(new RankSShadowVeilSpell());

    private static Supplier<AbstractSpell> register(AbstractSpell spell) {
        return SPELLS.register(spell.getSpellName(), () -> spell);
    }

    public static void register(IEventBus bus) {
        SPELLS.register(bus);
    }
}
