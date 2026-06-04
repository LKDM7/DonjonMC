package neoforge.donjonmc.player;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

/**
 * Gère les équipes de scoreboard qui affichent le badge de rang [E]/[D]/... au-dessus du nom.
 */
public final class RankTeamManager {

    private RankTeamManager() {}

    private record RankDef(String team, String label, ChatFormatting color) {}

    private static final RankDef[] RANKS = {
        new RankDef("djmc_E", "E", ChatFormatting.GRAY),
        new RankDef("djmc_D", "D", ChatFormatting.GREEN),
        new RankDef("djmc_C", "C", ChatFormatting.BLUE),
        new RankDef("djmc_B", "B", ChatFormatting.DARK_PURPLE),
        new RankDef("djmc_A", "A", ChatFormatting.GOLD),
        new RankDef("djmc_S", "S", ChatFormatting.RED),
        new RankDef("djmc_N", "N", ChatFormatting.WHITE),
    };

    /** Crée les équipes si elles n'existent pas encore. Idempotent. */
    public static void ensureTeams(MinecraftServer server) {
        Scoreboard sb = server.getScoreboard();
        for (RankDef def : RANKS) {
            if (sb.getPlayerTeam(def.team()) == null) {
                PlayerTeam team = sb.addPlayerTeam(def.team());
                team.setPlayerPrefix(
                    Component.literal("[" + def.label() + "] ").withStyle(def.color())
                );
                team.setNameTagVisibility(Team.Visibility.ALWAYS);
                team.setCollisionRule(Team.CollisionRule.ALWAYS);
            }
        }
    }

    /** Retire le joueur de toutes les équipes de rang et l'ajoute à celle correspondant à son niveau. */
    public static void updatePlayerTeam(ServerPlayer player) {
        ensureTeams(player.server);
        int level = player.getData(ModAttachments.PLAYER_DATA).getLevel();
        String target = teamForLevel(level);
        Scoreboard sb = player.server.getScoreboard();
        String name = player.getScoreboardName();

        for (RankDef def : RANKS) {
            PlayerTeam t = sb.getPlayerTeam(def.team());
            if (t != null && t.getPlayers().contains(name)) {
                sb.removePlayerFromTeam(name, t);
            }
        }

        PlayerTeam team = sb.getPlayerTeam(target);
        if (team != null) sb.addPlayerToTeam(name, team);
    }

    /** Retourne la ChatFormatting correspondant au rang d'un niveau donné (utilisée par le leaderboard). */
    public static ChatFormatting colorForLevel(int level) {
        if (level >= 80) return ChatFormatting.WHITE;
        if (level >= 60) return ChatFormatting.RED;
        if (level >= 40) return ChatFormatting.GOLD;
        if (level >= 30) return ChatFormatting.DARK_PURPLE;
        if (level >= 20) return ChatFormatting.BLUE;
        if (level >= 10) return ChatFormatting.GREEN;
        return ChatFormatting.GRAY;
    }

    private static String teamForLevel(int level) {
        if (level >= 80) return "djmc_N";
        if (level >= 60) return "djmc_S";
        if (level >= 40) return "djmc_A";
        if (level >= 30) return "djmc_B";
        if (level >= 20) return "djmc_C";
        if (level >= 10) return "djmc_D";
        return "djmc_E";
    }
}
