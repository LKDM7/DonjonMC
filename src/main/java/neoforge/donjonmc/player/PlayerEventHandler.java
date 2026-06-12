package neoforge.donjonmc.player;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import neoforge.donjonmc.dungeon.DungeonRank;
import neoforge.donjonmc.dungeon.mob.DungeonMob;
import neoforge.donjonmc.punishment.SandWormEntity;
import neoforge.donjonmc.quest.DailyQuestEventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import neoforge.donjonmc.Config;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.network.SyncPlayerDataPacket;
import neoforge.donjonmc.spell.SpellUnlockHandler;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class PlayerEventHandler {

    private PlayerEventHandler() {}

    // ── IDs des modificateurs d'attributs ──────────────────────────────────────
    private static final ResourceLocation HEALTH_LEVEL_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "level_health_bonus");
    private static final ResourceLocation HEALTH_MODIFIER_ID = HEALTH_LEVEL_MOD;
    private static final ResourceLocation DAMAGE_STAT_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "stat_attack_bonus");
    private static final ResourceLocation SPEED_STAT_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "stat_speed_bonus");
    private static final ResourceLocation HEALTH_STAT_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "stat_health_bonus");

    // Modificateurs de classe
    private static final ResourceLocation CLASS_HEALTH_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "class_health_bonus");
    private static final ResourceLocation CLASS_DAMAGE_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "class_damage_bonus");
    private static final ResourceLocation CLASS_SPEED_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "class_speed_bonus");

    // Modificateurs de mana (Iron's Spells 'n Spellbooks MAX_MANA attribute)
    private static final ResourceLocation MANA_INTEL_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "intel_mana_bonus");
    private static final ResourceLocation MANA_CLASS_MOD =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "class_mana_bonus");

    // ── Coefficients par point de statistique ──────────────────────────────────
    // Force : 30 pts = +2.5 dégâts → 1/12 par point
    public static final double STRENGTH_PER_POINT = 1.0 / 12.0;
    // Agilité : 30 pts = Speed I (+0.02) → 1/1500 par point
    public static final double AGILITY_PER_POINT  = 1.0 / 1500.0;
    // Vitalité : 25 pts = +10 cœurs (+20 PV) → 0.8 PV par point
    public static final double VITALITY_PER_POINT = 0.8;

    private static long dungeonMobXp(DungeonMob mob) {
        if (mob.getPersistentData().getBoolean("dungeon_boss")) return 0L; // handled by onBossKilled
        int rankOrd = mob.getPersistentData().getInt("dungeon_rank_ord");
        return switch (DungeonRank.fromOrdinal(rankOrd)) {
            case D -> 16L;
            case C -> 40L;
            case B -> 100L;
            case A -> 240L;
            case S -> 600L;
        };
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);

        if (!data.isInitialized()) {
            data.setInitialized(true);

            // Rang de départ aléatoire : E (72%), D (20%), C (8%)
            // D : niveaux 1-5, C : niveaux 6-10, points = niveau × 2 (identique au level-up)
            java.util.Random rng = new java.util.Random(
                player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits());
            int roll = rng.nextInt(100);
            int startLevel;
            if      (roll < 8)  startLevel = rng.nextInt(5) + 6; // C : 6-10
            else if (roll < 28) startLevel = rng.nextInt(5) + 1; // D : 1-5
            else                startLevel = 0;                    // E

            data.setLevel(startLevel);
            data.addSkillPoints(startLevel * 2);
            player.setData(ModAttachments.PLAYER_DATA, data);

            player.sendSystemMessage(Component.translatable("donjonmc.system.first_join"));
            player.sendSystemMessage(Component.translatable("donjonmc.system.rank_assessed",
                LevelHelper.rankForLevel(startLevel)));
        }

        applyHealthModifier(player, data.getLevel());
        applyStatModifiers(player, data);
        applyClassModifiers(player, data);

        // Si le joueur est connecté dans la dimension d'épreuve sans épreuve active, le renvoyer
        if (player instanceof ServerPlayer sp
                && sp.level().dimension().equals(ClassTrialHandler.TRIAL_DIMENSION)
                && !ClassTrialHandler.isInTrial(sp.getUUID())) {
            ServerLevel overworld = sp.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            sp.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                overworld,
                new net.minecraft.world.phys.Vec3(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5),
                net.minecraft.world.phys.Vec3.ZERO, 0f, 0f, false,
                net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING
            ));
        }

        if (player instanceof ServerPlayer sp) {
            RankTeamManager.updatePlayerTeam(sp);
            sendSyncPacket(sp);
        }
    }

    // Les modificateurs transients sont perdus lors d'un Clone (mort, portail de l'End).
    // copyOnDeath() sur l'attachment préserve les données ; on réapplique tous les modificateurs.
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        applyHealthModifier(player, data.getLevel());
        applyStatModifiers(player, data);
        applyClassModifiers(player, data);
    }

    /**
     * Retrouve le joueur responsable d'un kill. Couvre les cas où la source de dégâts
     * n'est pas directement le joueur : sorts à projectile ou zone d'effet, invocations
     * (TraceableEntity → propriétaire), et dégâts indirects type poison/brûlure via le
     * crédit de kill vanilla (dernier joueur ayant frappé dans les ~5 dernières secondes).
     */
    public static ServerPlayer killerPlayer(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer sp) return sp;
        if (event.getSource().getDirectEntity() instanceof TraceableEntity te
                && te.getOwner() instanceof ServerPlayer sp) return sp;
        if (event.getEntity().getKillCredit() instanceof ServerPlayer sp) return sp;
        return null;
    }

    @SubscribeEvent
    public static void onMobDeath(LivingDeathEvent event) {
        LivingEntity killed = event.getEntity();

        // Mort du joueur dans la dimension d'épreuve → nettoie le trial
        if (killed instanceof ServerPlayer sp) {
            ClassTrialHandler.onPlayerDeathInTrial(sp);
            return;
        }

        ServerPlayer player = killerPlayer(event);
        if (player == null) return;

        // Quête unique : compteur de monstres tués (tout monstre hostile)
        if (killed instanceof Monster) {
            neoforge.donjonmc.quest.UniqueQuestManager.getInstance().onMobKilled(player);
        }

        // Monstre de l'épreuve → pas d'XP de mod
        if (ClassTrialHandler.onMobKilled(killed, player)) return;

        if (!(killed instanceof DungeonMob dm)) return;

        // Mob de punition (Sand Worm) → XP fixe selon niveau du joueur
        if (dm instanceof SandWormEntity) {
            int playerLevel = dm.getPersistentData().getInt("punishment_player_level");
            long xp = 30L + playerLevel; // 30 à 80 XP
            DailyQuestEventHandler.onDungeonMobKilled(player, dm);
            addXp(player, xp);
            return;
        }

        // Seuls les mobs de donjon (avec instance_id) donnent de l'XP de niveau du mod
        if (!dm.getPersistentData().contains("instance_id")) return;

        // Notification quête donjon + kill count
        int instanceId = dm.getPersistentData().getInt("instance_id");
        if (dm.getPersistentData().getBoolean("dungeon_boss")) {
            DailyQuestEventHandler.onDungeonBossKilled(player);
        } else {
            DailyQuestEventHandler.onDungeonMobKilled(player, dm);
            neoforge.donjonmc.dungeon.DungeonManager.getInstance().addDungeonKill(instanceId);
        }

        long xp = dungeonMobXp(dm);
        if (xp <= 0L) return;
        addXp(player, xp);
    }

    // ── XP de minage (minerais rares) ──────────────────────────────────────────

    /** Minerais précieux qui donnent de l'XP de mod quand ils sont minés, et leur valeur. */
    private static final java.util.Map<Block, Long> ORE_XP = java.util.Map.ofEntries(
        java.util.Map.entry(Blocks.DIAMOND_ORE,            33L),
        java.util.Map.entry(Blocks.DEEPSLATE_DIAMOND_ORE,  33L),
        java.util.Map.entry(Blocks.EMERALD_ORE,            40L),
        java.util.Map.entry(Blocks.DEEPSLATE_EMERALD_ORE,  40L),
        java.util.Map.entry(Blocks.ANCIENT_DEBRIS,         50L),
        java.util.Map.entry(Blocks.GOLD_ORE,                8L),
        java.util.Map.entry(Blocks.DEEPSLATE_GOLD_ORE,      8L),
        java.util.Map.entry(Blocks.NETHER_GOLD_ORE,         4L),
        java.util.Map.entry(Blocks.LAPIS_ORE,               5L),
        java.util.Map.entry(Blocks.DEEPSLATE_LAPIS_ORE,     5L)
    );

    @SubscribeEvent
    public static void onOreMined(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (player.getAbilities().instabuild) return; // pas d'XP en créatif
        Block block = event.getState().getBlock();
        Long xp = ORE_XP.get(block);
        if (xp == null) return;
        // Anti-farm : pas d'XP avec la Silk Touch (sinon on replace le minerai à l'infini).
        if (hasSilkTouch(player)) return;
        addXpDirect(player, xp);
        // Quête unique : compteur de diamants minés
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
            neoforge.donjonmc.quest.UniqueQuestManager.getInstance().onDiamondMined(player);
        }
    }

    private static boolean hasSilkTouch(ServerPlayer player) {
        Holder<Enchantment> silk = player.level().registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silk, player.getMainHandItem()) > 0;
    }

    // ── XP de succès (advancements vanilla) ─────────────────────────────────────

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var display = event.getAdvancement().value().display();
        if (display.isEmpty()) return; // ignore les advancements techniques / de recette (sans affichage)
        long xp = switch (display.get().getType()) {
            case TASK      -> 100L;
            case GOAL      -> 300L;
            case CHALLENGE -> 800L;
        };
        addXpDirect(player, xp);
    }

    // ── Respec des stats (joueur) ───────────────────────────────────────────────

    public static final int  RESPEC_COST_LEVELS = 5;

    /** Cooldown du respec en ms (jours configurables dans donjonmc-common.toml). */
    public static long respecCooldownMs() {
        return Config.respecCooldownDays * 24L * 60L * 60L * 1000L;
    }

    /**
     * Tente un respec des stats pour le joueur. Conditions : niveau strictement supérieur au
     * coût (pour rester ≥ 1) et cooldown de 7 jours IRL. Envoie le message approprié au joueur
     * et renvoie {@code true} si le respec a été effectué. Partagé par la commande et le bouton GUI.
     */
    public static boolean tryRespec(ServerPlayer p) {
        PlayerData data = p.getData(ModAttachments.PLAYER_DATA);

        if (data.getLevel() <= RESPEC_COST_LEVELS) {
            p.sendSystemMessage(Component.translatable(
                "donjonmc.cmd.stats.respec.need_level", RESPEC_COST_LEVELS + 1));
            return false;
        }

        long now  = System.currentTimeMillis();
        long last = data.getLastRespecMs();
        if (last > 0L && now - last < respecCooldownMs()) {
            long remaining = respecCooldownMs() - (now - last);
            long days  = remaining / (24L * 60L * 60L * 1000L);
            long hours = (remaining / (60L * 60L * 1000L)) % 24L;
            p.sendSystemMessage(Component.translatable(
                "donjonmc.cmd.stats.respec.cooldown", days, hours));
            return false;
        }

        int refund = data.getStrength() + data.getAgility() + data.getVitality()
            + data.getIntelligence() + data.getPerception();
        data.setStrength(0);
        data.setAgility(0);
        data.setVitality(0);
        data.setIntelligence(0);
        data.setPerception(0);
        data.addSkillPoints(refund);

        data.setLevel(data.getLevel() - RESPEC_COST_LEVELS);
        data.setXp(0);
        data.setLastRespecMs(now);
        p.setData(ModAttachments.PLAYER_DATA, data);

        applyHealthModifier(p, data.getLevel());
        applyStatModifiers(p, data);
        RankTeamManager.updatePlayerTeam(p);
        sendSyncPacket(p);

        p.sendSystemMessage(Component.translatable(
            "donjonmc.cmd.stats.respec.success", refund, RESPEC_COST_LEVELS));
        return true;
    }

    // --- API publique ---

    public static void applyHealthModifier(Player player, int level) {
        var attr = player.getAttribute(Attributes.MAX_HEALTH);
        if (attr == null) return;

        attr.removeModifier(HEALTH_MODIFIER_ID);
        attr.addTransientModifier(new AttributeModifier(
            HEALTH_MODIFIER_ID,
            LevelHelper.healthModifier(level),
            AttributeModifier.Operation.ADD_VALUE
        ));

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /**
     * Applique les modificateurs d'attributs dérivés des statistiques du joueur.
     * Doit être appelé après chaque modification des stats (connexion, dépense de point).
     */
    public static void applyStatModifiers(Player player, PlayerData data) {
        setTransientMod(player, Attributes.ATTACK_DAMAGE,   DAMAGE_STAT_MOD,
            data.getStrength()  * STRENGTH_PER_POINT);
        setTransientMod(player, Attributes.MOVEMENT_SPEED,  SPEED_STAT_MOD,
            data.isSpeedEnabled() ? data.getAgility() * AGILITY_PER_POINT : 0.0);
        setTransientMod(player, Attributes.MAX_HEALTH,      HEALTH_STAT_MOD,
            data.getVitality()  * VITALITY_PER_POINT);

        // Intelligence → MAX_MANA (Iron's Spells 'n Spellbooks)
        // +5 mana par point d'intelligence (max +250 à intel 50)
        setTransientMod(player, AttributeRegistry.MAX_MANA, MANA_INTEL_MOD,
            data.getIntelligence() * 5.0);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void setTransientMod(Player player, Holder<Attribute> attribute,
            ResourceLocation id, double value) {
        var inst = player.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value > 0.0) {
            inst.addTransientModifier(
                new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE)
            );
        }
    }

    /** Donne directement l'XP à UN joueur sans partage de groupe (pour récompenses personnelles). */
    public static void addXpDirect(ServerPlayer player, long amount) {
        giveXpToPlayer(player, amount);
    }

    public static void addXp(ServerPlayer killer, long amount) {
        java.util.List<ServerPlayer> recipients =
            neoforge.donjonmc.raid.RaidManager.getInstance().getNearbyActiveMembers(killer, 30.0);

        long share = recipients.size() > 1 ? amount / recipients.size() : amount;

        int maxTier = 0;
        if (recipients.size() > 1) {
            for (ServerPlayer p : recipients) {
                maxTier = Math.max(maxTier,
                    LevelHelper.rankTier(p.getData(ModAttachments.PLAYER_DATA).getLevel()));
            }
        }

        for (ServerPlayer member : recipients) {
            long memberXp = share;
            if (recipients.size() > 1) {
                int tier = LevelHelper.rankTier(member.getData(ModAttachments.PLAYER_DATA).getLevel());
                if (maxTier - tier >= 2) memberXp = (long)(memberXp * 1.5); // carry bonus
            }
            giveXpToPlayer(member, memberXp);
        }
    }

    private static void giveXpToPlayer(ServerPlayer player, long amount) {
        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
        if (data.getLevel() >= LevelHelper.MAX_LEVEL) return;

        // Quête GAIN_MOD_XP
        neoforge.donjonmc.quest.DailyQuestManager.getInstance().onModXpGained(player, amount);

        int levelBefore = data.getLevel();
        long currentXp  = data.getXp() + amount;
        while (currentXp >= LevelHelper.xpRequired(data.getLevel()) && data.getLevel() < LevelHelper.MAX_LEVEL) {
            currentXp -= LevelHelper.xpRequired(data.getLevel());
            levelUp(player, data);
        }
        data.setXp(currentXp);
        player.setData(ModAttachments.PLAYER_DATA, data);
        sendSyncPacket(player);

        // Quête LEVEL_UP
        if (data.getLevel() > levelBefore) {
            neoforge.donjonmc.quest.DailyQuestManager.getInstance().onLevelUp(player);
            // Quêtes uniques de palier de niveau. Appelé ici (PLAYER_DATA déjà commit) et non
            // dans levelUp() pour éviter une réentrance de giveXpToPlayer via la récompense.
            neoforge.donjonmc.quest.UniqueQuestManager.getInstance().onLevelReached(player, data.getLevel());
        }
    }

    /**
     * Applique les modificateurs d'attributs liés à la classe du joueur.
     * Tank : +20 PV max ; Assassin : +0.05 vitesse, +3 dégâts.
     * Mage et Guérisseur sont gérés via la formule de mana et de régénération.
     */
    public static void applyClassModifiers(Player player, PlayerData data) {
        PlayerClass cls = data.getPlayerClass();
        setTransientMod(player, Attributes.MAX_HEALTH, CLASS_HEALTH_MOD,
            cls == PlayerClass.TANK ? 20.0 : 0.0);
        setTransientMod(player, Attributes.MOVEMENT_SPEED, CLASS_SPEED_MOD,
            cls == PlayerClass.ASSASSIN ? 0.05 : 0.0);
        setTransientMod(player, Attributes.ATTACK_DAMAGE, CLASS_DAMAGE_MOD,
            cls == PlayerClass.ASSASSIN ? 3.0 : 0.0);

        // Mage : +50% MAX_MANA (en flat, calculé sur la base intel courante)
        double mageManaBonus = (cls == PlayerClass.MAGE)
            ? (100.0 + data.getIntelligence() * 5.0) * 0.5
            : 0.0;
        setTransientMod(player, AttributeRegistry.MAX_MANA, MANA_CLASS_MOD, mageManaBonus);

        // Guérisseur : pas de bonus de MAX_MANA mais ×2 régén (géré dans onLivingTick)
    }

    // ── Régénération du mana + sync cooldowns (1×/s) ─────────────────────────

    @SubscribeEvent
    public static void onLivingTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        PlayerData data = player.getData(ModAttachments.PLAYER_DATA);

        // Perception → Glowing sur les monstres hostiles proches (désactivable dans le GUI)
        int perception = data.getPerception();
        if (perception > 0 && data.isPerceptionEnabled()) {
            double radius = 3.0 + perception * 0.5;
            player.level().getEntitiesOfClass(Monster.class,
                player.getBoundingBox().inflate(radius)
            ).forEach(mob -> mob.addEffect(
                new MobEffectInstance(MobEffects.GLOWING, 25, 0, false, false)
            ));
        }

        // Régénération mana via Iron's Spells 'n Spellbooks MagicData
        // 0.67 + 0.054×Intel → regen/s (max ~3.4/s à intel 50) ; ×2 pour le Guérisseur
        MagicData magicData = MagicData.getPlayerMagicData(player);
        float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
        if (magicData.getMana() < maxMana) {
            float rate = 0.67f + data.getIntelligence() * 0.054f;
            if (data.getPlayerClass() == PlayerClass.HEALER) rate *= 2f;
            magicData.addMana(rate);
        }

        sendSyncPacket(player);
    }

    public static void sendSyncPacket(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
            SyncPlayerDataPacket.from(player, player.getData(ModAttachments.PLAYER_DATA)));
    }

    private static void levelUp(ServerPlayer player, PlayerData data) {
        data.setLevel(data.getLevel() + 1);
        data.addSkillPoints(2);

        applyHealthModifier(player, data.getLevel());
        // Ne pas resoigner un joueur en train de mourir : remettre sa vie > 0 pendant
        // l'event de mort le fait passer pour vivant côté serveur → l'écran de mort se
        // bloque (le clic « respawn » est ignoré). Il réapparaîtra à pleine vie de toute façon.
        if (!player.isDeadOrDying()) {
            player.setHealth(player.getMaxHealth());
        }

        player.getActiveEffects().stream()
            .filter(e -> !e.getEffect().value().isBeneficial())
            .map(e -> e.getEffect())
            .toList()
            .forEach(player::removeEffect);

        player.sendSystemMessage(Component.translatable("donjonmc.system.level_up", data.getLevel()));
        player.sendSystemMessage(Component.translatable("donjonmc.system.skill_points", data.getSkillPoints()));
        SpellUnlockHandler.onLevelUp(player, data.getLevel());
        RankTeamManager.updatePlayerTeam(player);
        sendSyncPacket(player);

        // Niveau 50 : le joueur choisit sa classe dans le menu Hunter (onglet Classe)
        if (data.getLevel() == 50 && data.getPlayerClass() == PlayerClass.NONE) {
            player.sendSystemMessage(Component.translatable("donjonmc.trial.choose_class"));
        }
    }
}
