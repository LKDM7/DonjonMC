package neoforge.donjonmc.dungeon.mob.entity;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import neoforge.donjonmc.dungeon.mob.DungeonMob;

public class IgrisEntity extends DungeonMob {

    /** Empêche le sursaut de Résistance de se redéclencher à chaque tick sous 50 % PV. */
    private boolean enrageTriggered = false;

    public IgrisEntity(EntityType<? extends DungeonMob> type, Level level) {
        super(type, level, "igris", "dark_igris");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 30.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    public void tick() {
        super.tick();
        // Sursaut défensif : sous 50 % de PV, Résistance II pendant 30 s, une seule fois.
        if (!enrageTriggered && !level().isClientSide
                && getHealth() < getMaxHealth() * 0.5f) {
            enrageTriggered = true;
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 1, false, true));
        }
    }
}
