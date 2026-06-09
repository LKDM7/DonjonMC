package neoforge.donjonmc.quest;

import java.util.List;

import static neoforge.donjonmc.quest.QuestType.*;

/** Liste fixe des quêtes uniques (succès permanents). Partagée client + serveur. */
public final class UniqueQuestRegistry {

    private UniqueQuestRegistry() {}

    public static final List<UniqueQuestDef> ALL = List.of(
        new UniqueQuestDef(0, "donjonmc.unique.0.name", LEVEL_UP,          10, "any",      500L),
        new UniqueQuestDef(1, "donjonmc.unique.1.name", LEVEL_UP,          25, "any",     1500L),
        new UniqueQuestDef(2, "donjonmc.unique.2.name", LEVEL_UP,          50, "any",     5000L),
        new UniqueQuestDef(3, "donjonmc.unique.3.name", KILL_BOSS,          1, "any",      800L),
        new UniqueQuestDef(4, "donjonmc.unique.4.name", KILL_BOSS,         25, "any",     4000L),
        new UniqueQuestDef(5, "donjonmc.unique.5.name", KILL_MOB,         500, "any",     1000L),
        new UniqueQuestDef(6, "donjonmc.unique.6.name", MINE_ORE,         100, "diamond", 2000L),
        new UniqueQuestDef(7, "donjonmc.unique.7.name", COMPLETE_DUNGEON,   1, "A",       3000L),
        new UniqueQuestDef(8, "donjonmc.unique.8.name", COMPLETE_DUNGEON,   1, "S",       6000L),
        new UniqueQuestDef(9, "donjonmc.unique.9.name", COMPLETE_DUNGEON,  50, "any",     5000L)
    );

    public static final int COUNT = ALL.size();

    public static UniqueQuestDef byId(int id) {
        return (id >= 0 && id < ALL.size()) ? ALL.get(id) : null;
    }
}
