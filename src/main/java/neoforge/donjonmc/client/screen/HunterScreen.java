package neoforge.donjonmc.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.client.ClientDailyQuestCache;
import neoforge.donjonmc.client.ClientPlayerDataCache;
import neoforge.donjonmc.client.ClientRaidCache;
import neoforge.donjonmc.dungeon.DungeonRank;
import neoforge.donjonmc.network.RaidActionPacket;
import neoforge.donjonmc.network.SpendSkillPointPacket;
import neoforge.donjonmc.network.ToggleSpeedPacket;
import neoforge.donjonmc.player.LevelHelper;
import neoforge.donjonmc.player.PlayerClass;
import neoforge.donjonmc.player.StatType;
import neoforge.donjonmc.raid.RaidRole;

public class HunterScreen extends Screen {

    // Dimensions du panneau
    private static final int W = 280;
    private static final int H = 220;

    // Layout
    private static final int TAB_Y         = 21;
    private static final int TAB_H         = 18;
    private static final int CONTENT_Y     = TAB_Y + TAB_H + 4; // = 43
    private static final int STATS_HEADER_Y = CONTENT_Y + 6;    // = 49
    private static final int STATS_FIRST_Y  = STATS_HEADER_Y + 24; // = 73
    private static final int STATS_ROW_H   = 14;

    // Palette — "Système" Solo Leveling (bleu nuit + glow cyan)
    private static final int C_BG        = 0xC0000814; // overlay écran
    private static final int C_PANEL_TOP = 0xF00B1730; // dégradé panneau (haut)
    private static final int C_PANEL_BOT = 0xF005091A; // dégradé panneau (bas)
    private static final int C_PANEL     = 0xF00A1428; // aplat de secours
    private static final int C_BORDER    = 0xFF2FD8FF; // bordure cyan vive
    private static final int C_GLOW      = 0x402FD8FF; // lueur cyan (alpha bas)
    private static final int C_TAB_ON    = 0xFF0E2A44;
    private static final int C_TAB_OFF   = 0xFF0A1626;
    private static final int C_TAB_HOV   = 0xFF123A5C;
    private static final int C_XP_BG     = 0xFF081626;
    private static final int C_XP_FG     = 0xFF1E9AD0; // bas du dégradé de barre
    private static final int C_XP_FG2    = 0xFF7BE9FF; // haut du dégradé de barre
    private static final int C_TEXT      = 0xFFE6F4FF;
    private static final int C_DIM       = 0xFF6E8CA8;
    private static final int C_GOLD      = 0xFFFFD86B;
    private static final int C_ACCENT    = 0xFF4FE3FF; // cyan accent (titre, soulignés)
    private static final int C_PLUS      = 0xFF0E5A6E;
    private static final int C_PLUS_H    = 0xFF1A86A8;

    private int activeTab = 0;
    private int left, top;

    // Stats tab click zone
    private int speedToggleY = -1;

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

        // Panneau principal : dégradé bleu nuit + lueur + bordure cyan + accents de coin
        g.fillGradient(left, top, left + W, top + H, C_PANEL_TOP, C_PANEL_BOT);
        glowBorder(g, left, top, W, H);
        corners(g, left, top, W, H);

        // Titre "Système" avec losange
        g.drawCenteredString(this.font, "◈ " + t("donjonmc.gui.title"), left + W / 2, top + 9, C_ACCENT);
        g.fill(left + 1, top + 19, left + W - 1, top + 20, C_BORDER);

        renderTabs(g, mx, my);
        g.fill(left + 1, top + CONTENT_Y - 2, left + W - 1, top + CONTENT_Y - 1, C_BORDER);

