package neoforge.donjonmc.client;

public final class ClientPunishmentCache {

    private ClientPunishmentCache() {}

    /** Secondes restantes. -1 = aucune instance active (HUD caché). */
    public static long remainingSeconds = -1L;
}
