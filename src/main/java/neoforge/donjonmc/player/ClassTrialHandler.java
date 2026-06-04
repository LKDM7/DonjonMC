package neoforge.donjonmc.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import neoforge.donjonmc.Donjonmc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClassTrialHandler {

    private ClassTrialHandler() {}

    public static final ResourceKey<Level> TRIAL_DIMENSION = ResourceKey.create(
        Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "class_trial")
    );

    // Centre de l'arène dans la dimension vide
    private static final BlockPos ARENA_CENTER = new BlockPos(0, 64, 0);
    private static final int ARENA_RADIUS = 10;

    // playerUUID → ensemble des UUID de monstres encore en vie
    private static final Map<UUID, Set<UUID>> activeTrials = new HashMap<>();

    // ── API publique ────────────────────────────────────────────────────────

    public static void startTrial(ServerPlayer player) {
        if (activeTrials.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.already_started"));
            return;
        }

        ServerLevel trialLevel = player.server.getLevel(TRIAL_DIMENSION);
        if (trialLevel == null) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.error"));
            return;
        }

        buildArenaIfNeeded(trialLevel);

        Set<UUID> mobs = new HashSet<>();
        activeTrials.put(player.getUUID(), mobs);

        double x = ARENA_CENTER.getX() + 0.5;
        double y = ARENA_CENTER.getY() + 1.0;
        double z = ARENA_CENTER.getZ() + 0.5;

        player.changeDimension(new DimensionTransition(
            trialLevel, new Vec3(x, y, z), Vec3.ZERO, 0f, 0f, false,
            DimensionTransition.DO_NOTHING
        ));

        spawnTrialMobs(trialLevel, player, mobs);

        player.sendSystemMessage(Component.translatable("donjonmc.trial.start"));
    }

    /**
     * Appelé quand un monstre meurt.
     * Retourne true si c'était un monstre de l'épreuve (ne pas accorder l'XP normale).
     */
    public static boolean onMobKilled(LivingEntity mob, ServerPlayer killer) {
        Set<UUID> mobs = activeTrials.get(killer.getUUID());
        if (mobs == null) return false;

        if (!mobs.remove(mob.getUUID())) return false;

        if (mobs.isEmpty()) {
            activeTrials.remove(killer.getUUID());
            completeTrial(killer);
        }
        return true;
    }

    /** Appelé si le joueur meurt pendant l'épreuve. */
    public static void onPlayerDeathInTrial(ServerPlayer player) {
        Set<UUID> mobs = activeTrials.remove(player.getUUID());
        if (mobs == null) return;

        // Retire les monstres restants de la dimension
        ServerLevel trialLevel = player.server.getLevel(TRIAL_DIMENSION);
        if (trialLevel != null) {
            for (UUID id : mobs) {
                Entity e = trialLevel.getEntity(id);
                if (e != null) e.discard();
            }
        }
        player.sendSystemMessage(Component.translatable("donjonmc.trial.fail"));
    }

    /** Vérifie si un joueur est actuellement dans une épreuve. */
    public static boolean isInTrial(UUID uuid) {
        return activeTrials.containsKey(uuid);
    }

    // ── Logique interne ─────────────────────────────────────────────────────

    private static void buildArenaIfNeeded(ServerLevel level) {
        // Le bloc central est non-air si l'arène a déjà été construite
        if (!level.getBlockState(ARENA_CENTER).isAir()) return;

        // Sol
        for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
            for (int dz = -ARENA_RADIUS; dz <= ARENA_RADIUS; dz++) {
                level.setBlock(ARENA_CENTER.offset(dx, 0, dz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }

        // Murs (4 blocs de haut)
        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = -ARENA_RADIUS; dx <= ARENA_RADIUS; dx++) {
                level.setBlock(ARENA_CENTER.offset(dx, dy, -ARENA_RADIUS),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
                level.setBlock(ARENA_CENTER.offset(dx, dy, ARENA_RADIUS),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
            for (int dz = -ARENA_RADIUS + 1; dz < ARENA_RADIUS; dz++) {
                level.setBlock(ARENA_CENTER.offset(-ARENA_RADIUS, dy, dz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
                level.setBlock(ARENA_CENTER.offset(ARENA_RADIUS, dy, dz),
                    Blocks.STONE_BRICKS.defaultBlockState(), 3);
            }
        }

        // Torches sur les murs intérieurs
        level.setBlock(ARENA_CENTER.offset(ARENA_RADIUS - 1, 2, 0),
            Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(ARENA_CENTER.offset(-ARENA_RADIUS + 1, 2, 0),
            Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(ARENA_CENTER.offset(0, 2, ARENA_RADIUS - 1),
            Blocks.TORCH.defaultBlockState(), 3);
        level.setBlock(ARENA_CENTER.offset(0, 2, -ARENA_RADIUS + 1),
            Blocks.TORCH.defaultBlockState(), 3);
    }

    private static void spawnTrialMobs(ServerLevel level, ServerPlayer player, Set<UUID> mobs) {
        // 3 Zombies en armure de fer
        for (int i = 0; i < 3; i++) {
            Zombie z = new Zombie(EntityType.ZOMBIE, level);
            double angle = (2 * Math.PI * i) / 3;
            z.moveTo(ARENA_CENTER.getX() + Math.cos(angle) * 5,
                     ARENA_CENTER.getY() + 1,
                     ARENA_CENTER.getZ() + Math.sin(angle) * 5, 0f, 0f);
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            z.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            z.setDropChance(EquipmentSlot.HEAD, 0f);
            z.setDropChance(EquipmentSlot.CHEST, 0f);
            level.addFreshEntity(z);
            mobs.add(z.getUUID());
        }

        // 2 Squelettes
        for (int i = 0; i < 2; i++) {
            Skeleton sk = new Skeleton(EntityType.SKELETON, level);
            double angle = (2 * Math.PI * i) / 2 + Math.PI / 3;
            sk.moveTo(ARENA_CENTER.getX() + Math.cos(angle) * 7,
                      ARENA_CENTER.getY() + 1,
                      ARENA_CENTER.getZ() + Math.sin(angle) * 7, 0f, 0f);
            sk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            sk.setDropChance(EquipmentSlot.MAINHAND, 0f);
            level.addFreshEntity(sk);
            mobs.add(sk.getUUID());
        }

        // 1 Squelette de Wither nommé « Gardien de l'Épreuve »
        WitherSkeleton boss = new WitherSkeleton(EntityType.WITHER_SKELETON, level);
        boss.moveTo(ARENA_CENTER.getX(), ARENA_CENTER.getY() + 1, ARENA_CENTER.getZ() + 7, 0f, 0f);
        boss.setCustomName(Component.translatable("donjonmc.trial.boss")
            .withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
        boss.setCustomNameVisible(true);
        boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        boss.setDropChance(EquipmentSlot.MAINHAND, 0f);
        level.addFreshEntity(boss);
        mobs.add(boss.getUUID());

        // Définit le joueur comme cible initiale pour tous
        for (UUID id : mobs) {
            Entity e = level.getEntity(id);
            if (e instanceof Mob mob) mob.setTarget(player);
        }
    }

    private static void completeTrial(ServerPlayer player) {
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        PlayerClass cls = determineClass(data);
        data.setPlayerClass(cls);
        player.setData(ModAttachments.PLAYER_DATA, data);

        // Applique les bonus de classe (attributs, modificateurs)
        PlayerEventHandler.applyClassModifiers(player, data);

        // Donne le sort de classe via ISB
        neoforge.donjonmc.spell.SpellUnlockHandler.onClassUnlocked(player, cls);

        // Soin complet (ex : Tank avec +20 PV)
        player.setHealth(player.getMaxHealth());

        // Retour dans l'overworld (point d'apparition du monde)
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.changeDimension(new DimensionTransition(
            overworld,
            new Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5),
            Vec3.ZERO, 0f, 0f, false,
            DimensionTransition.DO_NOTHING
        ));

        player.sendSystemMessage(Component.translatable("donjonmc.trial.complete",
            Component.translatable(cls.nameLangKey())));
        PlayerEventHandler.sendSyncPacket(player);
    }

    private static PlayerClass determineClass(PlayerData d) {
        int str  = d.getStrength();
        int agi  = d.getAgility();
        int vit  = d.getVitality();
        int intel = d.getIntelligence();
        int perc = d.getPerception();

        int max = Math.max(str, Math.max(agi, Math.max(vit, Math.max(intel, perc))));

        // Priorité en cas d'égalité : Force > Agilité > Intelligence > Healer
        if (str == max)    return PlayerClass.TANK;
        if (agi == max)    return PlayerClass.ASSASSIN;
        if (intel == max)  return PlayerClass.MAGE;
        return PlayerClass.HEALER; // Vitalité ou Perception dominante
    }
}
