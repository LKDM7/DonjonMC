package neoforge.donjonmc.player;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.DungeonGenerator;
import neoforge.donjonmc.dungeon.DungeonManager;
import neoforge.donjonmc.dungeon.DungeonRank;
import neoforge.donjonmc.punishment.PunishmentManager;
import neoforge.donjonmc.quest.DailyQuestManager;
import neoforge.donjonmc.raid.RaidManager;
import neoforge.donjonmc.raid.RaidRole;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class ModCommands {

    private ModCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        // ── /raid ─────────────────────────────────────────────────────────────
        event.getDispatcher().register(
            Commands.literal("raid")

                .then(Commands.literal("create")
                    .executes(ModCommands::executeRaidCreate))

                .then(Commands.literal("invite")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeRaidInvite(ctx,
                            EntityArgument.getPlayer(ctx, "player")))))

                .then(Commands.literal("accept")
                    .executes(ModCommands::executeRaidAccept))

                .then(Commands.literal("leave")
                    .executes(ModCommands::executeRaidLeave))

                .then(Commands.literal("disband")
                    .executes(ModCommands::executeRaidDisband))

                .then(Commands.literal("role")
                    .then(Commands.argument("role", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (RaidRole r : RaidRole.values()) {
                                if (r != RaidRole.NONE) builder.suggest(r.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeRaidRole(ctx,
                            StringArgumentType.getString(ctx, "role")))))
        );

        // ── /donjonmc ─────────────────────────────────────────────────────────
        event.getDispatcher().register(
            Commands.literal("donjonmc")

                // /donjonmc trial <classe>  |  /donjonmc trial reset [player] (op, test)
                .then(Commands.literal("trial")
                    .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeTrialReset(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeTrialReset(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.argument("classe", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (PlayerClass c : PlayerClass.values()) {
                                if (c != PlayerClass.NONE) builder.suggest(c.name().toLowerCase());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeTrial(ctx,
                            StringArgumentType.getString(ctx, "classe")))))

                // /donjonmc quest start            → démarre (force) les quêtes quotidiennes
                // /donjonmc quest force [player]   → force même si déjà faites aujourd'hui (op)
                // /donjonmc quest done             → valide une quête (test)
                // /donjonmc quest test             → timer à 30s (test)
                // /donjonmc quest reset [player]   → réinitialise les quêtes
                .then(Commands.literal("quest")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("start")
                        .executes(ctx -> executeQuestStart(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeQuestStart(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("force")
                        .executes(ctx -> executeQuestForce(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeQuestForce(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("done")
                        .executes(ctx -> executeQuestDone(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeQuestDone(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("test")
                        .executes(ModCommands::executeQuestTest))
                    .then(Commands.literal("reset")
                        .executes(ctx -> executeQuestReset(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeQuestReset(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("disable")
                        .executes(ctx -> executeQuestDisable(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeQuestDisable(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("enable")
                        .executes(ctx -> executeQuestEnable(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeQuestEnable(ctx,
                                EntityArgument.getPlayer(ctx, "player"))))))

                // /donjonmc punishment                → déclenche sa propre instance de punition
                // /donjonmc punishment <joueur>       → force la punition d'un autre joueur (op 2)
                .then(Commands.literal("punishment")
                    .executes(ModCommands::executePunishmentSelf)
                    .then(Commands.argument("player", EntityArgument.player())
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executePunishmentTarget(ctx,
                            EntityArgument.getPlayer(ctx, "player"))))
                    .then(Commands.literal("release")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeReleasePunishment(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("test")
                        .requires(src -> src.hasPermission(2))
                        .executes(ModCommands::executePunishmentTest)))

                // /donjonmc dungeon exit             → quitter le donjon (tous joueurs)
                // /donjonmc dungeon spawn [rank]     → fait apparaître un portail (op 2)
                .then(Commands.literal("dungeon")
                    .then(Commands.literal("exit")
                        .executes(ModCommands::executeDungeonExit))
                    .then(Commands.literal("spawn")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeDungeonSpawn(ctx, null))
                        .then(Commands.argument("rank", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (DungeonRank r : DungeonRank.values())
                                    builder.suggest(r.name());
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executeDungeonSpawn(ctx,
                                StringArgumentType.getString(ctx, "rank")))))
                    .then(Commands.literal("join")
                        .executes(ModCommands::executeDungeonJoin))
                    .then(Commands.literal("style")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeDungeonStyle(ctx, "all"))
                        .then(Commands.argument("theme", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (String[] row : DungeonGenerator.THEME_TILES)
                                    builder.suggest(row[0]);
                                builder.suggest("all");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executeDungeonStyle(ctx,
                                StringArgumentType.getString(ctx, "theme"))))))

                // /donjonmc top     → classement des 10 meilleurs chasseurs en ligne
                .then(Commands.literal("top")
                    .executes(ModCommands::executeTop))

                // /donjonmc rematch → recrée le dernier groupe (invitations automatiques)
                .then(Commands.literal("rematch")
                    .executes(ModCommands::executeRaidRematch))

                // /donjonmc speed   → active/désactive le bonus de vitesse d'Agilité
                .then(Commands.literal("speed")
                    .executes(ModCommands::executeToggleSpeed))

                // /donjonmc sp <quantité>            → se donne des points à soi-même
                // /donjonmc sp <joueur> <quantité>   → donne à un autre joueur (op 2)
                .then(Commands.literal("sp")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10000))
                        .executes(ctx -> executeGiveSp(ctx, null,
                            IntegerArgumentType.getInteger(ctx, "amount")))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeGiveSp(ctx,
                                EntityArgument.getPlayer(ctx, "player"),
                                IntegerArgumentType.getInteger(ctx, "amount"))))
                    )
                )

                // /donjonmc stats reset [player]    → admin (op 2) : réinitialise/rembourse
                // /donjonmc stats respec            → joueur : respec payant (cooldown 7j + 30 niveaux)
                .then(Commands.literal("stats")
                    .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> executeStatsReset(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> executeStatsReset(ctx,
                                EntityArgument.getPlayer(ctx, "player")))))
                    .then(Commands.literal("respec")
                        .executes(ModCommands::executeStatsRespec)))

                // /donjonmc level set <n> [player]  → définit le niveau d'un joueur
                .then(Commands.literal("level")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("set")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, LevelHelper.MAX_LEVEL))
                            .executes(ctx -> executeSetLevel(ctx, null,
                                IntegerArgumentType.getInteger(ctx, "level")))
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> executeSetLevel(ctx,
                                    EntityArgument.getPlayer(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "level")))))))
        );
    }

    // ── /donjonmc rematch ─────────────────────────────────────────────────────

    private static int executeRaidRematch(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            RaidManager.getInstance().quickRematch(player);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc trial ──────────────────────────────────────────────────────

    /** Outil de test : remet la classe à NONE et efface le cooldown d'épreuve. */
    private static int executeTrialReset(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        try {
            ServerPlayer player = target != null ? target : ctx.getSource().getPlayerOrException();
            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            data.setPlayerClass(PlayerClass.NONE);
            data.setLastTrialFailMs(0L);
            player.setData(ModAttachments.PLAYER_DATA, data);

            PlayerEventHandler.applyClassModifiers(player, data); // retire les bonus de classe
            PlayerEventHandler.sendSyncPacket(player);

            ctx.getSource().sendSuccess(() -> Component.literal(
                "Classe et cooldown d'épreuve réinitialisés pour " + player.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static int executeTrial(CommandContext<CommandSourceStack> ctx, String className) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();

            PlayerClass chosen;
            try {
                chosen = PlayerClass.valueOf(className.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                chosen = PlayerClass.NONE;
            }
            if (chosen == PlayerClass.NONE) {
                ctx.getSource().sendFailure(
                    Component.translatable("donjonmc.trial.no_eligible"));
                return 0;
            }

            // Niveau, classe déjà acquise, cooldown et session : contrôlés par startTrial
            ClassTrialHandler.startTrial(player, chosen);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("donjonmc.trial.error"));
            return 0;
        }
    }

    // ── /raid ─────────────────────────────────────────────────────────────────

    private static int executeRaidCreate(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            switch (RaidManager.getInstance().createGroup(player)) {
                case OK         -> player.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.create.success"));
                case ALREADY_IN -> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.create.fail"));
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeRaidInvite(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            switch (RaidManager.getInstance().invite(player, target)) {
                case OK             -> player.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.invite.sent", target.getName().getString()));
                case NO_GROUP       -> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.invite.no_group"));
                case NOT_LEADER     -> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.invite.not_leader"));
                case FULL           -> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.invite.full"));
                case TARGET_IN_GROUP-> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.invite.already_in", target.getName().getString()));
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeRaidAccept(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            switch (RaidManager.getInstance().acceptInvite(player)) {
                case OK       -> player.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.accept.success"));
                case NO_INVITE-> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.accept.none"));
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeRaidLeave(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            switch (RaidManager.getInstance().leave(player)) {
                case OK          -> player.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.leave.success"));
                case NOT_IN_GROUP-> ctx.getSource().sendFailure(Component.translatable("donjonmc.raid.no_group"));
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeRaidDisband(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            switch (RaidManager.getInstance().disband(player)) {
                case OK         -> player.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.disband.success"));
                case NOT_LEADER -> ctx.getSource().sendFailure(Component.translatable("donjonmc.cmd.raid.invite.not_leader"));
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeRaidRole(CommandContext<CommandSourceStack> ctx, String roleName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            RaidRole role = RaidRole.NONE;
            for (RaidRole r : RaidRole.values()) {
                if (r.name().equalsIgnoreCase(roleName)) { role = r; break; }
            }
            if (role == RaidRole.NONE) {
                ctx.getSource().sendFailure(Component.literal("Unknown role. Use: vanguard, striker, support, porter"));
                return 0;
            }
            RaidManager.getInstance().setRole(player, role);
            player.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.role.set",
                Component.translatable(role.langKey()).getString()));
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    // ── /donjonmc quest ──────────────────────────────────────────────────────

    private static int executeQuestStart(CommandContext<CommandSourceStack> ctx,
                                          ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().forceAssign(p);
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeQuestForce(CommandContext<CommandSourceStack> ctx,
                                          ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().forceAssignNewDay(p);
            ctx.getSource().sendSuccess(
                () -> Component.literal("§5[System] §rQuêtes forcées pour §e" + p.getName().getString() + "§r."),
                true);
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeQuestDone(CommandContext<CommandSourceStack> ctx,
                                         ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().debugCompleteOne(p);
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeQuestTest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().skipToThirtySeconds(p);
            ctx.getSource().sendSuccess(
                () -> Component.translatable("donjonmc.cmd.quest.test_skip"), true);
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeQuestReset(CommandContext<CommandSourceStack> ctx,
                                          ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().resetQuests(p);
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeQuestDisable(CommandContext<CommandSourceStack> ctx,
                                            ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().disableForPlayer(p);
            if (target != null) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.quest.disabled", p.getName()), true);
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    private static int executeQuestEnable(CommandContext<CommandSourceStack> ctx,
                                           ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            DailyQuestManager.getInstance().enableForPlayer(p);
            if (target != null) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.quest.enabled", p.getName()), true);
            }
            return 1;
        } catch (Exception e) { ctx.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
    }

    // ── /donjonmc punishment ─────────────────────────────────────────────────

    private static int executePunishmentSelf(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PunishmentManager.getInstance().triggerForPlayer(player);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int executePunishmentTest(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            long currentTick = player.server.overworld().getGameTime();
            boolean found = PunishmentManager.getInstance()
                .skipToThirtySeconds(player.getUUID(), currentTick);
            if (found) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.punishment.test_skip"), true);
            } else {
                ctx.getSource().sendFailure(
                    Component.translatable("donjonmc.cmd.punishment.test_no_instance"));
            }
            return found ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int executePunishmentTarget(CommandContext<CommandSourceStack> ctx,
                                                ServerPlayer target) {
        try {
            PunishmentManager.getInstance().triggerForPlayer(target);
            ctx.getSource().sendSuccess(
                () -> Component.translatable("donjonmc.cmd.punishment.sent", target.getName()),
                true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int executeReleasePunishment(CommandContext<CommandSourceStack> ctx,
                                                 ServerPlayer target) {
        try {
            boolean ok = PunishmentManager.getInstance().releasePlayer(target);
            if (ok) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.punishment.released", target.getName()), true);
            } else {
                ctx.getSource().sendFailure(
                    Component.translatable("donjonmc.cmd.punishment.not_punished", target.getName()));
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc dungeon ─────────────────────────────────────────────────────

    private static int executeDungeonExit(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            DungeonManager.getInstance().forceExit(player);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int executeDungeonSpawn(CommandContext<CommandSourceStack> ctx, String rankName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            DungeonRank rank;
            if (rankName == null) {
                rank = DungeonRank.random(player.level().getRandom());
            } else {
                try {
                    rank = DungeonRank.valueOf(rankName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    ctx.getSource().sendFailure(Component.literal("Rang inconnu. Utilisez : D, C, B, A, S"));
                    return 0;
                }
            }
            DungeonManager.getInstance().spawnPortalAtPlayer(player, rank);
            ctx.getSource().sendSuccess(
                () -> Component.translatable("donjonmc.cmd.dungeon.portal.spawned", rank.name()), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int executeDungeonJoin(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            DungeonManager.getInstance().tryJoinGroupDungeon(player);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int executeDungeonStyle(CommandContext<CommandSourceStack> ctx, String theme) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerLevel dungeonLevel = player.server.getLevel(DungeonManager.DUNGEON_DIMENSION);
            if (dungeonLevel == null) {
                ctx.getSource().sendFailure(Component.translatable("donjonmc.dungeon.error.dimension"));
                return 0;
            }
            BlockPos galleryOrigin = new BlockPos(-1000, 64, 0);
            BlockPos spawnPos = DungeonGenerator.generateStyleGallery(dungeonLevel, galleryOrigin, theme);
            DungeonManager.getInstance().teleportToDungeonAt(player, spawnPos);
            player.sendSystemMessage(Component.translatable("donjonmc.cmd.dungeon.style.entered", theme));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc speed ──────────────────────────────────────────────────────

    private static int executeToggleSpeed(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            boolean nowEnabled = !data.isSpeedEnabled();
            data.setSpeedEnabled(nowEnabled);
            player.setData(ModAttachments.PLAYER_DATA, data);
            PlayerEventHandler.applyStatModifiers(player, data);
            PlayerEventHandler.sendSyncPacket(player);
            player.sendSystemMessage(Component.translatable(
                nowEnabled ? "donjonmc.cmd.speed.enabled" : "donjonmc.cmd.speed.disabled"));
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc sp ─────────────────────────────────────────────────────────

    private static int executeGiveSp(CommandContext<CommandSourceStack> ctx,
                                     ServerPlayer target, int amount) {
        try {
            // Si aucune cible explicite, c'est l'exécuteur lui-même
            ServerPlayer player = (target != null)
                ? target
                : ctx.getSource().getPlayerOrException();

            PlayerData data = player.getData(ModAttachments.PLAYER_DATA);
            data.addSkillPoints(amount);
            player.setData(ModAttachments.PLAYER_DATA, data);
            PlayerEventHandler.sendSyncPacket(player);

            // Feedback à la cible
            player.sendSystemMessage(
                Component.translatable("donjonmc.cmd.sp_received", amount));

            // Feedback à l'exécuteur (si différent de la cible)
            if (target != null) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.sp_given",
                        amount, player.getName()),
                    true);
            }

            return amount;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc stats reset ────────────────────────────────────────────────

    private static int executeStatsReset(CommandContext<CommandSourceStack> ctx,
                                         ServerPlayer target) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            PlayerData data = p.getData(ModAttachments.PLAYER_DATA);
            final int refund = data.getStrength() + data.getAgility() + data.getVitality()
                + data.getIntelligence() + data.getPerception();
            data.setStrength(0);
            data.setAgility(0);
            data.setVitality(0);
            data.setIntelligence(0);
            data.setPerception(0);
            data.addSkillPoints(refund);
            p.setData(ModAttachments.PLAYER_DATA, data);
            PlayerEventHandler.applyStatModifiers(p, data);
            PlayerEventHandler.sendSyncPacket(p);

            p.sendSystemMessage(Component.translatable("donjonmc.cmd.stats.reset_self", refund));
            if (target != null) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.stats.reset_other", p.getName(), refund), true);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc stats respec (joueur) ──────────────────────────────────────
    // Logique partagée avec le bouton GUI dans PlayerEventHandler.tryRespec.

    private static int executeStatsRespec(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            return PlayerEventHandler.tryRespec(p) ? 1 : 0;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc level set ──────────────────────────────────────────────────

    private static int executeSetLevel(CommandContext<CommandSourceStack> ctx,
                                       ServerPlayer target, int level) {
        try {
            ServerPlayer p = target != null ? target : ctx.getSource().getPlayerOrException();
            PlayerData data = p.getData(ModAttachments.PLAYER_DATA);
            data.setLevel(level);
            data.setXp(0);
            p.setData(ModAttachments.PLAYER_DATA, data);
            PlayerEventHandler.applyHealthModifier(p, level);
            PlayerEventHandler.applyStatModifiers(p, data);
            RankTeamManager.updatePlayerTeam(p);
            PlayerEventHandler.sendSyncPacket(p);

            p.sendSystemMessage(Component.translatable("donjonmc.cmd.level.set_self", level));
            if (target != null) {
                ctx.getSource().sendSuccess(
                    () -> Component.translatable("donjonmc.cmd.level.set_other", p.getName(), level), true);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    // ── /donjonmc top ─────────────────────────────────────────────────────────

    private static int executeTop(CommandContext<CommandSourceStack> ctx) {
        List<ServerPlayer> sorted = ctx.getSource().getServer()
            .getPlayerList().getPlayers().stream()
            .sorted(Comparator.comparingInt((ServerPlayer p) ->
                p.getData(ModAttachments.PLAYER_DATA).getLevel()).reversed())
            .limit(10)
            .toList();

        ctx.getSource().sendSuccess(() ->
            Component.literal("══════ Top Chasseurs (en ligne) ══════")
                .withStyle(ChatFormatting.GOLD), false);

        if (sorted.isEmpty()) {
            ctx.getSource().sendSuccess(() ->
                Component.literal("Aucun chasseur en ligne.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        for (int i = 0; i < sorted.size(); i++) {
            final int pos = i + 1;
            final ServerPlayer p = sorted.get(i);
            final PlayerData data = p.getData(ModAttachments.PLAYER_DATA);
            final int level = data.getLevel();
            final String rank = LevelHelper.rankForLevel(level);
            final ChatFormatting rankColor = RankTeamManager.colorForLevel(level);
            final String clsName = data.getPlayerClass().displayName;

            ctx.getSource().sendSuccess(() -> Component.empty()
                .append(Component.literal("#" + pos + " ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[" + rank + "] ").withStyle(rankColor))
                .append(Component.literal(p.getName().getString()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" — Niv." + level).withStyle(ChatFormatting.GREEN))
                .append(Component.literal(" — " + clsName).withStyle(ChatFormatting.AQUA)),
                false);
        }

        return 1;
    }
}
