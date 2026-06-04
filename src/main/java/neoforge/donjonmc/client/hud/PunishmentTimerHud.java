package neoforge.donjonmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.client.ClientPunishmentCache;

@OnlyIn(Dist.CLIENT)
public final class PunishmentTimerHud {

    private PunishmentTimerHud() {}

    // Couleurs
    private static final int C_BG         = 0xBB0D0000; // fond sombre rouge très discret
    private static final int C_BORDER     = 0xFF550000; // bordure bordeaux
    private static final int C_LABEL      = 0xFF773333; // "PUNITION" discret
    private static final int C_TIMER_NORM = 0xFFCC3333; // temps normal
    private static final int C_TIMER_WARN = 0xFFFF7700; // < 5 min
    private static final int C_TIMER_CRIT = 0xFFFF2222; // < 1 min (rouge vif)

    public static void render(GuiGraphics g, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        long secs = ClientPunishmentCache.remainingSeconds;
        if (secs < 0) return;

        long min = secs / 60;
        long sec = secs % 60;
        String timerStr = String.format("%d:%02d", min, sec);
        String label    = "PUNITION";

        int timerW = mc.font.width(timerStr);
        int labelW = mc.font.width(label);
        int innerW = Math.max(timerW, labelW);
        int boxW   = innerW + 14;
        int boxH   = 26;

        // Positionné en bas à droite, au-dessus de la hotbar
        int x = screenW - boxW - 6;
        int y = screenH - boxH - 48;

        // ── Fond ─────────────────────────────────────────────────────────────
        g.fill(x, y, x + boxW, y + boxH, C_BG);

        // ── Bordure 1 px ─────────────────────────────────────────────────────
        g.fill(x,          y,          x + boxW, y + 1,          C_BORDER); // haut
        g.fill(x,          y + boxH - 1, x + boxW, y + boxH,    C_BORDER); // bas
        g.fill(x,          y,          x + 1,    y + boxH,       C_BORDER); // gauche
        g.fill(x + boxW - 1, y,        x + boxW, y + boxH,      C_BORDER); // droite

        // ── Label ─────────────────────────────────────────────────────────────
        int lx = x + (boxW - labelW) / 2;
        g.drawString(mc.font, label, lx, y + 3, C_LABEL, false);

        // ── Ligne de séparation fine ──────────────────────────────────────────
        g.fill(x + 3, y + 13, x + boxW - 3, y + 14, C_BORDER);

        // ── Timer (couleur selon urgence) ─────────────────────────────────────
        int color = secs < 60 ? C_TIMER_CRIT : (secs < 300 ? C_TIMER_WARN : C_TIMER_NORM);
        int tx = x + (boxW - timerW) / 2;
        g.drawString(mc.font, timerStr, tx, y + 16, color, false);
    }
}
