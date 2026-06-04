package neoforge.donjonmc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerBossEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DungeonInstance {

    public static final int ZONE_WIDTH = 400;

    private final int        instanceId;
    private final DungeonRank rank;
    private final UUID       groupId;
    private final UUID       leaderId;
    private final Set<UUID>  playerIds;
    private final BlockPos   overworldReturn;
    private final long       startTick;

    private boolean   completed    = false;
    private BlockPos  bossCenter   = BlockPos.ZERO;
    private BlockPos  entrancePos  = BlockPos.ZERO;
    private final Set<UUID> deadInDungeon = new HashSet<>();
    private int       killCount    = 0;

    private ServerBossEvent bossBar      = null;
    private UUID            bossEntityId = null;

    public DungeonInstance(int instanceId, DungeonRank rank, UUID groupId,
                           UUID leaderId, Set<UUID> playerIds,
                           BlockPos overworldReturn, long startTick) {
        this.instanceId      = instanceId;
        this.rank            = rank;
        this.groupId         = groupId;
        this.leaderId        = leaderId;
        this.playerIds       = playerIds;
        this.overworldReturn = overworldReturn;
        this.startTick       = startTick;
    }

    /** Center of this instance's zone in the dungeon dimension. */
    public BlockPos zoneOrigin() {
        return new BlockPos(instanceId * ZONE_WIDTH, 64, 0);
    }

    /** Adds a player to this instance's member set (used when restoring from attachment). */
    public void addMember(UUID uid) { playerIds.add(uid); }

    public void markDead(UUID uid)  { deadInDungeon.add(uid); }
    public boolean isDead(UUID uid) { return deadInDungeon.contains(uid); }

    /** True when every original member has died — group defeat. */
    public boolean isAllDead() {
        return !playerIds.isEmpty() && playerIds.stream().allMatch(deadInDungeon::contains);
    }

    public int        getInstanceId()      { return instanceId; }
    public DungeonRank getRank()           { return rank; }
    public UUID       getGroupId()         { return groupId; }
    public Set<UUID>  getPlayerIds()       { return playerIds; }
    public BlockPos   getOverworldReturn() { return overworldReturn; }
    public long       getStartTick()       { return startTick; }
    public boolean    isCompleted()        { return completed; }
    public void       markCompleted()      { this.completed = true; }
    public BlockPos   getBossCenter()            { return bossCenter; }
    public void       setBossCenter(BlockPos pos) { this.bossCenter = pos; }
    public BlockPos   getEntrancePos()           { return entrancePos; }
    public void       setEntrancePos(BlockPos pos){ this.entrancePos = pos; }

    public ServerBossEvent getBossBar()               { return bossBar; }
    public void            setBossBar(ServerBossEvent b) { this.bossBar = b; }
    public UUID            getBossEntityId()           { return bossEntityId; }
    public void            setBossEntityId(UUID id)    { this.bossEntityId = id; }

    public int  getKillCount() { return killCount; }
    public void addKill()      { killCount++; }
}
