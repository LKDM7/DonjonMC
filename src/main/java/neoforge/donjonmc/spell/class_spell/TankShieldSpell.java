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
import net.minecraft.world.level.Level;

import java.util.List;

/** Sort de classe Tank — "Rempart du Gardien" (Holy, INSTANT) */
public class TankShieldSpell extends AbstractSpell {

    private static final ResourceLocation HOLY = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy");

    { this.baseManaCost = 50; this.manaCostPerLevel = 10; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.RARE)
        .setSchoolResource(HOLY)
        .setMaxLevel(5)
        .setCooldownSeconds(30)
        .build();

    @Override public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "tank_shield");
    }
    @Override public DefaultConfig getDefaultConfig() { return defaultConfig; }

    // Réservé à l'épreuve de classe : ni loot aléatoire ni fabrication possible.
    @Override public boolean allowLooting()  { return false; }
    @Override public boolean allowCrafting() { return false; }
    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.SELF_CAST_TWO_HANDS; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.tank_shield.desc"),
            Component.translatable("ui.irons_spellbooks.damage_reduction", Math.min((spellLevel + 1) * 20, 100)),
            Component.translatable("ui.irons_spellbooks.absorption", (spellLevel + 3) * 4),
            Component.translatable("ui.irons_spellbooks.effect_length", Utils.timeFromTicks((8 + spellLevel * 4) * 20, 1))
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        int duration = (8 + spellLevel * 4) * 20;
        // Mur défensif endgame : Résistance + grosse Absorption + auto-régen + ignifuge.
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, spellLevel,     false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,        duration, spellLevel + 2, false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION,      duration, 1,              false, true));
        entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,   duration, 0,              false, true));
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                entity.getX(), entity.getY() + 1, entity.getZ(), 40, 0.5, 1.0, 0.5, 0.1);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
