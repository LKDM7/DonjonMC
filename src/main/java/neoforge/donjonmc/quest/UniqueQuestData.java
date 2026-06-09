package neoforge.donjonmc.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/** État persistant des quêtes uniques d'un joueur (jamais reset). */
public class UniqueQuestData {

    public static final Codec<UniqueQuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.list(Codec.INT).optionalFieldOf("progress", List.of()).forGetter(d -> d.progress),
        Codec.list(Codec.BOOL).optionalFieldOf("completed", List.of()).forGetter(d -> d.completed)
    ).apply(i, UniqueQuestData::new));

    private final List<Integer> progress;
    private final List<Boolean> completed;

    public UniqueQuestData() {
        this(List.of(), List.of());
    }

    public UniqueQuestData(List<Integer> progress, List<Boolean> completed) {
        this.progress  = new ArrayList<>(progress);
        this.completed = new ArrayList<>(completed);
        ensureSize();
    }

    /** Complète les listes à la taille du registre (compat. ascendante si on ajoute des quêtes). */
    private void ensureSize() {
        while (progress.size()  < UniqueQuestRegistry.COUNT) progress.add(0);
        while (completed.size() < UniqueQuestRegistry.COUNT) completed.add(false);
    }

    public int     getProgress(int id)            { return progress.get(id); }
    public boolean isCompleted(int id)            { return completed.get(id); }
    public void    setProgress(int id, int v)     { progress.set(id, v); }
    public void    addProgress(int id, int a)     { progress.set(id, progress.get(id) + a); }
    public void    setCompleted(int id, boolean v){ completed.set(id, v); }
}
