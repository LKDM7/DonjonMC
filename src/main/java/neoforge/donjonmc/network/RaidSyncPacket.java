package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.ClientRaidCache;

import java.util.ArrayList;
import java.util.List;

/**
 * Encoding order:
 *   inGroup (bool)
 *   hasHistory (bool)
 *   hasPendingInvite (bool)
 *   inviterName (utf — empty string if none)
 *   invitablePlayers count (byte) + names (utf each)
 *   -- if !inGroup: done --
 *   leaderName (utf)
 *   isLeader (bool)
 *   members count (byte) + {name(utf), level(int), roleOrdinal(byte)} each
 *   portalRankOrdinal (int — -1 if no portal)
 *   portalX (int), portalY (int), portalZ (int)
 */
public record RaidSyncPacket(
    boolean inGroup,
    boolean hasHistory,
    boolean hasPendingInvite,
    String  inviterName,
    List<String>     invitablePlayers,
    String  leaderName,
    boolean isLeader,
    List<MemberInfo> members,
    int portalRankOrdinal,
    int portalX,
    int portalY,
    int portalZ
) implements CustomPacketPayload {

    public record MemberInfo(String name, int level, int roleOrdinal) {}

    public static final Type<RaidSyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_sync"));

    public static final StreamCodec<FriendlyByteBuf, RaidSyncPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RaidSyncPacket decode(FriendlyByteBuf buf) {
                boolean inGroup          = buf.readBoolean();
                boolean hasHistory       = buf.readBoolean();
                boolean hasPendingInvite = buf.readBoolean();
                String  inviterName      = buf.readUtf();

                int ipCount = buf.readByte() & 0xFF;
                List<String> invitable = new ArrayList<>(ipCount);
                for (int i = 0; i < ipCount; i++) invitable.add(buf.readUtf());

                if (!inGroup) {
                    return new RaidSyncPacket(false, hasHistory, hasPendingInvite,
                        inviterName, invitable, "", false, List.of(),
                        -1, 0, 0, 0);
                }

                String  leaderName = buf.readUtf();
                boolean isLeader   = buf.readBoolean();
                int count = buf.readByte() & 0xFF;
                List<MemberInfo> members = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    members.add(new MemberInfo(buf.readUtf(), buf.readInt(), buf.readByte() & 0xFF));
                }
                int portalRankOrdinal = buf.readInt();
                int portalX = buf.readInt(), portalY = buf.readInt(), portalZ = buf.readInt();
                return new RaidSyncPacket(true, hasHistory, hasPendingInvite,
                    inviterName, invitable, leaderName, isLeader, members,
                    portalRankOrdinal, portalX, portalY, portalZ);
            }

            @Override
            public void encode(FriendlyByteBuf buf, RaidSyncPacket p) {
                buf.writeBoolean(p.inGroup());
                buf.writeBoolean(p.hasHistory());
                buf.writeBoolean(p.hasPendingInvite());
                buf.writeUtf(p.inviterName());
                buf.writeByte(p.invitablePlayers().size());
                for (String s : p.invitablePlayers()) buf.writeUtf(s);
                if (!p.inGroup()) return;
                buf.writeUtf(p.leaderName());
                buf.writeBoolean(p.isLeader());
                buf.writeByte(p.members().size());
                for (MemberInfo m : p.members()) {
                    buf.writeUtf(m.name());
                    buf.writeInt(m.level());
                    buf.writeByte(m.roleOrdinal());
                }
                buf.writeInt(p.portalRankOrdinal());
                buf.writeInt(p.portalX());
                buf.writeInt(p.portalY());
                buf.writeInt(p.portalZ());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaidSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientRaidCache.update(packet));
    }
}
