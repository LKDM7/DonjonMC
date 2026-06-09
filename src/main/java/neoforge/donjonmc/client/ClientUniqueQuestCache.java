package neoforge.donjonmc.client;

import neoforge.donjonmc.network.SyncUniqueQuestPacket;

/** Cache client de l'état des quêtes uniques (rempli par SyncUniqueQuestPacket). */
public final class ClientUniqueQuestCache {

    private ClientUniqueQuestCache() {}

    public static int[]     progress  = new int[0];
    public static boolean[] completed = new boolean[0];

    public static void update(SyncUniqueQuestPacket p) {
        progress  = p.progress().clone();
        completed = p.completed().clone();
    }
}
