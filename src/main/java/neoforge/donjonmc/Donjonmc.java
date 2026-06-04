package neoforge.donjonmc;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import neoforge.donjonmc.client.ClientEventHandler;
import neoforge.donjonmc.network.ModNetwork;
import neoforge.donjonmc.spell.DonjonSpellRegistry;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import neoforge.donjonmc.dungeon.DungeonPortalEntity;
import neoforge.donjonmc.dungeon.mob.DungeonMobRegistry;
import neoforge.donjonmc.player.ModAttachments;
import org.slf4j.Logger;

@Mod(Donjonmc.MODID)
public class Donjonmc {

    public static final String MODID = "donjonmc";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items  ITEMS  = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);

    // ── Dungeon portal entity ────────────────────────────────────────────────
    public static final DeferredHolder<EntityType<?>, EntityType<DungeonPortalEntity>> PORTAL_ENTITY =
        ENTITY_TYPES.register("dungeon_portal", () ->
            EntityType.Builder.<DungeonPortalEntity>of(DungeonPortalEntity::new, MobCategory.MISC)
                .sized(1.2f, 2.5f)
                .clientTrackingRange(10)
                .build("donjonmc:dungeon_portal"));

    public Donjonmc(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);

        DungeonMobRegistry.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(DungeonMobRegistry::registerAttributeCreation);

        DonjonSpellRegistry.register(modEventBus);

        modEventBus.register(Config.class);
        modEventBus.register(ModNetwork.class);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.register(ClientEventHandler.class);
        }
    }
}
