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

        int bg     = (a << 24) | 0x120820;
        int border = (a << 24) | 0x8800FF;
        int text   = (a << 24) | 0xCC88FF;

        g.fill(x, y, x + boxW, y + boxH, bg);
        g.fill(x,           y,          x + boxW, y + 1,        border);
        g.fill(x,           y + boxH-1, x + boxW, y + boxH,     border);
        g.fill(x,           y,          x + 1,    y + boxH,     border);
        g.fill(x + boxW-1,  y,          x + boxW, y + boxH,     border);

        g.drawString(mc.font, popupText, x + 12, y + 5, text, false);
    }
}
