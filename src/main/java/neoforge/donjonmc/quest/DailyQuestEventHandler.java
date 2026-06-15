package neoforge.donjonmc.quest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.DungeonManager;
import neoforge.donjonmc.dungeon.mob.DungeonMob;

import java.util.Set;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class DailyQuestEventHandler {

    private DailyQuestEventHandler() {}

    private static final Set<String> IRON_ORES    = Set.of("iron_ore", "deepslate_iron_ore");
    private static final Set<String> GOLD_ORES    = Set.of("gold_ore", "deepslate_gold_ore", "nether_gold_ore");
    private static final Set<String> DIAMOND_ORES = Set.of("diamond_ore", "deepslate_diamond_ore");
    private static final Set<String> STONE_BLOCKS = Set.of(
        "stone", "cobblestone", "deepslate", "cobbled_deepslate", "granite", "diorite",
        "andesite", "tuff", "calcite", "dripstone_block", "blackstone", "basalt",
        "netherrack", "end_stone");

    // ── Server tick ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        DailyQuestManager.getInstance().onServerTick(event.getServer());
    }

    // ── Login / Logout ────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide()) return;
        DailyQuestManager mgr = DailyQuestManager.getInstance();
        mgr.assignIfNeeded(sp);
        mgr.syncOnLogin(sp);
        UniqueQuestManager.getInstance().syncToPlayer(sp);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DailyQuestManager.getInstance().onPlayerLogout(sp);
    }

    // ── Player tick (movement / depth) ────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DailyQuestManager.getInstance().onPlayerTick(sp);
    }

    // ── Kills ─────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity killed = event.getEntity();

        if (killed instanceof ServerPlayer sp) {
            if (!sp.level().dimension().equals(DungeonManager.DUNGEON_DIMENSION)) {
                DailyQuestManager.getInstance().onPlayerDied(sp);
            }
            return;
        }

        ServerPlayer player = neoforge.donjonmc.player.PlayerEventHandler.killerPlayer(event);
        if (player == null) return;
        if (killed instanceof DungeonMob) return; // handled by DungeonMobKilled hook
        if (!(killed instanceof Monster)) return;

        Level level = killed.level();
        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());
        String mobId = typeKey != null ? typeKey.getPath() : "unknown";

        DailyQuestManager mgr = DailyQuestManager.getInstance();

        if ("creeper".equals(mobId)) {
            mgr.onCreeperKilled(player);
        }

        // General kill (matchesFilter handles "any" vs specific)
        mgr.onProgress(player, QuestType.KILL_MOB, 1, mobId);
        mgr.onKillStreak(player);

        // Night kill (overworld only)
        long dayTime = level.getDayTime() % 24000L;
        boolean isNight = dayTime >= 13000L && dayTime < 23000L;
        if (isNight && level.dimension().equals(Level.OVERWORLD)) {
            mgr.onProgress(player, QuestType.KILL_AT_NIGHT, 1, "any");
        }

        // Bare-handed
        if (player.getMainHandItem().isEmpty()) {
            mgr.onProgress(player, QuestType.KILL_BAREHANDED, 1, "any");
        }

        // Projectile kill (direct entity is not a player)
        if (!(event.getSource().getDirectEntity() instanceof Player)
                && event.getSource().getDirectEntity() != null) {
            mgr.onProgress(player, QuestType.KILL_PROJECTILE, 1, "any");
        }

        // Kill at depth (overworld, below Y = -40)
        if (level.dimension().equals(Level.OVERWORLD) && player.getY() <= -40) {
            mgr.onProgress(player, QuestType.KILL_AT_DEPTH, 1, "any");
        }
    }

    // ── Called externally from PlayerEventHandler / DungeonManager ────────────

    public static void onDungeonMobKilled(ServerPlayer player, DungeonMob mob) {
        DailyQuestManager mgr = DailyQuestManager.getInstance();
        mgr.onProgress(player, QuestType.KILL_IN_DUNGEON, 1, "any");
        mgr.onKillStreak(player);
    }

    public static void onDungeonBossKilled(ServerPlayer player) {
        DailyQuestManager.getInstance().onProgress(player, QuestType.KILL_BOSS, 1, "any");
        UniqueQuestManager.getInstance().onBossKilled(player);
    }

    // ── Explosion damage (creeper streak reset) ───────────────────────────────

    @SubscribeEvent
    public static void onPlayerDamaged(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;
        DailyQuestManager.getInstance().onExplosionDamage(player);
    }

    // ── Heal ──────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int hp = (int) event.getAmount();
        if (hp <= 0) return;
        DailyQuestManager.getInstance().onProgress(player, QuestType.HEAL_HP, hp, "any");
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (event.isCanceled()) return;

        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
        String blockId = blockKey != null ? blockKey.getPath() : "";

        DailyQuestManager mgr = DailyQuestManager.getInstance();

        if (blockId.endsWith("_log")) {
            mgr.onProgress(player, QuestType.HARVEST_LOG, 1, "any");
            return;
        }

        if (blockId.endsWith("_ore")) {
            // Determine specific ore type; "other" won't match specific filter quests but matches "any"
            String oreFilter;
            if (IRON_ORES.contains(blockId))         oreFilter = "iron";
            else if (GOLD_ORES.contains(blockId))    oreFilter = "gold";
            else if (DIAMOND_ORES.contains(blockId)) oreFilter = "diamond";
            else                                      oreFilter = "other";
            // Single call: matchesFilter("any", oreFilter)=true, matchesFilter("iron", "iron")=true, etc.
            mgr.onProgress(player, QuestType.MINE_ORE, 1, oreFilter);
            return;
        }

        if (STONE_BLOCKS.contains(blockId)) {
            mgr.onProgress(player, QuestType.MINE_STONE, 1, "any");
        }
    }

    // ── Fishing ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getDrops().isEmpty()) return;
        DailyQuestManager.getInstance().onProgress(player, QuestType.FISH, 1, "any");
    }

    // ── Eating ───────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onItemUsed(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!event.getItem().has(DataComponents.FOOD)) return;
        DailyQuestManager.getInstance().onProgress(player, QuestType.EAT_FOOD, 1, "any");
    }

    // ── Crafting ──────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack result = event.getCrafting();
        if (result.isEmpty()) return;

        ResourceLocation itemKey = BuiltInRegistries.ITEM.getKey(result.getItem());
        String itemId = itemKey != null ? itemKey.getPath() : "";

        DailyQuestManager mgr = DailyQuestManager.getInstance();

        boolean isIronPlus = itemId.startsWith("iron_") || itemId.startsWith("golden_")
                          || itemId.startsWith("diamond_") || itemId.startsWith("netherite_");
        if (isIronPlus) mgr.onProgress(player, QuestType.CRAFT_ITEM, 1, "iron_plus");

        // Note : les potions ne passent PAS par ItemCraftedEvent (elles sont
        // brassées à l'alambic) → gérées dans onPotionBrewed ci-dessous.
    }

    // ── Brassage de potions (alambic) ─────────────────────────────────────────
    // Les potions ne sont pas « craftées » mais brassées : ItemCraftedEvent ne
    // se déclenche jamais pour elles. PlayerBrewedPotionEvent est l'event correct.

    @SubscribeEvent
    public static void onPotionBrewed(PlayerBrewedPotionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getStack();
        boolean isPotion = stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION)
                        || stack.is(Items.LINGERING_POTION);
        if (!isPotion) return;
        DailyQuestManager.getInstance().onProgress(player, QuestType.CRAFT_ITEM, 1, "potion");
    }

    // ── Troc avec un villageois ────────────────────────────────────────────────

    @SubscribeEvent
    public static void onVillagerTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DailyQuestManager.getInstance().onProgress(player, QuestType.TRADE_VILLAGER, 1, "any");
    }

    // ── Élevage d'animaux ──────────────────────────────────────────────────────
    // getCausedByPlayer() = le joueur qui a nourri les deux parents (peut être null
    // pour une reproduction naturelle, qu'on ignore).

    @SubscribeEvent
    public static void onAnimalBred(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) return;
        DailyQuestManager.getInstance().onProgress(player, QuestType.BREED_ANIMAL, 1, "any");
    }
}
