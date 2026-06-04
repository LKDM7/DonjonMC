package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.ClientDungeonCache;

/** Syncs dungeon HUD state to client every second. rankOrdinal = -1 → HUD hidden. */
public record SyncDungeonHudPacket(int rankOrdinal, long elapsedSeconds, int killCount, long remainingSeconds)
        implements CustomPacketPayload {

    public static final Type<SyncDungeonHudPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "sync_dungeon_hud"));

    public static final StreamCodec<FriendlyByteBuf, SyncDungeonHudPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncDungeonHudPacket decode(FriendlyByteBuf buf) {
                return new SyncDungeonHudPacket(buf.readInt(), buf.readLong(), buf.readInt(), buf.readLong());
            }
            @Override
            public void encode(FriendlyByteBuf buf, SyncDungeonHudPacket p) {
                buf.writeInt(p.rankOrdinal());
                buf.writeLong(p.elapsedSeconds());
                buf.writeInt(p.killCount());
                buf.writeLong(p.remainingSeconds());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncDungeonHudPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDungeonCache.update(packet));
    }
}
