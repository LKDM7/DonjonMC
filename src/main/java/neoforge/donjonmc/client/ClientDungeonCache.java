package neoforge.donjonmc.client;

import neoforge.donjonmc.network.SyncDungeonHudPacket;

public final class ClientDungeonCache {

    private ClientDungeonCache() {}

    /** -1 = pas dans un donjon (HUD caché). */
    public static int  rankOrdinal      = -1;
    public static long elapsedSeconds   = 0L;
    public static int  killCount        = 0;
    public static long remainingSeconds = 0L;

    public static void update(SyncDungeonHudPacket p) {
        rankOrdinal      = p.rankOrdinal();
        elapsedSeconds   = p.elapsedSeconds();
        killCount        = p.killCount();
        remainingSeconds = p.remainingSeconds();
    }
}
