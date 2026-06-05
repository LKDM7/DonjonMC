package neoforge.donjonmc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.network.SyncDungeonHudPacket;
import neoforge.donjonmc.player.ModAttachments;
import neoforge.donjonmc.quest.DailyQuestManager;
import neoforge.donjonmc.player.PlayerEventHandler;
import neoforge.donjonmc.raid.RaidGroup;
import neoforge.donjonmc.raid.RaidManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class DungeonManager {

    public static final ResourceKey<Level> DUNGEON_DIMENSION = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "dungeon"));

    private static final DungeonManager INSTANCE = new DungeonManager();
    public static DungeonManager getInstance() { return INSTANCE; }

    private DungeonManager() {}

    /** instanceId → instance */
    private final Map<Integer, DungeonInstance> activeInstances = new HashMap<>();
    /** player UUID → instanceId */
    private final Map<UUID, Integer> playerToInstance = new HashMap<>();
    /** player UUID → dungeon tick after which the entrance portal proximity is active */
    private final Map<UUID, Long> entryGraceTick = new HashMap<>();

    private final AtomicInteger idCounter = new AtomicInteger(0);

    public void clear() {
        activeInstances.clear();
        playerToInstance.clear();
        entryGraceTick.clear();
        idCounter.set(0);
    }

    // ── Portal Spawn ──────────────────────────────────────────────────────────

    public void trySpawnPortal(ServerLevel overworld) {
        List<ServerPlayer> players = overworld.players();
        if (players.isEmpty()) return;

        ServerPlayer target = players.get(overworld.random.nextInt(players.size()));
        DungeonRank rank = DungeonRank.random(overworld.random);

        int dx = (overworld.random.nextBoolean() ? 1 : -1) * (80 + overworld.random.nextInt(170));
        int dz = (overworld.random.nextBoolean() ? 1 : -1) * (80 + overworld.random.nextInt(170));
        int px = target.getBlockX() + dx;
        int pz = target.getBlockZ() + dz;
        int py = overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);

        // S'assurer que le bloc au niveau du portail est accessible (pas sous un surplomb)
        BlockPos candidate = new BlockPos(px, py, pz);
        if (!overworld.getBlockState(candidate).isAir()) candidate = candidate.above();

        spawnPortal(overworld, candidate, rank);
    }

    /** Spawns a portal at the player's feet (command). */
    public void spawnPortalAtPlayer(ServerPlayer player, DungeonRank rank) {
        if (!(player.level() instanceof ServerLevel sl)) return;
        BlockPos pos = player.blockPosition();
        spawnPortal(sl, pos, rank);
    }

    private void spawnPortal(ServerLevel level, BlockPos pos, DungeonRank rank) {
        DungeonPortalEntity portal = new DungeonPortalEntity(Donjonmc.PORTAL_ENTITY.get(), level);
        portal.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        portal.init(rank);
        level.addFreshEntity(portal);

        Component rankComp = Component.translatable(rank.langKey())
            .withStyle(style -> style.withColor(rankColor(rank)));
        Component msg = Component.translatable("donjonmc.dungeon.portal.appeared", rankComp,
            Component.literal(String.valueOf(pos.getX())),
            Component.literal(String.valueOf(pos.getY())),
            Component.literal(String.valueOf(pos.getZ())));
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }

    // ── Activation Validation ─────────────────────────────────────────────────

    public void tryActivate(ServerPlayer leader, DungeonPortalEntity portal) {
        DungeonRank rank = portal.getRank();

        // Auto-create a solo group so a player without a group can enter without running /raid create
        if (RaidManager.getInstance().getGroup(leader.getUUID()).isEmpty()) {
            RaidManager.getInstance().createGroup(leader);
        }

        RaidGroup group = RaidManager.getInstance().getGroup(leader.getUUID()).orElse(null);

        if (group == null || !group.isLeader(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("donjonmc.dungeon.activate.not_leader"));
            return;
        }

        List<ServerPlayer> members = group.getMembers().stream()
            .map(uuid -> leader.server.getPlayerList().getPlayer(uuid))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (members.size() < rank.minPlayers) {
            leader.sendSystemMessage(Component.translatable(
                "donjonmc.dungeon.activate.too_few", rank.minPlayers, members.size()));
            return;
        }

        List<String> farPlayers = members.stream()
            .filter(p -> p.distanceTo(portal) > 10.0)
            .map(p -> p.getName().getString())
            .collect(Collectors.toList());

        if (!farPlayers.isEmpty()) {
            leader.sendSystemMessage(Component.translatable(
                "donjonmc.dungeon.activate.not_ready", String.join(", ", farPlayers)));
            return;
        }

        BlockPos portalPos = portal.blockPosition();
        createInstance(leader.server, group, rank, new HashSet<>(group.getMembers()), portalPos);
        portal.discard();
    }

    // ── Instance Management ───────────────────────────────────────────────────

    private void createInstance(MinecraftServer server, RaidGroup group,
                                DungeonRank rank, Set<UUID> memberIds, BlockPos overworldPos) {

        ServerLevel dungeonLevel = server.getLevel(DUNGEON_DIMENSION);
        if (dungeonLevel == null) {
            server.getPlayerList().getPlayers().stream()
                .filter(p -> group.contains(p.getUUID()))
                .forEach(p -> p.sendSystemMessage(
                    Component.translatable("donjonmc.dungeon.error.dimension")));
            return;
        }

        int id = idCounter.getAndIncrement();
        long startTick = server.overworld().getGameTime();

        DungeonInstance instance = new DungeonInstance(
            id, rank, group.getId(), group.getLeaderId(), memberIds, overworldPos, startTick);
        activeInstances.put(id, instance);

        BlockPos zoneOrigin = instance.zoneOrigin();
        DungeonGenerator.GenerationResult gen = DungeonGenerator.generate(dungeonLevel, zoneOrigin, rank, id);
        instance.setBossCenter(gen.bossCenter());
        if (gen.bossEntityId() != null) {
            instance.setBossEntityId(gen.bossEntityId());
        }
        BlockPos entranceSpawn = gen.spawnPos();

        // Portail de sortie à l'entrée (face avant de la première salle)
        instance.setEntrancePos(entranceSpawn);
        spawnEntrancePortal(dungeonLevel, zoneOrigin.offset(3, 1, 0), id);

        // Clear portal data from group (portal has been activated, entering dungeon)
        group.clearPortal();
        RaidManager.getInstance().syncToGroup(group, server);

        long graceTick = dungeonLevel.getGameTime() + 200L; // 10s grace — skip entrance portal proximity
        for (UUID memberId : memberIds) {
            ServerPlayer sp = server.getPlayerList().getPlayer(memberId);
            if (sp == null) continue;
            playerToInstance.put(memberId, id);
            entryGraceTick.put(memberId, graceTick);
            saveDungeonData(sp, instance, entranceSpawn, overworldPos);
            teleportToWithEffect(sp, dungeonLevel, entranceSpawn);
            sp.sendSystemMessage(Component.translatable("donjonmc.dungeon.entered",
                Component.translatable(rank.langKey())));
        }

        // Boss bar
        if (instance.getBossEntityId() != null) {
            setupBossBar(instance, server, rank);
        }

        // Notifie les membres du groupe qui ne sont PAS dans le donjon (reconnexion future)
        Component joinBtn = Component.literal(" §a[Rejoindre]§r")
            .withStyle(s -> s.withClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/donjonmc dungeon join")));
        Component notif = Component.translatable("donjonmc.dungeon.group.in_dungeon",
            Component.translatable(rank.langKey())).append(joinBtn);
        for (UUID uid : group.getMembers()) {
            if (memberIds.contains(uid)) continue; // déjà téléporté
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp != null) sp.sendSystemMessage(notif);
        }
    }

    // ── Player Death in Dungeon ───────────────────────────────────────────────

    public void onPlayerDeathInDungeon(ServerPlayer player) {
        Integer instId = playerToInstance.get(player.getUUID());

        if (instId == null) {
            // Untracked player in dungeon — ensure they can respawn into overworld
            DungeonSaveData data = player.getData(ModAttachments.DUNGEON_SAVE);
            if (!data.isActive()) {
                BlockPos returnPos = player.server.overworld().getSharedSpawnPos();
                data = new DungeonSaveData(true, -1, 0, -1L, returnPos, BlockPos.ZERO,
                    ResourceLocation.parse("minecraft:overworld"), true);
            } else {
                data.setDied(true);
            }
            player.setData(ModAttachments.DUNGEON_SAVE, data);
            PacketDistributor.sendToPlayer(player, new SyncDungeonHudPacket(-1, 0L, 0, 0L));
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.player.died"));
            return;
        }

        DungeonInstance inst = activeInstances.get(instId);
        if (inst == null) return;

        inst.markDead(player.getUUID());
        playerToInstance.remove(player.getUUID());
        entryGraceTick.remove(player.getUUID());
        if (inst.getBossBar() != null) inst.getBossBar().removePlayer(player);

        // Flag attachment so returnIfStranded knows to teleport after respawn
        DungeonSaveData data = player.getData(ModAttachments.DUNGEON_SAVE);
        data.setDied(true);
        player.setData(ModAttachments.DUNGEON_SAVE, data);

        // Clear HUD for this player
        PacketDistributor.sendToPlayer(player, new SyncDungeonHudPacket(-1, 0L, 0, 0L));

        player.sendSystemMessage(Component.translatable("donjonmc.dungeon.player.died"));

        // If all members are now dead → group defeat, give 50% XP
        if (inst.isAllDead()) {
            if (inst.getBossBar() != null) {
                inst.getBossBar().removeAllPlayers();
                inst.setBossBar(null);
            }
            MinecraftServer server = player.server;
            for (UUID uid : inst.getPlayerIds()) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uid);
                if (sp != null) PlayerEventHandler.addXp(sp, inst.getRank().xpReward / 2);
            }
            for (UUID uid : inst.getPlayerIds()) {
                ServerPlayer sp = server.getPlayerList().getPlayer(uid);
                if (sp != null)
                    sp.sendSystemMessage(Component.translatable("donjonmc.dungeon.defeat"));
            }
            activeInstances.remove(instId);
        }
    }

    // ── Persistence: save / clear / restore ──────────────────────────────────

    private void saveDungeonData(ServerPlayer player, DungeonInstance inst,
                                 BlockPos entrancePos, BlockPos returnPos) {
        DungeonSaveData data = new DungeonSaveData(
            true, inst.getInstanceId(), inst.getRank().ordinal(),
            inst.getStartTick(), returnPos, entrancePos,
            player.level().dimension().location(), false
        );
        player.setData(ModAttachments.DUNGEON_SAVE, data);
    }

    private void clearDungeonData(ServerPlayer player) {
        player.setData(ModAttachments.DUNGEON_SAVE, new DungeonSaveData());
    }

    /** Called on player login — restores dungeon state after disconnect or server restart. */
    public void restoreFromAttachment(ServerPlayer player) {
        DungeonSaveData data = player.getData(ModAttachments.DUNGEON_SAVE);
        if (!data.isActive()) return;

        if (data.isDied()) {
            // Player died offline — attachment will be cleared by returnIfStranded after respawn.
            // Nothing to restore.
            player.setData(ModAttachments.DUNGEON_SAVE, new DungeonSaveData());
            return;
        }

        int instId       = data.getInstanceId();
        DungeonRank rank = DungeonRank.fromOrdinal(data.getRankOrdinal());

        DungeonInstance existing = activeInstances.get(instId);
        if (existing != null) {
            // Same server session — player just reconnected while instance is still live
            existing.addMember(player.getUUID());
            playerToInstance.put(player.getUUID(), instId);
        } else {
            // Server restarted — recreate minimal instance so tracking works
            Set<UUID> members = new HashSet<>();
            members.add(player.getUUID());
            DungeonInstance inst = new DungeonInstance(
                instId, rank,
                player.getUUID(), player.getUUID(), members,
                data.getReturnPos(), data.getStartTick()
            );
            activeInstances.put(instId, inst);
            idCounter.updateAndGet(cur -> Math.max(cur, instId + 1));
            playerToInstance.put(player.getUUID(), instId);
        }

        player.sendSystemMessage(Component.translatable("donjonmc.dungeon.resumed",
            Component.translatable(rank.langKey())));
    }

    /** Called after player respawn (PlayerEvent.Clone) to send dead players back to overworld. */
    public void returnIfStranded(ServerPlayer player) {
        DungeonSaveData data = player.getData(ModAttachments.DUNGEON_SAVE);
        if (!data.isActive() || !data.isDied()) return;
        BlockPos returnPos = data.getReturnPos().equals(BlockPos.ZERO)
            ? player.server.overworld().getSharedSpawnPos()
            : data.getReturnPos();
        teleportTo(player, player.server.overworld(), returnPos);
        player.setData(ModAttachments.DUNGEON_SAVE, new DungeonSaveData());
    }

    // ── Boss Bar ──────────────────────────────────────────────────────────────

    private void setupBossBar(DungeonInstance instance, MinecraftServer server, DungeonRank rank) {
        String bossKey = switch (rank) {
            case D -> "entity.donjonmc.giga_goblin";
            case C -> "entity.donjonmc.spider_boss";
            case B -> "entity.donjonmc.boss_golem";
            case A -> "entity.donjonmc.igris";
            case S -> "entity.donjonmc.kamish";
        };
        BossEvent.BossBarColor barColor = switch (rank) {
            case D -> BossEvent.BossBarColor.BLUE;
            case C -> BossEvent.BossBarColor.GREEN;
            case B -> BossEvent.BossBarColor.PURPLE;
            case A -> BossEvent.BossBarColor.YELLOW;
            case S -> BossEvent.BossBarColor.RED;
        };
        ServerBossEvent bar = new ServerBossEvent(
            Component.translatable(bossKey),
            barColor,
            BossEvent.BossBarOverlay.PROGRESS
        );
        for (UUID uid : instance.getPlayerIds()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp != null) bar.addPlayer(sp);
        }
        instance.setBossBar(bar);
    }

    public void updateBossBars(ServerLevel dungeonLevel) {
        for (DungeonInstance inst : activeInstances.values()) {
            ServerBossEvent bar = inst.getBossBar();
            UUID bossId = inst.getBossEntityId();
            if (bar == null || bossId == null) continue;
            var entity = dungeonLevel.getEntity(bossId);
            if (entity instanceof LivingEntity living) {
                bar.setProgress(Math.max(0f, living.getHealth() / living.getMaxHealth()));
            }
        }
    }

    // ── HUD Sync ──────────────────────────────────────────────────────────────

    public void syncHudForAllActive(MinecraftServer server) {
        long currentTick = server.overworld().getGameTime();
        List<Integer> expired = new ArrayList<>();
        for (Map.Entry<Integer, DungeonInstance> entry : activeInstances.entrySet()) {
            DungeonInstance inst = entry.getValue();
            if (inst.isCompleted()) continue;
            long elapsed   = Math.max(0L, (currentTick - inst.getStartTick()) / 20L);
            long remaining = Math.max(0L, inst.getRank().timeLimitSeconds - elapsed);
            SyncDungeonHudPacket packet = new SyncDungeonHudPacket(
                inst.getRank().ordinal(), elapsed, inst.getKillCount(), remaining);
            for (UUID uid : inst.getPlayerIds()) {
                if (!playerToInstance.containsKey(uid)) continue;
                ServerPlayer sp = server.getPlayerList().getPlayer(uid);
                if (sp != null) PacketDistributor.sendToPlayer(sp, packet);
            }
            if (remaining <= 0) expired.add(entry.getKey());
        }
        for (int id : expired) onTimeLimitExpired(server, id);
    }

    private void onTimeLimitExpired(MinecraftServer server, int instanceId) {
        DungeonInstance instance = activeInstances.get(instanceId);
        if (instance == null || instance.isCompleted()) return;
        instance.markCompleted();

        if (instance.getBossBar() != null) {
            instance.getBossBar().removeAllPlayers();
            instance.setBossBar(null);
        }

        ServerLevel overworld = server.overworld();
        for (UUID uid : instance.getPlayerIds()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp == null) continue;
            playerToInstance.remove(uid);
            entryGraceTick.remove(uid);
            PacketDistributor.sendToPlayer(sp, new SyncDungeonHudPacket(-1, 0L, 0, 0L));
            sp.sendSystemMessage(Component.translatable("donjonmc.dungeon.time_expired"));
            if (sp.level().dimension().equals(DUNGEON_DIMENSION)) {
                clearDungeonData(sp);
                BlockPos ret = instance.getOverworldReturn();
                teleportTo(sp, overworld, ret.equals(BlockPos.ZERO) ? overworld.getSharedSpawnPos() : ret);
            } else {
                clearDungeonData(sp);
            }
        }
        activeInstances.remove(instanceId);
    }

    // ── Boss killed ───────────────────────────────────────────────────────────

    public void onBossKilled(ServerLevel dungeonLevel, int instanceId) {
        DungeonInstance instance = activeInstances.get(instanceId);
        if (instance == null || instance.isCompleted()) return;
        instance.markCompleted();

        if (instance.getBossBar() != null) {
            instance.getBossBar().removeAllPlayers();
            instance.setBossBar(null);
        }

        BlockPos bossCenter = instance.getBossCenter();
        spawnExitPortal(dungeonLevel, bossCenter, instanceId);
        giveXpReward(dungeonLevel, instance);

        // Notifier le système de quêtes
        long elapsedTicks = dungeonLevel.getGameTime() - instance.getStartTick();
        int  groupSize    = instance.getPlayerIds().size();
        for (UUID uid : instance.getPlayerIds()) {
            ServerPlayer sp = dungeonLevel.getServer().getPlayerList().getPlayer(uid);
            if (sp != null) {
                DailyQuestManager.getInstance().onDungeonCompleted(
                    sp, instance.getRank(), elapsedTicks, groupSize);
            }
        }
    }

    private void spawnEntrancePortal(ServerLevel level, BlockPos pos, int instanceId) {
        DungeonPortalEntity portal = new DungeonPortalEntity(Donjonmc.PORTAL_ENTITY.get(), level);
        portal.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        DungeonInstance inst = activeInstances.get(instanceId);
        if (inst != null) portal.init(inst.getRank());
        portal.setAsEntranceExit(instanceId);
        level.addFreshEntity(portal);
    }

    private void spawnExitPortal(ServerLevel level, BlockPos pos, int instanceId) {
        DungeonPortalEntity exit = new DungeonPortalEntity(Donjonmc.PORTAL_ENTITY.get(), level);
        exit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        DungeonInstance inst = activeInstances.get(instanceId);
        if (inst != null) exit.init(inst.getRank());
        exit.setAsExit(instanceId);
        level.addFreshEntity(exit);

        for (UUID uid : (inst != null ? inst.getPlayerIds() : Set.<UUID>of())) {
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(uid);
            if (sp != null)
                sp.sendSystemMessage(Component.translatable("donjonmc.dungeon.boss.defeated"));
        }
    }

    public void completedByCartenon(ServerLevel dungeonLevel, int instanceId) {
        DungeonInstance instance = activeInstances.get(instanceId);
        if (instance == null) return;
        BlockPos cartenonCenter = instance.getBossCenter().offset(22, 0, 0);
        spawnExitPortal(dungeonLevel, cartenonCenter, instanceId);
        giveXpReward(dungeonLevel, instance);
    }

    private void giveXpReward(ServerLevel dungeonLevel, DungeonInstance instance) {
        MinecraftServer server = dungeonLevel.getServer();

        // Collect alive players to compute fair share + carry bonus
        List<ServerPlayer> alive = instance.getPlayerIds().stream()
            .filter(uid -> !instance.isDead(uid))
            .map(server.getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toList());

        long pool  = instance.getRank().xpReward;
        int  count = Math.max(1, alive.size());
        long share = pool / count;

        // Max rank tier among alive players (for carry bonus)
        int maxTier = 0;
        if (count > 1) {
            for (ServerPlayer sp : alive) {
                maxTier = Math.max(maxTier,
                    neoforge.donjonmc.player.LevelHelper.rankTier(
                        sp.getData(ModAttachments.PLAYER_DATA).getLevel()));
            }
        }
        final int maxTierFinal = maxTier;

        for (UUID uid : instance.getPlayerIds()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp == null) continue;

            long xp;
            if (instance.isDead(uid)) {
                // Mort → moitié d'une part équitable
                xp = share / 2;
            } else {
                xp = share;
                // Carry bonus : joueur 2+ rangs en dessous du meilleur → +50 % XP
                if (count > 1) {
                    int tier = neoforge.donjonmc.player.LevelHelper.rankTier(
                        sp.getData(ModAttachments.PLAYER_DATA).getLevel());
                    if (maxTierFinal - tier >= 2) {
                        xp = (long)(xp * 1.5);
                        sp.sendSystemMessage(Component.translatable("donjonmc.dungeon.carry_bonus"));
                    }
                }
            }
            PlayerEventHandler.addXpDirect(sp, xp);
        }
    }

    // ── Sortie forcée (/donjonmc dungeon exit) ────────────────────────────────

    public void forceExit(ServerPlayer player) {
        DungeonSaveData data = player.getData(ModAttachments.DUNGEON_SAVE);
        BlockPos returnPos;

        if (data.isActive() && !data.getReturnPos().equals(BlockPos.ZERO)) {
            returnPos = data.getReturnPos();
        } else if (player.level().dimension().equals(DUNGEON_DIMENSION)) {
            returnPos = player.server.overworld().getSharedSpawnPos();
        } else {
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.exit.not_inside"));
            return;
        }

        playerToInstance.remove(player.getUUID());
        clearDungeonData(player);
        PacketDistributor.sendToPlayer(player, new SyncDungeonHudPacket(-1, 0L, 0, 0L));
        teleportTo(player, player.server.overworld(), returnPos);
        player.sendSystemMessage(Component.translatable("donjonmc.dungeon.exited"));
    }

    public void tryJoinGroupDungeon(ServerPlayer player) {
        if (playerToInstance.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.join.already_inside"));
            return;
        }
        Optional<neoforge.donjonmc.raid.RaidGroup> groupOpt =
            RaidManager.getInstance().getGroup(player.getUUID());
        if (groupOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.join.not_member"));
            return;
        }
        UUID groupId = groupOpt.get().getId();
        DungeonInstance target = null;
        for (DungeonInstance inst : activeInstances.values()) {
            if (inst.getGroupId().equals(groupId) && !inst.isCompleted()) { target = inst; break; }
        }
        if (target == null) {
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.join.not_found"));
            return;
        }
        ServerLevel dungeonLevel = player.server.getLevel(DUNGEON_DIMENSION);
        if (dungeonLevel == null) {
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.error.dimension"));
            return;
        }
        BlockPos returnPos = player.blockPosition();
        playerToInstance.put(player.getUUID(), target.getInstanceId());
        target.addMember(player.getUUID());
        saveDungeonData(player, target, target.getEntrancePos(), returnPos);
        teleportTo(player, dungeonLevel, target.getEntrancePos());
        player.sendSystemMessage(Component.translatable("donjonmc.dungeon.join.success",
            Component.translatable(target.getRank().langKey())));
    }

    public void notifyIfGroupInDungeon(ServerPlayer player) {
        if (playerToInstance.containsKey(player.getUUID())) return;
        Optional<neoforge.donjonmc.raid.RaidGroup> groupOpt =
            RaidManager.getInstance().getGroup(player.getUUID());
        if (groupOpt.isEmpty()) return;
        UUID groupId = groupOpt.get().getId();
        for (DungeonInstance inst : activeInstances.values()) {
            if (!inst.getGroupId().equals(groupId) || inst.isCompleted()) continue;
            Component joinBtn = Component.literal(" §a[Rejoindre]§r")
                .withStyle(s -> s.withClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND, "/donjonmc dungeon join")));
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.group.in_dungeon",
                Component.translatable(inst.getRank().langKey())).append(joinBtn));
            break;
        }
    }

    public void teleportToDungeonAt(ServerPlayer player, BlockPos pos) {
        ServerLevel dungeonLevel = player.server.getLevel(DUNGEON_DIMENSION);
        if (dungeonLevel == null) {
            player.sendSystemMessage(Component.translatable("donjonmc.dungeon.error.dimension"));
            return;
        }
        teleportTo(player, dungeonLevel, pos);
    }

    // ── Exit portal proximity check ───────────────────────────────────────────

    public void checkExitProximity(ServerLevel dungeonLevel, DungeonPortalEntity exitPortal, int instanceId) {
        DungeonInstance instance = activeInstances.get(instanceId);
        if (instance == null) return;

        MinecraftServer server = dungeonLevel.getServer();
        long now = dungeonLevel.getGameTime();
        for (UUID uid : instance.getPlayerIds()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp == null) continue;
            if (!sp.level().dimension().equals(DUNGEON_DIMENSION)) continue;
            Long grace = entryGraceTick.get(uid);
            if (grace != null) {
                if (now < grace) continue;
                entryGraceTick.remove(uid);
            }
            if (sp.distanceTo(exitPortal) > 3.0) continue;
            exitPlayer(sp, instance);
        }

        cleanupIfEmpty(instanceId, instance);
    }

    public void exitPlayerViaPortal(ServerPlayer sp, int instanceId) {
        DungeonInstance instance = activeInstances.get(instanceId);
        if (instance == null) return;
        if (!sp.level().dimension().equals(DUNGEON_DIMENSION)) return;
        exitPlayer(sp, instance);
        cleanupIfEmpty(instanceId, instance);
    }

    private void exitPlayer(ServerPlayer sp, DungeonInstance instance) {
        playerToInstance.remove(sp.getUUID());
        entryGraceTick.remove(sp.getUUID());
        if (instance.getBossBar() != null) instance.getBossBar().removePlayer(sp);
        clearDungeonData(sp);
        PacketDistributor.sendToPlayer(sp, new SyncDungeonHudPacket(-1, 0L, 0, 0L));
        teleportTo(sp, sp.server.overworld(), instance.getOverworldReturn());
        sp.sendSystemMessage(Component.translatable("donjonmc.dungeon.exited"));
    }

    private void cleanupIfEmpty(int instanceId, DungeonInstance instance) {
        boolean allGone = instance.getPlayerIds().stream()
            .noneMatch(uid -> playerToInstance.containsKey(uid));
        if (allGone) {
            if (instance.getBossBar() != null) instance.getBossBar().removeAllPlayers();
            activeInstances.remove(instanceId);
        }
    }

    public void addDungeonKill(int instanceId) {
        DungeonInstance inst = activeInstances.get(instanceId);
        if (inst != null) inst.addKill();
    }

    public Optional<DungeonInstance> getInstanceFor(UUID playerId) {
        Integer id = playerToInstance.get(playerId);
        return id != null ? Optional.ofNullable(activeInstances.get(id)) : Optional.empty();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static void teleportTo(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.changeDimension(new DimensionTransition(
            level,
            new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
            Vec3.ZERO, 0f, 0f, false,
            DimensionTransition.DO_NOTHING));
    }

    private static void teleportToWithEffect(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.changeDimension(new DimensionTransition(
            level,
            new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
            Vec3.ZERO, 0f, 0f, false,
            entity -> {
                if (entity instanceof ServerPlayer sp) {
                    // 5s Resistance V = immunité le temps de s'orienter
                    sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
                }
            }));
    }

    private static int rankColor(DungeonRank rank) {
        return switch (rank) {
            case D -> 0x5555FF;
            case C -> 0x55FF55;
            case B -> 0xAA00AA;
            case A -> 0xFFAA00;
            case S -> 0xFF5555;
        };
    }
}
