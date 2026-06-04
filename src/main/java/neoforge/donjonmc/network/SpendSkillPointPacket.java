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
import neoforge.donjonmc.player.StatType;
import neoforge.donjonmc.quest.DailyQuestManager;

public record SpendSkillPointPacket(StatType stat) implements CustomPacketPayload {

    public static final Type<SpendSkillPointPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "spend_skill_point"));

    public static final StreamCodec<FriendlyByteBuf, SpendSkillPointPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SpendSkillPointPacket decode(FriendlyByteBuf buf) {
                return new SpendSkillPointPacket(StatType.values()[buf.readByte()]);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SpendSkillPointPacket p) {
                buf.writeByte(p.stat().ordinal());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SpendSkillPointPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            if (data.getSkillPoints() <= 0) return;

            switch (packet.stat()) {
                case STRENGTH     -> data.setStrength(data.getStrength() + 1);
                case AGILITY      -> data.setAgility(data.getAgility() + 1);
                case VITALITY     -> data.setVitality(data.getVitality() + 1);
                case INTELLIGENCE -> data.setIntelligence(data.getIntelligence() + 1);
                case PERCEPTION   -> data.setPerception(data.getPerception() + 1);
            }
            data.spendSkillPoint();
            player.setData(ModAttachments.PLAYER_DATA, data);
            DailyQuestManager.getInstance().onStatPointSpent(player);

            PlayerEventHandler.applyStatModifiers(player, data);
            PlayerEventHandler.applyClassModifiers(player, data);

            PacketDistributor.sendToPlayer(player, SyncPlayerDataPacket.from(data));
        });
    }
}
