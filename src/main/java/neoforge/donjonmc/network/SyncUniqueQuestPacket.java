package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.ClientUniqueQuestCache;

/** Synchronise l'état des quêtes uniques au client (progression + complétion par quête). */
public record SyncUniqueQuestPacket(
    int[] progress,
    boolean[] completed
) implements CustomPacketPayload {

    public static final Type<SyncUniqueQuestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "sync_unique_quest"));

    public static final StreamCodec<FriendlyByteBuf, SyncUniqueQuestPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncUniqueQuestPacket decode(FriendlyByteBuf buf) {
                int n = buf.readVarInt();
                int[] prog = new int[n];
                boolean[] done = new boolean[n];
                for (int i = 0; i < n; i++) {
                    prog[i] = buf.readInt();
                    done[i] = buf.readBoolean();
                }
                return new SyncUniqueQuestPacket(prog, done);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SyncUniqueQuestPacket p) {
                buf.writeVarInt(p.progress().length);
                for (int i = 0; i < p.progress().length; i++) {
                    buf.writeInt(p.progress()[i]);
                    buf.writeBoolean(p.completed()[i]);
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncUniqueQuestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientUniqueQuestCache.update(packet));
    }
}
