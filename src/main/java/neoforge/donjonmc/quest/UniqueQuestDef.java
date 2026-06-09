package neoforge.donjonmc.quest;

/**
 * Définition d'une quête unique (permanente, complétable une seule fois).
 * Pour le type LEVEL_UP, {@code target} est le niveau à atteindre ; pour les autres types
 * c'est un compteur cumulatif. {@code filter} = "any" ou un filtre spécifique (ex. "diamond",
 * ou un rang de donjon "A"/"S" pour COMPLETE_DUNGEON).
 */
public record UniqueQuestDef(
    int id,
    String nameKey,
    QuestType type,
    int target,
    String filter,
    long xpReward
) {}
