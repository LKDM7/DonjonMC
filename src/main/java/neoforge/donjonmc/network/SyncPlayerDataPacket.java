package neoforge.donjonmc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.ClientPlayerDataCache;
import neoforge.donjonmc.player.PlayerData;

public record SyncPlayerDataPacket(
    int level, long xp, long xpRequired, int skillPoints,
    int strength, int agility, int vitality, int intelligence, int perception,
    int playerClassOrdinal, boolean speedEnabled
) implements CustomPacketPayload {

    public static final Type<SyncPlayerDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "sync_player_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncPlayerDataPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncPlayerDataPacket decode(FriendlyByteBuf buf) {
                return new SyncPlayerDataPacket(
                    buf.readInt(), buf.readLong(), buf.readLong(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readByte() & 0xFF, buf.readBoolean()
                );
            }

            @Override
            public void encode(FriendlyByteBuf buf, SyncPlayerDataPacket p) {
                buf.writeInt(p.level()); buf.writeLong(p.xp()); buf.writeLong(p.xpRequired());
                buf.writeInt(p.skillPoints());
                buf.writeInt(p.strength()); buf.writeInt(p.agility()); buf.writeInt(p.vitality());
                buf.writeInt(p.intelligence()); buf.writeInt(p.perception());
                buf.writeByte(p.playerClassOrdinal());
                buf.writeBoolean(p.speedEnabled());
            }
        };

    public static SyncPlayerDataPacket from(PlayerData data) {
        return new SyncPlayerDataPacket(
            data.getLevel(), data.getXp(), data.xpForNextLevel(), data.getSkillPoints(),
            data.getStrength(), data.getAgility(), data.getVitality(),
            data.getIntelligence(), data.getPerception(),
            data.getPlayerClassOrdinal(), data.isSpeedEnabled()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncPlayerDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientPlayerDataCache.update(packet));
    }
}
