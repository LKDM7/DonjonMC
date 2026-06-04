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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.Comparator;

/**
 * Sort de classe Assassin — "Frappe de l'Ombre"
 * Téléportation vers l'ennemi le plus proche (<15+niveau×2 blocs), dégâts + aveuglement.
 * Classe: Assassin | School: Ender
 */
public class AssassinBlinkSpell extends AbstractSpell {

    private static final ResourceLocation ENDER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ender");

    { this.baseManaCost = 35; this.manaCostPerLevel = 8; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(ENDER)
        .setMaxLevel(5)
        .setCooldownSeconds(12)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "assassin_blink");
    }

    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.SLASH_ANIMATION; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        double radius = 15.0 + spellLevel * 2.0;

        var target = level.getEntitiesOfClass(LivingEntity.class,
                entity.getBoundingBox().inflate(radius), e -> e instanceof Monster)
            .stream()
            .min(Comparator.comparingDouble(e -> e.distanceToSqr(entity)))
            .orElse(null);

        if (target == null) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL,
                entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
        }

        // Téléportation derrière la cible
        double angle = target.getYRot() * Math.PI / 180.0;
        entity.teleportTo(target.getX() + Math.sin(angle), target.getY(), target.getZ() - Math.cos(angle));

        float damage = 10f + spellLevel * 5f;
        target.hurt(entity.damageSources().magic(), damage);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40 + spellLevel * 20, 0, false, true));

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL,
                entity.getX(), entity.getY() + 1, entity.getZ(), 20, 0.3, 0.5, 0.3, 0.3);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
