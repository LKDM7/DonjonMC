package neoforge.donjonmc.spell.class_spell;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Sort de classe Healer — "Aura de Vie"
 * Soigne le lanceur + alliés proches (joueurs), REGENERATION sur tous.
 * Classe: Healer | School: Holy
 */
public class HealerAuraSpell extends AbstractSpell {

    private static final ResourceLocation HOLY = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy");

    { this.baseManaCost = 60; this.manaCostPerLevel = 10; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(HOLY)
        .setMaxLevel(5)
        .setCooldownSeconds(20)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "healer_aura");
    }

    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.CAST_KNEELING_PRAYER; }
    @Override public AnimationHolder getCastFinishAnimation() { return SpellAnimations.ANIMATION_LONG_CAST_FINISH; }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        double radius   = 10.0 + spellLevel * 2.0;
        float  heal     = 4f + spellLevel * 3f;
        int    regenDur = (4 + spellLevel * 2) * 20;

        // Soigne et donne Régénération au lanceur
        entity.heal(heal);
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDur, spellLevel, false, true));

        // Effet sur les joueurs alliés à portée
        level.getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(radius),
                ally -> ally != entity)
            .forEach(ally -> {
                ally.heal(heal);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDur, spellLevel, false, true));
            });

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART,
                entity.getX(), entity.getY() + 1.5, entity.getZ(), 20, radius * 0.4, 0.5, radius * 0.4, 0.05);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                entity.getX(), entity.getY() + 0.5, entity.getZ(), 15, radius * 0.3, 0.3, radius * 0.3, 0.05);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
