package neoforge.donjonmc.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.client.ClientDailyQuestCache;
import neoforge.donjonmc.client.ClientPlayerDataCache;
import neoforge.donjonmc.client.ClientRaidCache;
import neoforge.donjonmc.client.ClientUniqueQuestCache;
import neoforge.donjonmc.dungeon.DungeonRank;
import neoforge.donjonmc.quest.UniqueQuestDef;
import neoforge.donjonmc.quest.UniqueQuestRegistry;
import neoforge.donjonmc.network.ChooseClassPacket;
import neoforge.donjonmc.network.RaidActionPacket;
import neoforge.donjonmc.network.RespecStatsPacket;
import neoforge.donjonmc.network.SpendSkillPointPacket;
import neoforge.donjonmc.network.TogglePerceptionPacket;
import neoforge.donjonmc.network.ToggleSpeedPacket;
import neoforge.donjonmc.player.ClassTrialHandler;
import neoforge.donjonmc.player.LevelHelper;
import neoforge.donjonmc.player.PlayerClass;
import neoforge.donjonmc.player.StatType;
import neoforge.donjonmc.raid.RaidRole;

public class HunterScreen extends Screen {

    // Dimensions du panneau
    private static final int W = 320;
    private static final int H = 240;

    // Layout
    private static final int TAB_Y         = 21;
    private static final int TAB_H         = 18;
    private static final int CONTENT_Y     = TAB_Y + TAB_H + 4; // = 43
    private static final int STATS_HEADER_Y = CONTENT_Y + 6;    // = 49
    private static final int STATS_FIRST_Y  = STATS_HEADER_Y + 24; // = 73
    private static final int STATS_ROW_H   = 14;

    // Palette — "Système" Solo Leveling modernisé (bleu nuit profond + cyan doux)
    private static final int C_BG        = 0xD8030710; // overlay écran, plus sombre
    private static final int C_PANEL_TOP = 0xF80D1830; // dégradé panneau (haut)
    private static final int C_PANEL_BOT = 0xF804060E; // dégradé panneau (bas)
    private static final int C_PANEL     = 0xF80A1426; // aplat de secours
    private static final int C_BORDER    = 0xFF2BB8DD; // bordure cyan adoucie
    private static final int C_BORDER_SOFT = 0x4030B8E0; // séparateurs discrets
    private static final int C_GLOW      = 0x3328C8F0; // lueur cyan (alpha bas)
    private static final int C_TAB_ON    = 0xFF12304E;
    private static final int C_TAB_OFF   = 0xFF071120;
    private static final int C_TAB_HOV   = 0xFF143C5C;
    private static final int C_XP_BG     = 0xFF060F1C;
    private static final int C_XP_FG     = 0xFF1E9AD0; // bas du dégradé de barre
    private static final int C_XP_FG2    = 0xFF7BE9FF; // haut du dégradé de barre
    private static final int C_TEXT      = 0xFFE6F4FF;
    private static final int C_DIM       = 0xFF647F9C;
    private static final int C_GOLD      = 0xFFFFD86B;
    private static final int C_ACCENT    = 0xFF53E0FF; // cyan accent (titre, soulignés)
    private static final int C_PLUS      = 0xFF0E5A6E;
    private static final int C_PLUS_H    = 0xFF1A86A8;
    private static final int C_ROW_ALT   = 0x1840C8F0; // fond alterné des rangées

    private int activeTab = 0;
    private int left, top;

    // Stats tab click zones
    private int speedToggleY      = -1;
    private int perceptionToggleY = -1;
    private int respecBtnY        = -1;

    // Quests tab click zone (toggle HUD)
    private int questHudToggleY = -1;

    // Raid tab click zones — set each frame by renderRaid, consumed by mouseClicked
    private int          raidCreateY    = -1;
    private int          raidAcceptY    = -1;
    private int          raidDeclineY   = -1;
    private int          raidRematchY   = -1;  // "not in group" rematch btn
    private int          raidRoleY      = -1;
    private int          raidActionY    = -1;  // leave / disband / rematch-in-group
    private int          raidRematchInGroupX = -1;
    private final java.util.List<String>  raidInviteNames = new java.util.ArrayList<>();
    private final java.util.List<Integer> raidInviteY     = new java.util.ArrayList<>();
    // Défilement de la liste d'invitation
    private int raidInviteScroll    = 0;
    private int raidInviteAreaTop    = -1;
    private int raidInviteAreaBottom = -1;

    public HunterScreen() {
        super(Component.empty());
    }

    /** Raccourci de traduction côté client. */
    private String t(String key) {
        return Component.translatable(key).getString();
    }

