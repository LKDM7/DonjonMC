package neoforge.donjonmc.client;

import neoforge.donjonmc.network.RaidSyncPacket;
import neoforge.donjonmc.network.RaidSyncPacket.MemberInfo;
import neoforge.donjonmc.raid.RaidRole;

import java.util.ArrayList;
import java.util.List;

public final class ClientRaidCache {

    private ClientRaidCache() {}

    public static boolean      inGroup          = false;
    public static boolean      hasHistory       = false;
    public static boolean      hasPendingInvite = false;
    public static String       inviterName      = "";
    public static List<String> invitablePlayers = List.of();
    public static String       leaderName       = "";
    public static boolean      isLeader         = false;
    public static List<MemberInfo> members      = List.of();

    /** -1 if no portal tracked, otherwise the DungeonRank ordinal. */
    public static int  portalRankOrdinal = -1;
    public static int  portalX = 0, portalY = 0, portalZ = 0;

    public static void update(RaidSyncPacket p) {
        inGroup          = p.inGroup();
        hasHistory       = p.hasHistory();
        hasPendingInvite = p.hasPendingInvite();
        inviterName      = p.inviterName();
        invitablePlayers = new ArrayList<>(p.invitablePlayers());
        leaderName       = p.leaderName();
        isLeader         = p.isLeader();
        members          = new ArrayList<>(p.members());
        portalRankOrdinal = p.portalRankOrdinal();
        portalX          = p.portalX();
        portalY          = p.portalY();
        portalZ          = p.portalZ();
    }

    public static RaidRole myRole(String selfName) {
        for (MemberInfo m : members) {
            if (m.name().equals(selfName)) return RaidRole.fromOrdinal(m.roleOrdinal());
        }
        return RaidRole.NONE;
    }
}
