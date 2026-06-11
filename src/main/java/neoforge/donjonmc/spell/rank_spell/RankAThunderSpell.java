package neoforge.donjonmc.spell.rank_spell;

import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
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
 * Sort rang A — "Foudre du Chasseur"
 * Frappe (1+niveau) cibles avec la foudre, en chaîne entre les plus proches.
 * Rang requis: A (niveau 40+)
 */
public class RankAThunderSpell extends AbstractSpell {

    private static final ResourceLocation LIGHTNING = ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lightning");

    { this.baseManaCost = 55; this.manaCostPerLevel = 10; }

    private final DefaultConfig defaultConfig = new DefaultConfig()
        .setMinRarity(SpellRarity.EPIC)
        .setSchoolResource(LIGHTNING)
        .setMaxLevel(5)
        .setCooldownSeconds(10)
        .build();

    @Override
    public DefaultConfig getDefaultConfig() { return defaultConfig; }

    @Override
    public ResourceLocation getSpellResource() {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "rank_a_thunder");
    }

    @Override public CastType getCastType() { return CastType.INSTANT; }
    @Override public AnimationHolder getCastStartAnimation() { return SpellAnimations.OVERHEAD_MELEE_SWING_ANIMATION; }
    @Override public AnimationHolder getCastFinishAnimation() { return AnimationHolder.pass(); }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, LivingEntity caster) {
        return List.of(
            Component.translatable("spell.donjonmc.rank_a_thunder.desc"),
            Component.translatable("ui.irons_spellbooks.max_victims", 1 + spellLevel),
            Component.translatable("ui.irons_spellbooks.distance", 30)
        );
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity,
                       CastSource castSource, MagicData playerMagicData) {
        if (!(level instanceof ServerLevel sl)) {
            super.onCast(level, spellLevel, entity, castSource, playerMagicData);
            return;
        }

        int targets = 1 + spellLevel;

        sl.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(30.0))
            .stream()
            .sorted(Comparator.comparingDouble(m -> m.distanceToSqr(entity)))
            .limit(targets)
            .forEach(mob -> {
                // Foudre cosmétique (ne détruit pas les blocs, ne propage pas le feu)
                net.minecraft.world.entity.LightningBolt bolt =
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
                if (bolt != null) {
                    bolt.moveTo(mob.getX(), mob.getY(), mob.getZ());
                    bolt.setVisualOnly(false);
                    sl.addFreshEntity(bolt);
                }
            });

        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }
}
