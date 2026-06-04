package neoforge.donjonmc.punishment;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import neoforge.donjonmc.dungeon.mob.DungeonMobRegistry;

public final class PunishmentArenaGenerator {

    private PunishmentArenaGenerator() {}

    private static final int HALF          = 150;
    private static final int MAX_RELIEF    = 8;
    private static final int BORDER_HEIGHT = 8;

    public static BlockPos generate(ServerLevel level, BlockPos center, int playerLevel) {
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        int xMin = cx - HALF, xMax = cx + HALF;
        int zMin = cz - HALF, zMax = cz + HALF;
        int wallTop = cy + MAX_RELIEF + BORDER_HEIGHT;

        BlockState floor  = Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        BlockState wall   = Blocks.RED_SANDSTONE.defaultBlockState();
        BlockState pillar = Blocks.CUT_RED_SANDSTONE.defaultBlockState();
        BlockState top    = Blocks.RED_SANDSTONE_SLAB.defaultBlockState();

        // 1 — Floor
        for (int x = xMin; x <= xMax; x++) {
            for (int z = zMin; z <= zMax; z++) {
                int relief = noise(x, z, MAX_RELIEF);
                for (int y = cy - 1; y <= cy + relief; y++) {
                    setBlock(level, x, y, z, floor);
                }
            }
        }

        // 2 — Border walls
        for (int x = xMin - 1; x <= xMax + 1; x++) {
            for (int y = cy - 1; y <= wallTop; y++) {
                setBlock(level, x, y, zMin - 1, wall);
                setBlock(level, x, y, zMax + 1, wall);
            }
        }
        for (int z = zMin; z <= zMax; z++) {
            for (int y = cy - 1; y <= wallTop; y++) {
                setBlock(level, xMin - 1, y, z, wall);
                setBlock(level, xMax + 1, y, z, wall);
            }
        }

        // 3 — Spires
        buildSpires(level, cx, cy, cz, xMin, xMax, zMin, zMax, pillar, top);

        // 4 — Sand Worms (seul mob autorisé dans cette dimension)
        spawnSandWorms(level, center, playerLevel);

        int spawnRelief = noise(cx, cz, MAX_RELIEF);
        return new BlockPos(cx, cy + spawnRelief + 1, cz);
    }

    // ── Spires ────────────────────────────────────────────────────────────────

    private static void buildSpires(ServerLevel level,
                                     int cx, int cy, int cz,
                                     int xMin, int xMax, int zMin, int zMax,
                                     BlockState pillar, BlockState top) {
        for (int gx = xMin + 12; gx < xMax - 12; gx += 25) {
            for (int gz = zMin + 12; gz < zMax - 12; gz += 25) {
                if (Math.abs(gx - cx) < 30 && Math.abs(gz - cz) < 30) continue;
                int ox = ((gx * 1337 + gz * 7) & 0xF) - 8;
                int oz = ((gx * 31  + gz * 997) & 0xF) - 8;
                int sx = gx + ox, sz = gz + oz;
                int baseRelief = noise(sx, sz, MAX_RELIEF);
                int baseY = cy + baseRelief;
                int height = 8 + ((sx * 17 + sz * 13) & 0xF) % 15;
                int w = ((sx + sz) % 3 == 0) ? 2 : 1;
                for (int y = baseY; y <= baseY + height - 1; y++)
                    for (int dx = 0; dx < w; dx++)
                        for (int dz = 0; dz < w; dz++)
                            setBlock(level, sx + dx, y, sz + dz, pillar);
                for (int dx = 0; dx < w; dx++)
                    for (int dz = 0; dz < w; dz++)
                        setBlock(level, sx + dx, baseY + height, sz + dz, top);
            }
        }
    }

    // ── Sand Worm spawning ────────────────────────────────────────────────────

    private static void spawnSandWorms(ServerLevel level, BlockPos center, int playerLevel) {
        // 8 vers au niveau 0 → 20 au niveau 50
        int count = 8 + playerLevel * 12 / 50;
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        // Rayon max 60 blocs (dans le tracking range client de 128 blocs)
        int radius = 60;

        int spawned = 0;
        int attempts = 0;
        while (spawned < count && attempts < count * 4) {
            attempts++;
            int dx = level.random.nextInt(radius * 2) - radius;
            int dz = level.random.nextInt(radius * 2) - radius;
            // Zone safe autour du point de spawn joueur (15 blocs)
            if (Math.abs(dx) < 15 && Math.abs(dz) < 15) continue;
            int x = cx + dx, z = cz + dz;
            int y = cy + noise(x, z, MAX_RELIEF) + 1;
            spawnWorm(level, x, y, z, playerLevel);
            spawned++;
        }
        neoforge.donjonmc.Donjonmc.LOGGER.info("[Punishment] Spawned {} sand worms (level {})", spawned, playerLevel);
    }

    private static void spawnWorm(ServerLevel level, int x, int y, int z, int playerLevel) {
        SandWormEntity worm = DungeonMobRegistry.SAND_WORM.get().create(level);
        if (worm == null) {
            neoforge.donjonmc.Donjonmc.LOGGER.error("[Punishment] SandWormEntity.create() returned null");
            return;
        }

        worm.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);

        // Stats scalées au niveau du joueur
        double hp  = 60.0 + playerLevel * 3.0;
        double atk =  8.0 + playerLevel * 0.35;
        var hpAttr  = worm.getAttribute(Attributes.MAX_HEALTH);
        var atkAttr = worm.getAttribute(Attributes.ATTACK_DAMAGE);
        if (hpAttr  != null) hpAttr .setBaseValue(hp);
        if (atkAttr != null) atkAttr.setBaseValue(atk);
        worm.setHealth((float) hp);

        worm.getPersistentData().putBoolean("punishment_mob", true);
        worm.getPersistentData().putInt("punishment_player_level", playerLevel);

        worm.setCustomName(Component.literal("§c[Punition] §rVer des Sables"));
        worm.setCustomNameVisible(true);

        // addFreshEntity directement — pas de finalizeSpawn pour éviter
        // l'interférence du FinalizeSpawnEvent dans la dimension de punition
        level.addFreshEntity(worm);
    }

    // ── Noise ─────────────────────────────────────────────────────────────────

    private static int noise(int x, int z, int max) {
        double n = Math.sin(x * 0.28)  * Math.cos(z * 0.28)
                 + Math.sin(x * 0.63 + z * 0.41) * 0.6
                 + Math.sin(x * 0.11 - z * 0.19) * 0.3;
        int h = (int)((n + 1.9) / 3.8 * (max + 1));
        return Math.max(0, Math.min(max, h));
    }

    private static void setBlock(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }
}
