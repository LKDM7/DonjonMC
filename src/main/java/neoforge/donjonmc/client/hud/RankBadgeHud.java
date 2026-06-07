package neoforge.donjonmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.client.ClientPlayerDataCache;
import neoforge.donjonmc.player.LevelHelper;

@OnlyIn(Dist.CLIENT)
public final class RankBadgeHud {

    private RankBadgeHud() {}

    private static final int C_BG  = 0xBB0A0A14;
    private static final int C_SEP = 0xFF333355;

    public static void render(GuiGraphics g, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        int level = ClientPlayerDataCache.level;
        String rank  = LevelHelper.rankForLevel(level);
        int rankColor = LevelHelper.rankColor(level) | 0xFF000000;

        String rankPart  = "RANG " + rank;
        String levelPart = "Nv. " + level;

        int rankW  = mc.font.width(rankPart);
        int levelW = mc.font.width(levelPart);
        int sepW   = 6; // espace + barre + espace
        int innerW = rankW + sepW + levelW;
        int boxW   = innerW + 16;
        int boxH   = 14;

        int x = 6;
        int y = 20; // descendu pour ne pas chevaucher le HUD d'un autre mod en haut à gauche

        // Fond
        g.fill(x, y, x + boxW, y + boxH, C_BG);
        // Bordure couleur du rang
        g.fill(x,           y,          x + boxW, y + 1,        rankColor & 0x88FFFFFF);
        g.fill(x,           y + boxH-1, x + boxW, y + boxH,     rankColor & 0x88FFFFFF);
        g.fill(x,           y,          x + 1,    y + boxH,     rankColor & 0x88FFFFFF);
        g.fill(x + boxW-1,  y,          x + boxW, y + boxH,     rankColor & 0x88FFFFFF);

        int pad = 8;
        // "RANG X"
        g.drawString(mc.font, rankPart, x + pad, y + 3, rankColor, false);
        // Séparateur vertical
        int sepX = x + pad + rankW + 2;
        g.fill(sepX, y + 2, sepX + 1, y + boxH - 2, C_SEP);
        // "Nv. 12"
        g.drawString(mc.font, levelPart, sepX + 3, y + 3, 0xFFCCCCCC, false);
    }
}
