package neoforge.donjonmc.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.dungeon.DungeonRank;
import neoforge.donjonmc.network.SyncDailyQuestPacket;
import neoforge.donjonmc.player.ModAttachments;
import neoforge.donjonmc.punishment.PunishmentManager;
import neoforge.donjonmc.quest.QuestDef.Difficulty;

import java.util.*;
import java.util.stream.Collectors;

public final class DailyQuestManager {

    private static final DailyQuestManager INSTANCE = new DailyQuestManager();
    public static DailyQuestManager getInstance() { return INSTANCE; }
    private DailyQuestManager() {}

    // ── Transient server-side tracking (reset on restart, acceptable) ─────────

    private final Map<UUID, Long>    survivalStartTick              = new HashMap<>();
    private final Map<UUID, Integer> creeperNoExpCount              = new HashMap<>();
    private final Map<UUID, Boolean> tookExplosionSinceLastCreeper  = new HashMap<>();
    private final Map<UUID, Integer> killStreaks                     = new HashMap<>();
    private final Map<UUID, double[]> lastPositions                 = new HashMap<>();
    private final Map<UUID, Double>  walkAccum                      = new HashMap<>();
    private final Map<UUID, Double>  swimAccum                      = new HashMap<>();

    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END   = 23000L;

    // ── Assignment ─────────────────────────────────────────────────────────────

    public void assignIfNeeded(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (data.isDisabled()) return;

        long nowMs = System.currentTimeMillis();
        boolean elapsed24h = nowMs - data.getLastAssignedDay() >= 24L * 60L * 60L * 1000L;

        if (!elapsed24h && data.hasQuests()) {
            if (data.isActive()) syncToPlayer(player, data);
            return;
        }

        doAssign(player, data, nowMs);
    }

    private void doAssign(ServerPlayer player, DailyQuestData data, long assignTimeMs) {
        UUID uid = player.getUUID();
        long realDay = assignTimeMs / (24L * 60L * 60L * 1000L);
        long seed = uid.getMostSignificantBits() ^ uid.getLeastSignificantBits()
                    ^ (realDay * 0x9e3779b97f4a7c15L);
        Random rng = new Random(seed);

        List<QuestDef> easy   = shuffle(QuestRegistry.byDifficulty(Difficulty.EASY),   rng);
        List<QuestDef> normal = shuffle(QuestRegistry.byDifficulty(Difficulty.NORMAL), rng);
        List<QuestDef> hard   = shuffle(QuestRegistry.byDifficulty(Difficulty.HARD),   rng);

        // 2 easy + 1 normal + 1 hard, then shuffle order for display
        List<Integer> ids = new ArrayList<>(List.of(
            easy.get(0).id(), easy.get(1).id(),
            normal.get(0).id(),
            hard.get(0).id()
        ));
        Collections.shuffle(ids, rng);

        long now = player.server.overworld().getGameTime();
        data.assign(ids, now, assignTimeMs);
        player.setData(ModAttachments.DAILY_QUEST, data);

        // Reset transient tracking
        survivalStartTick.put(uid, now);
        creeperNoExpCount.put(uid, 0);
        tookExplosionSinceLastCreeper.put(uid, false);
        killStreaks.put(uid, 0);
        walkAccum.put(uid, 0.0);
        swimAccum.put(uid, 0.0);
        lastPositions.remove(uid);

        // Notify player
        player.sendSystemMessage(Component.translatable("donjonmc.quest.assigned"));
        for (int i = 0; i < ids.size(); i++) {
            QuestDef def = QuestRegistry.byId(ids.get(i));
            if (def != null) {
                player.sendSystemMessage(Component.translatable(
                    "donjonmc.quest.slot", i + 1,
                    Component.translatable(def.nameKey() + ".name")));
            }
        }

        syncToPlayer(player, data);
    }

