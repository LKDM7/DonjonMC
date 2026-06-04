package neoforge.donjonmc.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public class DailyQuestData {

    public static final long DURATION_TICKS = 60L * 60L * 20L; // 1h

    public static final Codec<DailyQuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.fieldOf("active").forGetter(d -> d.active),
        Codec.LONG.optionalFieldOf("timerStartTick", -1L).forGetter(d -> d.timerStartTick),
        Codec.LONG.optionalFieldOf("lastAssignedDay", -1L).forGetter(d -> d.lastAssignedDay),
        Codec.list(Codec.INT).optionalFieldOf("questIds", List.of()).forGetter(d -> d.questIds),
        Codec.list(Codec.INT).optionalFieldOf("progress", List.of()).forGetter(d -> d.progress),
        Codec.list(Codec.BOOL).optionalFieldOf("completed", List.of()).forGetter(d -> d.completed),
        Codec.INT.optionalFieldOf("nightState", 0).forGetter(d -> d.nightState),
        Codec.INT.optionalFieldOf("gainedModXp", 0).forGetter(d -> d.gainedModXp),
        Codec.INT.optionalFieldOf("spentStatPoints", 0).forGetter(d -> d.spentStatPoints),
        Codec.BOOL.optionalFieldOf("disabled", false).forGetter(d -> d.disabled)
    ).apply(i, DailyQuestData::new));

    private boolean active;
    private long timerStartTick;
    private long lastAssignedDay;
    private List<Integer> questIds;
    private List<Integer> progress;
    private List<Boolean> completed;
    private int nightState;   // 0=none, 1=tracking night, 2=night survived
    private int gainedModXp;
    private int spentStatPoints;
    private boolean disabled;

    public DailyQuestData() {
        this(false, -1L, -1L,
             new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
             0, 0, 0, false);
    }

    public DailyQuestData(boolean active, long timerStartTick, long lastAssignedDay,
                          List<Integer> questIds, List<Integer> progress, List<Boolean> completed,
                          int nightState, int gainedModXp, int spentStatPoints, boolean disabled) {
        this.active           = active;
        this.timerStartTick   = timerStartTick;
        this.lastAssignedDay  = lastAssignedDay;
        this.questIds         = new ArrayList<>(questIds);
        this.progress         = new ArrayList<>(progress);
        this.completed        = new ArrayList<>(completed);
        this.nightState       = nightState;
        this.gainedModXp      = gainedModXp;
        this.spentStatPoints  = spentStatPoints;
        this.disabled         = disabled;
    }

    // ── Assignment ─────────────────────────────────────────────────────────────

    public void assign(List<Integer> ids, long startTick, long day) {
        this.questIds   = new ArrayList<>(ids);
        this.progress   = new ArrayList<>();
        this.completed  = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            this.progress.add(0);
            this.completed.add(false);
        }
        this.active          = true;
        this.timerStartTick  = startTick;
        this.lastAssignedDay = day;
        this.nightState      = 0;
        this.gainedModXp     = 0;
        this.spentStatPoints = 0;
    }

    // ── Slot access ────────────────────────────────────────────────────────────

    public int questCount() { return questIds.size(); }
    public boolean hasQuests() { return !questIds.isEmpty(); }

    public int getQuestId(int slot)   { return questIds.get(slot); }
    public int getProgress(int slot)  { return progress.get(slot); }
    public boolean isCompleted(int slot) { return completed.get(slot); }

    public void addProgress(int slot, int amount) {
        progress.set(slot, progress.get(slot) + amount);
    }

    public void setProgress(int slot, int value) { progress.set(slot, value); }
    public void setCompleted(int slot, boolean v) { completed.set(slot, v); }

    public int countCompleted() {
        int n = 0;
        for (boolean b : completed) if (b) n++;
        return n;
    }

    public boolean allCompleted() { return countCompleted() >= questIds.size(); }

    // ── Timer ──────────────────────────────────────────────────────────────────

    public long ticksRemaining(long now) {
        if (timerStartTick < 0) return DURATION_TICKS;
        return (timerStartTick + DURATION_TICKS) - now;
    }

    public void skipToThirtySeconds(long now) {
        this.timerStartTick = now - (DURATION_TICKS - 30L * 20L);
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public boolean isActive()          { return active; }
    public long getLastAssignedDay()   { return lastAssignedDay; }
    public int  getNightState()        { return nightState; }
    public int  getGainedModXp()       { return gainedModXp; }
    public int  getSpentStatPoints()   { return spentStatPoints; }

    public void setActive(boolean v)              { this.active = v; }
    public void setNightState(int v)              { this.nightState = v; }
    public void addGainedModXp(int amount)        { this.gainedModXp += amount; }
    public void incrementSpentStatPoints()        { this.spentStatPoints++; }
    public void setTimerStartTick(long v)         { this.timerStartTick = v; }

    public boolean isDisabled()                   { return disabled; }
    public void setDisabled(boolean v)            { this.disabled = v; }
}
