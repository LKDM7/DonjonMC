package neoforge.donjonmc.punishment;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import neoforge.donjonmc.dungeon.mob.DungeonMob;

public class SandWormEntity extends DungeonMob {

    public SandWormEntity(EntityType<? extends DungeonMob> type, Level level) {
        super(type, level, "centipedo", "centipie");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.MAX_HEALTH,     60.0)
                .add(Attributes.ATTACK_DAMAGE,   8.0)
                .add(Attributes.ARMOR,          10.0)
                .add(Attributes.FOLLOW_RANGE,   64.0);
    }
}
