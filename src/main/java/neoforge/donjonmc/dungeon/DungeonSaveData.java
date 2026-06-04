package neoforge.donjonmc.dungeon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** État de donjon persisté par joueur. Survit aux décos et restarts serveur. */
public class DungeonSaveData {

    public static final Codec<DungeonSaveData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.fieldOf("active").forGetter(d -> d.active),
        Codec.INT.optionalFieldOf("instanceId", -1).forGetter(d -> d.instanceId),
        Codec.INT.optionalFieldOf("rankOrdinal", 0).forGetter(d -> d.rankOrdinal),
        Codec.LONG.optionalFieldOf("startTick", -1L).forGetter(d -> d.startTick),
        BlockPos.CODEC.optionalFieldOf("returnPos", BlockPos.ZERO).forGetter(d -> d.returnPos),
        BlockPos.CODEC.optionalFieldOf("entrancePos", BlockPos.ZERO).forGetter(d -> d.entrancePos),
        ResourceLocation.CODEC.optionalFieldOf("returnDimLoc",
            ResourceLocation.parse("minecraft:overworld")).forGetter(d -> d.returnDimLoc),
        Codec.BOOL.optionalFieldOf("died", false).forGetter(d -> d.died)
    ).apply(i, DungeonSaveData::new));

    private boolean          active       = false;
    private int              instanceId   = -1;
    private int              rankOrdinal  = 0;
    private long             startTick    = -1L;
    private BlockPos         returnPos    = BlockPos.ZERO;
    private BlockPos         entrancePos  = BlockPos.ZERO;
    private ResourceLocation returnDimLoc = ResourceLocation.parse("minecraft:overworld");
    private boolean          died         = false;

    public DungeonSaveData() {}

    public DungeonSaveData(boolean active, int instanceId, int rankOrdinal, long startTick,
                            BlockPos returnPos, BlockPos entrancePos,
                            ResourceLocation returnDimLoc, boolean died) {
        this.active       = active;
        this.instanceId   = instanceId;
        this.rankOrdinal  = rankOrdinal;
        this.startTick    = startTick;
        this.returnPos    = returnPos;
        this.entrancePos  = entrancePos;
        this.returnDimLoc = returnDimLoc;
        this.died         = died;
    }

    public ResourceKey<Level> getReturnDimension() {
        return ResourceKey.create(Registries.DIMENSION, returnDimLoc);
    }

    public boolean          isActive()        { return active; }
    public int              getInstanceId()   { return instanceId; }
    public int              getRankOrdinal()  { return rankOrdinal; }
    public long             getStartTick()    { return startTick; }
    public BlockPos         getReturnPos()    { return returnPos; }
    public BlockPos         getEntrancePos()  { return entrancePos; }
    public ResourceLocation getReturnDimLoc() { return returnDimLoc; }
    public boolean          isDied()          { return died; }

    public void setActive(boolean v)          { this.active = v; }
    public void setInstanceId(int v)          { this.instanceId = v; }
    public void setRankOrdinal(int v)         { this.rankOrdinal = v; }
    public void setStartTick(long v)          { this.startTick = v; }
    public void setReturnPos(BlockPos v)      { this.returnPos = v; }
    public void setEntrancePos(BlockPos v)    { this.entrancePos = v; }
    public void setReturnDimLoc(ResourceLocation v) { this.returnDimLoc = v; }
    public void setDied(boolean v)            { this.died = v; }
}
