package neoforge.donjonmc.client;

import neoforge.donjonmc.network.SyncDailyQuestPacket;

public final class ClientDailyQuestCache {

    private ClientDailyQuestCache() {}

    public static int  questsDone       = 0;
    public static int  questsTotal      = 4;
    /** Secondes restantes. -1 = pas de quêtes actives (HUD caché). */
    public static long remainingSeconds = -1L;

    /** Préférence du joueur : afficher ou masquer le HUD quêtes. */
    public static boolean hudVisible       = true;

    public static int[]     questIds       = new int[]{-1, -1, -1, -1};
    public static int[]     questProgress  = new int[4];
    public static int[]     questTargets   = new int[4];
    public static boolean[] questCompleted = new boolean[4];

    public static void update(SyncDailyQuestPacket p) {
        questsDone      = p.questsDone();
        questsTotal     = p.questsTotal();
        remainingSeconds = p.remainingSeconds();
        questIds       = p.questIds().clone();
        questProgress  = p.questProgress().clone();
        questTargets   = p.questTargets().clone();
        questCompleted = p.questCompleted().clone();
    }
}
