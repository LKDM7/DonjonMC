package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.player.PlayerEventHandler;

/** Demande de respec des stats depuis le bouton du GUI (client → serveur). */
public record RespecStatsPacket() implements CustomPacketPayload {

    public static final Type<RespecStatsPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "respec_stats"));

    public static final StreamCodec<FriendlyByteBuf, RespecStatsPacket> STREAM_CODEC =
        StreamCodec.unit(new RespecStatsPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RespecStatsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Conditions (niveau, cooldown) vérifiées côté serveur dans tryRespec.
                PlayerEventHandler.tryRespec(player);
            }
        });
    }
}
