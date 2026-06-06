package neoforge.donjonmc.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import neoforge.donjonmc.client.ClientDailyQuestCache;
import neoforge.donjonmc.client.ClientPunishmentCache;

@OnlyIn(Dist.CLIENT)
public final class DailyQuestHud {

    private DailyQuestHud() {}

    private static final int C_BG         = 0xBB00100A;
    private static final int C_BORDER     = 0xFF1A5C30;
    private static final int C_LABEL      = 0xFF2E7A45;
    private static final int C_TIMER_OK   = 0xFF44AA55;
    private static final int C_TIMER_WARN = 0xFFFF8800;
    private static final int C_TIMER_CRIT = 0xFFFF2222;
    private static final int C_COUNT      = 0xFFAABBAA;
    private static final int C_DONE_TEXT  = 0xFF55FF55;

    // Couleurs de difficulté (point + barre)
    private static final int C_EASY   = 0xFF888888; // gris
    private static final int C_NORMAL = 0xFFFFAA00; // or
    private static final int C_HARD   = 0xFFFF4444; // rouge

    // Mini barre de progression
    private static final int BAR_W    = 28;
    private static final int BAR_H    = 4;
    private static final int C_BAR_BG = 0xFF1A2A1A;

    private static final int PAD      = 6;
    private static final int DOT      = 4; // taille du carré de difficulté

    /** Retourne la couleur selon l'ID de quête. IDs 0-14=Facile, 15-34=Normale, 35-43=Difficile. */
    private static int diffColor(int questId) {
        if (questId < 15) return C_EASY;
        if (questId < 35) return C_NORMAL;
        return C_HARD;
    }

    public static void render(GuiGraphics g, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;

        if (!ClientDailyQuestCache.hudVisible) return;
        long secs = ClientDailyQuestCache.remainingSeconds;
        if (secs < 0) return;

        int done  = ClientDailyQuestCache.questsDone;
        int total = ClientDailyQuestCache.questsTotal;

        String timerStr = String.format("%d:%02d", secs / 60, secs % 60);
        String countStr = done + "/" + total;
        String label    = "QUÊTES";

        int timerW = mc.font.width(timerStr);
        int labelW = mc.font.width(label);
        // Droite du header : "2/4  59:30" avec 5px de séparation
        String rightHeader = countStr + "  " + timerStr;
        int rightHeaderW   = mc.font.width(rightHeader);

        // Préparer les données des lignes de quête
        int[]     ids   = ClientDailyQuestCache.questIds;
        int[]     prog  = ClientDailyQuestCache.questProgress;
        int[]     tgt   = ClientDailyQuestCache.questTargets;
        boolean[] comp  = ClientDailyQuestCache.questCompleted;

        int lineCount = 0;
        int maxNameW  = 0;
        String[] names = new String[4];
        for (int i = 0; i < 4; i++) {
            if (ids[i] < 0) { names[i] = null; continue; }
            lineCount++;
            String raw = Component.translatable("donjonmc.quest." + ids[i] + ".name").getString();
            if (raw.length() > 20) raw = raw.substring(0, 18) + "…";
            names[i] = raw;
            maxNameW = Math.max(maxNameW, mc.font.width(raw));
        }

        int rightSectionW  = BAR_W + 4 + mc.font.width("99/99");
        int questLineInner = DOT + 3 + maxNameW + 4 + rightSectionW;
        int headerInner    = labelW + 8 + rightHeaderW;
        int innerW         = Math.max(headerInner, questLineInner);
        int boxW           = innerW + PAD * 2;
        // Header : 1 ligne (14px) + séparateur (1px) + lignes quêtes
        int boxH           = 14 + (lineCount > 0 ? 1 + lineCount * 11 : 0);

        int punishH = (ClientPunishmentCache.remainingSeconds >= 0) ? 26 + 4 : 0;
        boolean left = ClientDailyQuestCache.questHudLeft;
        int x = left ? 6 : screenW - boxW - 6;
        int y = screenH - boxH - 48 - punishH;

        // ── Fond + bordure ────────────────────────────────────────────────────
        g.fill(x, y, x + boxW, y + boxH, C_BG);
        g.fill(x,           y,          x + boxW, y + 1,        C_BORDER);
        g.fill(x,           y + boxH-1, x + boxW, y + boxH,     C_BORDER);
        g.fill(x,           y,          x + 1,    y + boxH,     C_BORDER);
        g.fill(x + boxW-1,  y,          x + boxW, y + boxH,     C_BORDER);

        // ── Header : une seule ligne — label | count  timer ─────────────────
        g.drawString(mc.font, label, x + PAD, y + 3, C_LABEL, false);
        // count en gris, timer coloré, séparés par 5px
        int timerColor = secs < 120 ? C_TIMER_CRIT : (secs < 600 ? C_TIMER_WARN : C_TIMER_OK);
        int rightX = x + boxW - PAD;
        g.drawString(mc.font, timerStr, rightX - timerW,                       y + 3, timerColor, false);
        int countX = rightX - timerW - 5 - mc.font.width(countStr);
        g.drawString(mc.font, countStr, countX,                                y + 3, C_COUNT,    false);

        if (lineCount == 0) return;

        // Séparateur
        g.fill(x + 3, y + 14, x + boxW - 3, y + 15, C_BORDER);

        // ── Lignes de quête ───────────────────────────────────────────────────
        int lineY = y + 17;
        for (int i = 0; i < 4; i++) {
            if (names[i] == null) continue;

            int dc    = diffColor(ids[i]);
            boolean c = comp[i];

            // Point de difficulté (carré coloré)
            int dotX = x + PAD;
            int dotY = lineY + (mc.font.lineHeight - DOT) / 2;
            if (c) {
                g.fill(dotX, dotY, dotX + DOT, dotY + DOT, C_DONE_TEXT);
            } else {
                g.fill(dotX, dotY, dotX + DOT, dotY + DOT, dc);
            }

            // Nom de la quête
            int nameColor = c ? C_DONE_TEXT : 0xFFDDDDDD;
            g.drawString(mc.font, names[i], dotX + DOT + 3, lineY, nameColor, false);

            // Section droite : barre ou ✓
            int slotX = x + boxW - PAD - rightSectionW;
            if (c) {
                g.drawString(mc.font, "✓", slotX + rightSectionW - mc.font.width("✓"), lineY, C_DONE_TEXT, false);
            } else {
                int p = prog[i], t = Math.max(1, tgt[i]);
                int barY = lineY + (mc.font.lineHeight - BAR_H) / 2;
                g.fill(slotX, barY, slotX + BAR_W, barY + BAR_H, C_BAR_BG);
                int filled = (int)(BAR_W * Math.min(p, t) / (float) t);
                if (filled > 0) g.fill(slotX, barY, slotX + filled, barY + BAR_H, dc);
                String progStr = p + "/" + t;
                g.drawString(mc.font, progStr, slotX + BAR_W + 4, lineY, 0xFF888888, false);
            }

            lineY += 11;
        }
    }
}
