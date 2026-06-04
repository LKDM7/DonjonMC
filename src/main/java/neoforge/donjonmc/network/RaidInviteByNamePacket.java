package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.raid.RaidManager;

public record RaidInviteByNamePacket(String targetName) implements CustomPacketPayload {

    public static final Type<RaidInviteByNamePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_invite_name"));

    public static final StreamCodec<FriendlyByteBuf, RaidInviteByNamePacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public RaidInviteByNamePacket decode(FriendlyByteBuf buf) {
                return new RaidInviteByNamePacket(buf.readUtf());
            }
            @Override public void encode(FriendlyByteBuf buf, RaidInviteByNamePacket p) {
                buf.writeUtf(p.targetName());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaidInviteByNamePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer leader)) return;
            ServerPlayer target = leader.server.getPlayerList().getPlayerByName(packet.targetName());
            if (target != null) RaidManager.getInstance().invite(leader, target);
        });
    }
}
