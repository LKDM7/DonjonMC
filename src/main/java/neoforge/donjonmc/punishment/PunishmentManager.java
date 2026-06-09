package neoforge.donjonmc.punishment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.network.SyncPunishmentTimerPacket;
import neoforge.donjonmc.player.ModAttachments;
import neoforge.donjonmc.player.PlayerData;
import neoforge.donjonmc.player.PlayerEventHandler;
import neoforge.donjonmc.player.StatType;

import java.util.*;

public final class PunishmentManager {

    public static final ResourceKey<Level> PUNISHMENT_DIMENSION = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "punishment"));

    private static final PunishmentManager INSTANCE = new PunishmentManager();
    public static PunishmentManager getInstance() { return INSTANCE; }
    private PunishmentManager() {}

    private static final int MAX_SLOTS = 2;

    private final Map<UUID, PunishmentInstance> active     = new HashMap<>();
    private final Set<Integer>                  freeSlots  = new HashSet<>(Set.of(0, 1));

    // ── Trigger ───────────────────────────────────────────────────────────────

    /**
     * Starts a punishment instance for the player.
     * Called on quest failure or via the admin command.
     */
    public void triggerForPlayer(ServerPlayer player) {
        if (active.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.already_active"));
            return;
        }

        // Cooldown : 1 punition max par jour Minecraft
        long currentDay = player.server.overworld().getGameTime() / 24000L;
        PunishmentData saved = player.getData(ModAttachments.PUNISHMENT_DATA);
        if (saved.getLastPunishmentDay() >= currentDay) {
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.cooldown"));
            return;
        }

        ServerLevel punishLevel = player.server.getLevel(PUNISHMENT_DIMENSION);
        if (punishLevel == null) {
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.error.dimension"));
            return;
        }

        if (freeSlots.isEmpty()) {
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.full"));
            return;
        }

        int slot = freeSlots.iterator().next();
        freeSlots.remove(slot);
        BlockPos returnPos = player.blockPosition();
        ResourceKey<Level> returnDim = player.level().dimension();
        long startTick = player.server.overworld().getGameTime();

        PunishmentInstance inst = new PunishmentInstance(
            slot, player.getUUID(), returnPos, returnDim, startTick);
        active.put(player.getUUID(), inst);

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        BlockPos spawnPos = PunishmentArenaGenerator.generate(
            punishLevel, inst.arenaCenter(), data.getLevel());

        teleportTo(player, punishLevel, spawnPos);

        // Persister l'état sur disque + marquer le jour de punition
        saveToDisk(player, inst, spawnPos, currentDay);

        player.sendSystemMessage(Component.translatable("donjonmc.punishment.entered"));
        long totalSecs = PunishmentInstance.DURATION_TICKS / 20;
        sendTimerMessage(player, totalSecs);
        sendTimerPacket(player, totalSecs);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public void onServerTick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<UUID> toRemove = new ArrayList<>();

        for (PunishmentInstance inst : active.values()) {
            if (inst.isCompleted()) { toRemove.add(inst.getPlayerId()); continue; }

            long remaining = inst.ticksRemaining(now);

            ServerPlayer sp = server.getPlayerList().getPlayer(inst.getPlayerId());

            // Sync HUD every second
            if (sp != null) sendTimerPacket(sp, remaining / 20);

            // Chat messages at 10 min, 5 min, 1 min — window of 20 ticks to survive lag spikes
            if (isTimerThreshold(remaining, 10 * 60 * 20)
                    || isTimerThreshold(remaining, 5 * 60 * 20)
                    || isTimerThreshold(remaining, 60 * 20)) {
                if (sp != null) sendTimerMessage(sp, remaining / 20);
            }

            if (remaining <= 0) {
                inst.markCompleted();
                if (sp != null) onSurvival(sp, inst, server);
                toRemove.add(inst.getPlayerId());
            }
        }
        toRemove.forEach(active::remove);
    }

    // ── Survival (timer reached 0) ────────────────────────────────────────────

    private void onSurvival(ServerPlayer player, PunishmentInstance inst, MinecraftServer server) {
        freeSlots.add(inst.getSlotId());
        clearFromDisk(player);
        sendTimerPacket(player, -1);
        player.sendSystemMessage(Component.translatable("donjonmc.punishment.survived"));
        ServerLevel returnLevel = server.getLevel(inst.getReturnDimension());
        if (returnLevel == null) returnLevel = server.overworld();
        teleportTo(player, returnLevel, inst.getReturnPos());
    }

    // ── Player death in punishment ────────────────────────────────────────────

    public void onPlayerDeathInPunishment(ServerPlayer player) {
        PunishmentInstance inst = active.remove(player.getUUID());
        if (inst == null) return;
        inst.markCompleted();
        freeSlots.add(inst.getSlotId());
        clearFromDisk(player);
        sendTimerPacket(player, -1);
        applyStatPenalty(player);
        // Respawn destination is handled by Minecraft (overworld spawn) + returnIfStranded safety
    }

    /**
     * Safety check called on Clone (respawn). If the player ends up in the punishment
     * dimension with no active instance, send them to the overworld spawn.
     */
    public void returnIfStranded(ServerPlayer player) {
        if (!player.level().dimension().equals(PUNISHMENT_DIMENSION)) return;
        if (active.containsKey(player.getUUID())) return;

        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        teleportTo(player, overworld, spawn);
    }

    // ── Stat penalty: -1 on 3 random non-zero stats ───────────────────────────

    private void applyStatPenalty(ServerPlayer player) {
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);

        List<StatType> nonZero = new ArrayList<>();
        for (StatType s : StatType.values()) {
            if (getValue(data, s) > 0) nonZero.add(s);
        }

        if (nonZero.isEmpty()) {
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.death.no_stats"));
        } else {
            Collections.shuffle(nonZero, new Random());
            int count = Math.min(3, nonZero.size());
            for (int i = 0; i < count; i++) {
                StatType stat = nonZero.get(i);
                setValue(data, stat, Math.max(0, getValue(data, stat) - 1));
            }
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.death.penalty"));
        }

        player.setData(ModAttachments.PLAYER_DATA, data);
        PlayerEventHandler.applyStatModifiers(player, data);
        PlayerEventHandler.sendSyncPacket(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Sets the timer so the instance expires in exactly 30 seconds. Used for testing. */
    public boolean skipToThirtySeconds(UUID playerId, long currentTick) {
        PunishmentInstance inst = active.get(playerId);
        if (inst == null) return false;
        inst.setStartTick(currentTick - (PunishmentInstance.DURATION_TICKS - 30L * 20L));
        return true;
    }

    public boolean isInPunishment(UUID playerId) {
        return active.containsKey(playerId);
    }

    /** null si aucune instance active pour ce joueur. */
    public PunishmentInstance getActiveInstance(UUID playerId) {
        return active.get(playerId);
    }

    private static void teleportTo(ServerPlayer player, ServerLevel level, BlockPos pos) {
        boolean enteringPunishment = level.dimension().equals(PUNISHMENT_DIMENSION);
        player.changeDimension(new DimensionTransition(
            level,
            new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
            Vec3.ZERO, 0f, 0f, false,
            entity -> {
                if (enteringPunishment && entity instanceof ServerPlayer sp) {
                    // 5s Resistance V = immunité le temps de charger/s'orienter
                    sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
                }
            }));
    }

    // ── Persistance disque ────────────────────────────────────────────────────

    private static void saveToDisk(ServerPlayer player, PunishmentInstance inst,
                                    BlockPos spawnPos, long day) {
        PunishmentData data = player.getData(ModAttachments.PUNISHMENT_DATA);
        data.setActive(true);
        data.setSlotId(inst.getSlotId());
        data.setTimerStartTick(inst.getStartTick());
        data.setReturnPos(inst.getReturnPos());
        data.setSpawnPos(spawnPos);
        data.setReturnDimLoc(inst.getReturnDimension().location());
        data.setLastPunishmentDay(day);
        player.setData(ModAttachments.PUNISHMENT_DATA, data);
    }

    private static void clearFromDisk(ServerPlayer player) {
        // Préserve lastPunishmentDay pour maintenir le cooldown même après la fin de la punition
        long lastDay = player.getData(ModAttachments.PUNISHMENT_DATA).getLastPunishmentDay();
        PunishmentData fresh = new PunishmentData();
        fresh.setLastPunishmentDay(lastDay);
        player.setData(ModAttachments.PUNISHMENT_DATA, fresh);
    }

    /**
     * Appelé à la connexion du joueur.
     * Recrée l'instance en mémoire si une punition était en cours au moment de la déco/restart.
     */
    public void restoreFromAttachment(ServerPlayer player) {
        if (active.containsKey(player.getUUID())) return; // déjà restauré

        PunishmentData saved = player.getData(ModAttachments.PUNISHMENT_DATA);
        if (!saved.isActive()) return;

        long now = player.server.overworld().getGameTime();
        long remaining = (saved.getTimerStartTick() + PunishmentInstance.DURATION_TICKS) - now;

        if (remaining <= 0) {
            // Timer expiré pendant l'absence → pénalité appliquée maintenant
            clearFromDisk(player);
            applyStatPenalty(player);
            sendTimerPacket(player, -1);
            player.sendSystemMessage(Component.translatable("donjonmc.punishment.expired_offline"));
            return;
        }

        // Remettre le slot comme occupé
        int slot = saved.getSlotId();
        freeSlots.remove(slot);

        // Recréer l'instance avec le timing d'origine
        PunishmentInstance inst = new PunishmentInstance(
            slot, player.getUUID(),
            saved.getReturnPos(), saved.getReturnDimension(),
            saved.getTimerStartTick());
        active.put(player.getUUID(), inst);

        // Téléporter dans la dimension si le joueur n'y est plus
        if (!player.level().dimension().equals(PUNISHMENT_DIMENSION)) {
            ServerLevel punishLevel = player.server.getLevel(PUNISHMENT_DIMENSION);
            if (punishLevel != null) teleportTo(player, punishLevel, saved.getSpawnPos());
        }

        sendTimerPacket(player, remaining / 20);
        player.sendSystemMessage(Component.translatable("donjonmc.punishment.resumed",
            String.format("%d:%02d", (remaining / 20) / 60, (remaining / 20) % 60)));
    }

    private static boolean isTimerThreshold(long remaining, long target) {
        return remaining <= target && remaining > target - 20;
    }

    private static void sendTimerPacket(ServerPlayer player, long seconds) {
        PacketDistributor.sendToPlayer(player, new SyncPunishmentTimerPacket(seconds));
    }

    private static void sendTimerMessage(ServerPlayer player, long secondsRemaining) {
        long min = secondsRemaining / 60;
        long sec = secondsRemaining % 60;
        player.sendSystemMessage(Component.translatable(
            "donjonmc.punishment.timer", String.format("%d:%02d", min, sec)));
    }

    private static int getValue(PlayerData data, StatType stat) {
        return switch (stat) {
            case STRENGTH     -> data.getStrength();
            case AGILITY      -> data.getAgility();
            case VITALITY     -> data.getVitality();
            case INTELLIGENCE -> data.getIntelligence();
            case PERCEPTION   -> data.getPerception();
        };
    }

    private static void setValue(PlayerData data, StatType stat, int value) {
        switch (stat) {
            case STRENGTH     -> data.setStrength(value);
            case AGILITY      -> data.setAgility(value);
            case VITALITY     -> data.setVitality(value);
            case INTELLIGENCE -> data.setIntelligence(value);
            case PERCEPTION   -> data.setPerception(value);
        }
    }
}
