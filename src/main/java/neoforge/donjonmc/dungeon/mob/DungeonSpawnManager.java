package neoforge.donjonmc.dungeon.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import neoforge.donjonmc.dungeon.DungeonRank;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DungeonSpawnManager {

    private DungeonSpawnManager() {}

    // ── Rank scaling ──────────────────────────────────────────────────────────
    //   hpMult / atkMult appliqués sur les stats de base du mob

    private record RankScale(double hpMult, double atkMult, int minMobs, int maxMobs) {}

    private static final Map<DungeonRank, RankScale> RANK_SCALE = Map.of(
        DungeonRank.D, new RankScale(1.0,  1.0,  1, 2),
        DungeonRank.C, new RankScale(1.8,  1.5,  1, 2),
        DungeonRank.B, new RankScale(3.0,  2.5,  2, 3),
        DungeonRank.A, new RankScale(5.0,  4.0,  2, 3),
        DungeonRank.S, new RankScale(8.0,  6.0,  2, 4)
    );

    private static final Map<DungeonRank, RankScale> BOSS_SCALE = Map.of(
        DungeonRank.D, new RankScale(2.0,  2.0,  1, 1),
        DungeonRank.C, new RankScale(4.0,  3.0,  1, 1),
        DungeonRank.B, new RankScale(7.0,  5.0,  1, 1),
        DungeonRank.A, new RankScale(12.0, 8.0,  1, 1),
        DungeonRank.S, new RankScale(20.0, 14.0, 1, 1)
    );

    // ── Theme → mob pool ──────────────────────────────────────────────────────

    private static final Map<String, List<EntityType<? extends DungeonMob>>> THEME_MOBS = Map.ofEntries(
        Map.entry("goblins",    List.of(DungeonMobRegistry.GOBLIN.get(), DungeonMobRegistry.NEW_GOBLIN.get(), DungeonMobRegistry.SKULL.get())),
        Map.entry("goblin_new", List.of(DungeonMobRegistry.GOBLIN.get(), DungeonMobRegistry.NEW_GOBLIN.get())),
        Map.entry("forest",     List.of(DungeonMobRegistry.GOBLIN.get(), DungeonMobRegistry.NEW_GOBLIN.get())),
        Map.entry("hobgoblin",  List.of(DungeonMobRegistry.HOBGOBLIN_CLUB.get(), DungeonMobRegistry.HOBGOBLIN_BOMBER.get())),
        Map.entry("golems",     List.of(DungeonMobRegistry.SHADOW_SOLDIER.get(), DungeonMobRegistry.UNDEAD.get())),
        Map.entry("deep",       List.of(DungeonMobRegistry.SHADOW_SOLDIER.get(), DungeonMobRegistry.UNDEAD.get())),
        Map.entry("hond",       List.of(DungeonMobRegistry.DEMON_GUARD.get(), DungeonMobRegistry.WILD_DEMON.get())),
        Map.entry("castle",     List.of(DungeonMobRegistry.UNDEAD.get(), DungeonMobRegistry.SHADOW_SOLDIER.get(), DungeonMobRegistry.DEMON_GUARD.get())),
        Map.entry("ice",        List.of(DungeonMobRegistry.ICE_BEAR.get(), DungeonMobRegistry.ICE_ELF.get())),
        Map.entry("orc",        List.of(DungeonMobRegistry.ORC.get(), DungeonMobRegistry.GIGA_GOBLIN.get())),
        Map.entry("high_orc",   List.of(DungeonMobRegistry.ORC.get(), DungeonMobRegistry.GIGA_GOBLIN.get())),
        Map.entry("ant",        List.of(DungeonMobRegistry.ANT.get(), DungeonMobRegistry.CENTIPIE.get()))
    );

    // ── Rank → boss ───────────────────────────────────────────────────────────

    private static final Map<DungeonRank, EntityType<? extends DungeonMob>> RANK_BOSS = Map.of(
        DungeonRank.D, DungeonMobRegistry.GIGA_GOBLIN.get(),
        DungeonRank.C, DungeonMobRegistry.SPIDER_BOSS.get(),
        DungeonRank.B, DungeonMobRegistry.BOSS_GOLEM.get(),
        DungeonRank.A, DungeonMobRegistry.IGRIS.get(),
        DungeonRank.S, DungeonMobRegistry.KAMISH.get()
    );

    // ── Public API ────────────────────────────────────────────────────────────

    public static void spawnForRoom(ServerLevel level, BlockPos roomOrigin,
                                    String theme, DungeonRank rank, int instanceId) {
        List<EntityType<? extends DungeonMob>> pool = THEME_MOBS.getOrDefault(theme,
                List.of(DungeonMobRegistry.GOBLIN.get()));
        RankScale scale = RANK_SCALE.getOrDefault(rank, RANK_SCALE.get(DungeonRank.D));
        int count = scale.minMobs() + level.random.nextInt(scale.maxMobs() - scale.minMobs() + 1);
        for (int i = 0; i < count; i++) {
            EntityType<? extends DungeonMob> type = pool.get(level.random.nextInt(pool.size()));
            spawnAt(level, type, randomRoomPos(level, roomOrigin), scale, instanceId, false, rank);
        }
    }

    public static DungeonMob spawnBoss(ServerLevel level, BlockPos center,
                                       DungeonRank rank, int instanceId) {
        EntityType<? extends DungeonMob> bossType = RANK_BOSS.getOrDefault(rank,
                DungeonMobRegistry.GIGA_GOBLIN.get());
        RankScale scale = BOSS_SCALE.getOrDefault(rank, BOSS_SCALE.get(DungeonRank.D));
        return spawnAt(level, bossType, center, scale, instanceId, true, rank);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static DungeonMob spawnAt(ServerLevel level, EntityType<? extends DungeonMob> type,
                                      BlockPos pos, RankScale scale, int instanceId, boolean isBoss,
                                      DungeonRank rank) {
        DungeonMob mob = type.create(level);
        if (mob == null) return null;

        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                level.random.nextFloat() * 360f, 0f);

        var hpAttr  = mob.getAttribute(Attributes.MAX_HEALTH);
        var atkAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (hpAttr  != null) hpAttr .setBaseValue(hpAttr .getBaseValue()  * scale.hpMult());
        if (atkAttr != null) atkAttr.setBaseValue(atkAttr.getBaseValue()   * scale.atkMult());
        mob.setHealth(mob.getMaxHealth());

        mob.getPersistentData().putInt("instance_id", instanceId);
        mob.getPersistentData().putInt("dungeon_rank_ord", rank.ordinal());
        if (isBoss) mob.getPersistentData().putBoolean("dungeon_boss", true);

        int color = ((int)(rank.pr * 255) << 16) | ((int)(rank.pg * 255) << 8) | (int)(rank.pb * 255);
        Component nameplate = Component.literal("[" + rank.name() + "] ")
                .withStyle(s -> s.withColor(color))
                .append(mob.getType().getDescription());
        mob.setCustomName(nameplate);
        mob.setCustomNameVisible(true);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null);
        level.addFreshEntity(mob);
        return mob;
    }

    private static BlockPos randomRoomPos(ServerLevel level, BlockPos origin) {
        int x = origin.getX() + 1 + level.random.nextInt(6);
        int z = origin.getZ() + 1 + level.random.nextInt(6);
        int y = origin.getY() + 1;
        return new BlockPos(x, y, z);
    }
}
