package neoforge.donjonmc.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.client.model.DungeonMobModel;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

@OnlyIn(Dist.CLIENT)
public class DungeonMobRenderer extends GeoEntityRenderer<DungeonMob> {

    public DungeonMobRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DungeonMobModel());
    }
}
