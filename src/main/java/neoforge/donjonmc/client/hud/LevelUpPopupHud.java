package neoforge.donjonmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.player.LevelHelper;

@OnlyIn(Dist.CLIENT)
public final class LevelUpPopupHud {

    private LevelUpPopupHud() {}

    private static final long SHOW_MS    = 4000L;
    private static final long FADE_MS    = 500L;

    private static long   triggerTime = -1L;
    private static String popupText   = "";

    public static void trigger(int newLevel) {
        String rank = LevelHelper.rankForLevel(newLevel);
        popupText   = "⬆  NIVEAU " + newLevel + "  ·  RANG " + rank;
        triggerTime = System.currentTimeMillis();
    }

    public static void render(GuiGraphics g, int screenW, int screenH) {
        if (triggerTime < 0) return;
        Minecraft mc = Minecraft.getInstance();

        long elapsed = System.currentTimeMillis() - triggerTime;
        if (elapsed > SHOW_MS) { triggerTime = -1; return; }

        // Alpha : fade in 0→FADE_MS, full FADE_MS→(SHOW_MS-FADE_MS), fade out
        float alpha;
        if (elapsed < FADE_MS) {
            alpha = (float) elapsed / FADE_MS;
        } else if (elapsed > SHOW_MS - FADE_MS) {
            alpha = (float)(SHOW_MS - elapsed) / FADE_MS;
        } else {
            alpha = 1f;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        int a = (int)(alpha * 0xFF);
        if (a <= 0) return;

        int textW = mc.font.width(popupText);
        int boxW  = textW + 24;
        int boxH  = 18;
        int x     = screenW / 2 - boxW / 2;
        int y     = 20;

        // Palette « Système » cyan + alpha animé
        int bg     = (a << 24) | 0x0A1428;
        int glow   = ((a / 4) << 24) | 0x2FD8FF;
        int border = (a << 24) | 0x2FD8FF;
        int accent = (a << 24) | 0x4FE3FF;
        int text   = (a << 24) | 0xE6F4FF;

        // Animation « pop » : zoom à l'apparition (ease-out), puis stable
        float scale = 1f;
        if (elapsed < FADE_MS) {
            float t = (float) elapsed / FADE_MS;
            scale = 0.7f + 0.3f * (1f - (1f - t) * (1f - t)); // 0.7 → 1.0
        }

        var pose = g.pose();
        pose.pushPose();
        float cxp = screenW / 2f, cyp = y + boxH / 2f;
        pose.translate(cxp, cyp, 0);
        pose.scale(scale, scale, 1f);
        pose.translate(-cxp, -cyp, 0);

        // Fond + halo + bordure
        g.fill(x, y, x + boxW, y + boxH, bg);
        box(g, x - 1, y - 1, boxW + 2, boxH + 2, glow);
        box(g, x, y, boxW, boxH, border);

        // Accents cyan aux coins
        int n = 4;
        g.fill(x, y, x + n, y + 1, accent);
        g.fill(x, y, x + 1, y + n, accent);
        g.fill(x + boxW - n, y, x + boxW, y + 1, accent);
        g.fill(x + boxW - 1, y, x + boxW, y + n, accent);
        g.fill(x, y + boxH - 1, x + n, y + boxH, accent);
        g.fill(x, y + boxH - n, x + 1, y + boxH, accent);
        g.fill(x + boxW - n, y + boxH - 1, x + boxW, y + boxH, accent);
        g.fill(x + boxW - 1, y + boxH - n, x + boxW, y + boxH, accent);

        g.drawString(mc.font, popupText, x + 12, y + 5, text, false);

        pose.popPose();
    }

    /** Cadre 1px. */
    private static void box(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color);
        g.fill(x,         y + h - 1, x + w,     y + h,     color);
        g.fill(x,         y,         x + 1,     y + h,     color);
        g.fill(x + w - 1, y,         x + w,     y + h,     color);
    }
}
