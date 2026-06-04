package neoforge.donjonmc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import neoforge.donjonmc.dungeon.mob.DungeonSpawnManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DungeonGenerator {

    private static final int STEP = 8;

    public static final String[][] THEME_TILES = {
        {"castle",    "dungeon_castle","dungeon_castle_carpet","dungeon_castle_carpet2",
                      "dungeon_castle_dark","dungeon_castle_empty","dungeon_castle_ruined",
                      "dungeon_castle_tresury","dungeon_castle_window"},
        {"hobgoblin", "hobgoblin_chain","hobgoblin_crystal","hobgoblin_crystals",
                      "hobgoblin_dirt","hobgoblin_fence","hobgoblin_lamp",
                      "hobgoblin_lians","hobgoblin_normal","hobgoblin_torch"},
        {"high_orc",  "high_orc_1","high_orc_2","high_orc_3",
                      "high_orc_4","high_orc_5","high_orc_6"},
        {"orc",       "orc_2","orc_3","orc_4"},
        {"ice",       "dungeon_ice_1","dungeon_ice_2","dungeon_ice_3",
                      "dungeon_ice_4","dungeon_ice_5","dungeon_ice_6"},
        {"goblin_new","goblin_1","goblin_2","goblin_3",
                      "goblin_4","goblin_5","goblin_6"},
        {"hond",      "dungeon_hell","dungeon_hell_magic_crystal"},
        {"goblins",   "dungeon_stone_fence","dungeon_stone_hole",
                      "dungeon_stone_torch","dungeon_stone_torches",
                      "dungeon_stone_cracked","dungeon_stone_crystals",
                      "dungeon_stone_empty"},
        {"golems",    "dungeon_stone_cracked","dungeon_stone_crystals",
                      "dungeon_stone_empty"},
        {"forest",    "dungeon_forest_grass","dungeon_forest_grass2",
                      "dungeon_forest_grass3"},
        {"deep",      "dungeon_deepslate_dark","dungeon_deepslate_torch"},
        {"ant",       "dungeon_ant","dungeon_ant_2",
                      "dungeon_ant_3","dungeon_ant_crystals"},
    };

    public record GenerationResult(BlockPos spawnPos, BlockPos bossCenter, UUID bossEntityId) {}

    private record RoomEntry(double x, double y, double z, String theme) {}

    public static GenerationResult generate(ServerLevel level, BlockPos origin, DungeonRank rank, int instanceId) {
        RandomSource rng = level.random;

        Direction dir    = Direction.NORTH;
        String    theme  = "NONE";
        int    maxSwitch = 0;
        double dx = origin.getX(), dy = origin.getY(), dz = origin.getZ();
        boolean entranceDone = false;

        // ── Phase 1 : collecter les positions ──────────────────────────────────
        List<RoomEntry> rooms = new ArrayList<>();
        rooms.add(new RoomEntry(dx, dy, dz, theme));

        int iterations = rng.nextInt(6) + 6;
        for (int i = 0; i < iterations; i++) {
            int turn = rng.nextInt(3) + 1;
            if (turn == 2)      dir = dir.getClockWise();
            else if (turn == 3) dir = dir.getCounterClockWise();
            else                dir = dir.getClockWise().getClockWise();

            if (maxSwitch < 3 && (theme.equals("NONE") || rng.nextFloat() <= 0.2f)) {
                maxSwitch++;
                theme = pickTheme(theme, rank, rng);
            }

            int steps = entranceDone ? 6 : (rng.nextInt(3) + 5);
            if (!entranceDone) entranceDone = true;

            for (int s = 0; s < steps; s++) {
                double nextX = dx + dir.getStepX() * STEP;
                double nextZ = dz + dir.getStepZ() * STEP;
                if (Math.abs(origin.getX() - nextX) > 160 || Math.abs(origin.getZ() - nextZ) > 160)
                    dir = dir.getClockWise();
                dx += dir.getStepX() * STEP;
                dz += dir.getStepZ() * STEP;
                rooms.add(new RoomEntry(dx, dy, dz, theme));
            }
        }

        double bossX = dx + dir.getStepX() * STEP;
        double bossZ = dz + dir.getStepZ() * STEP;

        // ── Phase 2a : remplir toutes les salles (coquille 10×10×10) ──────────
        // Traiter d'abord le boss pour que le fill des salles passe par-dessus
        fillStone(level, bossX - STEP, dy, bossZ - STEP, STEP * 4, STEP * 2, STEP * 4);
        String bossRoomName = rng.nextInt(2) == 0 ? "big_bossroom_deepslate" : "big_bossroom_forest";
        placeStructure(level, bossRoomName, bossX, dy, bossZ, false);

        for (RoomEntry room : rooms) {
            fillStone(level, room.x() - 1, room.y() - 1, room.z() - 1, STEP + 2, STEP + 2, STEP + 2);
        }

        // ── Phase 2b : creuser tous les intérieurs ─────────────────────────────
        // L'intérieur couvre tout le tile (x=0-7, y=1-5, z=0-7), pas de mur intérieur.
        // Les salles adjacentes se connectent automatiquement : chaque salle creuse
        // z=0 et z=7, qui sont les mêmes positions que la coquille extérieure des voisins.
        for (RoomEntry room : rooms) {
            BlockPos start = BlockPos.containing(room.x(), room.y(), room.z());
            for (int bx = 0; bx < STEP; bx++)
                for (int by = 1; by <= 5; by++)
                    for (int bz = 0; bz < STEP; bz++)
                        level.setBlock(start.offset(bx, by, bz), Blocks.AIR.defaultBlockState(), 2);
        }

        // ── Phase 2c : ouvrir la porte vers la salle du boss ──────────────────
        // Pour SOUTH/EAST : on perce uniquement la coquille extérieure (off = STEP).
        // Pour NORTH/WEST : la boss room est à -STEP, il faut creuser tout le couloir
        // de la coquille extérieure (-1) jusqu'à la face de la boss room (-STEP).
        BlockPos lastStart = BlockPos.containing(dx, dy, dz);
        boolean posDir = (dir == Direction.SOUTH || dir == Direction.EAST);
        int doorFrom = posDir ? STEP : -STEP;
        int doorTo   = posDir ? STEP : -1;
        for (int off = Math.min(doorFrom, doorTo); off <= Math.max(doorFrom, doorTo); off++) {
            for (int a = 0; a < STEP; a++) {
                for (int by = 1; by <= 5; by++) {
                    BlockPos pos = (dir.getAxis() == Direction.Axis.Z)
                        ? lastStart.offset(a, by, off)
                        : lastStart.offset(off, by, a);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // ── Phase 2d : tuiles décoratives (ignoreAir → stone shell intact) ────
        for (RoomEntry room : rooms) {
            placeStructure(level, tileName(room.theme(), rng), room.x(), room.y(), room.z(), true);
            if (!room.theme().equals("NONE") && rng.nextFloat() < 0.5f) {
                BlockPos roomOrigin = BlockPos.containing(room.x(), room.y(), room.z());
                DungeonSpawnManager.spawnForRoom(level, roomOrigin, room.theme(), rank, instanceId);
            }
        }

        BlockPos bossCenter = BlockPos.containing(bossX + 6, dy + 1, bossZ + 6);
        DungeonMob boss = DungeonSpawnManager.spawnBoss(level, bossCenter, rank, instanceId);
        UUID bossEntityId = boss != null ? boss.getUUID() : null;

        BlockPos spawnPos = origin.offset(3, 2, 3);
        return new GenerationResult(spawnPos, bossCenter, bossEntityId);
    }

    // ── Style gallery (debug command) ─────────────────────────────────────────

    public static BlockPos generateStyleGallery(ServerLevel level, BlockPos origin, String theme) {
        if ("all".equals(theme)) {
            for (int t = 0; t < THEME_TILES.length; t++)
                placeThemeRow(level, origin.getX() + t * STEP * 2, origin.getY(), origin.getZ(), THEME_TILES[t]);
        } else {
            for (String[] row : THEME_TILES) {
                if (row[0].equals(theme)) {
                    placeThemeRow(level, origin.getX(), origin.getY(), origin.getZ(), row);
                    break;
                }
            }
        }
        return origin.offset(3, 2, 3);
    }

    private static void placeThemeRow(ServerLevel level, int wx, int wy, int startZ, String[] row) {
        int count = row.length - 1;
        for (int i = 0; i < count; i++)
            fillStone(level, wx - 1, wy - 1, startZ + i * STEP - 1, STEP + 2, STEP + 2, STEP + 2);
        for (int i = 0; i < count; i++) {
            BlockPos start = new BlockPos(wx, wy, startZ + i * STEP);
            for (int bx = 0; bx < STEP; bx++)
                for (int by = 1; by <= 5; by++)
                    for (int bz = 0; bz < STEP; bz++)
                        level.setBlock(start.offset(bx, by, bz), Blocks.AIR.defaultBlockState(), 2);
        }
        for (int i = 0; i < count; i++)
            placeStructure(level, row[i + 1], wx, wy, startZ + i * STEP, true);
    }

    // ── Theme selection ───────────────────────────────────────────────────────

    private static String pickTheme(String current, DungeonRank rank, RandomSource rng) {
        if (current.equals("NONE")) {
            return switch (rank) {
                case D -> pick(rng, "goblins", "goblin_new", "forest");
                case C -> pick(rng, "goblins", "goblin_new", "forest", "hobgoblin", "golems");
                case B -> pick(rng, "hobgoblin", "golems", "deep", "hond", "castle");
                case A -> pick(rng, "castle", "ice", "orc", "high_orc");
                case S -> pick(rng, "ice", "orc", "high_orc", "ant");
            };
        }
        return switch (rank) {
            case D -> pick(rng, "goblins", "goblin_new", "forest");
            case C -> pick(rng, "hobgoblin", "golems", "deep");
            case B -> pick(rng, "hond", "castle", "ice");
            case A -> pick(rng, "orc", "high_orc", "castle");
            case S -> pick(rng, "high_orc", "ant");
        };
    }

    private static String tileName(String theme, RandomSource rng) {
        return switch (theme) {
            case "castle"     -> pick(rng, "dungeon_castle", "dungeon_castle_carpet",
                                         "dungeon_castle_carpet2", "dungeon_castle_dark",
                                         "dungeon_castle_empty", "dungeon_castle_ruined",
                                         "dungeon_castle_tresury", "dungeon_castle_window");
            case "hobgoblin"  -> pick(rng, "hobgoblin_chain", "hobgoblin_crystal",
                                         "hobgoblin_crystals", "hobgoblin_dirt", "hobgoblin_fence",
                                         "hobgoblin_lamp", "hobgoblin_lians", "hobgoblin_normal",
                                         "hobgoblin_torch");
            case "high_orc"   -> pick(rng, "high_orc_1", "high_orc_2", "high_orc_3",
                                         "high_orc_4", "high_orc_5", "high_orc_6");
            case "orc"        -> pick(rng, "orc_2", "orc_3", "orc_4");
            case "ice"        -> pick(rng, "dungeon_ice_1", "dungeon_ice_2", "dungeon_ice_3",
                                         "dungeon_ice_4", "dungeon_ice_5", "dungeon_ice_6");
            case "goblin_new" -> pick(rng, "goblin_1", "goblin_2", "goblin_3",
                                         "goblin_4", "goblin_5", "goblin_6");
            case "hond"       -> pick(rng, "dungeon_hell", "dungeon_hell_magic_crystal");
            case "goblins"    -> pick(rng, "dungeon_stone_fence", "dungeon_stone_hole",
                                         "dungeon_stone_torch", "dungeon_stone_torches",
                                         "dungeon_stone_cracked", "dungeon_stone_crystals",
                                         "dungeon_stone_empty");
            case "golems"     -> pick(rng, "dungeon_stone_cracked", "dungeon_stone_crystals",
                                         "dungeon_stone_empty");
            case "forest"     -> pick(rng, "dungeon_forest_grass", "dungeon_forest_grass2",
                                         "dungeon_forest_grass3");
            case "deep"       -> pick(rng, "dungeon_deepslate_dark", "dungeon_deepslate_torch");
            case "ant"        -> pick(rng, "dungeon_ant", "dungeon_ant_2",
                                         "dungeon_ant_3", "dungeon_ant_crystals");
            default           -> "goblin_1";
        };
    }

    private static String pick(RandomSource rng, String... pool) {
        return pool[rng.nextInt(pool.length)];
    }

    // ── Stone fill ────────────────────────────────────────────────────────────

    private static void fillStone(ServerLevel level, double x, double y, double z, int w, int h, int d) {
        BlockPos start = BlockPos.containing(x, y, z);
        for (int bx = 0; bx < w; bx++)
            for (int by = 0; by < h; by++)
                for (int bz = 0; bz < d; bz++)
                    level.setBlock(start.offset(bx, by, bz), Blocks.STONE.defaultBlockState(), 2);
    }

    // ── Structure placement via classpath ─────────────────────────────────────

    private static void placeStructure(ServerLevel level, String name, double x, double y, double z,
                                        boolean ignoreAir) {
        String resourcePath = "/data/" + Donjonmc.MODID + "/structures/" + name + ".nbt";
        try (InputStream is = DungeonGenerator.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                Donjonmc.LOGGER.warn("[DungeonGen] Structure not found: {}", resourcePath);
                return;
            }
            CompoundTag nbt = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
            HolderGetter<Block> blockGetter = level.registryAccess().lookupOrThrow(Registries.BLOCK);
            StructureTemplate template = new StructureTemplate();
            template.load(blockGetter, nbt);
            BlockPos pos = BlockPos.containing(x, y, z);
            StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE).setMirror(Mirror.NONE);
            if (ignoreAir) {
                settings.addProcessor(new BlockIgnoreProcessor(List.of(Blocks.AIR)));
            }
            template.placeInWorld(level, pos, pos, settings, level.random, 3);
        } catch (IOException e) {
            Donjonmc.LOGGER.error("[DungeonGen] Failed to load {}: {}", name, e.getMessage());
        }
    }
}
