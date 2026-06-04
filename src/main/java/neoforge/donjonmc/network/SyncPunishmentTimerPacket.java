package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.ClientPunishmentCache;

/**
 * Sent server → client every second while a punishment instance is active.
 * remainingSeconds = -1 signals the instance ended (hides the HUD).
 */
public record SyncPunishmentTimerPacket(long remainingSeconds) implements CustomPacketPayload {

    public static final Type<SyncPunishmentTimerPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "sync_punishment_timer"));

    public static final StreamCodec<FriendlyByteBuf, SyncPunishmentTimerPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncPunishmentTimerPacket decode(FriendlyByteBuf buf) {
                return new SyncPunishmentTimerPacket(buf.readLong());
            }
            @Override
            public void encode(FriendlyByteBuf buf, SyncPunishmentTimerPacket p) {
                buf.writeLong(p.remainingSeconds());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncPunishmentTimerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPunishmentCache.remainingSeconds = packet.remainingSeconds());
    }
}
