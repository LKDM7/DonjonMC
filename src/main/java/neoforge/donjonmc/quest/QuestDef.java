package neoforge.donjonmc.quest;

public record QuestDef(
    int id,
    String nameKey,
    QuestType type,
    int target,
    int param2,
    String filter,
    Difficulty difficulty
) {
    public enum Difficulty { EASY, NORMAL, HARD }
}
