package neoforge.donjonmc.punishment;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import neoforge.donjonmc.Donjonmc;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class PunishmentEventHandler {

    private PunishmentEventHandler() {}

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.level().dimension().equals(PunishmentManager.PUNISHMENT_DIMENSION)) return;
        PunishmentManager.getInstance().onPlayerDeathInPunishment(sp);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        PunishmentManager.getInstance().returnIfStranded(sp);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide()) return;

        // Restaure l'instance depuis le disque (survie aux restarts et décos)
        PunishmentManager.getInstance().restoreFromAttachment(sp);

        // Safety net : joueur dans la dimension sans instance active → renvoyer au spawn
        if (sp.level().dimension().equals(PunishmentManager.PUNISHMENT_DIMENSION)
                && !PunishmentManager.getInstance().isInPunishment(sp.getUUID())) {
            ServerLevel overworld = sp.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            sp.changeDimension(new DimensionTransition(
                overworld,
                new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5),
                Vec3.ZERO, 0f, 0f, false,
                DimensionTransition.DO_NOTHING));
        }
    }

    // Bloque toutes les commandes pour les joueurs dans l'instance (sauf op niveau 2+)
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var source = event.getParseResults().getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.level().dimension().equals(PunishmentManager.PUNISHMENT_DIMENSION)) return;
        if (!PunishmentManager.getInstance().isInPunishment(sp.getUUID())) return;
        if (source.hasPermission(2)) return; // les ops peuvent encore utiliser des commandes

        event.setCanceled(true);
        sp.sendSystemMessage(Component.translatable("donjonmc.punishment.no_commands"));
    }

    // Bloque tout changement de dimension tant que l'instance n'est pas terminée
    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!sp.level().dimension().equals(PunishmentManager.PUNISHMENT_DIMENSION)) return;

        PunishmentInstance inst = PunishmentManager.getInstance().getActiveInstance(sp.getUUID());
        // inst null = instance terminée (mort/survie) → on laisse passer le tp de retour
        if (inst == null || inst.isCompleted()) return;

        event.setCanceled(true);
        sp.sendSystemMessage(Component.translatable("donjonmc.punishment.no_escape"));
    }

    // Bloque tout placement de blocs dans la dimension de punition
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(PunishmentManager.PUNISHMENT_DIMENSION)) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("donjonmc.punishment.no_place"));
        }
    }

    /** Seul le SandWorm peut rester dans la dimension de punition. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        if (!sl.dimension().equals(PunishmentManager.PUNISHMENT_DIMENSION)) return;
        if (event.getEntity() instanceof SandWormEntity) return;
        // Pas de setCanceled — on discard seulement pour ne pas bloquer addFreshEntity
        event.getEntity().discard();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        PunishmentManager.getInstance().onServerTick(event.getServer());
    }
}
