package neoforge.donjonmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.client.ClientPlayerDataCache;

@OnlyIn(Dist.CLIENT)
public final class XpBarHud {

    private XpBarHud() {}

    private static final int BAR_W = 182;
    private static final int BAR_H = 5;

    private static final int C_SHADOW   = 0xAA000000;
    private static final int C_BG       = 0xFF100820;
    private static final int C_FILL     = 0xFF7722CC;
    private static final int C_FILL_HI  = 0xFFAA44FF;
    private static final int C_FILL_LOW = 0xFF4A0E88;
    private static final int C_BORDER   = 0xFF4A1A7A;
    private static final int C_LABEL    = 0xFF6644AA;
    private static final int C_VALUE    = 0xFFAA88EE;

    public static void render(GuiGraphics g, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        int level = ClientPlayerDataCache.level;
        if (level >= 50) return; // max level — pas de barre

        long xp       = ClientPlayerDataCache.xp;
        long xpNeeded = ClientPlayerDataCache.xpRequired;
        if (xpNeeded <= 0) return;

        int x     = screenW / 2 - BAR_W / 2;
        // Juste au-dessus de la barre de mana (y=screenH-49) + texte de mana (10px) + gap (2px)
        int barY  = screenH - 49 - 10 - 2 - BAR_H;
        int textY = barY - 9;

        // Textes
        String label  = "XP";
        String values = String.format("%,d / %,d", xp, xpNeeded)
                             .replace(',', ' '); // espace fine comme séparateur de milliers
        g.drawString(mc.font, label,  x,                                   textY, C_LABEL, false);
        g.drawString(mc.font, values, x + BAR_W - mc.font.width(values),   textY, C_VALUE, false);

        // Ombre
        g.fill(x - 1, barY - 1, x + BAR_W + 1, barY + BAR_H + 1, C_SHADOW);
        // Fond
        g.fill(x, barY, x + BAR_W, barY + BAR_H, C_BG);

        // Remplissage
        int filled = (int)(BAR_W * Math.min(xp, xpNeeded) / (double) xpNeeded);
        if (filled > 0) {
            g.fill(x,     barY + 1, x + filled, barY + BAR_H - 1, C_FILL);
            g.fill(x,     barY,     x + filled, barY + 1,          C_FILL_HI);
            g.fill(x,     barY + BAR_H - 1, x + filled, barY + BAR_H, C_FILL_LOW);
        }

        // Bordure
        g.fill(x,              barY,              x + BAR_W,     barY + 1,       C_BORDER);
        g.fill(x,              barY + BAR_H - 1,  x + BAR_W,     barY + BAR_H,   C_BORDER);
        g.fill(x,              barY,              x + 1,          barY + BAR_H,   C_BORDER);
        g.fill(x + BAR_W - 1, barY,              x + BAR_W,      barY + BAR_H,   C_BORDER);
    }
}
