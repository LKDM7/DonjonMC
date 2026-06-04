package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.ClientDailyQuestCache;

/**
 * Syncs daily quest state to client every second.
 * remainingSeconds = -1 → quests not active (HUD hidden).
 * questIds[i] = -1 → slot unused.
 */
public record SyncDailyQuestPacket(
    int questsDone,
    int questsTotal,
    long remainingSeconds,
    int[] questIds,
    int[] questProgress,
    int[] questTargets,
    boolean[] questCompleted
) implements CustomPacketPayload {

    public static final Type<SyncDailyQuestPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "sync_daily_quest"));

    public static final StreamCodec<FriendlyByteBuf, SyncDailyQuestPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncDailyQuestPacket decode(FriendlyByteBuf buf) {
                int done    = buf.readInt();
                int total   = buf.readInt();
                long remain = buf.readLong();
                int[] ids   = new int[4];
                int[] prog  = new int[4];
                int[] tgt   = new int[4];
                boolean[] completed = new boolean[4];
                for (int i = 0; i < 4; i++) {
                    ids[i]  = buf.readInt();
                    prog[i] = buf.readInt();
                    tgt[i]  = buf.readInt();
                    completed[i] = buf.readBoolean();
                }
                return new SyncDailyQuestPacket(done, total, remain, ids, prog, tgt, completed);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SyncDailyQuestPacket p) {
                buf.writeInt(p.questsDone());
                buf.writeInt(p.questsTotal());
                buf.writeLong(p.remainingSeconds());
                for (int i = 0; i < 4; i++) {
                    buf.writeInt(p.questIds()[i]);
                    buf.writeInt(p.questProgress()[i]);
                    buf.writeInt(p.questTargets()[i]);
                    buf.writeBoolean(p.questCompleted()[i]);
                }
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncDailyQuestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDailyQuestCache.update(packet));
    }
}