    // Admin command: force assign same-day quests (same seed = same quests)
    public void forceAssign(ServerPlayer player) {
        doAssign(player, player.getData(ModAttachments.DAILY_QUEST), System.currentTimeMillis());
    }

    // OP command: force entirely new quests (shifts to next 24h window → different seed)
    public void forceAssignNewDay(ServerPlayer player) {
        long nextWindow = System.currentTimeMillis() + 24L * 60L * 60L * 1000L;
        doAssign(player, player.getData(ModAttachments.DAILY_QUEST), nextWindow);
    }

    // ── Progress API ───────────────────────────────────────────────────────────

    public void onProgress(ServerPlayer player, QuestType type, int amount, String filter) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;

        boolean changed = false;
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null || def.type() != type) continue;
            if (!matchesFilter(def.filter(), filter)) continue;

            data.addProgress(i, amount);
            if (data.getProgress(i) >= def.target()) {
                data.setCompleted(i, true);
                onQuestSlotDone(player, def, data);
            }
            changed = true;
        }

        if (changed) {
            player.setData(ModAttachments.DAILY_QUEST, data);
            syncToPlayer(player, data);
        }
    }

    public void onModXpGained(ServerPlayer player, long amount) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;
        if (!hasIncompleteQuestOfType(data, QuestType.GAIN_MOD_XP)) return;

        data.addGainedModXp((int) Math.min(amount, Integer.MAX_VALUE));

        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null || def.type() != QuestType.GAIN_MOD_XP) continue;
            int accumulated = data.getGainedModXp();
            data.setProgress(i, accumulated);
            if (accumulated >= def.target()) {
                data.setCompleted(i, true);
                player.setData(ModAttachments.DAILY_QUEST, data);
                onQuestSlotDone(player, def, data);
                return;
            }
            break;
        }
        player.setData(ModAttachments.DAILY_QUEST, data);
        syncToPlayer(player, data);
    }

    public void onStatPointSpent(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;
        data.incrementSpentStatPoints();
        player.setData(ModAttachments.DAILY_QUEST, data);
        // Use accumulated counter for SPEND_STAT_POINTS
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null || def.type() != QuestType.SPEND_STAT_POINTS) continue;
            int spent = data.getSpentStatPoints();
            data.setProgress(i, spent);
            if (spent >= def.target()) {
                data.setCompleted(i, true);
                onQuestSlotDone(player, def, data);
            }
            player.setData(ModAttachments.DAILY_QUEST, data);
            syncToPlayer(player, data);
            break;
        }
    }

    public void onDungeonCompleted(ServerPlayer player, DungeonRank rank, long elapsedTicks, int groupSize) {
        // Quêtes uniques (indépendantes de l'état des quêtes quotidiennes).
        UniqueQuestManager.getInstance().onDungeonCompleted(player, rank);

        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;
        long elapsedSecs = elapsedTicks / 20L;
        boolean changed = false;

        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null) continue;
            boolean eligible = switch (def.type()) {
                case COMPLETE_DUNGEON       -> rank.ordinal() >= def.param2();
                case COMPLETE_DUNGEON_TIMED -> elapsedSecs <= def.param2();
                case COMPLETE_DUNGEON_ALLY  -> groupSize > 1;
                default -> false;
            };
            if (!eligible) continue;
            data.addProgress(i, 1);
            if (data.getProgress(i) >= def.target()) {
                data.setCompleted(i, true);
                player.setData(ModAttachments.DAILY_QUEST, data);
                onQuestSlotDone(player, def, data);
                data = player.getData(ModAttachments.DAILY_QUEST);
            }
            changed = true;
        }
        if (changed) {
            player.setData(ModAttachments.DAILY_QUEST, data);
            syncToPlayer(player, data);
        }
    }

    // ── Kill tracking (creeper streak, kill streak) ───────────────────────────

    public void onExplosionDamage(ServerPlayer player) {
        UUID uid = player.getUUID();
        tookExplosionSinceLastCreeper.put(uid, true);
        resetProgressForType(player, QuestType.KILL_CREEPER_NO_EXP);
    }

    public void onCreeperKilled(ServerPlayer player) {
        UUID uid = player.getUUID();
        boolean tookExplosion = tookExplosionSinceLastCreeper.getOrDefault(uid, false);
        tookExplosionSinceLastCreeper.put(uid, false);

        if (tookExplosion) {
            creeperNoExpCount.put(uid, 0);
            resetProgressForType(player, QuestType.KILL_CREEPER_NO_EXP);
        } else {
            int count = creeperNoExpCount.merge(uid, 1, Integer::sum);
            setProgressForType(player, QuestType.KILL_CREEPER_NO_EXP, count);
        }
    }

    public void onKillStreak(ServerPlayer player) {
        int streak = killStreaks.merge(player.getUUID(), 1, Integer::sum);
        setProgressForType(player, QuestType.KILL_STREAK, streak);
    }

    // ── Player events ─────────────────────────────────────────────────────────

    public void onPlayerDied(ServerPlayer player) {
        UUID uid = player.getUUID();
        long now = player.server.overworld().getGameTime();
        survivalStartTick.put(uid, now);
        killStreaks.put(uid, 0);
        resetProgressForType(player, QuestType.KILL_STREAK);

        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (data.isActive() && data.getNightState() == 1) {
            data.setNightState(0);
            player.setData(ModAttachments.DAILY_QUEST, data);
        }
    }

    public void onLevelUp(ServerPlayer player) {
        onProgress(player, QuestType.LEVEL_UP, 1, "any");
    }

    // ── Player tick (movement, depth) ─────────────────────────────────────────

    public void onPlayerTick(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;

        UUID uid = player.getUUID();
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double[] last = lastPositions.get(uid);

        if (last != null) {
            double dx = px - last[0], dz = pz - last[2];
            double hDist = Math.sqrt(dx * dx + dz * dz);

            if (hasIncompleteQuestOfType(data, QuestType.SWIM_BLOCKS) && player.isUnderWater()) {
                double accum = swimAccum.merge(uid, hDist, Double::sum);
                int blocks = (int) accum;
                if (blocks > 0) {
                    swimAccum.put(uid, accum - blocks);
                    onProgress(player, QuestType.SWIM_BLOCKS, blocks, "any");
                    data = player.getData(ModAttachments.DAILY_QUEST);
                }
            } else if (hasIncompleteQuestOfType(data, QuestType.WALK_BLOCKS) && player.onGround()) {
                double accum = walkAccum.merge(uid, hDist, Double::sum);
                int blocks = (int) accum;
                if (blocks > 0) {
                    walkAccum.put(uid, accum - blocks);
                    onProgress(player, QuestType.WALK_BLOCKS, blocks, "any");
                    data = player.getData(ModAttachments.DAILY_QUEST);
                }
            }
        }

        lastPositions.put(uid, new double[]{px, py, pz});

        // Depth check (overworld only)
        if (player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            if (hasIncompleteQuestOfType(data, QuestType.REACH_DEPTH)) {
                int targetY = getParam2ForType(data, QuestType.REACH_DEPTH);
                if (py <= targetY) {
                    onProgress(player, QuestType.REACH_DEPTH, 1, "any");
                }
            }
        }
    }

    // ── Server tick (timer, night, survival minutes) ──────────────────────────

    public void onServerTick(MinecraftServer server) {
        long now  = server.overworld().getGameTime();
        long dayT = server.overworld().getDayTime() % 24000L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);

            // Auto-assign if 24h IRL have elapsed
            long nowMs = System.currentTimeMillis();
            if (nowMs - data.getLastAssignedDay() >= 24L * 60L * 60L * 1000L || !data.hasQuests()) {
                assignIfNeeded(player);
                data = player.getData(ModAttachments.DAILY_QUEST);
            }

            if (!data.isActive()) continue;

            long remaining = data.ticksRemaining(now);

            // Timer warnings — window of 20 ticks handles skipped exact values after restart
            if (isTimerThreshold(remaining)) {
                player.sendSystemMessage(Component.translatable(
                    "donjonmc.quest.timer_warning", remaining / 20 / 60, (remaining / 20) % 60));
            }

            if (remaining <= 0) {
                onTimerExpired(player, data);
                continue;
            }

            // Sync HUD every second (already throttled by event handler)
            syncToPlayer(player, data);

            // Night tracking
            if (hasIncompleteQuestOfType(data, QuestType.SURVIVE_NIGHT)) {
                data = updateNightState(player, data, dayT, now);
            }

            // Survival minutes — le handler externe throttle déjà à 1×/s,
            // ne pas re-gater sur now%20 (getGameTime désaligné du tickCount).
            if (hasIncompleteQuestOfType(data, QuestType.SURVIVE_MINUTES)) {
                checkSurvivalMinutes(player, data, now);
            }
        }
    }

    // ── Night tracking ────────────────────────────────────────────────────────

    private DailyQuestData updateNightState(ServerPlayer player, DailyQuestData data, long dayT, long now) {
        if (data.getNightState() == 2) return data;

        boolean isNight = dayT >= NIGHT_START && dayT < NIGHT_END;

        // If player is sleeping during night, invalidate the night tracking
        if (isNight && player.isSleeping() && data.getNightState() == 1) {
            data.setNightState(0);
            player.setData(ModAttachments.DAILY_QUEST, data);
            return data;
        }

        if (isNight && data.getNightState() == 0) {
            data.setNightState(1);
            player.setData(ModAttachments.DAILY_QUEST, data);
        } else if (!isNight && data.getNightState() == 1) {
            // Dawn — survived the night
            data.setNightState(2);
            player.setData(ModAttachments.DAILY_QUEST, data);
            onProgress(player, QuestType.SURVIVE_NIGHT, 1, "any");
            data = player.getData(ModAttachments.DAILY_QUEST);
        }
        return data;
    }

    // ── Survival minutes ──────────────────────────────────────────────────────

    private void checkSurvivalMinutes(ServerPlayer player, DailyQuestData data, long now) {
        Long startTick = survivalStartTick.computeIfAbsent(player.getUUID(), k -> now);
        long elapsedMinutes = (now - startTick) / (60L * 20L);

        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null || def.type() != QuestType.SURVIVE_MINUTES) continue;

            int newProg = (int) Math.min(elapsedMinutes, def.target());
            if (newProg > data.getProgress(i)) {
                data.setProgress(i, newProg);
                if (newProg >= def.target()) {
                    data.setCompleted(i, true);
                    onQuestSlotDone(player, def, data);
                }
                player.setData(ModAttachments.DAILY_QUEST, data);
                syncToPlayer(player, data);
            }
            // Pas de break : 28 (normale) et 38 (difficile) peuvent coexister,
            // elles partagent le même survivalStartTick.
        }
    }

    // ── Success / Failure ─────────────────────────────────────────────────────

    private void onQuestSlotDone(ServerPlayer player, QuestDef def, DailyQuestData data) {
        long xpBonus = switch (def.difficulty()) {
            case EASY   -> 150L;
            case NORMAL -> 350L;
            case HARD   -> 700L;
        };
        neoforge.donjonmc.player.PlayerEventHandler.addXpDirect(player, xpBonus);

        player.sendSystemMessage(Component.translatable("donjonmc.quest.one_done",
            Component.translatable(def.nameKey() + ".name"),
            data.countCompleted(), data.questCount()));

        if (data.allCompleted()) onAllDone(player, data);
    }

    private void onAllDone(ServerPlayer player, DailyQuestData data) {
        data.setActive(false);
        player.setData(ModAttachments.DAILY_QUEST, data);
        syncHide(player);

        // +2 points de compétence pour avoir complété les 4 quêtes
        neoforge.donjonmc.player.PlayerData pdata = player.getData(
            neoforge.donjonmc.player.ModAttachments.PLAYER_DATA);
        pdata.addSkillPoints(2);
        player.setData(neoforge.donjonmc.player.ModAttachments.PLAYER_DATA, pdata);
        neoforge.donjonmc.player.PlayerEventHandler.sendSyncPacket(player);

        player.sendSystemMessage(Component.translatable("donjonmc.quest.all_done"));
        player.sendSystemMessage(Component.translatable("donjonmc.quest.skill_points_reward"));
    }

    private void onTimerExpired(ServerPlayer player, DailyQuestData data) {
        data.setActive(false);
        player.setData(ModAttachments.DAILY_QUEST, data);
        syncHide(player);
        player.sendSystemMessage(Component.translatable("donjonmc.quest.failed"));
        PunishmentManager.getInstance().triggerForPlayer(player);
    }

    // ── Admin helpers ─────────────────────────────────────────────────────────

    public void debugCompleteOne(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) {
            player.sendSystemMessage(Component.translatable("donjonmc.quest.not_active"));
            return;
        }
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null) continue;
            data.setProgress(i, def.target());
            data.setCompleted(i, true);
            player.setData(ModAttachments.DAILY_QUEST, data);
            onQuestSlotDone(player, def, data);
            return;
        }
    }

    public void skipToThirtySeconds(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) {
            player.sendSystemMessage(Component.translatable("donjonmc.quest.not_active"));
            return;
        }
        long now = player.server.overworld().getGameTime();
        data.skipToThirtySeconds(now);
        player.setData(ModAttachments.DAILY_QUEST, data);
        // Relire depuis l'attachment après sauvegarde pour garantir la cohérence
        syncToPlayer(player, player.getData(ModAttachments.DAILY_QUEST));
    }

    public void resetQuests(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        data.setActive(false);
        player.setData(ModAttachments.DAILY_QUEST, new DailyQuestData());
        syncHide(player);
        player.sendSystemMessage(Component.translatable("donjonmc.quest.reset"));
    }

    public void disableForPlayer(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (data.isActive()) {
            data.setActive(false);
            player.setData(ModAttachments.DAILY_QUEST, data);
            syncHide(player);
        }
        data = player.getData(ModAttachments.DAILY_QUEST);
        data.setDisabled(true);
        player.setData(ModAttachments.DAILY_QUEST, data);
        player.sendSystemMessage(Component.translatable("donjonmc.quest.disabled"));
    }

    public void enableForPlayer(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        data.setDisabled(false);
        player.setData(ModAttachments.DAILY_QUEST, data);
        player.sendSystemMessage(Component.translatable("donjonmc.quest.enabled"));
    }

    public void onPlayerLogout(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;
        long now = player.server.overworld().getGameTime();
        data.pauseTimer(now);
        player.setData(ModAttachments.DAILY_QUEST, data);
    }

    public void syncOnLogin(ServerPlayer player) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) { syncHide(player); return; }
        long now = player.server.overworld().getGameTime();
        data.resumeTimer(now);
        player.setData(ModAttachments.DAILY_QUEST, data);
        // Re-amorce les compteurs transitoires (perdus au reboot) depuis la
        // progression persistée, sinon le premier événement écraserait la
        // progression sauvegardée (ex. KILL_STREAK 36/40 → 1/40).
        seedTransientCounters(player, data, now);
        syncToPlayer(player, data);
    }

    /**
     * Restaure les compteurs serveur transitoires à partir de la progression
     * sauvegardée, pour les quêtes dont {@code setProgressForType} pilote la
     * progression (et l'écraserait sinon après un redémarrage).
     */
    private void seedTransientCounters(ServerPlayer player, DailyQuestData data, long now) {
        UUID uid = player.getUUID();
        survivalStartTick.put(uid, now);
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null) continue;
            switch (def.type()) {
                case KILL_STREAK         -> killStreaks.put(uid, data.getProgress(i));
                case KILL_CREEPER_NO_EXP -> creeperNoExpCount.put(uid, data.getProgress(i));
                case SURVIVE_MINUTES     -> survivalStartTick.put(uid,
                                                now - (long) data.getProgress(i) * 60L * 20L);
                default -> {}
            }
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    private void syncToPlayer(ServerPlayer player, DailyQuestData data) {
        long now = player.server.overworld().getGameTime();
        long remaining = data.ticksRemaining(now) / 20L;

        int[] ids  = new int[4];
        int[] prog = new int[4];
        int[] tgt  = new int[4];
        boolean[] done = new boolean[4];
        Arrays.fill(ids, -1);

        for (int i = 0; i < Math.min(4, data.questCount()); i++) {
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            ids[i]  = data.getQuestId(i);
            prog[i] = data.getProgress(i);
            tgt[i]  = def != null ? def.target() : 1;
            done[i] = data.isCompleted(i);
        }

        int questsDone = data.countCompleted();
        PacketDistributor.sendToPlayer(player,
            new SyncDailyQuestPacket(questsDone, data.questCount(), remaining, ids, prog, tgt, done));
    }

    private void syncHide(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            new SyncDailyQuestPacket(0, 4, -1L,
                new int[]{-1,-1,-1,-1}, new int[4], new int[4], new boolean[4]));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private boolean matchesFilter(String questFilter, String eventFilter) {
        if ("any".equals(questFilter)) return true;
        if ("spider".equals(questFilter)) return "spider".equals(eventFilter) || "cave_spider".equals(eventFilter);
        return questFilter.equals(eventFilter);
    }

    private boolean hasQuestOfType(DailyQuestData data, QuestType type) {
        for (int i = 0; i < data.questCount(); i++) {
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def != null && def.type() == type) return true;
        }
        return false;
    }

    private boolean hasIncompleteQuestOfType(DailyQuestData data, QuestType type) {
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def != null && def.type() == type) return true;
        }
        return false;
    }

    private int getParam2ForType(DailyQuestData data, QuestType type) {
        for (int i = 0; i < data.questCount(); i++) {
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def != null && def.type() == type) return def.param2();
        }
        return 0;
    }

    private void resetProgressForType(ServerPlayer player, QuestType type) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;
        boolean changed = false;
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null || def.type() != type) continue;
            data.setProgress(i, 0);
            changed = true;
        }
        if (changed) {
            player.setData(ModAttachments.DAILY_QUEST, data);
            syncToPlayer(player, data);
        }
    }

    private void setProgressForType(ServerPlayer player, QuestType type, int value) {
        DailyQuestData data = player.getData(ModAttachments.DAILY_QUEST);
        if (!data.isActive()) return;
        boolean changed = false;
        for (int i = 0; i < data.questCount(); i++) {
            if (data.isCompleted(i)) continue;
            QuestDef def = QuestRegistry.byId(data.getQuestId(i));
            if (def == null || def.type() != type) continue;
            data.setProgress(i, value);
            if (value >= def.target()) {
                data.setCompleted(i, true);
                onQuestSlotDone(player, def, data);
            }
            changed = true;
        }
        if (changed) {
            player.setData(ModAttachments.DAILY_QUEST, data);
            syncToPlayer(player, data);
        }
    }

    private static boolean isTimerThreshold(long remaining) {
        long[] thresholds = { 30 * 60 * 20L, 10 * 60 * 20L, 5 * 60 * 20L, 60 * 20L };
        for (long t : thresholds) {
            if (remaining <= t && remaining > t - 20) return true;
        }
        return false;
    }

    private static <T> List<T> shuffle(List<T> list, Random rng) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, rng);
        return copy;
    }
}
