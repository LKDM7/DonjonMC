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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/**
 * Sort rang C — "Flamme du Donjon"
 * Embrase la cible la plus proche dans la direction du regard et inflige des dégâts de feu.
 * Rang requis: C (niveau 20+)
 */
public class RankCFlameSpell extends AbstractSpell {

    private static final ResourceLocation FIRE = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "fire");

    { this.baseManaCost = 30; this.manaCostPerLevel = 7; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.UNCOMMON)
        .setSchoolResource(FIRE)
        .setMaxLevel(5)
        .setCooldownSeconds(8)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "rank_c_flame");
    }

    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.ANIMATION_INSTANT_CAST; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.rank_c_flame.desc"),
            Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(10f + spellLevel * 6f, 1)),
            Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks((8 + spellLevel * 4) * 20, 1)),
            Component.translatable("ui.irons_spellbooks.distance", 20)
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        var target = level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(20.0))
            .stream()
            .min(Comparator.comparingDouble(m -> m.distanceToSqr(entity)))
            .orElse(null);

        if (target != null) {
            target.igniteForSeconds(8 + spellLevel * 4);
            target.hurt(entity.damageSources().onFire(), 10f + spellLevel * 6f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FLAME,
                    target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
                sl.sendParticles(ParticleTypes.LAVA,
                    target.getX(), target.getY() + 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
            }
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
