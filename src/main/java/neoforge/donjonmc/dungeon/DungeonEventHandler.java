package neoforge.donjonmc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import neoforge.donjonmc.player.ModAttachments;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class DungeonEventHandler {

    private DungeonEventHandler() {}

    /** Portal spawn interval: 8–14 minutes. */
    private static final int PORTAL_INTERVAL_MIN = 20 * 60 * 8;
    private static final int PORTAL_INTERVAL_MAX = 20 * 60 * 14;
    private static int nextPortalTick = PORTAL_INTERVAL_MIN;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        DungeonManager.getInstance().clear();
        nextPortalTick = PORTAL_INTERVAL_MIN;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        if (sl.players().isEmpty()) return;

        if (--nextPortalTick <= 0) {
            DungeonManager.getInstance().trySpawnPortal(sl);
            nextPortalTick = PORTAL_INTERVAL_MIN
                + sl.random.nextInt(PORTAL_INTERVAL_MAX - PORTAL_INTERVAL_MIN);
        }

        // Sync dungeon HUD every second
        if (sl.getGameTime() % 20 == 0) {
            DungeonManager.getInstance().syncHudForAllActive(sl.getServer());
        }
    }

    /** Boss (DungeonMob with dungeon_boss=true) killed → complete instance. */
    @SubscribeEvent
    public static void onBossDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof DungeonMob mob)) return;
        if (!mob.getPersistentData().getBoolean("dungeon_boss")) return;
        int instanceId = mob.getPersistentData().getInt("instance_id");
        if (mob.level() instanceof ServerLevel sl) {
            DungeonManager.getInstance().onBossKilled(sl, instanceId);
        }
    }

    /** Player death inside the dungeon dimension. */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(DungeonManager.DUNGEON_DIMENSION)) return;
        DungeonManager.getInstance().onPlayerDeathInDungeon(player);
    }

    /** After respawn: copy dungeon data from dead entity then send player back to overworld. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getOriginal() instanceof ServerPlayer original)) return;
        sp.setData(ModAttachments.DUNGEON_SAVE, original.getData(ModAttachments.DUNGEON_SAVE));
        DungeonManager.getInstance().returnIfStranded(sp);
    }

    /** On login: restore dungeon session (survive disconnect / server restart). */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DungeonManager.getInstance().restoreFromAttachment(sp);
        DungeonManager.getInstance().notifyIfGroupInDungeon(sp);
    }

    /** Prevent block breaking inside the dungeon dimension (ops level 2+ are exempt). */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(DungeonManager.DUNGEON_DIMENSION)) return;
        if (player.hasPermissions(2)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDungeonTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(DungeonManager.DUNGEON_DIMENSION)) return;
        if (sl.getGameTime() % 10 != 0) return;
        for (ServerPlayer player : sl.players()) {
            BlockPos center = player.blockPosition();
            for (int bx = -16; bx <= 16; bx++)
                for (int by = -4; by <= 4; by++)
                    for (int bz = -16; bz <= 16; bz++) {
                        BlockPos pos = center.offset(bx, by, bz);
                        if (sl.getBlockState(pos).is(Blocks.FIRE))
                            sl.removeBlock(pos, false);
                    }
        }
        DungeonManager.getInstance().updateBossBars(sl);
    }

    @SubscribeEvent
    public static void onCartenonAttacked(LivingIncomingDamageEvent event) {
        if (!event.getEntity().getPersistentData().getBoolean("cartenon_statue")) return;
        event.setCanceled(true);
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            player.hurt(player.level().damageSources().genericKill(), Float.MAX_VALUE);
        }
    }

    /** Block ALL non-DungeonMob spawns inside the dungeon dimension. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(DungeonManager.DUNGEON_DIMENSION)) return;
        if (event.getEntity() instanceof DungeonMob) return;
        event.setCanceled(true);
        event.getEntity().discard();
    }
}
