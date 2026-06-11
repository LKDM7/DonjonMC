package neoforge.donjonmc.spell.rank_spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sort rang S — "Voile des Ténèbres"
 * Applique Flétrissement, Aveuglement, Ralentissement et Incandescence à tous les ennemis proches.
 * Rang requis: S (niveau 60+)
 */
public class RankSShadowVeilSpell extends AbstractSpell {

    private static final ResourceLocation ENDER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ender");

    { this.baseManaCost = 80; this.manaCostPerLevel = 15; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.LEGENDARY)
        .setSchoolResource(ENDER)
        .setMaxLevel(5)
        .setCooldownSeconds(60)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "rank_s_shadow_veil");
    }

    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.PREPARE_CROSS_ARMS; }
    @Override public AnimationHolder getCastFinishAnimation() { return SpellAnimations.CAST_T_POSE; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.rank_s_shadow_veil.desc"),
            Component.translatable("ui.irons_spellbooks.radius", (int) (15.0 + spellLevel * 2.0)),
            Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks((8 + spellLevel * 2) * 20, 1))
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        double radius  = 15.0 + spellLevel * 2.0;
        int    dur     = (8 + spellLevel * 2) * 20;
        int    witherAmp = Math.min(spellLevel - 1, 2);

        level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(radius))
            .forEach(mob -> {
                mob.addEffect(new MobEffectInstance(MobEffects.WITHER,           dur, witherAmp, false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,        dur, 0,         false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,dur, 3,         false, true));
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING,          dur, 0,         false, false));
            });

        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.PORTAL,
                entity.getX(), entity.getY() + 1, entity.getZ(),
                80, radius * 0.6, 1.0, radius * 0.6, 0.15);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL,
                entity.getX(), entity.getY() + 1, entity.getZ(),
                40, radius * 0.4, 1.5, radius * 0.4, 0.1);
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
