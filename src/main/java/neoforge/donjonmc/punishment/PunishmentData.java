package neoforge.donjonmc.punishment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class PunishmentData {

    public static final Codec<PunishmentData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.fieldOf("active").forGetter(d -> d.active),
        Codec.INT.optionalFieldOf("slotId", -1).forGetter(d -> d.slotId),
        Codec.LONG.optionalFieldOf("timerStartTick", -1L).forGetter(d -> d.timerStartTick),
        BlockPos.CODEC.optionalFieldOf("returnPos", BlockPos.ZERO).forGetter(d -> d.returnPos),
        BlockPos.CODEC.optionalFieldOf("spawnPos", BlockPos.ZERO).forGetter(d -> d.spawnPos),
        ResourceLocation.CODEC.optionalFieldOf("returnDim",
            ResourceLocation.parse("minecraft:overworld")).forGetter(d -> d.returnDimLoc),
        Codec.LONG.optionalFieldOf("lastPunishmentDay", -1L).forGetter(d -> d.lastPunishmentDay)
    ).apply(i, PunishmentData::new));

    private boolean          active            = false;
    private int              slotId            = -1;
    private long             timerStartTick    = -1L;
    private BlockPos         returnPos         = BlockPos.ZERO;
    private BlockPos         spawnPos          = BlockPos.ZERO;
    private ResourceLocation returnDimLoc      = ResourceLocation.parse("minecraft:overworld");
    /** Jour Minecraft (getGameTime/24000) où la dernière punition a été déclenchée. -1 = jamais. */
    private long             lastPunishmentDay = -1L;

    public PunishmentData() {}

    public PunishmentData(boolean active, int slotId, long timerStartTick,
                           BlockPos returnPos, BlockPos spawnPos, ResourceLocation returnDimLoc,
                           long lastPunishmentDay) {
        this.active            = active;
        this.slotId            = slotId;
        this.timerStartTick    = timerStartTick;
        this.returnPos         = returnPos;
        this.spawnPos          = spawnPos;
        this.returnDimLoc      = returnDimLoc;
        this.lastPunishmentDay = lastPunishmentDay;
    }

    public ResourceKey<Level> getReturnDimension() {
        return ResourceKey.create(Registries.DIMENSION, returnDimLoc);
    }

    public boolean          isActive()             { return active; }
    public int              getSlotId()            { return slotId; }
    public long             getTimerStartTick()    { return timerStartTick; }
    public BlockPos         getReturnPos()         { return returnPos; }
    public BlockPos         getSpawnPos()          { return spawnPos; }
    public ResourceLocation getReturnDimLoc()      { return returnDimLoc; }
    public long             getLastPunishmentDay() { return lastPunishmentDay; }

    public void setActive(boolean v)               { this.active = v; }
    public void setSlotId(int v)                   { this.slotId = v; }
    public void setTimerStartTick(long v)          { this.timerStartTick = v; }
    public void setReturnPos(BlockPos v)           { this.returnPos = v; }
    public void setSpawnPos(BlockPos v)            { this.spawnPos = v; }
    public void setReturnDimLoc(ResourceLocation v){ this.returnDimLoc = v; }
    public void setLastPunishmentDay(long v)       { this.lastPunishmentDay = v; }
}
