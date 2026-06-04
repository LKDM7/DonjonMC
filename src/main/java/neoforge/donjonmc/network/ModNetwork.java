package neoforge.donjonmc.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {

    private ModNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
            SyncPlayerDataPacket.TYPE,
            SyncPlayerDataPacket.STREAM_CODEC,
            SyncPlayerDataPacket::handle
        );

        registrar.playToServer(
            SpendSkillPointPacket.TYPE,
            SpendSkillPointPacket.STREAM_CODEC,
            SpendSkillPointPacket::handle
        );

        registrar.playToServer(
            ToggleSpeedPacket.TYPE,
            ToggleSpeedPacket.STREAM_CODEC,
            ToggleSpeedPacket::handle
        );

        registrar.playToClient(
            RaidSyncPacket.TYPE,
            RaidSyncPacket.STREAM_CODEC,
            RaidSyncPacket::handle
        );

        registrar.playToClient(
            SyncPunishmentTimerPacket.TYPE,
            SyncPunishmentTimerPacket.STREAM_CODEC,
            SyncPunishmentTimerPacket::handle
        );

        registrar.playToClient(
            SyncDailyQuestPacket.TYPE,
            SyncDailyQuestPacket.STREAM_CODEC,
            SyncDailyQuestPacket::handle
        );

        registrar.playToServer(
            RaidActionPacket.TYPE,
            RaidActionPacket.STREAM_CODEC,
            RaidActionPacket::handle
        );

        registrar.playToServer(
            RaidInviteByNamePacket.TYPE,
            RaidInviteByNamePacket.STREAM_CODEC,
            RaidInviteByNamePacket::handle
        );

        registrar.playToClient(
            SyncDungeonHudPacket.TYPE,
            SyncDungeonHudPacket.STREAM_CODEC,
            SyncDungeonHudPacket::handle
        );

    }
}
