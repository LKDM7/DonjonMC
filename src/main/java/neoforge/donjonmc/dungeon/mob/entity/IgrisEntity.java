package neoforge.donjonmc.dungeon.mob.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import neoforge.donjonmc.dungeon.mob.DungeonMob;

public class IgrisEntity extends DungeonMob {

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
}
