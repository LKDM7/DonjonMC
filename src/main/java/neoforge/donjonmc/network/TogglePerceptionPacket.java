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

/** Client → serveur : active/désactive l'effet Glowing de la stat Perception. */
public record TogglePerceptionPacket() implements CustomPacketPayload {

    public static final Type<TogglePerceptionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "toggle_perception"));

    public static final StreamCodec<FriendlyByteBuf, TogglePerceptionPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public TogglePerceptionPacket decode(FriendlyByteBuf buf) { return new TogglePerceptionPacket(); }
            @Override public void encode(FriendlyByteBuf buf, TogglePerceptionPacket p) {}
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TogglePerceptionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            data.setPerceptionEnabled(!data.isPerceptionEnabled());
            player.setData(ModAttachments.PLAYER_DATA, data);
            PacketDistributor.sendToPlayer(player, SyncPlayerDataPacket.from(player, data));
        });
    }
}
