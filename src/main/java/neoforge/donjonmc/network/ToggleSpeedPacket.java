package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.player.ModAttachments;
import neoforge.donjonmc.player.PlayerData;
import neoforge.donjonmc.player.PlayerEventHandler;

public record ToggleSpeedPacket() implements CustomPacketPayload {

    public static final Type<ToggleSpeedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "toggle_speed"));

    public static final StreamCodec<FriendlyByteBuf, ToggleSpeedPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public ToggleSpeedPacket decode(FriendlyByteBuf buf) { return new ToggleSpeedPacket(); }
            @Override public void encode(FriendlyByteBuf buf, ToggleSpeedPacket p) {}
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ToggleSpeedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            data.setSpeedEnabled(!data.isSpeedEnabled());
            player.setData(ModAttachments.PLAYER_DATA, data);
            PlayerEventHandler.applyStatModifiers(player, data);
            PacketDistributor.sendToPlayer(player, SyncPlayerDataPacket.from(data));
        });
    }
}
