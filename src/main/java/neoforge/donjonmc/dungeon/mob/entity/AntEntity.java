package neoforge.donjonmc.dungeon.mob.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

public class AntEntity extends DungeonMob {

    private static final RawAnimation ANT_IDLE = RawAnimation.begin().thenLoop("Idle");
    private static final RawAnimation ANT_WALK = RawAnimation.begin().thenLoop("walk");

    public AntEntity(EntityType<? extends DungeonMob> type, Level level) {
        super(type, level, "ant", "ant");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.7)
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 2.0);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, state -> {
            if (state.isMoving()) return state.setAndContinue(ANT_WALK);
            return state.setAndContinue(ANT_IDLE);
        }));
    }
}
