package neoforge.donjonmc.client.model;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class DungeonMobModel extends GeoModel<DungeonMob> {

    @Override
    public ResourceLocation getModelResource(DungeonMob entity) {
        return ResourceLocation.fromNamespaceAndPath("donjonmc",
                "geo/" + entity.getGeoName() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DungeonMob entity) {
        return ResourceLocation.fromNamespaceAndPath("donjonmc",
                "textures/entity/" + entity.getTextureName() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(DungeonMob entity) {
        return ResourceLocation.fromNamespaceAndPath("donjonmc",
                "animations/" + entity.getGeoName() + ".animation.json");
    }
}
