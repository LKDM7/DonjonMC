package neoforge.donjonmc.spell.class_spell;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/**
 * Sort de classe Mage — "Déluge Arcanique"
 * Dégâts magiques sur (2+niveau) ennemis les plus proches (<25 blocs).
 * Classe: Mage | School: Ender
 */
public class MageArcaneDelugeSpell extends AbstractSpell {

    private static final ResourceLocation ENDER = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "ender");

    { this.baseManaCost = 55; this.manaCostPerLevel = 8; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.UNCOMMON)
        .setSchoolResource(ENDER)
        .setMaxLevel(7)
        .setCooldownSeconds(31)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    // Réservé à l'épreuve de classe : ni loot aléatoire ni fabrication possible.
    @Override public boolean allowLooting()  { return false; }
    @Override public boolean allowCrafting() { return false; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "mage_arcane_deluge");
    }

    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.CAST_T_POSE; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.mage_arcane_deluge.desc"),
            Component.translatable("ui.irons_spellbooks.damage", Utils.stringTruncation(12f + spellLevel * 6f, 1)),
            Component.translatable("ui.irons_spellbooks.max_victims", 3 + spellLevel),
            Component.translatable("ui.irons_spellbooks.distance", 30)
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        int maxTargets = 3 + spellLevel;
        float damage    = 12f + spellLevel * 6f;

        var targets = level.getEntitiesOfClass(LivingEntity.class,
                entity.getBoundingBox().inflate(30.0), e -> e instanceof Monster)
            .stream()
            .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(entity)))
            .limit(maxTargets)
            .toList();

        for (LivingEntity t : targets) {
            DamageSources.applyDamage(t, damage, this.getDamageSource(entity));
            // Embrasement arcanique + affaiblissement sur chaque cible touchée
            t.setRemainingFireTicks(100);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,         100, 1, false, true));
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.WITCH,
                    t.getX(), t.getY() + 1, t.getZ(), 12, 0.3, 0.4, 0.3, 0.08);
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    t.getX(), t.getY() + 1, t.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
            }
        }

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
