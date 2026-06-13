package neoforge.donjonmc;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config commune (config/donjonmc-common.toml, côté serveur ET client).
 * Les valeurs sont mises en cache dans des champs statiques à chaque
 * chargement/reload du fichier ; les défauts ci-dessous servent de filet
 * tant que le fichier n'est pas chargé.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Portails ─────────────────────────────────────────────────────────────

    private static final ModConfigSpec.IntValue PORTAL_LIFETIME_MINUTES = BUILDER
        .comment("Durée de vie d'un portail de donjon non utilisé, en minutes.")
        .defineInRange("portals.lifetimeMinutes", 10, 1, 180);

    private static final ModConfigSpec.IntValue EXIT_PORTAL_MINUTES = BUILDER
        .comment("Durée de vie du portail de sortie apparu à la fin du donjon, en minutes.")
        .defineInRange("portals.exitLifetimeMinutes", 5, 1, 60);

    private static final ModConfigSpec.IntValue ENTRANCE_EXIT_MINUTES = BUILDER
        .comment("Durée de vie du portail de sortie situé à l'entrée du donjon, en minutes (= durée max d'une instance).")
        .defineInRange("portals.entranceExitLifetimeMinutes", 120, 10, 600);

    private static final ModConfigSpec.IntValue SPAWN_INTERVAL_MIN_MINUTES = BUILDER
        .comment("Intervalle minimum entre deux spawns naturels de portail dans l'overworld, en minutes.")
        .defineInRange("portals.spawnIntervalMinMinutes", 8, 1, 600);

    private static final ModConfigSpec.IntValue SPAWN_INTERVAL_MAX_MINUTES = BUILDER
        .comment("Intervalle maximum entre deux spawns naturels de portail, en minutes (doit être >= au minimum).")
        .defineInRange("portals.spawnIntervalMaxMinutes", 14, 1, 600);

    private static final ModConfigSpec.IntValue HALO_RANGE_BLOCKS = BUILDER
        .comment("Rayon (en blocs) dans lequel un joueur déclenche le halo lumineux du portail.",
                 "0 = halo désactivé. Au-delà de ~160 blocs l'entité n'est plus envoyée aux clients.")
        .defineInRange("portals.haloRangeBlocks", 120, 0, 200);

    private static final ModConfigSpec.BooleanValue GATE_SOUND_ENABLED = BUILDER
        .comment("Joue un son de 'gate' (ouverture de portail de l'End) à l'apparition d'un portail.")
        .define("portals.gateSoundEnabled", true);

    private static final ModConfigSpec.IntValue GATE_SOUND_RANGE_BLOCKS = BUILDER
        .comment("Distance (en blocs) à laquelle le son de gate est audible.")
        .defineInRange("portals.gateSoundRangeBlocks", 120, 16, 512);

    private static final ModConfigSpec.IntValue AMBIENT_SOUND_RANGE_BLOCKS = BUILDER
        .comment("Rayon (en blocs) du son d'ambiance joué en continu près d'un portail. 0 = désactivé.")
        .defineInRange("portals.ambientSoundRangeBlocks", 25, 0, 64);

    private static final ModConfigSpec.IntValue FAKE_DUNGEON_CHANCE_PERCENT = BUILDER
        .comment("Chance (en %) qu'un portail de rang C soit un 'double donjon' : annoncé C,",
                 "mais rang B ou A une fois à l'intérieur. 0 = désactivé.")
        .defineInRange("portals.fakeDungeonChancePercent", 15, 0, 100);

    // ── Cooldowns ────────────────────────────────────────────────────────────

    private static final ModConfigSpec.IntValue RESPEC_COOLDOWN_DAYS = BUILDER
        .comment("Cooldown du respec de stats, en jours IRL.")
        .defineInRange("cooldowns.respecCooldownDays", 7, 0, 365);

    private static final ModConfigSpec.IntValue TRIAL_COOLDOWN_HOURS = BUILDER
        .comment("Cooldown après un échec à l'épreuve de classe, en heures IRL.")
        .defineInRange("cooldowns.trialCooldownHours", 24, 0, 720);

    static final ModConfigSpec SPEC = BUILDER.build();

    // ── Valeurs en cache (défauts = mêmes valeurs que le TOML par défaut) ────

    public static int portalLifetimeMinutes      = 10;
    public static int exitPortalMinutes          = 5;
    public static int entranceExitMinutes        = 120;
    public static int spawnIntervalMinMinutes    = 8;
    public static int spawnIntervalMaxMinutes    = 14;
    public static int haloRangeBlocks            = 120;
    public static boolean gateSoundEnabled       = true;
    public static int gateSoundRangeBlocks       = 120;
    public static int ambientSoundRangeBlocks    = 25;
    public static int fakeDungeonChancePercent   = 15;
    public static int respecCooldownDays         = 7;
    public static int trialCooldownHours         = 24;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        portalLifetimeMinutes   = PORTAL_LIFETIME_MINUTES.get();
        exitPortalMinutes       = EXIT_PORTAL_MINUTES.get();
        entranceExitMinutes     = ENTRANCE_EXIT_MINUTES.get();
        spawnIntervalMinMinutes = SPAWN_INTERVAL_MIN_MINUTES.get();
        spawnIntervalMaxMinutes = Math.max(SPAWN_INTERVAL_MAX_MINUTES.get(), spawnIntervalMinMinutes);
        haloRangeBlocks         = HALO_RANGE_BLOCKS.get();
        gateSoundEnabled        = GATE_SOUND_ENABLED.get();
        gateSoundRangeBlocks    = GATE_SOUND_RANGE_BLOCKS.get();
        ambientSoundRangeBlocks = AMBIENT_SOUND_RANGE_BLOCKS.get();
        fakeDungeonChancePercent = FAKE_DUNGEON_CHANCE_PERCENT.get();
        respecCooldownDays      = RESPEC_COOLDOWN_DAYS.get();
        trialCooldownHours      = TRIAL_COOLDOWN_HOURS.get();
    }
}