    // ─── Initialisation ──────────────────────────────────────────────────────

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top  = (this.height - H) / 2;
    }

    // ─── Rendu ───────────────────────────────────────────────────────────────

    @Override
    protected void renderBlurredBackground(float partialTick) {
        // Désactive le flou gaussien sur le monde — on gère notre propre fond
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // super en premier : gère l'overlay de fond AVANT notre panneau
        super.render(g, mx, my, pt);

        // Fond semi-transparent sur tout l'écran
        g.fill(0, 0, this.width, this.height, C_BG);

        // Panneau principal : dégradé bleu nuit + double lueur + accents de coin
        g.fillGradient(left, top, left + W, top + H, C_PANEL_TOP, C_PANEL_BOT);
        glowBorder(g, left, top, W, H);
        corners(g, left, top, W, H);

        // En-tête : bandeau dégradé + titre + soulignement en fondu
        g.fillGradient(left + 1, top + 1, left + W - 1, top + 19, 0x5018486E, 0x00000000);
        g.drawCenteredString(this.font, "◈ " + t("donjonmc.gui.title"), left + W / 2, top + 7, C_ACCENT);
        fadeLine(g, left + 14, top + 18, W - 28, C_ACCENT);

        renderTabs(g, mx, my);

        switch (activeTab) {
            case 0 -> renderProfile(g);
            case 1 -> renderStats(g, mx, my);
            case 2 -> renderQuests(g, mx, my);
            case 3 -> renderClasse(g, mx, my);
            case 4 -> renderRaid(g, mx, my);
            case 5 -> renderUniques(g);
        }
    }

    /** Ligne horizontale qui s'estompe vers les bords (effet "énergie"). */
    private void fadeLine(GuiGraphics g, int x, int y, int w, int color) {
        int steps = 8;
        int seg = Math.max(1, w / (2 * steps));
        for (int i = 0; i < steps; i++) {
            int alpha = 0x20 + (0xD0 * (i + 1) / steps);
            int c = (color & 0x00FFFFFF) | (alpha << 24);
            g.fill(x + i * seg, y, x + (i + 1) * seg, y + 1, c);
            g.fill(x + w - (i + 1) * seg, y, x + w - i * seg, y + 1, c);
        }
        g.fill(x + steps * seg, y, x + w - steps * seg, y + 1, color);
    }

    private static final int TAB_COUNT = 6;

    private void renderTabs(GuiGraphics g, int mx, int my) {
        String[] labels = {
            t("donjonmc.gui.tab.profil"),
            t("donjonmc.gui.tab.stats"),
            t("donjonmc.gui.tab.quetes"),
            t("donjonmc.gui.tab.classe"),
            t("donjonmc.gui.tab.raid"),
            t("donjonmc.gui.tab.uniques")
        };
        // Bandeau de fond des onglets
        g.fill(left + 1, top + TAB_Y, left + W - 1, top + TAB_Y + TAB_H, C_TAB_OFF);

        int tw = (W - 2) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = left + 1 + i * tw;
            int tW = (i == TAB_COUNT - 1) ? (W - 2 - tw * (TAB_COUNT - 1)) : tw;
            int ty = top + TAB_Y;
            boolean on  = activeTab == i;
            boolean hov = mx >= tx && mx < tx + tW && my >= ty && my < ty + TAB_H;

            if (on) {
                g.fillGradient(tx, ty, tx + tW, ty + TAB_H, C_TAB_ON, 0xFF0A1C30);
                g.fill(tx, ty + TAB_H - 2, tx + tW, ty + TAB_H, C_ACCENT); // soulignement épais
                g.fill(tx, ty, tx + tW, ty + 1, 0x6053E0FF);               // liseré haut subtil
            } else if (hov) {
                g.fill(tx, ty, tx + tW, ty + TAB_H, C_TAB_HOV);
                g.fill(tx, ty + TAB_H - 1, tx + tW, ty + TAB_H, 0x8053E0FF);
            }
            // Séparateur vertical discret entre onglets
            if (i > 0) g.fill(tx, ty + 4, tx + 1, ty + TAB_H - 4, C_BORDER_SOFT);

            g.drawCenteredString(this.font, labels[i], tx + tW / 2, ty + 5,
                on ? 0xFFFFFFFF : (hov ? C_TEXT : C_DIM));
        }
        // Ligne de base sous le bandeau
        g.fill(left + 1, top + TAB_Y + TAB_H, left + W - 1, top + TAB_Y + TAB_H + 1, C_BORDER_SOFT);
    }

    // ─── Boutons modernes ────────────────────────────────────────────────────

    /** Toggle vert/rouge avec liseré haut lumineux et hover éclairci. */
    private void btnToggle(GuiGraphics g, int x, int y, int w, int h,
                           String label, boolean hovered, boolean on) {
        int base = on ? (hovered ? 0xFF155A38 : 0xFF0E3F28) : (hovered ? 0xFF551515 : 0xFF330D0D);
        int edge = on ? 0xFF2FBF6F : 0xFFB04040;
        g.fill(x, y, x + w, y + h, base);
        g.fill(x, y, x + w, y + 1, (edge & 0x00FFFFFF) | 0x80000000);
        border(g, x, y, w, h, (edge & 0x00FFFFFF) | 0x90000000);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2 + 1,
            on ? 0xFFB8FFD0 : 0xFFFFB8B8);
    }

    /** Bouton d'action cyan (respec, épreuve, créer un raid…). */
    private void btnAction(GuiGraphics g, int x, int y, int w, int h,
                           String label, boolean hovered, boolean enabled, int textColor) {
        int base = !enabled ? 0xFF131722 : (hovered ? 0xFF17506E : 0xFF0E3850);
        int edge = !enabled ? 0xFF2A3344 : C_BORDER;
        g.fill(x, y, x + w, y + h, base);
        if (enabled) g.fill(x, y, x + w, y + 1, 0x8053E0FF);
        border(g, x, y, w, h, (edge & 0x00FFFFFF) | (enabled ? 0xB0000000 : 0x70000000));
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2 + 1,
            enabled ? textColor : C_DIM);
    }

    private void renderProfile(GuiGraphics g) {
        String name = minecraft.player != null ? minecraft.player.getName().getString() : "?";
        int  lvl    = ClientPlayerDataCache.level;
        long xp     = ClientPlayerDataCache.xp;
        long xpMax  = ClientPlayerDataCache.xpRequired;
        int  sp     = ClientPlayerDataCache.skillPoints;
        String rank = LevelHelper.rankForLevel(lvl);
        int rankCol = LevelHelper.rankColor(lvl);

        int y  = top + CONTENT_Y + 8;
        int cx = left + 14;

        // Nom | badge de rang encadré
        String hunterStr = t("donjonmc.gui.profile.hunter") + name;
        g.drawString(this.font, hunterStr, cx, y, C_TEXT);
        String rankStr = t("donjonmc.gui.profile.rank") + rank;
        int bw = this.font.width(rankStr) + 10;
        int bx = left + W - 14 - bw;
        g.fill(bx, y - 4, bx + bw, y + 10, (rankCol & 0x00FFFFFF) | 0x28000000);
        border(g, bx, y - 4, bw, 14, (rankCol & 0x00FFFFFF) | 0xA0000000);
        g.drawString(this.font, rankStr, bx + 5, y - 1, rankCol);

        // Niveau (gros chiffre doré)
        y += 20;
        String levelLabel = t("donjonmc.gui.profile.level");
        g.drawString(this.font, levelLabel, cx, y, C_DIM);
        g.drawString(this.font, String.valueOf(lvl), cx + this.font.width(levelLabel), y, C_GOLD);

        // Barre XP : libellé + pourcentage à droite
        y += 18;
        double xpFrac = xpMax > 0 ? (double) Math.min(xp, xpMax) / xpMax : 0;
        g.drawString(this.font, t("donjonmc.gui.profile.xp"), cx, y, C_DIM);
        String pctStr = (int) (xpFrac * 100) + " %";
        g.drawString(this.font, pctStr, left + W - 14 - this.font.width(pctStr), y, C_ACCENT);
        y += 11;
        glowBar(g, cx, y, W - 28, 9, xpFrac, C_XP_FG2, C_XP_FG);
        y += 12;
        g.drawCenteredString(this.font, xp + " / " + xpMax, left + W / 2, y, C_DIM);

        // Séparateur
        y += 14;
        fadeLine(g, cx, y, W - 28, C_BORDER);

        // Classe avec pastille de couleur
        y += 8;
        PlayerClass cls   = ClientPlayerDataCache.playerClass();
        String clsLabel   = t("donjonmc.gui.profile.class");
        String clsName    = Component.translatable(cls.nameLangKey()).getString();
        g.drawString(this.font, clsLabel, cx, y, C_DIM);
        int chipX = cx + this.font.width(clsLabel);
        g.fill(chipX, y + 1, chipX + 6, y + 7, cls.color);
        g.drawString(this.font, clsName, chipX + 10, y, cls.color);

        // Points de compétence
        y += 14;
        String spLabel = t("donjonmc.gui.profile.sp");
        g.drawString(this.font, spLabel, cx, y, C_DIM);
        g.drawString(this.font, String.valueOf(sp),
            cx + this.font.width(spLabel), y, sp > 0 ? C_GOLD : C_TEXT);

        // ── Encart carrière (stats vanilla persistantes + compteur donjons) ───
        y += 14;
        fadeLine(g, cx, y, W - 28, C_BORDER);
        y += 6;
        g.drawString(this.font, t("donjonmc.gui.profile.career"), cx, y, C_ACCENT);
        y += 12;

        int colW = (W - 28) / 2;
        long playSecs = ClientPlayerDataCache.playTimeTicks / 20L;
        String playStr = (playSecs / 3600) + "h " + (playSecs % 3600) / 60 + "min";

        careerStat(g, cx,        y,      t("donjonmc.gui.profile.career.kills"),
            String.valueOf(ClientPlayerDataCache.mobKills));
        careerStat(g, cx + colW, y,      t("donjonmc.gui.profile.career.dungeons"),
            String.valueOf(ClientPlayerDataCache.dungeonsCleared));
        careerStat(g, cx,        y + 12, t("donjonmc.gui.profile.career.deaths"),
            String.valueOf(ClientPlayerDataCache.deaths));
        careerStat(g, cx + colW, y + 12, t("donjonmc.gui.profile.career.playtime"), playStr);
    }

    /** Une ligne d'encart carrière : libellé gris + valeur dorée alignée. */
    private void careerStat(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(this.font, label, x, y, C_DIM);
        g.drawString(this.font, value, x + this.font.width(label) + 4, y, C_GOLD);
    }

    private void renderStats(GuiGraphics g, int mx, int my) {
        speedToggleY      = -1;
        perceptionToggleY = -1;
        respecBtnY        = -1;
        int sp = ClientPlayerDataCache.skillPoints;
        int y  = top + STATS_HEADER_Y;
        int cx = left + 12;

        String spLabel = t("donjonmc.gui.stats.available");
        g.drawString(this.font, spLabel, cx, y, C_DIM);
        g.drawString(this.font, String.valueOf(sp),
            cx + this.font.width(spLabel), y, sp > 0 ? C_GOLD : C_TEXT);

        y += 16;
        fadeLine(g, cx, y, W - 24, C_BORDER);

        for (StatType stat : StatType.values()) {
            int ry = top + STATS_FIRST_Y + stat.ordinal() * STATS_ROW_H;

            // Rangée alternée + mini-jauge de stat
            if (stat.ordinal() % 2 == 0) {
                g.fill(cx - 2, ry - 3, left + W - 10, ry + 11, C_ROW_ALT);
            }
            String statName = Component.translatable(stat.langKey()).getString();
            g.drawString(this.font, statName, cx + 3, ry, C_TEXT);

            int statVal = ClientPlayerDataCache.getStat(stat);
            g.drawString(this.font, String.valueOf(statVal), left + 130, ry, C_GOLD);

            boolean atMax   = statVal >= StatType.MAX;
            boolean canPlus = sp > 0 && !atMax;

            // Mini-jauge (0..MAX) à droite de la valeur
            int gx = left + 170;
            int gw = left + W - 14 - gx;
            if (canPlus || atMax) gw -= 24; // place pour le bouton + ou le label MAX
            g.fill(gx, ry + 2, gx + gw, ry + 6, C_XP_BG);
            int gfill = (int) (gw * Math.min(statVal, StatType.MAX) / (double) StatType.MAX);
            if (gfill > 0) g.fillGradient(gx, ry + 2, gx + gfill, ry + 6, C_XP_FG2, C_XP_FG);
            border(g, gx, ry + 2, gw, 4, C_BORDER_SOFT);

            if (canPlus) {
                int bx  = left + 148;
                int by  = ry - 1;
                boolean hov = mx >= bx && mx < bx + 14 && my >= by && my < by + 11;
                g.fill(bx, by, bx + 14, by + 11, hov ? C_PLUS_H : C_PLUS);
                g.fill(bx, by, bx + 14, by + 1, 0x8053E0FF);
                border(g, bx, by, 14, 11, (C_BORDER & 0x00FFFFFF) | 0x90000000);
                g.drawCenteredString(this.font, "+", bx + 7, by + 1, 0xFFFFFFFF);
            } else if (atMax) {
                g.drawString(this.font, "MAX", gx + gw + 6, ry, C_GOLD);
            }
        }

        // ── Toggle vitesse Agilité ────────────────────────────────────────────
        int btnW = W - 24;
        int toggleY = top + STATS_FIRST_Y + StatType.values().length * STATS_ROW_H + 8;
        fadeLine(g, cx, toggleY - 4, btnW, C_BORDER);

        boolean speedOn = ClientPlayerDataCache.speedEnabled;
        boolean hSpd = mx >= cx && mx < cx + btnW && my >= toggleY && my < toggleY + 13;
        btnToggle(g, cx, toggleY, btnW, 13,
            t("donjonmc.gui.stats.speed_toggle") + " : "
                + (speedOn ? t("donjonmc.gui.stats.speed_on") : t("donjonmc.gui.stats.speed_off")),
            hSpd, speedOn);
        speedToggleY = toggleY;

        // ── Toggle Glowing Perception ─────────────────────────────────────────
        int percY = toggleY + 17;
        boolean percOn = ClientPlayerDataCache.perceptionEnabled;
        boolean hPerc = mx >= cx && mx < cx + btnW && my >= percY && my < percY + 13;
        btnToggle(g, cx, percY, btnW, 13,
            t("donjonmc.gui.stats.perception_toggle") + " : "
                + (percOn ? t("donjonmc.gui.stats.speed_on") : t("donjonmc.gui.stats.speed_off")),
            hPerc, percOn);
        perceptionToggleY = percY;

        // ── Bouton respec ─────────────────────────────────────────────────────
        int respecY = percY + 17;
        boolean hR = mx >= cx && mx < cx + btnW && my >= respecY && my < respecY + 13;
        btnAction(g, cx, respecY, btnW, 13, t("donjonmc.gui.stats.respec"), hR, true, C_GOLD);
        respecBtnY = respecY;
    }

    private void renderQuests(GuiGraphics g, int mx, int my) {
        questHudToggleY = -1;
        int cx = left + 12;
        int y  = top + CONTENT_Y + 6;

        // Titre + timer ou statut
        g.drawCenteredString(this.font, t("donjonmc.gui.quests.title"), left + W / 2, y, C_TEXT);
        y += 14;
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 6;

        long secs = ClientDailyQuestCache.remainingSeconds;

        if (secs < 0) {
            // Pas de quêtes actives
            g.drawCenteredString(this.font, t("donjonmc.gui.quests.none"), left + W / 2, y + 20, C_DIM);
        } else {
            // Timer
            String timerStr = String.format("%d:%02d", secs / 60, secs % 60);
            int done  = ClientDailyQuestCache.questsDone;
            int total = ClientDailyQuestCache.questsTotal;
            String progress = done + " / " + total;
            int timerColor = secs < 120 ? 0xFFFF2222 : (secs < 600 ? 0xFFFF8800 : 0xFF44AA55);
            g.drawString(this.font, progress, cx, y, C_DIM);
            g.drawString(this.font, timerStr,
                left + W - 12 - this.font.width(timerStr), y, timerColor);
            y += 13;

            // 4 lignes de quêtes
            int[] ids  = ClientDailyQuestCache.questIds;
            int[] prog = ClientDailyQuestCache.questProgress;
            int[] tgts = ClientDailyQuestCache.questTargets;
            boolean[] comp = ClientDailyQuestCache.questCompleted;

            for (int i = 0; i < 4; i++) {
                if (ids[i] < 0) continue;

                int dotColor;
                if      (ids[i] < 15) dotColor = 0xFF888888;
                else if (ids[i] < 35) dotColor = 0xFFFFAA00;
                else                  dotColor = 0xFFFF4444;

                g.fill(cx, y + 2, cx + 4, y + 6, comp[i] ? 0xFF55FF55 : dotColor);

                String name = Component.translatable("donjonmc.quest." + ids[i] + ".name").getString();
                if (name.length() > 22) name = name.substring(0, 20) + "…";
                g.drawString(this.font, name, cx + 7, y, comp[i] ? 0xFF55FF55 : C_TEXT);

                String progStr = comp[i] ? "✓" : (prog[i] + "/" + tgts[i]);
                int progColor  = comp[i] ? 0xFF55FF55 : C_DIM;
                g.drawString(this.font, progStr,
                    left + W - 12 - this.font.width(progStr), y, progColor);

                // Description de la quête
                String desc = Component.translatable("donjonmc.quest." + ids[i] + ".desc").getString();
                if (desc.length() > 36) desc = desc.substring(0, 34) + "…";
                g.drawString(this.font, desc, cx + 7, y + 9, comp[i] ? 0xFF3A7A3A : 0xFF4C5C7A);

                // Mini barre de progression (quêtes en cours uniquement)
                if (!comp[i] && tgts[i] > 0) {
                    int barY = y + 18;
                    int barW = W - 24;
                    g.fill(cx, barY, cx + barW, barY + 2, 0xFF1A2A1A);
                    int filled = (int)(barW * Math.min(prog[i], tgts[i]) / (float) tgts[i]);
                    if (filled > 0) g.fill(cx, barY, cx + filled, barY + 2, dotColor);
                    y += 21;
                } else {
                    y += 19;
                }
            }
        }

        // ── Toggle HUD ────────────────────────────────────────────────────────
        y = top + H - 26;
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 6;
        questHudToggleY = y;
        boolean hudOn = ClientDailyQuestCache.hudVisible;
        int btnW = W - 24;
        boolean hov = mx >= cx && mx < cx + btnW && my >= y && my < y + 13;
        btnToggle(g, cx, y, btnW, 13,
            t(hudOn ? "donjonmc.gui.quests.hud_hide" : "donjonmc.gui.quests.hud_show"),
            hov, hudOn);
    }

    private void renderUniques(GuiGraphics g) {
        int cx = left + 12;
        int y  = top + CONTENT_Y + 6;

        g.drawCenteredString(this.font, t("donjonmc.gui.uniques.title"), left + W / 2, y, C_TEXT);
        y += 12;
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 6;

        int[]     prog = ClientUniqueQuestCache.progress;
        boolean[] done = ClientUniqueQuestCache.completed;

        for (UniqueQuestDef def : UniqueQuestRegistry.ALL) {
            int id = def.id();
            int p  = id < prog.length ? prog[id] : 0;
            boolean c = id < done.length && done[id];

            // Pastille d'état
            g.fill(cx, y + 2, cx + 4, y + 6, c ? 0xFF55FF55 : C_GOLD);

            // Récompense XP (extrême droite)
            String xpStr = "+" + def.xpReward();
            g.drawString(this.font, xpStr, left + W - 12 - this.font.width(xpStr), y, C_GOLD);

            // Progression / coché (juste à gauche de l'XP)
            String progStr = c ? "✓" : (p + "/" + def.target());
            int progX = left + W - 12 - this.font.width(xpStr) - 6 - this.font.width(progStr);
            g.drawString(this.font, progStr, progX, y, c ? 0xFF55FF55 : C_DIM);

            // Nom (tronqué pour ne pas chevaucher)
            String name = Component.translatable(def.nameKey()).getString();
            int maxNameW = progX - (cx + 7) - 4;
            while (this.font.width(name) > maxNameW && name.length() > 1) {
                name = name.substring(0, name.length() - 1);
            }
            g.drawString(this.font, name, cx + 7, y, c ? 0xFF55FF55 : C_TEXT);

            y += 13;
        }
    }

    private void renderClasse(GuiGraphics g, int mx, int my) {
        PlayerClass cls = ClientPlayerDataCache.playerClass();
        if (cls == PlayerClass.NONE) {
            renderClassChoice(g, mx, my);
            return;
        }
        String clsName     = Component.translatable(cls.nameLangKey()).getString();
        String clsDesc     = Component.translatable(cls.descLangKey()).getString();
        int cx = left + 12;
        int y  = top + CONTENT_Y + 6;

        // Indicateur coloré + nom
        g.fill(cx, y, cx + 6, y + this.font.lineHeight, cls.color);
        g.drawString(this.font, clsName, cx + 10, y, cls.color);
        y += 14;
        fadeLine(g, cx, y, W - 24, C_BORDER);

        // Description (multiligne)
        y += 8;
        for (net.minecraft.util.FormattedCharSequence line :
                this.font.split(Component.literal(clsDesc), W - 28)) {
            g.drawString(this.font, line, cx, y, C_DIM);
            y += this.font.lineHeight + 1;
        }

        // Bonus de classe
        y += 6;
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 6;
        g.drawString(this.font, t("donjonmc.gui.class.bonus"), cx, y, C_TEXT);
        y += 12;

        switch (cls) {
            case TANK ->     g.drawString(this.font, t("donjonmc.gui.class.bonus.tank"),     cx + 4, y, C_GOLD);
            case ASSASSIN -> {
                g.drawString(this.font, t("donjonmc.gui.class.bonus.assassin1"), cx + 4, y, C_GOLD);
                y += 12;
                g.drawString(this.font, t("donjonmc.gui.class.bonus.assassin2"), cx + 4, y, C_GOLD);
            }
            case MAGE ->     g.drawString(this.font, t("donjonmc.gui.class.bonus.mage"),    cx + 4, y, C_GOLD);
            case HEALER ->   g.drawString(this.font, t("donjonmc.gui.class.bonus.healer"),  cx + 4, y, C_GOLD);
            case NONE ->     g.drawString(this.font, t("donjonmc.gui.class.none_hint"),      cx + 4, y, C_DIM);
        }

        if (cls == PlayerClass.NONE) {
            y += 24;
            g.drawString(this.font, t("donjonmc.gui.class.command"), cx, y, C_DIM);
        }
    }

    // ─── Choix de classe (niveau 50, épreuve) ────────────────────────────────

    private static final PlayerClass[] CHOOSABLE = {
        PlayerClass.TANK, PlayerClass.ASSASSIN, PlayerClass.MAGE, PlayerClass.HEALER
    };
    private static final int CARD_GAP = 6;
    private static final int CARD_H   = 52;

    private int selectedClassOrdinal = -1;

    private int cardW()  { return (W - 24 - CARD_GAP) / 2; }
    private int cardX(int i) { return left + 12 + (i % 2) * (cardW() + CARD_GAP); }
    private int cardY(int i) { return top + CONTENT_Y + 18 + (i / 2) * (CARD_H + CARD_GAP); }
    private int trialBtnY()  { return top + H - 22; }

    /** Cooldown restant après un échec d'épreuve, calculé depuis le cache client. */
    private static long trialCooldownRemainingMs() {
        long last = ClientPlayerDataCache.lastTrialFailMs;
        if (last <= 0) return 0;
        long elapsed = System.currentTimeMillis() - last;
        return Math.max(0, ClassTrialHandler.cooldownMs() - elapsed);
    }

    private void renderClassChoice(GuiGraphics g, int mx, int my) {
        g.drawCenteredString(this.font, t("donjonmc.gui.class.choose_title"),
            left + W / 2, top + CONTENT_Y + 6, C_TEXT);

        for (int i = 0; i < CHOOSABLE.length; i++) {
            PlayerClass c = CHOOSABLE[i];
            int bx = cardX(i), by = cardY(i), bw = cardW();
            boolean sel = selectedClassOrdinal == c.ordinal();
            boolean hov = mx >= bx && mx < bx + bw && my >= by && my < by + CARD_H;

            // Carte : fond dégradé, bordure colorée à la sélection, hover lumineux
            if (sel) {
                g.fillGradient(bx, by, bx + bw, by + CARD_H, C_TAB_ON, 0xFF0A1C30);
                border(g, bx, by, bw, CARD_H, C_ACCENT);
                g.fill(bx, by, bx + bw, by + 1, c.color); // liseré haut couleur classe
            } else {
                g.fill(bx, by, bx + bw, by + CARD_H, hov ? C_TAB_HOV : C_TAB_OFF);
                border(g, bx, by, bw, CARD_H, hov ? C_BORDER : 0xFF24304A);
            }

            // Pastille + nom
            g.fill(bx + 5, by + 5, bx + 9, by + 11, c.color);
            g.drawString(this.font, Component.translatable(c.nameLangKey()).getString(),
                bx + 13, by + 4, c.color);

            // Description (2 lignes max)
            int ly = by + 16;
            var lines = this.font.split(Component.translatable(c.descLangKey()), bw - 10);
            for (int l = 0; l < Math.min(2, lines.size()); l++) {
                g.drawString(this.font, lines.get(l), bx + 5, ly, C_DIM);
                ly += this.font.lineHeight;
            }

            // Aperçu du sort de classe
            g.drawString(this.font,
                t("donjonmc.gui.class.spell." + c.name().toLowerCase(java.util.Locale.ROOT)),
                bx + 5, by + CARD_H - 11, C_GOLD);
        }

        // ── Bouton "Commencer l'épreuve" ──────────────────────────────────────
        int cx = left + 12, btnW = W - 24, by = trialBtnY();
        long cooldown = trialCooldownRemainingMs();
        boolean lowLevel = ClientPlayerDataCache.level < 50;
        boolean enabled  = !lowLevel && cooldown <= 0 && selectedClassOrdinal > 0;
        boolean hovBtn   = mx >= cx && mx < cx + btnW && my >= by && my < by + 15;

        String label;
        if (lowLevel) {
            label = t("donjonmc.gui.class.level_required");
        } else if (cooldown > 0) {
            long h = cooldown / 3_600_000L;
            long m = (cooldown % 3_600_000L) / 60_000L;
            label = Component.translatable("donjonmc.gui.class.cooldown_wait", h, m).getString();
        } else if (selectedClassOrdinal <= 0) {
            label = t("donjonmc.gui.class.select_hint");
        } else {
            label = t("donjonmc.gui.class.start_trial");
        }

        btnAction(g, cx, by, btnW, 15, label, hovBtn, enabled, 0xFFFFFFFF);
    }

    private void renderRaid(GuiGraphics g, int mx, int my) {
        // Reset click zones
        raidCreateY = raidAcceptY = raidDeclineY = raidRematchY = -1;
        raidRoleY = raidActionY = raidRematchInGroupX = -1;
        raidInviteNames.clear();
        raidInviteY.clear();

        int cx = left + 12;
        int y  = top + CONTENT_Y + 4;

        // ── Not in a group ────────────────────────────────────────────────────
        if (!ClientRaidCache.inGroup) {

            // Pending invite
            if (ClientRaidCache.hasPendingInvite) {
                g.drawString(this.font, "♛ " + ClientRaidCache.inviterName, cx, y, C_GOLD);
                g.drawString(this.font, t("donjonmc.raid.invited_you"),
                    cx + this.font.width("♛ " + ClientRaidCache.inviterName) + 4, y, C_DIM);
                y += 14;

                raidAcceptY = y;
                boolean hAcc = mx >= cx && mx < cx + 68 && my >= y && my < y + 14;
                g.fill(cx, y, cx + 68, y + 14, hAcc ? C_PLUS_H : C_PLUS);
                border(g, cx, y, 68, 14, 0xFF145A32);
                g.drawCenteredString(this.font, t("donjonmc.raid.accept"), cx + 34, y + 3, 0xFFFFFFFF);

                raidDeclineY = y;
                int dx = cx + 76;
                boolean hDec = mx >= dx && mx < dx + 68 && my >= y && my < y + 14;
                g.fill(dx, y, dx + 68, y + 14, hDec ? 0xFF661111 : 0xFF330000);
                border(g, dx, y, 68, 14, 0xFF991111);
                g.drawCenteredString(this.font, t("donjonmc.raid.decline"), dx + 34, y + 3, 0xFFFFAAAA);
                y += 20;
                fadeLine(g, cx, y, W - 24, C_BORDER);
                y += 8;
            }

            // Status + Create button
            g.drawString(this.font, t("donjonmc.raid.no_group"), cx, y, C_DIM);
            y += 14;
            raidCreateY = y;
            boolean hCreate = mx >= cx && mx < cx + 90 && my >= y && my < y + 14;
            g.fill(cx, y, cx + 90, y + 14, hCreate ? C_TAB_HOV : C_TAB_OFF);
            border(g, cx, y, 90, 14, C_BORDER);
            g.drawCenteredString(this.font, t("donjonmc.raid.create"), cx + 45, y + 3, C_TEXT);

            // Quick Rematch
            if (ClientRaidCache.hasHistory) {
                y += 20;
                raidRematchY = y;
                boolean hR = mx >= cx && mx < cx + 110 && my >= y && my < y + 14;
                g.fill(cx, y, cx + 110, y + 14, hR ? C_PLUS_H : C_PLUS);
                border(g, cx, y, 110, 14, 0xFF145A32);
                g.drawCenteredString(this.font, t("donjonmc.raid.rematch"), cx + 55, y + 3, 0xFFFFFFFF);
            }
            return;
        }

        // ── In a group ────────────────────────────────────────────────────────
        String leaderLabel = t("donjonmc.raid.leader");
        String leaderStr   = ClientRaidCache.isLeader
            ? (minecraft.player != null ? minecraft.player.getName().getString() : "?")
            : ClientRaidCache.leaderName;
        g.drawString(this.font, leaderLabel, cx, y, C_DIM);
        g.drawString(this.font, "♛ " + leaderStr, cx + this.font.width(leaderLabel), y, C_GOLD);

        y += 13;
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 4;

        for (var member : ClientRaidCache.members) {
            RaidRole role = RaidRole.fromOrdinal(member.roleOrdinal());
            String rank   = LevelHelper.rankForLevel(member.level());
            g.drawString(this.font, member.name(), cx, y, C_TEXT);
            g.drawString(this.font, "[" + rank + "]", cx + 76, y, LevelHelper.rankColor(member.level()));
            g.drawString(this.font, Component.translatable(role.langKey()).getString(),
                cx + 106, y, role.color);
            y += 11;
        }

        // ── Role selector ─────────────────────────────────────────────────────
        y += 4;
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 5;
        g.drawString(this.font, t("donjonmc.raid.role_label"), cx, y, C_DIM);
        y += 11;

        raidRoleY = y;
        String selfName = minecraft.player != null ? minecraft.player.getName().getString() : "";
        RaidRole myRole = ClientRaidCache.myRole(selfName);
        RaidRole[] selectable = { RaidRole.VANGUARD, RaidRole.STRIKER, RaidRole.SUPPORT, RaidRole.PORTER };
        int bw = (W - 24) / 4;
        for (int i = 0; i < selectable.length; i++) {
            RaidRole r = selectable[i];
            int bx = cx + i * bw;
            boolean sel = myRole == r;
            boolean hov = mx >= bx && mx < bx + bw - 2 && my >= y && my < y + 12;
            g.fill(bx, y, bx + bw - 2, y + 12, sel ? 0xFF1A0050 : (hov ? C_TAB_HOV : C_TAB_OFF));
            border(g, bx, y, bw - 2, 12, sel ? C_BORDER : 0xFF333366);
            g.drawCenteredString(this.font, Component.translatable(r.langKey()).getString(),
                bx + (bw - 2) / 2, y + 2, sel ? r.color : C_DIM);
        }

        // Advance past role buttons (height 12) + gap
        y += 14;

        // ── Portal section ────────────────────────────────────────────────────
        fadeLine(g, cx, y, W - 24, C_BORDER);
        y += 4;
        if (ClientRaidCache.portalRankOrdinal >= 0) {
            DungeonRank pRank = DungeonRank.fromOrdinal(ClientRaidCache.portalRankOrdinal);
            String rankStr = Component.translatable(pRank.langKey()).getString();
            g.drawString(this.font, t("donjonmc.raid.portal.label") + " " + rankStr, cx, y, C_GOLD);
            y += 11;
            String coords = ClientRaidCache.portalX + ", " + ClientRaidCache.portalY + ", " + ClientRaidCache.portalZ;
            g.drawString(this.font, t("donjonmc.raid.portal.coords") + " " + coords, cx + 4, y, C_DIM);
            y += 13;
        } else {
            g.drawString(this.font, t("donjonmc.raid.portal.none"), cx, y, C_DIM);
            y += 13;
        }

        // ── Invite list (leader only) — viewport scrollable ───────────────────
        raidInviteAreaTop = raidInviteAreaBottom = -1;
        if (ClientRaidCache.isLeader && !ClientRaidCache.invitablePlayers.isEmpty()) {
            java.util.List<String> all = ClientRaidCache.invitablePlayers;

            fadeLine(g, cx, y, W - 24, C_BORDER);
            y += 5;
            g.drawString(this.font, t("donjonmc.raid.invite_player") + " (" + all.size() + ")", cx, y, C_DIM);
            y += 11;

            final int rowH       = 12;
            int areaTop          = y;
            int areaBottom       = top + H - 20; // réserve la place pour les boutons d'action
            int visible          = Math.max(1, (areaBottom - areaTop) / rowH);
            int maxScroll        = Math.max(0, all.size() - visible);
            if (raidInviteScroll > maxScroll) raidInviteScroll = maxScroll;
            if (raidInviteScroll < 0)         raidInviteScroll = 0;
            raidInviteAreaTop    = areaTop;
            raidInviteAreaBottom = areaBottom;

            int end = Math.min(all.size(), raidInviteScroll + visible);
            for (int i = raidInviteScroll; i < end; i++) {
                String pName = all.get(i);
                raidInviteNames.add(pName);
                raidInviteY.add(y);
                g.drawString(this.font, pName, cx, y, C_TEXT);
                int ibx = left + W - 12 - 40;
                boolean hov = mx >= ibx && mx < ibx + 40 && my >= y && my < y + 10;
                g.fill(ibx, y, ibx + 40, y + 10, hov ? C_PLUS_H : C_PLUS);
                border(g, ibx, y, 40, 10, C_BORDER);
                g.drawCenteredString(this.font, t("donjonmc.raid.invite"), ibx + 20, y + 1, 0xFFFFFFFF);
                y += rowH;
            }

            // Indicateurs de défilement (molette)
            if (raidInviteScroll > 0)
                g.drawString(this.font, "▲", left + W - 20, areaTop, C_ACCENT);
            if (end < all.size())
                g.drawString(this.font, "▼", left + W - 20, areaBottom - 8, C_ACCENT);

            y = areaBottom + 2; // boutons d'action ancrés en bas
        } else {
            y += 4;
        }

        // ── Action buttons ────────────────────────────────────────────────────
        raidActionY = y;
        if (ClientRaidCache.isLeader) {
            boolean hD = mx >= cx && mx < cx + 68 && my >= y && my < y + 12;
            g.fill(cx, y, cx + 68, y + 12, hD ? 0xFF661111 : 0xFF330000);
            border(g, cx, y, 68, 12, 0xFF991111);
            g.drawCenteredString(this.font, t("donjonmc.raid.disband"), cx + 34, y + 2, 0xFFFFAAAA);

            if (ClientRaidCache.hasHistory) {
                raidRematchInGroupX = cx + 78;
                int rx = raidRematchInGroupX;
                boolean hR = mx >= rx && mx < rx + 80 && my >= y && my < y + 12;
                g.fill(rx, y, rx + 80, y + 12, hR ? C_PLUS_H : C_PLUS);
                border(g, rx, y, 80, 12, 0xFF145A32);
                g.drawCenteredString(this.font, t("donjonmc.raid.rematch"), rx + 40, y + 2, 0xFFFFFFFF);
            }
        } else {
            boolean hL = mx >= cx && mx < cx + 60 && my >= y && my < y + 12;
            g.fill(cx, y, cx + 60, y + 12, hL ? 0xFF553300 : 0xFF331100);
            border(g, cx, y, 60, 12, 0xFF885500);
            g.drawCenteredString(this.font, t("donjonmc.raid.leave"), cx + 30, y + 2, 0xFFFFCC88);
        }
    }

    // ─── Entrées ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        int tw = (W - 2) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = left + 1 + i * tw;
            int tW = (i == TAB_COUNT - 1) ? (W - 2 - tw * (TAB_COUNT - 1)) : tw;
            int ty = top + TAB_Y;
            if (mx >= tx && mx < tx + tW && my >= ty && my < ty + TAB_H) {
                activeTab = i;
                return true;
            }
        }

        if (activeTab == 2) {
            int cx = left + 12;
            int btnW = W - 24;
            if (questHudToggleY >= 0
                    && my >= questHudToggleY && my < questHudToggleY + 13
                    && mx >= cx && mx < cx + btnW) {
                ClientDailyQuestCache.hudVisible = !ClientDailyQuestCache.hudVisible;
                return true;
            }
        }

        if (activeTab == 1) {
            int cx = left + 12;
            int btnW = W - 24;
            if (ClientPlayerDataCache.skillPoints > 0) {
                for (StatType stat : StatType.values()) {
                    if (ClientPlayerDataCache.getStat(stat) >= StatType.MAX) continue; // au plafond, pas de +
                    int ry = top + STATS_FIRST_Y + stat.ordinal() * STATS_ROW_H;
                    int bx = left + 148, by = ry - 1;
                    if (mx >= bx && mx < bx + 14 && my >= by && my < by + 11) {
                        PacketDistributor.sendToServer(new SpendSkillPointPacket(stat));
                        return true;
                    }
                }
            }
            if (speedToggleY >= 0 && my >= speedToggleY && my < speedToggleY + 13
                    && mx >= cx && mx < cx + btnW) {
                PacketDistributor.sendToServer(new ToggleSpeedPacket());
                return true;
            }
            if (perceptionToggleY >= 0 && my >= perceptionToggleY && my < perceptionToggleY + 13
                    && mx >= cx && mx < cx + btnW) {
                PacketDistributor.sendToServer(new TogglePerceptionPacket());
                return true;
            }
            if (respecBtnY >= 0 && my >= respecBtnY && my < respecBtnY + 13
                    && mx >= cx && mx < cx + btnW) {
                PacketDistributor.sendToServer(new RespecStatsPacket());
                return true;
            }
        }

        if (activeTab == 3 && ClientPlayerDataCache.playerClass() == PlayerClass.NONE) {
            // Sélection d'une carte de classe
            for (int i = 0; i < CHOOSABLE.length; i++) {
                int bx = cardX(i), by = cardY(i);
                if (mx >= bx && mx < bx + cardW() && my >= by && my < by + CARD_H) {
                    selectedClassOrdinal = CHOOSABLE[i].ordinal();
                    return true;
                }
            }
            // Bouton "Commencer l'épreuve"
            int cx = left + 12, btnW = W - 24, by = trialBtnY();
            if (mx >= cx && mx < cx + btnW && my >= by && my < by + 15) {
                boolean enabled = ClientPlayerDataCache.level >= 50
                    && trialCooldownRemainingMs() <= 0
                    && selectedClassOrdinal > 0;
                if (enabled) {
                    PacketDistributor.sendToServer(new ChooseClassPacket(selectedClassOrdinal));
                    onClose();
                }
                return true;
            }
        }

        if (activeTab == 4) {
            int cx = left + 12;
            if (!ClientRaidCache.inGroup) {
                if (raidAcceptY >= 0 && my >= raidAcceptY && my < raidAcceptY + 14
                        && mx >= cx && mx < cx + 68) {
                    PacketDistributor.sendToServer(RaidActionPacket.accept()); return true;
                }
                if (raidDeclineY >= 0 && my >= raidDeclineY && my < raidDeclineY + 14
                        && mx >= cx + 76 && mx < cx + 144) {
                    PacketDistributor.sendToServer(RaidActionPacket.decline()); return true;
                }
                if (raidCreateY >= 0 && my >= raidCreateY && my < raidCreateY + 14
                        && mx >= cx && mx < cx + 90) {
                    PacketDistributor.sendToServer(RaidActionPacket.create()); return true;
                }
                if (raidRematchY >= 0 && my >= raidRematchY && my < raidRematchY + 14
                        && mx >= cx && mx < cx + 110) {
                    PacketDistributor.sendToServer(RaidActionPacket.rematch()); return true;
                }
            } else {
                // Role buttons
                RaidRole[] sel = { RaidRole.VANGUARD, RaidRole.STRIKER, RaidRole.SUPPORT, RaidRole.PORTER };
                int bw = (W - 24) / 4;
                if (raidRoleY >= 0 && my >= raidRoleY && my < raidRoleY + 12) {
                    for (int i = 0; i < sel.length; i++) {
                        int bx = cx + i * bw;
                        if (mx >= bx && mx < bx + bw - 2) {
                            PacketDistributor.sendToServer(RaidActionPacket.setRole(sel[i]));
                            return true;
                        }
                    }
                }
                // Invite buttons
                int ibx = left + W - 12 - 40;
                for (int i = 0; i < raidInviteY.size(); i++) {
                    int iy = raidInviteY.get(i);
                    if (mx >= ibx && mx < ibx + 40 && my >= iy && my < iy + 10) {
                        PacketDistributor.sendToServer(
                            new neoforge.donjonmc.network.RaidInviteByNamePacket(raidInviteNames.get(i)));
                        return true;
                    }
                }
                // Action buttons
                if (raidActionY >= 0 && my >= raidActionY && my < raidActionY + 12) {
                    if (ClientRaidCache.isLeader) {
                        if (mx >= cx && mx < cx + 68) {
                            PacketDistributor.sendToServer(RaidActionPacket.disband()); return true;
                        }
                        if (raidRematchInGroupX >= 0 && mx >= raidRematchInGroupX
                                && mx < raidRematchInGroupX + 80) {
                            PacketDistributor.sendToServer(RaidActionPacket.rematch()); return true;
                        }
                    } else if (mx >= cx && mx < cx + 60) {
                        PacketDistributor.sendToServer(RaidActionPacket.leave()); return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // Défilement de la liste d'invitation (onglet Raid)
        if (activeTab == 4 && raidInviteAreaTop >= 0
                && my >= raidInviteAreaTop && my <= raidInviteAreaBottom) {
            if (dy > 0)      raidInviteScroll--;
            else if (dy < 0) raidInviteScroll++;
            if (raidInviteScroll < 0) raidInviteScroll = 0;
            return true; // le clamp haut est appliqué au rendu
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ─── Utilitaires ─────────────────────────────────────────────────────────

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color);
        g.fill(x,         y + h - 1, x + w,     y + h,     color);
        g.fill(x,         y,         x + 1,     y + h,     color);
        g.fill(x + w - 1, y,         x + w,     y + h,     color);
    }

    /** Bordure cyan avec lueur externe (effet « système »). */
    private void glowBorder(GuiGraphics g, int x, int y, int w, int h) {
        border(g, x - 1, y - 1, w + 2, h + 2, C_GLOW); // halo
        border(g, x, y, w, h, C_BORDER);               // trait net
    }

    /** Accents cyan en L aux 4 coins du panneau. */
    private void corners(GuiGraphics g, int x, int y, int w, int h) {
        int n = 6; // longueur des branches
        // haut-gauche
        g.fill(x, y, x + n, y + 1, C_ACCENT);
        g.fill(x, y, x + 1, y + n, C_ACCENT);
        // haut-droite
        g.fill(x + w - n, y, x + w, y + 1, C_ACCENT);
        g.fill(x + w - 1, y, x + w, y + n, C_ACCENT);
        // bas-gauche
        g.fill(x, y + h - 1, x + n, y + h, C_ACCENT);
        g.fill(x, y + h - n, x + 1, y + h, C_ACCENT);
        // bas-droite
        g.fill(x + w - n, y + h - 1, x + w, y + h, C_ACCENT);
        g.fill(x + w - 1, y + h - n, x + w, y + h, C_ACCENT);
    }

    /** Barre de progression avec dégradé vertical + liseré de lueur. */
    private void glowBar(GuiGraphics g, int x, int y, int w, int h, double frac, int fgTop, int fgBot) {
        g.fill(x, y, x + w, y + h, C_XP_BG);
        int filled = (int) (w * Math.max(0.0, Math.min(1.0, frac)));
        if (filled > 0) {
            g.fillGradient(x, y, x + filled, y + h, fgTop, fgBot);
            g.fill(x, y, x + filled, y + 1, fgTop); // liseré lumineux en haut
        }
        border(g, x, y, w, h, C_BORDER);
    }
}