        switch (activeTab) {
            case 0 -> renderProfile(g);
            case 1 -> renderStats(g, mx, my);
            case 2 -> renderQuests(g);
            case 3 -> renderClasse(g);
            case 4 -> renderRaid(g, mx, my);
        }
    }

    private static final int TAB_COUNT = 5;

    private void renderTabs(GuiGraphics g, int mx, int my) {
        String[] labels = {
            t("donjonmc.gui.tab.profil"),
            t("donjonmc.gui.tab.stats"),
            t("donjonmc.gui.tab.quetes"),
            t("donjonmc.gui.tab.classe"),
            t("donjonmc.gui.tab.raid")
        };
        int tw = (W - 2) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            int tx = left + 1 + i * tw;
            int tW = (i == TAB_COUNT - 1) ? (W - 2 - tw * (TAB_COUNT - 1)) : tw;
            int ty = top + TAB_Y;
            boolean on  = activeTab == i;
            boolean hov = mx >= tx && mx < tx + tW && my >= ty && my < ty + TAB_H;
            g.fill(tx, ty, tx + tW, ty + TAB_H, on ? C_TAB_ON : (hov ? C_TAB_HOV : C_TAB_OFF));
            if (on) g.fill(tx, ty + TAB_H - 1, tx + tW, ty + TAB_H, C_ACCENT);
            g.drawCenteredString(this.font, labels[i], tx + tW / 2, ty + 5, on ? C_TEXT : C_DIM);
        }
    }

    private void renderProfile(GuiGraphics g) {
        String name = minecraft.player != null ? minecraft.player.getName().getString() : "?";
        int  lvl    = ClientPlayerDataCache.level;
        long xp     = ClientPlayerDataCache.xp;
        long xpMax  = ClientPlayerDataCache.xpRequired;
        int  sp     = ClientPlayerDataCache.skillPoints;
        String rank = LevelHelper.rankForLevel(lvl);
        int rankCol = LevelHelper.rankColor(lvl);

        int y  = top + CONTENT_Y + 6;
        int cx = left + 12;

        // Nom | Rang
        String hunterStr = t("donjonmc.gui.profile.hunter") + name;
        String rankStr   = t("donjonmc.gui.profile.rank") + rank;
        g.drawString(this.font, hunterStr, cx, y, C_TEXT);
        g.drawString(this.font, rankStr, left + W - 12 - this.font.width(rankStr), y, rankCol);

        // Niveau
        y += 16;
        String levelLabel = t("donjonmc.gui.profile.level");
        g.drawString(this.font, levelLabel, cx, y, C_DIM);
        g.drawString(this.font, String.valueOf(lvl), cx + this.font.width(levelLabel), y, C_GOLD);

        // Barre XP
        y += 18;
        g.drawString(this.font, t("donjonmc.gui.profile.xp"), cx, y, C_DIM);
        y += 11;
        int barX = cx, barW = W - 24;
        double xpFrac = xpMax > 0 ? (double) Math.min(xp, xpMax) / xpMax : 0;
        glowBar(g, barX, y, barW, 8, xpFrac, C_XP_FG2, C_XP_FG);
        y += 11;
        g.drawCenteredString(this.font, xp + " / " + xpMax, left + W / 2, y, C_DIM);

        // Classe
        y += 18;
        PlayerClass cls   = ClientPlayerDataCache.playerClass();
        String clsLabel   = t("donjonmc.gui.profile.class");
        String clsName    = Component.translatable(cls.nameLangKey()).getString();
        g.drawString(this.font, clsLabel, cx, y, C_DIM);
        g.drawString(this.font, clsName, cx + this.font.width(clsLabel), y, cls.color);

        // Points de compétence
        y += 14;
        String spLabel = t("donjonmc.gui.profile.sp");
        g.drawString(this.font, spLabel, cx, y, C_DIM);
        g.drawString(this.font, String.valueOf(sp),
            cx + this.font.width(spLabel), y, sp > 0 ? C_GOLD : C_TEXT);
    }

    private void renderStats(GuiGraphics g, int mx, int my) {
        speedToggleY = -1;
        int sp = ClientPlayerDataCache.skillPoints;
        int y  = top + STATS_HEADER_Y;
        int cx = left + 12;

        String spLabel = t("donjonmc.gui.stats.available");
        g.drawString(this.font, spLabel, cx, y, C_DIM);
        g.drawString(this.font, String.valueOf(sp),
            cx + this.font.width(spLabel), y, sp > 0 ? C_GOLD : C_TEXT);

        y += 16;
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);

        for (StatType stat : StatType.values()) {
            int ry = top + STATS_FIRST_Y + stat.ordinal() * STATS_ROW_H;
            String statName = Component.translatable(stat.langKey()).getString();
            g.drawString(this.font, statName, cx + 3, ry, C_TEXT);
            g.drawString(this.font,
                String.valueOf(ClientPlayerDataCache.getStat(stat)), left + 130, ry, C_GOLD);

            if (sp > 0) {
                int bx  = left + 148;
                int by  = ry - 1;
                boolean hov = mx >= bx && mx < bx + 14 && my >= by && my < by + 11;
                g.fill(bx, by, bx + 14, by + 11, hov ? C_PLUS_H : C_PLUS);
                border(g, bx, by, 14, 11, 0xFF145A32);
                g.drawCenteredString(this.font, "+", bx + 7, by + 1, 0xFFFFFFFF);
            }
        }

        // ── Toggle vitesse Agilité ────────────────────────────────────────────
        int btnW = W - 24;
        int toggleY = top + STATS_FIRST_Y + StatType.values().length * STATS_ROW_H + 8;
        g.fill(cx, toggleY - 4, cx + btnW, toggleY - 3, C_BORDER);

        boolean speedOn = ClientPlayerDataCache.speedEnabled;
        boolean hSpd = mx >= cx && mx < cx + btnW && my >= toggleY && my < toggleY + 13;
        int bgOn  = hSpd ? 0xFF1A7A2A : C_PLUS;
        int bgOff = hSpd ? 0xFF661111 : 0xFF330000;
        g.fill(cx, toggleY, cx + btnW, toggleY + 13, speedOn ? bgOn : bgOff);
        border(g, cx, toggleY, btnW, 13, speedOn ? 0xFF145A32 : 0xFF991111);

        String label = t("donjonmc.gui.stats.speed_toggle") + " : "
            + (speedOn ? t("donjonmc.gui.stats.speed_on") : t("donjonmc.gui.stats.speed_off"));
        g.drawCenteredString(this.font, label, cx + btnW / 2, toggleY + 3,
            speedOn ? 0xFFAAFFAA : 0xFFFFAAAA);
        speedToggleY = toggleY;
    }

    private void renderQuests(GuiGraphics g) {
        questHudToggleY = -1;
        int cx = left + 12;
        int y  = top + CONTENT_Y + 6;

        // Titre + timer ou statut
        g.drawCenteredString(this.font, t("donjonmc.gui.quests.title"), left + W / 2, y, C_TEXT);
        y += 14;
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
        y += 6;
        questHudToggleY = y;
        boolean hudOn = ClientDailyQuestCache.hudVisible;
        boolean hov   = false; // hover géré par mouseClicked
        int btnW = W - 24;
        g.fill(cx, y, cx + btnW, y + 13,
            hudOn ? C_PLUS : 0xFF330000);
        border(g, cx, y, btnW, 13,
            hudOn ? 0xFF145A32 : 0xFF991111);
        String btnLabel = t(hudOn ? "donjonmc.gui.quests.hud_hide" : "donjonmc.gui.quests.hud_show");
        g.drawCenteredString(this.font, btnLabel, cx + btnW / 2, y + 3,
            hudOn ? 0xFFAAFFAA : 0xFFFFAAAA);
    }

    private void renderClasse(GuiGraphics g) {
        PlayerClass cls    = ClientPlayerDataCache.playerClass();
        String clsName     = Component.translatable(cls.nameLangKey()).getString();
        String clsDesc     = Component.translatable(cls.descLangKey()).getString();
        int cx = left + 12;
        int y  = top + CONTENT_Y + 6;

        // Indicateur coloré + nom
        g.fill(cx, y, cx + 6, y + this.font.lineHeight, cls.color);
        g.drawString(this.font, clsName, cx + 10, y, cls.color);
        y += 14;
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);

        // Description (multiligne)
        y += 8;
        for (net.minecraft.util.FormattedCharSequence line :
                this.font.split(Component.literal(clsDesc), W - 28)) {
            g.drawString(this.font, line, cx, y, C_DIM);
            y += this.font.lineHeight + 1;
        }

        // Bonus de classe
        y += 6;
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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
                g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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
        g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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

            g.fill(cx, y, left + W - 12, y + 1, C_BORDER);
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
