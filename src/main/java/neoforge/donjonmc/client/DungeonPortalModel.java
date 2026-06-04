package neoforge.donjonmc.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.dungeon.DungeonPortalEntity;
import software.bernie.geckolib.model.GeoModel;

@OnlyIn(Dist.CLIENT)
public class DungeonPortalModel extends GeoModel<DungeonPortalEntity> {

    @Override
    public ResourceLocation getModelResource(DungeonPortalEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "geo/portal.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DungeonPortalEntity entity) {
        String tex = switch (entity.getRank()) {
            case A, S -> "portal_red";
            case B    -> "portal_purple";
            default   -> "portal";  // D, C
        };
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "textures/entity/" + tex + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(DungeonPortalEntity entity) {
        return ResourceLocation.fromNamespaceAndPath("donjonmc", "animations/portal.animation.json");
    }
}
