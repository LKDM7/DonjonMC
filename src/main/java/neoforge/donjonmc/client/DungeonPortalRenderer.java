package neoforge.donjonmc.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.dungeon.DungeonPortalEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class DungeonPortalRenderer extends GeoEntityRenderer<DungeonPortalEntity> {

    public DungeonPortalRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DungeonPortalModel());
        this.shadowRadius = 0f;
    }
}
