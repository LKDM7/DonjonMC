package neoforge.donjonmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.client.ClientDailyQuestCache;
import neoforge.donjonmc.client.ClientDungeonCache;
import neoforge.donjonmc.client.ClientPunishmentCache;

@OnlyIn(Dist.CLIENT)
public final class DungeonHud {

    private DungeonHud() {}

    private static final int[] RANK_COLORS = {
        0xFF5588FF, // D — bleu
        0xFF55FF55, // C — vert
        0xFFAA44CC, // B — violet
        0xFFFFAA00, // A — orange
        0xFFFF4444, // S — rouge
    };
    private static final String[] RANK_NAMES = { "D", "C", "B", "A", "S" };

    private static final int C_BG      = 0xBB000D1A;
    private static final int C_BORDER  = 0xFF1A3A5C;
    private static final int C_ELAPSED = 0xFF778899;
    private static final int C_KILLS   = 0xFFAABBCC;

    // Limite de temps par rang (doit correspondre à DungeonRank.timeLimitSeconds)
    private static final long[] RANK_TIME_LIMITS = { 2700L, 1800L, 1500L, 1200L, 900L };

    public static void render(GuiGraphics g, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        int rank = ClientDungeonCache.rankOrdinal;
        if (rank < 0) return;

        long elapsed   = ClientDungeonCache.elapsedSeconds;
        long remaining = ClientDungeonCache.remainingSeconds;
        int  kills     = ClientDungeonCache.killCount;

        long limit     = (rank < RANK_TIME_LIMITS.length) ? RANK_TIME_LIMITS[rank] : 2700L;
        float ratio    = limit > 0 ? (float) remaining / limit : 1f;
        int timeColor;
        if (ratio > 0.5f) {
            timeColor = 0xFF55FF55;
        } else if (ratio > 0.25f) {
            timeColor = 0xFFFFAA00;
        } else {
            timeColor = (System.currentTimeMillis() / 400 % 2 == 0) ? 0xFFFF4444 : 0xFF882222;
        }

        String rankStr      = "DONJON  [RANG " + RANK_NAMES[Math.min(rank, 4)] + "]";
        String remainingStr = String.format("%d:%02d", remaining / 60, remaining % 60);
        String killStr      = "⚔  " + kills + " éliminé" + (kills > 1 ? "s" : "");
        String elapsedStr   = String.format("+%d:%02d", elapsed / 60, elapsed % 60);

        int rankStrW      = mc.font.width(rankStr);
        int remainingStrW = mc.font.width(remainingStr);
        int killStrW      = mc.font.width(killStr);
        int elapsedStrW   = mc.font.width(elapsedStr);

        int pad    = 7;
        int innerW = Math.max(rankStrW + remainingStrW + pad, killStrW + elapsedStrW + pad);
        int boxW   = innerW + pad * 2;
        int boxH   = 26;

        int punishH = (ClientPunishmentCache.remainingSeconds >= 0) ? 26 + 4 : 0;
        int questH  = (ClientDailyQuestCache.remainingSeconds  >= 0) ? 28 + 4 : 0;
        int x = screenW - boxW - 6;
        int y = screenH - boxH - 48 - punishH - questH;

        // ── Fond ──────────────────────────────────────────────────────────────
        g.fill(x, y, x + boxW, y + boxH, C_BG);

        // ── Bordure ───────────────────────────────────────────────────────────
        int rankColor = RANK_COLORS[Math.min(rank, 4)];
        g.fill(x,           y,          x + boxW, y + 1,        rankColor);
        g.fill(x,           y + boxH-1, x + boxW, y + boxH,     C_BORDER);
        g.fill(x,           y,          x + 1,    y + boxH,     C_BORDER);
        g.fill(x + boxW-1,  y,          x + boxW, y + boxH,     C_BORDER);

        // ── Ligne 1 : rang + temps restant (coloré) ───────────────────────────
        g.drawString(mc.font, rankStr,      x + pad,                         y + 4, rankColor, false);
        g.drawString(mc.font, remainingStr, x + boxW - remainingStrW - pad,  y + 4, timeColor, false);

        // ── Séparateur ────────────────────────────────────────────────────────
        g.fill(x + 3, y + 13, x + boxW - 3, y + 14, C_BORDER);

        // ── Ligne 2 : kills + temps écoulé ────────────────────────────────────
        g.drawString(mc.font, killStr,    x + pad,                       y + 16, C_KILLS,   false);
        g.drawString(mc.font, elapsedStr, x + boxW - elapsedStrW - pad,  y + 16, C_ELAPSED, false);
    }
}
