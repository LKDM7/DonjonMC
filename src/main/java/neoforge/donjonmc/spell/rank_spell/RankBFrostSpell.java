package neoforge.donjonmc.spell.rank_spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * Sort rang B — "Tempête Givrée"
 * Ralentit et inflige des dégâts à tous les ennemis dans un vaste rayon.
 * Rang requis: B (niveau 30+)
 */
public class RankBFrostSpell extends AbstractSpell {

    private static final ResourceLocation ICE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ice");

    { this.baseManaCost = 45; this.manaCostPerLevel = 9; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(ICE)
        .setMaxLevel(5)
        .setCooldownSeconds(14)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "rank_b_frost");
    }

    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.CHARGE_RAISED_HAND; }
    @Override public AnimationHolder getCastFinishAnimation() { return SpellAnimations.ANIMATION_LONG_CAST_FINISH; }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        double radius  = 8.0 + spellLevel * 2.0;
        int    slowAmp = Math.min(1 + spellLevel, 5);
        int    dur     = (4 + spellLevel * 2) * 20;
        float  damage  = 6f + spellLevel * 4f;

        level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(radius))
            .forEach(mob -> {
                mob.hurt(entity.damageSources().magic(), damage);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, slowAmp, false, true));
            });

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SNOWFLAKE,
                entity.getX(), entity.getY() + 1, entity.getZ(),
                60, radius * 0.7, 1.0, radius * 0.7, 0.02);
            sl.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                entity.getX(), entity.getY() + 1, entity.getZ(),
                30, radius * 0.5, 0.8, radius * 0.5, 0.1);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
