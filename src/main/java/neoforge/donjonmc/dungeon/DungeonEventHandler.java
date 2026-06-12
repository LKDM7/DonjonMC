package neoforge.donjonmc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import neoforge.donjonmc.Config;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import neoforge.donjonmc.player.ModAttachments;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class DungeonEventHandler {

    private DungeonEventHandler() {}

    /** Portal spawn interval (minutes configurables dans donjonmc-common.toml). */
    private static int intervalMinTicks() { return 20 * 60 * Config.spawnIntervalMinMinutes; }
    private static int intervalMaxTicks() { return 20 * 60 * Config.spawnIntervalMaxMinutes; }
    private static int nextPortalTick = 20 * 60 * 8;

    /**
     * Snapshot of each player's inventory captured at the moment of death (before vanilla
     * drops/clears it). Restored on respawn in {@link #onPlayerClone}. This guarantees the
     * player keeps their gear after a dungeon death regardless of the keepInventory gamerule
     * or the ordering between vanilla's respawn copy and the Clone event.
     */
    private static final Map<UUID, ListTag> deathInventories = new HashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        DungeonManager.getInstance().clear();
        nextPortalTick = intervalMinTicks();
    }

    /** Mondes chargés → restaure la file de nettoyage de zones et le compteur d'IDs. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ZoneClearSavedData data = ZoneClearSavedData.get(event.getServer().overworld());
        DungeonManager.getInstance().attachZonesSavedData(data);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        if (sl.players().isEmpty()) return;

        if (--nextPortalTick <= 0) {
            DungeonManager.getInstance().trySpawnPortal(sl);
            nextPortalTick = intervalMinTicks()
                + sl.random.nextInt(Math.max(1, intervalMaxTicks() - intervalMinTicks()));
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

    /** Cancel item drops when a player dies inside the dungeon dimension. */
    @SubscribeEvent
    public static void onDungeonPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(DungeonManager.DUNGEON_DIMENSION)) return;
        event.setCanceled(true);
    }

    /**
     * Right-click on a dungeon portal entity. Runs at HIGHEST priority with
     * {@code receiveCanceled = true} so that the portal stays usable even when a third-party
     * land-protection mod (claims / "zones") cancels the interaction inside its protected area.
     * We perform the portal logic exactly once here and cancel the event, which also prevents
     * vanilla from invoking {@link DungeonPortalEntity#interact} a second time.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPortalInteract(PlayerInteractEvent.EntityInteract event) {
        // Server-only: cancelling client-side would stop the interaction packet from being sent.
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof DungeonPortalEntity portal)) return;
        InteractionResult result = portal.interact(event.getEntity(), event.getHand());
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    /** Player death inside the dungeon dimension. */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(DungeonManager.DUNGEON_DIMENSION)) return;
        // Capture inventory NOW, before vanilla's death handling can drop/clear it.
        deathInventories.put(player.getUUID(), player.getInventory().save(new ListTag()));
        DungeonManager.getInstance().onPlayerDeathInDungeon(player);
    }

    /** Carry the dungeon session across death so the respawn handler can act on it. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getOriginal() instanceof ServerPlayer original)) return;
        sp.setData(ModAttachments.DUNGEON_SAVE, original.getData(ModAttachments.DUNGEON_SAVE));
    }

    /**
     * Restore the inventory captured at death and send the player back to the overworld.
     * Runs at LOWEST priority on {@link PlayerEvent.PlayerRespawnEvent} so it executes AFTER
     * vanilla and any other mod that touches the inventory during respawn (e.g. inventory- or
     * claim-managing mods in a modpack). On a dedicated server those mods would otherwise clobber
     * the restored gear, which is why this works in single-player but failed on the server when
     * the restore was done during the earlier Clone event.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ListTag savedInv = deathInventories.remove(sp.getUUID());
        if (savedInv != null) {
            sp.getInventory().load(savedInv);
            sp.inventoryMenu.broadcastChanges(); // push the restored items to the client
        }
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
        // Nettoyage différé des zones terminées (chaque tick, budget limité).
        DungeonManager.getInstance().tickZoneClears(sl, sl.getGameTime());
        if (sl.getGameTime() % 20 != 0) return;
        for (ServerPlayer player : sl.players()) {
            BlockPos center = player.blockPosition();
            for (int bx = -8; bx <= 8; bx++)
                for (int by = -4; by <= 4; by++)
                    for (int bz = -8; bz <= 8; bz++) {
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
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.cartenon.punished"));
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
