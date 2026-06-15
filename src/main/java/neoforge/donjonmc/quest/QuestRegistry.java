package neoforge.donjonmc.quest;

import neoforge.donjonmc.quest.QuestDef.Difficulty;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static neoforge.donjonmc.quest.QuestType.*;
import static neoforge.donjonmc.quest.QuestDef.Difficulty.*;

public final class QuestRegistry {

    private QuestRegistry() {}

    // 52 quêtes : 18 faciles (0-14, 44-46), 23 normales (15-34, 47-49), 11 difficiles (35-43, 50-51)
    public static final List<QuestDef> ALL = List.of(
        // ── FACILE ──────────────────────────────────────────────────────────────
        new QuestDef( 0, "donjonmc.quest.0",  KILL_MOB,              15,    0, "any",      EASY),
        new QuestDef( 1, "donjonmc.quest.1",  KILL_MOB,              10,    0, "skeleton", EASY),
        new QuestDef( 2, "donjonmc.quest.2",  KILL_MOB,              12,    0, "zombie",   EASY),
        new QuestDef( 3, "donjonmc.quest.3",  KILL_MOB,               8,    0, "creeper",  EASY),
        new QuestDef( 4, "donjonmc.quest.4",  KILL_MOB,               5,    0, "spider",   EASY),
        new QuestDef( 5, "donjonmc.quest.5",  HARVEST_LOG,           64,    0, "any",      EASY),
        new QuestDef( 6, "donjonmc.quest.6",  MINE_ORE,              16,    0, "iron",     EASY),
        new QuestDef( 7, "donjonmc.quest.7",  FISH,                   8,    0, "any",      EASY),
        new QuestDef( 8, "donjonmc.quest.8",  HEAL_HP,               40,    0, "any",      EASY),
        new QuestDef( 9, "donjonmc.quest.9",  WALK_BLOCKS,          500,    0, "any",      EASY),
        new QuestDef(10, "donjonmc.quest.10", SWIM_BLOCKS,          100,    0, "any",      EASY),
        new QuestDef(11, "donjonmc.quest.11", EAT_FOOD,              10,    0, "any",      EASY),
        new QuestDef(12, "donjonmc.quest.12", SURVIVE_NIGHT,          1,    0, "any",      EASY),
        new QuestDef(13, "donjonmc.quest.13", KILL_AT_NIGHT,          5,    0, "any",      EASY),
        new QuestDef(14, "donjonmc.quest.14", REACH_DEPTH,            1,  -20, "any",      EASY),
        // ── NORMALE ─────────────────────────────────────────────────────────────
        new QuestDef(15, "donjonmc.quest.15", KILL_MOB,              35,    0, "any",      NORMAL),
        new QuestDef(16, "donjonmc.quest.16", KILL_PROJECTILE,       20,    0, "any",      NORMAL),
        new QuestDef(17, "donjonmc.quest.17", KILL_CREEPER_NO_EXP,   3,    0, "any",      NORMAL),
        new QuestDef(18, "donjonmc.quest.18", KILL_AT_NIGHT,         20,    0, "any",      NORMAL),
        new QuestDef(19, "donjonmc.quest.19", KILL_BAREHANDED,       10,    0, "any",      NORMAL),
        new QuestDef(20, "donjonmc.quest.20", KILL_AT_DEPTH,          5,  -40, "any",      NORMAL),
        new QuestDef(21, "donjonmc.quest.21", COMPLETE_DUNGEON,       1,    0, "any",      NORMAL),
        new QuestDef(22, "donjonmc.quest.22", KILL_IN_DUNGEON,       20,    0, "any",      NORMAL),
        new QuestDef(23, "donjonmc.quest.23", KILL_BOSS,              2,    0, "any",      NORMAL),
        new QuestDef(24, "donjonmc.quest.24", COMPLETE_DUNGEON_TIMED, 1, 1500, "any",      NORMAL),
        new QuestDef(25, "donjonmc.quest.25", COMPLETE_DUNGEON_ALLY,  1,    0, "any",      NORMAL),
        new QuestDef(26, "donjonmc.quest.26", SPEND_STAT_POINTS,      5,    0, "any",      NORMAL),
        new QuestDef(27, "donjonmc.quest.27", HEAL_HP,              100,    0, "any",      NORMAL),
        new QuestDef(28, "donjonmc.quest.28", SURVIVE_MINUTES,       20,    0, "any",      NORMAL),
        new QuestDef(29, "donjonmc.quest.29", MINE_ORE,              32,    0, "any",      NORMAL),
        new QuestDef(30, "donjonmc.quest.30", CRAFT_ITEM,             5,    0, "iron_plus",NORMAL),
        new QuestDef(31, "donjonmc.quest.31", CRAFT_ITEM,             3,    0, "potion",   NORMAL),
        new QuestDef(32, "donjonmc.quest.32", MINE_ORE,              16,    0, "gold",     NORMAL),
        new QuestDef(33, "donjonmc.quest.33", GAIN_MOD_XP,          300,    0, "any",      NORMAL),
        new QuestDef(34, "donjonmc.quest.34", KILL_IN_DUNGEON,       25,    0, "any",      NORMAL),
        // ── DIFFICILE ────────────────────────────────────────────────────────────
        new QuestDef(35, "donjonmc.quest.35", COMPLETE_DUNGEON,       1,    1, "any",      HARD),
        new QuestDef(36, "donjonmc.quest.36", KILL_IN_DUNGEON,       60,    0, "any",      HARD),
        new QuestDef(37, "donjonmc.quest.37", COMPLETE_DUNGEON,       2,    0, "any",      HARD),
        new QuestDef(38, "donjonmc.quest.38", SURVIVE_MINUTES,       30,    0, "any",      HARD),
        new QuestDef(39, "donjonmc.quest.39", KILL_BOSS,              3,    0, "any",      HARD),
        new QuestDef(40, "donjonmc.quest.40", KILL_STREAK,           40,    0, "any",      HARD),
        new QuestDef(41, "donjonmc.quest.41", MINE_ORE,               5,    0, "diamond",  HARD),
        new QuestDef(42, "donjonmc.quest.42", COMPLETE_DUNGEON_TIMED, 1, 1200, "any",      HARD),
        new QuestDef(43, "donjonmc.quest.43", LEVEL_UP,               1,    0, "any",      HARD),
        // ── AJOUTS ───────────────────────────────────────────────────────────────
        new QuestDef(44, "donjonmc.quest.44", KILL_MOB,               3,    0, "enderman", EASY),
        new QuestDef(45, "donjonmc.quest.45", MINE_STONE,           128,    0, "any",      EASY),
        new QuestDef(46, "donjonmc.quest.46", BREED_ANIMAL,           2,    0, "any",      EASY),
        new QuestDef(47, "donjonmc.quest.47", TRADE_VILLAGER,         5,    0, "any",      NORMAL),
        new QuestDef(48, "donjonmc.quest.48", FISH,                  20,    0, "any",      NORMAL),
        new QuestDef(49, "donjonmc.quest.49", WALK_BLOCKS,         2000,    0, "any",      NORMAL),
        new QuestDef(50, "donjonmc.quest.50", KILL_BAREHANDED,       25,    0, "any",      HARD),
        new QuestDef(51, "donjonmc.quest.51", MINE_STONE,           512,    0, "any",      HARD)
    );

    private static final QuestDef[] BY_ID = new QuestDef[52];
    static {
        for (QuestDef q : ALL) BY_ID[q.id()] = q;
    }

    public static QuestDef byId(int id) {
        return (id >= 0 && id < BY_ID.length) ? BY_ID[id] : null;
    }

    public static List<QuestDef> byDifficulty(Difficulty diff) {
        return ALL.stream().filter(q -> q.difficulty() == diff).collect(Collectors.toList());
    }
}
