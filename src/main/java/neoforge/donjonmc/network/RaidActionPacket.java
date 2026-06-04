package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.raid.RaidManager;
import neoforge.donjonmc.raid.RaidRole;

public record RaidActionPacket(byte action, byte extra) implements CustomPacketPayload {

    // action constants
    public static final byte SET_ROLE = 0;
    public static final byte LEAVE    = 1;
    public static final byte DISBAND  = 2;
    public static final byte REMATCH  = 3;
    public static final byte CREATE   = 4;
    public static final byte ACCEPT   = 5;
    public static final byte DECLINE  = 6;

    public static RaidActionPacket setRole(RaidRole role) {
        return new RaidActionPacket(SET_ROLE, (byte) role.ordinal());
    }
    public static RaidActionPacket leave()   { return new RaidActionPacket(LEAVE,   (byte) 0); }
    public static RaidActionPacket disband() { return new RaidActionPacket(DISBAND, (byte) 0); }
    public static RaidActionPacket rematch() { return new RaidActionPacket(REMATCH, (byte) 0); }
    public static RaidActionPacket create()  { return new RaidActionPacket(CREATE,  (byte) 0); }
    public static RaidActionPacket accept()  { return new RaidActionPacket(ACCEPT,  (byte) 0); }
    public static RaidActionPacket decline() { return new RaidActionPacket(DECLINE, (byte) 0); }

    public static final Type<RaidActionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_action"));

    public static final StreamCodec<FriendlyByteBuf, RaidActionPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public RaidActionPacket decode(FriendlyByteBuf buf) {
                return new RaidActionPacket(buf.readByte(), buf.readByte());
            }
            @Override
            public void encode(FriendlyByteBuf buf, RaidActionPacket p) {
                buf.writeByte(p.action());
                buf.writeByte(p.extra());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RaidActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RaidManager rm = RaidManager.getInstance();
            switch (packet.action()) {
                case SET_ROLE -> rm.setRole(player, RaidRole.fromOrdinal(packet.extra() & 0xFF));
                case LEAVE    -> rm.leave(player);
                case DISBAND  -> rm.disband(player);
                case REMATCH  -> rm.quickRematch(player);
                case CREATE   -> rm.createGroup(player);
                case ACCEPT   -> rm.acceptInvite(player);
                case DECLINE  -> rm.declineInvite(player);
            }
        });
    }
}
