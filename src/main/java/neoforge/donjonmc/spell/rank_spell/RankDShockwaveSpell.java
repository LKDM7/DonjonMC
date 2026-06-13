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
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sort rang D — "Onde Primitive"
 * Repousse et inflige de légers dégâts à tous les ennemis proches.
 * Rang requis: D (niveau 10+)
 */
public class RankDShockwaveSpell extends AbstractSpell {

    private static final ResourceLocation EVOCATION = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "evocation");

    { this.baseManaCost = 20; this.manaCostPerLevel = 5; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.COMMON)
        .setSchoolResource(EVOCATION)
        .setMaxLevel(3)
        .setCooldownSeconds(15)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "rank_d_shockwave");
    }

    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.OVERHEAD_MELEE_SWING_ANIMATION; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.rank_d_shockwave.desc"),
            Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(3f + spellLevel * 2f, 1)),
            Component.translatable("ui.irons_spellbooks.radius", (int) (5.0 + spellLevel))
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        double radius = 5.0 + spellLevel;
        float  damage = 3f + spellLevel * 2f;

        for (int deg = 0; deg < 360; deg += 12) {
            double rad = Math.toRadians(deg);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.CRIT,
                    entity.getX() + Math.cos(rad) * (radius - 1),
                    entity.getY() + 0.5,
                    entity.getZ() + Math.sin(rad) * (radius - 1),
                    1, 0.05, 0.05, 0.05, 0.02);
            }
        }

        level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(radius))
            .forEach(mob -> {
                DamageSources.applyDamage(mob, damage, this.getDamageSource(entity));
                mob.knockback(2.0 + spellLevel * 0.3,
                    entity.getX() - mob.getX(), entity.getZ() - mob.getZ());
            });

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
