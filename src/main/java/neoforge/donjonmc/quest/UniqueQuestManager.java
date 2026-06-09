package neoforge.donjonmc.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.dungeon.DungeonRank;
import neoforge.donjonmc.network.SyncUniqueQuestPacket;
import neoforge.donjonmc.player.ModAttachments;
import neoforge.donjonmc.player.PlayerEventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Gère les quêtes uniques (succès permanents) : avance la progression depuis les events
 * existants, accorde l'XP une seule fois à la complétion et synchronise le client.
 *
 * <p>L'XP est toujours accordée APRÈS la sauvegarde des données : la récompense peut
 * déclencher un level-up (→ {@link #onLevelReached}) qui relit/réécrit l'attachment, donc
 * on évite tout écrasement de données en sauvegardant d'abord.
 */
public final class UniqueQuestManager {

    private static final UniqueQuestManager INSTANCE = new UniqueQuestManager();
    public static UniqueQuestManager getInstance() { return INSTANCE; }
    private UniqueQuestManager() {}

    // ── Points d'entrée (appelés depuis les sites d'event existants) ────────────

    public void onLevelReached(ServerPlayer player, int level) {
        UniqueQuestData data = player.getData(ModAttachments.UNIQUE_QUEST);
        List<UniqueQuestDef> finished = new ArrayList<>();
        boolean changed = false;
        for (UniqueQuestDef def : UniqueQuestRegistry.ALL) {
            if (def.type() != QuestType.LEVEL_UP || data.isCompleted(def.id())) continue;
            data.setProgress(def.id(), Math.min(level, def.target()));
            changed = true;
            if (level >= def.target()) { data.setCompleted(def.id(), true); finished.add(def); }
        }
        if (changed) commit(player, data, finished);
    }

    public void onBossKilled(ServerPlayer player)   { incr(player, QuestType.KILL_BOSS, "any",     1); }
    public void onMobKilled(ServerPlayer player)    { incr(player, QuestType.KILL_MOB,  "any",     1); }
    public void onDiamondMined(ServerPlayer player) { incr(player, QuestType.MINE_ORE,  "diamond", 1); }

    public void onDungeonCompleted(ServerPlayer player, DungeonRank rank) {
        UniqueQuestData data = player.getData(ModAttachments.UNIQUE_QUEST);
        List<UniqueQuestDef> finished = new ArrayList<>();
        boolean changed = false;
        for (UniqueQuestDef def : UniqueQuestRegistry.ALL) {
            if (def.type() != QuestType.COMPLETE_DUNGEON || data.isCompleted(def.id())) continue;
            boolean eligible = "any".equals(def.filter())
                || rank.ordinal() >= DungeonRank.valueOf(def.filter()).ordinal();
            if (!eligible) continue;
            data.addProgress(def.id(), 1);
            changed = true;
            if (data.getProgress(def.id()) >= def.target()) { data.setCompleted(def.id(), true); finished.add(def); }
        }
        if (changed) commit(player, data, finished);
    }

    private void incr(ServerPlayer player, QuestType type, String filter, int amount) {
        UniqueQuestData data = player.getData(ModAttachments.UNIQUE_QUEST);
        List<UniqueQuestDef> finished = new ArrayList<>();
        boolean changed = false;
        for (UniqueQuestDef def : UniqueQuestRegistry.ALL) {
            if (def.type() != type || !def.filter().equals(filter) || data.isCompleted(def.id())) continue;
            data.addProgress(def.id(), amount);
            changed = true;
            if (data.getProgress(def.id()) >= def.target()) { data.setCompleted(def.id(), true); finished.add(def); }
        }
        if (changed) commit(player, data, finished);
    }

    // ── Sauvegarde + récompenses ────────────────────────────────────────────────

    private void commit(ServerPlayer player, UniqueQuestData data, List<UniqueQuestDef> finished) {
        player.setData(ModAttachments.UNIQUE_QUEST, data);
        syncToPlayer(player);
        for (UniqueQuestDef def : finished) {
            player.sendSystemMessage(Component.translatable("donjonmc.unique.completed",
                Component.translatable(def.nameKey()), def.xpReward()));
            // Peut déclencher un level-up → onLevelReached (relit l'attachment déjà sauvegardé).
            PlayerEventHandler.addXpDirect(player, def.xpReward());
        }
        if (!finished.isEmpty()) syncToPlayer(player);
    }

    public void syncToPlayer(ServerPlayer player) {
        UniqueQuestData data = player.getData(ModAttachments.UNIQUE_QUEST);
        int n = UniqueQuestRegistry.COUNT;
        int[] prog = new int[n];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) {
            prog[i] = data.getProgress(i);
            done[i] = data.isCompleted(i);
        }
        PacketDistributor.sendToPlayer(player, new SyncUniqueQuestPacket(prog, done));
    }
}
