package neoforge.donjonmc.punishment;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class PunishmentInstance {

    public static final int ZONE_WIDTH     = 700;
    public static final int DURATION_TICKS = 15 * 60 * 20; // 15 minutes

    private final int              slotId;
    private final UUID             playerId;
    private final BlockPos         returnPos;
    private final ResourceKey<Level> returnDimension;
    private long                   startTick;
    private boolean                completed = false;

    public PunishmentInstance(int slotId, UUID playerId,
                               BlockPos returnPos, ResourceKey<Level> returnDimension,
                               long startTick) {
        this.slotId          = slotId;
        this.playerId        = playerId;
        this.returnPos       = returnPos;
        this.returnDimension = returnDimension;
        this.startTick       = startTick;
    }

    /** Center of this instance's arena in the punishment dimension. */
    public BlockPos arenaCenter() {
        return new BlockPos(slotId * ZONE_WIDTH, 64, 0);
    }

    public long ticksRemaining(long currentTick) {
        return (startTick + DURATION_TICKS) - currentTick;
    }

    public int               getSlotId()          { return slotId; }
    public UUID              getPlayerId()         { return playerId; }
    public BlockPos          getReturnPos()        { return returnPos; }
    public ResourceKey<Level> getReturnDimension() { return returnDimension; }
    public long              getStartTick()        { return startTick; }
    public void              setStartTick(long t)  { this.startTick = t; }
    public boolean           isCompleted()         { return completed; }
    public void              markCompleted()       { this.completed = true; }
}
