package neoforge.donjonmc.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import neoforge.donjonmc.Config;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.mob.DungeonMobRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Épreuve de classe (niveau 50) — donjon en 4 phases dans la Burning Arena.
 *
 * Déroulé :
 *   Phase 1 : vague de gobelins + un Giga Gobelin
 *   Phase 2 : donjon de rang B — 25 mobs scalés rang B + le Golem (boss rang B)
 *   Phase 3 : vague + Igris
 *   Phase 4 : vague + Ignis (cataclysm:ignis tel quel, fallback Wither Skeleton)
 *   Victoire  → classe CHOISIE appliquée, sort donné, retour overworld
 *   Mort/déco → échec, cooldown 24h IRL, retour overworld
 *
 * L'arène est posée depuis le template cataclysm:burning_arena1 (fallback :
 * arène en briques) sur une grille d'instances (1 slot par joueur), puis
 * RETIRÉE bloc par bloc à la fin de l'épreuve (victoire comme défaite).
 */
@EventBusSubscriber(modid = Donjonmc.MODID)
public final class ClassTrialHandler {

    private ClassTrialHandler() {}

    public static final ResourceKey<Level> TRIAL_DIMENSION = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "class_trial")
    );

    /** Cooldown après échec en ms (heures configurables dans donjonmc-common.toml). */
    public static long cooldownMs() {
        return Config.trialCooldownHours * 60L * 60L * 1000L;
    }

    private static final String  TRIAL_TAG        = "donjonmc_trial_owner";
    private static final int     SLOT_SPACING     = 1024;  // espacement entre instances
    private static final int     ARENA_Y          = 64;
    private static final int     FALLBACK_RADIUS  = 18;    // arène de secours (Ignis a besoin de place)
    private static final int     CLEANUP_BUDGET   = 20000; // blocs retirés par tick
    private static final int     PHASE_DELAY_TICKS  = 600; // 30 s de préparation avant chaque phase
    private static final int     RETURN_DELAY_TICKS = 200; // 10 s avant le TP retour en fin d'épreuve
    private static final String  IGNIS_ID         = "cataclysm:ignis";

    // Multiplicateurs rang B (alignés sur DungeonSpawnManager) pour la phase 2.
    private static final double  RANKB_MOB_HP     = 3.0;
    private static final double  RANKB_MOB_ATK    = 2.5;
    private static final double  RANKB_BOSS_HP    = 8.75;
    private static final double  RANKB_BOSS_ATK   = 5.5;

    /**
     * L'arène complète de Cataclysm fait 85×82×85, assemblée à partir de
     * 8 templates : 4 quadrants de sol (48 de haut) + 4 quadrants de plafond
     * (34 de haut) posés à y+48. Offsets relevés dans le bytecode de
     * Burning_Arena_Structure.start() (Cataclysm 3.30) — un template seul
     * ne donne qu'un quart d'arène.
     */
    private record ArenaPiece(ResourceLocation id, int dx, int dy, int dz) {
        static ArenaPiece of(String name, int dx, int dy, int dz) {
            return new ArenaPiece(
                ResourceLocation.fromNamespaceAndPath("cataclysm", name), dx, dy, dz);
        }
    }
    private static final List<ArenaPiece> ARENA_PIECES = List.of(
        ArenaPiece.of("burning_arena1",  0,  0,  0),
        ArenaPiece.of("burning_arena2",  0,  0, 38),
        ArenaPiece.of("burning_arena3", 47,  0,  0),
        ArenaPiece.of("burning_arena4", 47,  0, 38),
        ArenaPiece.of("burning_arena5",  0, 48,  0),
        ArenaPiece.of("burning_arena6",  0, 48, 38),
        ArenaPiece.of("burning_arena7", 47, 48,  0),
        ArenaPiece.of("burning_arena8", 47, 48, 38)
    );
    private static final int ARENA_XZ     = 85; // emprise au sol de l'arène assemblée
    private static final int ARENA_HEIGHT = 82;

    // ── Sessions ────────────────────────────────────────────────────────────

    private static final class TrialSession {
        final UUID        playerId;
        final PlayerClass chosenClass;
        final int         slot;
        final BlockPos    center;
        int               phase = 0;             // 0 = pas démarré, 1..3
        final Set<UUID>   aliveMobs = new HashSet<>();
        BlockPos          structMin = null;      // bounding box réelle de l'arène posée
        BlockPos          structMax = null;
        int               floorY;                // Y des pieds (joueur + mobs), fixé par buildArena
        int               pendingPhase = 0;      // phase programmée (0 = aucune)
        int               delayTicks   = 0;      // ticks restants avant son lancement

        TrialSession(UUID playerId, PlayerClass chosenClass, int slot) {
            this.playerId    = playerId;
            this.chosenClass = chosenClass;
            this.slot        = slot;
            this.center      = new BlockPos(slot * SLOT_SPACING, ARENA_Y, 0);
            this.floorY      = ARENA_Y + 1;
        }
    }

    private static final Map<UUID, TrialSession> sessions  = new HashMap<>();
    private static final Set<Integer>            usedSlots = new HashSet<>();

    /** Zones d'arène à effacer progressivement (évite un freeze serveur). */
    private static final ArrayDeque<CleanupJob> cleanupQueue = new ArrayDeque<>();

    private static final class CleanupJob {
        final BlockPos min, max;
        final int slot; // libéré quand la zone est entièrement effacée (-1 = aucun)
        int x, y, z;
        CleanupJob(BlockPos min, BlockPos max, int slot) {
            this.min = min; this.max = max; this.slot = slot;
            this.x = min.getX(); this.y = max.getY(); this.z = min.getZ(); // on efface de haut en bas
        }
    }

    /** Fins d'épreuve différées : 10 s sur place avant TP retour + effacement. */
    private static final class PendingEnd {
        final UUID playerId;
        final BlockPos min, max;
        final int slot;
        int ticks = RETURN_DELAY_TICKS;
        PendingEnd(UUID playerId, BlockPos min, BlockPos max, int slot) {
            this.playerId = playerId; this.min = min; this.max = max; this.slot = slot;
        }
    }
    private static final List<PendingEnd> pendingEnds = new ArrayList<>();

    // ── Vagues par phase (ajuste librement les compositions) ────────────────

    private record WaveEntry(Supplier<EntityType<? extends Mob>> type, int count) {}

    private static List<WaveEntry> waveForPhase(int phase) {
        List<WaveEntry> wave = new ArrayList<>();
        switch (phase) {
            case 1 -> {
                wave.add(new WaveEntry(DungeonMobRegistry.GOBLIN::get,        50));
                wave.add(new WaveEntry(DungeonMobRegistry.HOBGOBLIN_CLUB::get, 4));
                wave.add(new WaveEntry(DungeonMobRegistry.SKULL::get,          6));
                wave.add(new WaveEntry(DungeonMobRegistry.GIGA_GOBLIN::get,    1));
            }
            case 2 -> {
                // Donjon de rang B : 25 mobs scalés rang B (cf. scaleStats) + Golem.
                wave.add(new WaveEntry(DungeonMobRegistry.ORC::get,            10));
                wave.add(new WaveEntry(DungeonMobRegistry.HOBGOBLIN_CLUB::get,  8));
                wave.add(new WaveEntry(DungeonMobRegistry.HOBGOBLIN_BOMBER::get,7));
                // + Golem (BOSS_GOLEM), géré à part dans startPhase()
            }
            case 3 -> {
                wave.add(new WaveEntry(DungeonMobRegistry.UNDEAD::get,         9));
                wave.add(new WaveEntry(DungeonMobRegistry.SHADOW_SOLDIER::get, 8));
                // + Igris, géré à part dans startPhase()
            }
            case 4 -> {
                wave.add(new WaveEntry(DungeonMobRegistry.DEMON_GUARD::get,    3));
                wave.add(new WaveEntry(DungeonMobRegistry.WILD_DEMON::get,     2));
                // + Ignis, géré à part dans startPhase()
            }
        }
        return wave;
    }

    // ── API publique ─────────────────────────────────────────────────────────

    /**
     * Démarre l'épreuve pour la classe CHOISIE par le joueur (via le menu Hunter).
     * Vérifie niveau, cooldown 24h et session existante.
     */
    public static void startTrial(ServerPlayer player, PlayerClass chosenClass) {
        if (chosenClass == null || chosenClass == PlayerClass.NONE) return;

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);

        if (data.getLevel() < 50) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.level_too_low"));
            return;
        }
        if (data.getPlayerClassOrdinal() != PlayerClass.NONE.ordinal()) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.already_has_class"));
            return;
        }
        long remaining = cooldownRemainingMs(data);
        if (remaining > 0) {
            long h = remaining / 3_600_000L;
            long m = (remaining % 3_600_000L) / 60_000L;
            player.sendSystemMessage(Component.translatable("donjonmc.trial.cooldown", h, m));
            return;
        }
        if (sessions.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.already_started"));
            return;
        }

        ServerLevel trialLevel = player.server.getLevel(TRIAL_DIMENSION);
        if (trialLevel == null) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.error"));
            return;
        }

        TrialSession session = new TrialSession(player.getUUID(), chosenClass, allocateSlot());
        sessions.put(player.getUUID(), session);
        usedSlots.add(session.slot);

        buildArena(trialLevel, session);

        double x = session.center.getX() + 0.5;
        double y = session.floorY;
        double z = session.center.getZ() + 0.5;
        player.changeDimension(new DimensionTransition(
            trialLevel, new Vec3(x, y, z), Vec3.ZERO, 0f, 0f, false,
            DimensionTransition.DO_NOTHING
        ));

        player.sendSystemMessage(Component.translatable("donjonmc.trial.start",
            Component.translatable(chosenClass.nameLangKey())));

        schedulePhase(player, session, 1);
    }

    /** Programme une phase avec 30 s de préparation (compte à rebours dans onServerTick). */
    private static void schedulePhase(ServerPlayer player, TrialSession session, int phase) {
        session.pendingPhase = phase;
        session.delayTicks   = PHASE_DELAY_TICKS;
        player.sendSystemMessage(Component.translatable("donjonmc.trial.incoming",
            phase, PHASE_DELAY_TICKS / 20));
    }

    /** Compatibilité avec PlayerEventHandler : mob DonjonMC tué pendant l'épreuve. */
    public static boolean onMobKilled(LivingEntity mob, ServerPlayer killer) {
        TrialSession session = sessions.get(killer.getUUID());
        if (session == null) return false;
        return handleTrialMobDeath(mob, session);
    }

    /** Mort du joueur pendant l'épreuve → échec + cooldown 24h. */
    public static void onPlayerDeathInTrial(ServerPlayer player) {
        failTrial(player, true);
    }

    public static boolean isInTrial(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public static long cooldownRemainingMs(PlayerData data) {
        long last = data.getLastTrialFailMs();
        if (last <= 0) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, cooldownMs() - elapsed);
    }

    // ── Événements ───────────────────────────────────────────────────────────

    /** Couvre Ignis et toute mort de mob d'épreuve qui ne passe pas par PlayerEventHandler. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel)) return;
        if (!entity.getPersistentData().contains(TRIAL_TAG)) return;

        UUID owner = entity.getPersistentData().getUUID(TRIAL_TAG);
        TrialSession session = sessions.get(owner);
        if (session != null) handleTrialMobDeath(entity, session);
    }

    /** Aucun drop pour les mobs d'épreuve (sinon Ignis lâche son loot endgame). */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getPersistentData().contains(TRIAL_TAG)) {
            event.setCanceled(true);
        }
    }

    /** Pas d'XP vanilla non plus : la récompense, c'est la classe. */
    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getPersistentData().contains(TRIAL_TAG)) {
            event.setCanceled(true);
        }
    }

    /** Déconnexion en pleine épreuve → échec + cooldown (anti-exploit). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (sessions.containsKey(player.getUUID())) {
            failTrial(player, true);
        }
    }

    /** Compte à rebours des phases programmées + effacement progressif des arènes terminées. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickPendingPhases(event);
        reconcileWaves(event);
        tickPendingEnds(event);

        if (cleanupQueue.isEmpty()) return;
        ServerLevel level = event.getServer().getLevel(TRIAL_DIMENSION);
        if (level == null) { cleanupQueue.clear(); return; }

        CleanupJob job = cleanupQueue.peek();
        int budget = CLEANUP_BUDGET;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        while (budget-- > 0) {
            pos.set(job.x, job.y, job.z);
            if (!level.getBlockState(pos).isAir()) {
                // flag 2 | 16 : envoie au client mais saute les updates de voisinage
                // (inutiles ici, on remplace tout par du vide)
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
            // avance le curseur x → z → y (de haut en bas)
            if (++job.x > job.max.getX()) {
                job.x = job.min.getX();
                if (++job.z > job.max.getZ()) {
                    job.z = job.min.getZ();
                    if (--job.y < job.min.getY()) {
                        cleanupQueue.poll(); // zone terminée
                        usedSlots.remove(job.slot); // slot réutilisable
                        return;
                    }
                }
            }
        }
    }

    // ── Logique interne ──────────────────────────────────────────────────────

    /** Décompte les 30 s de préparation, affiche le compte à rebours, lance la phase. */
    private static void tickPendingPhases(ServerTickEvent.Post event) {
        if (sessions.isEmpty()) return;

        for (TrialSession session : List.copyOf(sessions.values())) {
            if (session.pendingPhase == 0) continue;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerId);
            if (player == null) continue; // déconnexion gérée par onLogout

            session.delayTicks--;
            if (session.delayTicks <= 0) {
                int phase = session.pendingPhase;
                session.pendingPhase = 0;
                ServerLevel trialLevel = event.getServer().getLevel(TRIAL_DIMENSION);
                if (trialLevel != null) startPhase(trialLevel, player, session, phase);
            } else if (session.delayTicks % 20 == 0 && session.delayTicks <= 200) {
                // 10 dernières secondes : compte à rebours en barre d'action
                player.displayClientMessage(Component.translatable("donjonmc.trial.countdown",
                    session.pendingPhase, session.delayTicks / 20), true);
            }
        }
    }

    private static boolean handleTrialMobDeath(LivingEntity mob, TrialSession session) {
        if (!session.aliveMobs.remove(mob.getUUID())) return false;
        if (!session.aliveMobs.isEmpty()) return true;

        // Vague nettoyée → phase suivante ou victoire
        ServerLevel level = (ServerLevel) mob.level();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.playerId);
        if (player == null) return true; // logout géré par onLogout

        advanceAfterWaveCleared(player, session);
        return true;
    }

    /** Vague terminée : enchaîne la phase suivante ou conclut l'épreuve. */
    private static void advanceAfterWaveCleared(ServerPlayer player, TrialSession session) {
        if (session.phase >= 4) {
            completeTrial(player, session);
        } else {
            schedulePhase(player, session, session.phase + 1);
        }
    }

    /**
     * Filet anti-soft-lock : un mob d'épreuve peut disparaître sans déclencher de
     * LivingDeathEvent (tombé dans le vide, déchargé, discard externe). Sans ça,
     * {@code aliveMobs} ne se vide jamais et la phase reste bloquée à vie.
     * Une fois par seconde, on purge les UUID qui ne correspondent plus à une
     * entité vivante, et si la vague se retrouve vide on enchaîne quand même.
     */
    private static void reconcileWaves(ServerTickEvent.Post event) {
        if (sessions.isEmpty()) return;
        if (event.getServer().getTickCount() % 20 != 0) return; // 1 fois/seconde

        ServerLevel level = event.getServer().getLevel(TRIAL_DIMENSION);
        if (level == null) return;

        for (TrialSession session : List.copyOf(sessions.values())) {
            // On ne réconcilie qu'une phase réellement en cours (pas pendant la
            // préparation de 30 s, où aliveMobs est légitimement vide).
            if (session.pendingPhase != 0 || session.phase < 1) continue;

            ServerPlayer player = event.getServer().getPlayerList().getPlayer(session.playerId);
            if (player == null) continue; // logout géré par onLogout

            session.aliveMobs.removeIf(id -> {
                Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });

            if (session.aliveMobs.isEmpty()) {
                advanceAfterWaveCleared(player, session);
            }
        }
    }

    private static void startPhase(ServerLevel level, ServerPlayer player, TrialSession session, int phase) {
        session.phase = phase;

        // Annonce dramatique : titre plein écran + sous-titre + chat + son
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(
            Component.translatable("donjonmc.trial.phase.title." + phase)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
            Component.translatable("donjonmc.trial.phase.sub." + phase)));
        player.sendSystemMessage(Component.translatable("donjonmc.trial.phase.sub." + phase));
        player.playNotifySound(
            phase == 4 ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.WITHER_SPAWN,
            SoundSource.HOSTILE, 0.7f, 1f);

        RandomSource random = level.getRandom();
        for (WaveEntry entry : waveForPhase(phase)) {
            for (int i = 0; i < entry.count(); i++) {
                Mob mob = entry.type().get().create(level);
                if (mob == null) continue;
                placeAroundCenter(mob, session, 6 + random.nextInt(6), random);
                if (phase == 2) scaleStats(mob, RANKB_MOB_HP, RANKB_MOB_ATK); // donjon rang B
                tagAndSpawn(level, player, session, mob);
            }
        }

        switch (phase) {
            case 2 -> {
                // Boss du donjon de rang B : le Golem, scalé comme un boss rang B.
                Mob golem = DungeonMobRegistry.BOSS_GOLEM.get().create(level);
                if (golem != null) {
                    placeAroundCenter(golem, session, 10, random);
                    scaleStats(golem, RANKB_BOSS_HP, RANKB_BOSS_ATK);
                    tagAndSpawn(level, player, session, golem);
                }
            }
            case 3 -> {
                Mob igris = DungeonMobRegistry.IGRIS.get().create(level);
                if (igris != null) {
                    placeAroundCenter(igris, session, 10, random);
                    tagAndSpawn(level, player, session, igris);
                }
            }
            case 4 -> spawnFinalBoss(level, player, session, random);
        }
    }

    /** Multiplie PV (et resoigne) + dégâts d'un mob ; valeurs rang B alignées sur DungeonSpawnManager. */
    private static void scaleStats(Mob mob, double hpMult, double atkMult) {
        var hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(hp.getBaseValue() * hpMult);
            mob.setHealth(mob.getMaxHealth());
        }
        var atk = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(atk.getBaseValue() * atkMult);
    }

    /** Ignis tel quel si Cataclysm est présent, sinon le Gardien Wither Skeleton. */
    private static void spawnFinalBoss(ServerLevel level, ServerPlayer player,
                                       TrialSession session, RandomSource random) {
        EntityType<?> type = EntityType.byString(IGNIS_ID).orElse(null);
        Mob boss = null;
        boolean isIgnis = false;

        if (type != null && type.create(level) instanceof Mob m) {
            boss = m;
            isIgnis = true;
        }
        if (boss == null) {
            // Fallback sans Cataclysm
            boss = EntityType.WITHER_SKELETON.create(level);
            if (boss != null) {
                boss.setCustomName(Component.translatable("donjonmc.trial.boss")
                    .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                boss.setCustomNameVisible(true);
            }
        }
        if (boss == null) return;

        placeAroundCenter(boss, session, 12, random);
        if (boss instanceof Mob mob) {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null);
        }
        if (isIgnis) {
            // -25 % de PV au total (après finalizeSpawn pour ne pas être écrasé)
            var maxHealth = boss.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(maxHealth.getBaseValue() * 0.765);
                boss.setHealth(boss.getMaxHealth());
            }
        }
        tagAndSpawn(level, player, session, boss);
    }

    private static void tagAndSpawn(ServerLevel level, ServerPlayer player,
                                    TrialSession session, Mob mob) {
        mob.getPersistentData().putUUID(TRIAL_TAG, session.playerId);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        session.aliveMobs.add(mob.getUUID());
        mob.setTarget(player);
    }

    private static void placeAroundCenter(Mob mob, TrialSession session, int radius, RandomSource random) {
        double angle = random.nextDouble() * 2 * Math.PI;
        mob.moveTo(session.center.getX() + Math.cos(angle) * radius,
                   session.floorY,
                   session.center.getZ() + Math.sin(angle) * radius,
                   random.nextFloat() * 360f, 0f);
    }

    private static void completeTrial(ServerPlayer player, TrialSession session) {
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        data.setPlayerClass(session.chosenClass);
        player.setData(ModAttachments.PLAYER_DATA, data);

        PlayerEventHandler.applyClassModifiers(player, data);
        neoforge.donjonmc.spell.SpellUnlockHandler.onClassUnlocked(player, session.chosenClass);
        player.setHealth(player.getMaxHealth());

        player.sendSystemMessage(Component.translatable("donjonmc.trial.complete",
            Component.translatable(session.chosenClass.nameLangKey())));

        endSession(player, session, true);
        PlayerEventHandler.sendSyncPacket(player);
    }

    private static void failTrial(ServerPlayer player, boolean applyCooldown) {
        TrialSession session = sessions.get(player.getUUID());
        if (session == null) return;

        if (applyCooldown) {
            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            data.setLastTrialFailMs(System.currentTimeMillis());
            player.setData(ModAttachments.PLAYER_DATA, data);
        }
        player.sendSystemMessage(Component.translatable("donjonmc.trial.fail"));
        endSession(player, session, false);
    }

    /** Clôture commune : purge des mobs, puis 10 s sur place avant TP retour + effacement. */
    private static void endSession(ServerPlayer player, TrialSession session, boolean victory) {
        sessions.remove(session.playerId);
        // Le slot n'est libéré qu'après effacement complet de l'arène (voir CleanupJob)

        ServerLevel trialLevel = player.server.getLevel(TRIAL_DIMENSION);
        if (trialLevel != null) {
            // Mobs restants
            for (UUID id : session.aliveMobs) {
                Entity e = trialLevel.getEntity(id);
                if (e != null) e.discard();
            }
            session.aliveMobs.clear();
        }

        // Retour différé : 10 s pour souffler (mort = respawn immédiat, géré ailleurs)
        if (player.isAlive() && player.level().dimension() == TRIAL_DIMENSION) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.return",
                RETURN_DELAY_TICKS / 20));
        }
        pendingEnds.add(new PendingEnd(session.playerId,
            session.structMin, session.structMax, session.slot));
    }

    /** Décompte des 10 s de fin : TP retour puis effacement de l'arène. */
    private static void tickPendingEnds(ServerTickEvent.Post event) {
        if (pendingEnds.isEmpty()) return;

        Iterator<PendingEnd> it = pendingEnds.iterator();
        while (it.hasNext()) {
            PendingEnd end = it.next();
            if (--end.ticks > 0) continue;
            it.remove();

            // TP retour — seulement si le joueur est encore dans l'arène et n'a pas
            // relancé une épreuve entre-temps
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(end.playerId);
            if (player != null && player.isAlive()
                    && player.level().dimension() == TRIAL_DIMENSION
                    && !sessions.containsKey(end.playerId)) {
                ServerLevel overworld = event.getServer().overworld();
                BlockPos spawn = overworld.getSharedSpawnPos();
                player.changeDimension(new DimensionTransition(
                    overworld,
                    new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5),
                    Vec3.ZERO, 0f, 0f, false,
                    DimensionTransition.DO_NOTHING
                ));
            }

            if (end.min != null && end.max != null) {
                cleanupQueue.add(new CleanupJob(end.min, end.max, end.slot));
            } else {
                usedSlots.remove(end.slot); // rien à effacer (arène jamais posée)
            }
        }
    }

    // ── Construction / placement de l'arène ──────────────────────────────────

    private static void buildArena(ServerLevel level, TrialSession session) {
        var manager = level.getStructureManager();

        // Assemble les 8 pièces de l'arène, centrée sur le slot
        BlockPos origin = session.center.offset(-ARENA_XZ / 2, 0, -ARENA_XZ / 2);
        boolean placed = false;
        for (ArenaPiece piece : ARENA_PIECES) {
            StructureTemplate template = manager.get(piece.id()).orElse(null);
            if (template == null) continue; // Cataclysm absent → fallback plus bas
            BlockPos at = origin.offset(piece.dx(), piece.dy(), piece.dz());
            // BlockIgnoreProcessor : ne pose ni les structure blocks (marqueurs de
            // données du mod) ni la netherrack (remblai de worldgen, moche en void)
            template.placeInWorld(level, at, at,
                new StructurePlaceSettings().addProcessor(new BlockIgnoreProcessor(
                    List.of(Blocks.STRUCTURE_BLOCK, Blocks.NETHERRACK))),
                level.getRandom(), 2);
            placed = true;
        }

        if (placed) {
            session.structMin = origin;
            session.structMax = origin.offset(ARENA_XZ, ARENA_HEIGHT, ARENA_XZ);
            session.floorY    = findArenaFloorY(level, session.center, origin.getY());
        } else {
            buildFallbackArena(level, session);
        }
    }

    /**
     * Cherche le sol réel de l'arène au centre : premier bloc solide (avec
     * 2 blocs d'air au-dessus) en partant du milieu de l'intérieur, sous le
     * plafond posé à y+48. Fallback : juste au-dessus de l'origine.
     */
    private static int findArenaFloorY(ServerLevel level, BlockPos center, int baseY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = baseY + 44; y >= baseY; y--) {
            pos.set(center.getX(), y, center.getZ());
            if (!level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(pos.above(2)).isAir()) {
                return y + 1;
            }
        }
        return baseY + 1;
    }

    /** Arène en briques agrandie (Ignis a besoin d'espace) si Cataclysm est absent. */
    private static void buildFallbackArena(ServerLevel level, TrialSession session) {
        BlockPos c = session.center;
        int r = FALLBACK_RADIUS;

        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                level.setBlock(c.offset(dx, 0, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);

        for (int dy = 1; dy <= 6; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                level.setBlock(c.offset(dx, dy, -r), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(c.offset(dx, dy,  r), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
            for (int dz = -r + 1; dz < r; dz++) {
                level.setBlock(c.offset(-r, dy, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(c.offset( r, dy, dz), Blocks.STONE_BRICKS.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            BlockPos torch = c.offset((int) (Math.cos(angle) * (r - 1)), 2,
                                      (int) (Math.sin(angle) * (r - 1)));
            level.setBlock(torch, Blocks.TORCH.defaultBlockState(), 2);
        }

        session.structMin = c.offset(-r, 0, -r);
        session.structMax = c.offset( r, 6,  r);
        session.floorY    = c.getY() + 1;
    }

    private static int allocateSlot() {
        int slot = 0;
        while (usedSlots.contains(slot)) slot++;
        return slot;
    }
}
