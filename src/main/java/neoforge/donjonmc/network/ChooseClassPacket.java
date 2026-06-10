package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.player.ClassTrialHandler;
import neoforge.donjonmc.player.PlayerClass;

/**
 * Client → serveur : le joueur a choisi sa classe dans le menu Hunter et
 * lance l'épreuve. Les contrôles (niveau 50, classe déjà acquise, cooldown
 * 24h, session en cours) sont faits côté serveur par ClassTrialHandler.
 */
public record ChooseClassPacket(int classOrdinal) implements CustomPacketPayload {

    public static final Type<ChooseClassPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "choose_class"));

    public static final StreamCodec<FriendlyByteBuf, ChooseClassPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public ChooseClassPacket decode(FriendlyByteBuf buf) {
                return new ChooseClassPacket(buf.readByte() & 0xFF);
            }

            @Override
            public void encode(FriendlyByteBuf buf, ChooseClassPacket p) {
                buf.writeByte(p.classOrdinal());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ChooseClassPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // fromOrdinal borne l'index (valeur hors plage → NONE, refusée par startTrial)
            ClassTrialHandler.startTrial(player, PlayerClass.fromOrdinal(packet.classOrdinal()));
        });
    }
}
