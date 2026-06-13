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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

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
        .setCooldownSeconds(30)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    // Réservé à l'épreuve de classe : ni loot aléatoire ni fabrication possible.
    @Override public boolean allowLooting()  { return false; }
    @Override public boolean allowCrafting() { return false; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "healer_aura");
    }

    @Override public CastType getCastType() { return CastType.LONG; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.CAST_KNEELING_PRAYER; }
    @Override public AnimationHolder getCastFinishAnimation() { return SpellAnimations.ANIMATION_LONG_CAST_FINISH; }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.healer_aura.desc"),
            Component.translatable("ui.irons_spellbooks.healing", Utils.stringTruncation(6f + spellLevel * 5f, 1)),
            Component.translatable("ui.irons_spellbooks.radius", (int) (12.0 + spellLevel * 3.0)),
            Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks((5 + spellLevel * 3) * 20, 1))
        );
    }

    /** Purge tous les effets néfastes d'une entité. */
    private static void cleanseHarmful(LivingEntity e) {
        e.getActiveEffects().stream()
            .map(MobEffectInstance::getEffect)
            .filter(h -> !h.value().isBeneficial())
            .toList()
            .forEach(e::removeEffect);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        double radius   = 12.0 + spellLevel * 3.0;
        float  heal     = 6f + spellLevel * 5f;
        int    regenDur = (5 + spellLevel * 3) * 20;

        // Soigne, purge les malus, Régénération + bouclier d'absorption au lanceur
        entity.heal(heal);
        cleanseHarmful(entity);
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDur, spellLevel + 1, false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,   regenDur, 1,              false, true));

        // Même soutien sur les joueurs alliés à portée
        level.getEntitiesOfClass(Player.class, entity.getBoundingBox().inflate(radius),
                ally -> ally != entity)
            .forEach(ally -> {
                ally.heal(heal);
                cleanseHarmful(ally);
                ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenDur, spellLevel + 1, false, true));
                ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,   regenDur, 1,              false, true));
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
