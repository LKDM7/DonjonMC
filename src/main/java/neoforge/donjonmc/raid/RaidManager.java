package neoforge.donjonmc.raid;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import net.minecraft.world.phys.Vec3;
import neoforge.donjonmc.network.RaidSyncPacket;
import neoforge.donjonmc.network.RaidSyncPacket.MemberInfo;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

public final class RaidManager {

    private static final RaidManager INSTANCE = new RaidManager();
    public static RaidManager getInstance() { return INSTANCE; }

    private RaidManager() {}

    // player UUID → their group
    private final Map<UUID, RaidGroup> playerToGroup    = new HashMap<>();
    // invitee UUID → leader UUID
    private final Map<UUID, UUID>      pendingInvites   = new HashMap<>();
    // invitee UUID → System.currentTimeMillis() when the invite was sent
    private final Map<UUID, Long>      inviteSentTime   = new HashMap<>();
    // invitee UUID → their old role (for rematch auto-assign)
    private final Map<UUID, RaidRole>  pendingRoles     = new HashMap<>();
    // leader UUID → last group snapshot
    private final Map<UUID, RaidHistory> history        = new HashMap<>();
    // anti-AFK: last recorded position
    private final Map<UUID, Vec3>      lastPos          = new HashMap<>();
    private final Map<UUID, Long>      lastMoveTime     = new HashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void clear() {
        playerToGroup.clear();
        pendingInvites.clear();
        inviteSentTime.clear();
        pendingRoles.clear();
        lastPos.clear();
        lastMoveTime.clear();
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<RaidGroup> getGroup(UUID playerId) {
        return Optional.ofNullable(playerToGroup.get(playerId));
    }

    public boolean isInGroup(UUID playerId) {
        return playerToGroup.containsKey(playerId);
    }

    public boolean hasHistory(UUID leaderId) {
        return history.containsKey(leaderId);
    }

    // ── Anti-AFK ──────────────────────────────────────────────────────────────

    public void updatePosition(ServerPlayer player) {
        Vec3 current = player.position();
        Vec3 last    = lastPos.put(player.getUUID(), current);
        if (last == null || current.distanceToSqr(last) > 0.01) {
            lastMoveTime.put(player.getUUID(), System.currentTimeMillis());
        }
    }

    public boolean isActive(ServerPlayer player) {
        Long t = lastMoveTime.get(player.getUUID());
        return t == null || (System.currentTimeMillis() - t) < 60_000;
    }

    // Returns group members within radius who are active, always includes the killer himself.
    public List<ServerPlayer> getNearbyActiveMembers(ServerPlayer killer, double radius) {
        RaidGroup group = playerToGroup.get(killer.getUUID());
        if (group == null) return Collections.singletonList(killer);

        return group.getMembers().stream()
            .map(uuid -> killer.server.getPlayerList().getPlayer(uuid))
            .filter(Objects::nonNull)
            // Le tueur est TOUJOURS inclus (combat sur place ne met pas à jour l'anti-AFK).
            // Les autres membres doivent être proches ET actifs.
            .filter(p -> p.getUUID().equals(killer.getUUID())
                    || (p.distanceTo(killer) <= radius && isActive(p)))
            .collect(Collectors.toList());
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    public enum CreateResult  { OK, ALREADY_IN }
    public enum InviteResult  { OK, NO_GROUP, NOT_LEADER, FULL, TARGET_IN_GROUP }
    public enum AcceptResult  { OK, NO_INVITE }
    public enum LeaveResult   { OK, NOT_IN_GROUP }
    public enum DisbandResult { OK, NOT_LEADER }

    public CreateResult createGroup(ServerPlayer leader) {
        if (playerToGroup.containsKey(leader.getUUID())) return CreateResult.ALREADY_IN;

        RaidGroup group = new RaidGroup(leader.getUUID());
        group.addMember(leader.getUUID());
        playerToGroup.put(leader.getUUID(), group);
        syncToGroup(group, leader.server);
        return CreateResult.OK;
    }

    public InviteResult invite(ServerPlayer leader, ServerPlayer target) {
        RaidGroup group = playerToGroup.get(leader.getUUID());
        if (group == null)                               return InviteResult.NO_GROUP;
        if (!group.isLeader(leader.getUUID()))           return InviteResult.NOT_LEADER;
        if (group.isFull())                              return InviteResult.FULL;
        if (playerToGroup.containsKey(target.getUUID())) return InviteResult.TARGET_IN_GROUP;

        pendingInvites.put(target.getUUID(), leader.getUUID());
        inviteSentTime.put(target.getUUID(), System.currentTimeMillis());
        target.sendSystemMessage(Component.translatable(
            "donjonmc.cmd.raid.invite.received", leader.getName().getString()));
        // Sync GUI for both: target sees pending invite, leader sees updated invitable list
        syncToPlayer(target, null);
        syncToGroup(group, leader.server);
        return InviteResult.OK;
    }

    public AcceptResult acceptInvite(ServerPlayer player) {
        UUID leaderId = pendingInvites.remove(player.getUUID());
        inviteSentTime.remove(player.getUUID());
        if (leaderId == null) return AcceptResult.NO_INVITE;

        RaidGroup group = playerToGroup.get(leaderId);
        if (group == null || group.isFull()) return AcceptResult.NO_INVITE;

        group.addMember(player.getUUID());
        playerToGroup.put(player.getUUID(), group);

        // Auto-assign role from rematch if available
        RaidRole rematchRole = pendingRoles.remove(player.getUUID());
        if (rematchRole != null && rematchRole != RaidRole.NONE) {
            group.setRole(player.getUUID(), rematchRole);
            RaidEventHandler.applyRoleModifiers(player, rematchRole);
        }

        syncToGroup(group, player.server);
        return AcceptResult.OK;
    }

    public void declineInvite(ServerPlayer player) {
        pendingInvites.remove(player.getUUID());
        inviteSentTime.remove(player.getUUID());
        pendingRoles.remove(player.getUUID());
        syncToPlayer(player, null);
    }

    public LeaveResult leave(ServerPlayer player) {
        RaidGroup group = playerToGroup.get(player.getUUID()); // peek before remove
        if (group == null) return LeaveResult.NOT_IN_GROUP;

        UUID leaderId = group.getLeaderId();

        // Sauvegarder l'historique AVANT de retirer le joueur
        if (group.getMembers().size() == 1) {
            // Dernier membre → historique pour ce joueur comme chef solo
            saveHistory(player.getUUID(), group);
        } else if (group.isLeader(player.getUUID())) {
            // Le chef part mais il reste des membres → sauvegarder pour le chef sortant
            saveHistory(player.getUUID(), group);
        }

        playerToGroup.remove(player.getUUID());
        RaidEventHandler.applyRoleModifiers(player, RaidRole.NONE);
        group.removeMember(player.getUUID());

        if (group.isEmpty()) {
            // rien (déjà sauvegardé ci-dessus)
        } else if (!group.getLeaderId().equals(leaderId)) {
            // Le chef vient de partir : transférer le chef
            UUID newLeader = group.getMembers().get(0);
            group.setLeaderId(newLeader);
            ServerPlayer newLeaderPlayer = player.server.getPlayerList().getPlayer(newLeader);
            if (newLeaderPlayer != null) {
                newLeaderPlayer.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.new_leader"));
            }
            syncToGroup(group, player.server);
        } else {
            syncToGroup(group, player.server);
        }

        syncToPlayer(player, null);
        return LeaveResult.OK;
    }

    public DisbandResult disband(ServerPlayer leader) {
        RaidGroup group = playerToGroup.get(leader.getUUID());
        if (group == null || !group.isLeader(leader.getUUID())) return DisbandResult.NOT_LEADER;

        saveHistory(leader.getUUID(), group);

        for (UUID member : group.getMembers()) {
            ServerPlayer sp = leader.server.getPlayerList().getPlayer(member);
            if (sp != null) {
                RaidEventHandler.applyRoleModifiers(sp, RaidRole.NONE);
                playerToGroup.remove(member);
                syncToPlayer(sp, null);
                if (!sp.getUUID().equals(leader.getUUID())) {
                    sp.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.disband.broadcast"));
                }
            } else {
                playerToGroup.remove(member);
            }
        }
        return DisbandResult.OK;
    }

    public void setRole(ServerPlayer player, RaidRole role) {
        RaidGroup group = playerToGroup.get(player.getUUID());
        if (group == null) return;
        group.setRole(player.getUUID(), role);
        RaidEventHandler.applyRoleModifiers(player, role);
        syncToGroup(group, player.server);
    }

    public boolean quickRematch(ServerPlayer leader) {
        RaidHistory hist = history.get(leader.getUUID());
        if (hist == null) {
            leader.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.rematch.no_history"));
            return false;
        }
        if (playerToGroup.containsKey(leader.getUUID())) {
            leader.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.rematch.already_in"));
            return false;
        }

        RaidGroup group = new RaidGroup(leader.getUUID());
        group.addMember(leader.getUUID());
        RaidRole leaderRole = hist.getRole(leader.getUUID());
        group.setRole(leader.getUUID(), leaderRole);
        playerToGroup.put(leader.getUUID(), group);
        RaidEventHandler.applyRoleModifiers(leader, leaderRole);

        for (UUID memberId : hist.getMembers()) {
            if (memberId.equals(leader.getUUID())) continue;
            ServerPlayer member = leader.server.getPlayerList().getPlayer(memberId);
            if (member == null) continue;

            pendingInvites.put(memberId, leader.getUUID());
            pendingRoles.put(memberId, hist.getRole(memberId));
            member.sendSystemMessage(Component.translatable(
                "donjonmc.cmd.raid.invite.received", leader.getName().getString()));
        }

        syncToGroup(group, leader.server);
        leader.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.rematch.sent"));
        return true;
    }

    public void handleDisconnect(ServerPlayer player) {
        lastPos.remove(player.getUUID());
        lastMoveTime.remove(player.getUUID());
        pendingInvites.remove(player.getUUID());
        inviteSentTime.remove(player.getUUID());
        pendingRoles.remove(player.getUUID());

        RaidGroup group = playerToGroup.remove(player.getUUID());
        if (group == null) return;

        RaidEventHandler.applyRoleModifiers(player, RaidRole.NONE);
        group.removeMember(player.getUUID());

        if (group.isEmpty()) {
            saveHistory(player.getUUID(), group);
            return;
        }
        if (group.isLeader(player.getUUID())) {
            UUID newLeader = group.getMembers().get(0);
            group.setLeaderId(newLeader);
            saveHistory(player.getUUID(), group);
            ServerPlayer newLeaderPlayer = player.server.getPlayerList().getPlayer(newLeader);
            if (newLeaderPlayer != null) {
                newLeaderPlayer.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.new_leader"));
            }
        }
        syncToGroup(group, player.server);
    }

    /** Expire les invitations de raid restées sans réponse après 60 secondes. */
    public void cleanupExpiredInvites(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : inviteSentTime.entrySet()) {
            if (now - entry.getValue() > 60_000L) expired.add(entry.getKey());
        }
        for (UUID uid : expired) {
            pendingInvites.remove(uid);
            inviteSentTime.remove(uid);
            pendingRoles.remove(uid);
            ServerPlayer sp = server.getPlayerList().getPlayer(uid);
            if (sp != null) {
                sp.sendSystemMessage(Component.translatable("donjonmc.cmd.raid.invite.expired"));
                syncToPlayer(sp, null);
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void saveHistory(UUID leaderId, RaidGroup group) {
        if (group.getMembers().isEmpty()) return;
        history.put(leaderId, new RaidHistory(group.getMembers(), group.getRolesMap()));
    }

    /** Associates a portal (rank + coordinates) with the group of the given player and syncs to all members. */
    public void setGroupPortal(UUID memberId, int rankOrdinal, int x, int y, int z, MinecraftServer server) {
        RaidGroup group = playerToGroup.get(memberId);
        if (group == null) return;
        group.setPortal(rankOrdinal, x, y, z);
        syncToGroup(group, server);
    }

    public void syncToGroup(RaidGroup group, MinecraftServer server) {
        for (UUID memberId : group.getMembers()) {
            ServerPlayer sp = server.getPlayerList().getPlayer(memberId);
            if (sp != null) syncToPlayer(sp, group);
        }
    }

    public void syncToPlayer(ServerPlayer player, RaidGroup group) {
        boolean hasPending = pendingInvites.containsKey(player.getUUID());
        String  inviterName = "";
        if (hasPending) {
            UUID leaderId = pendingInvites.get(player.getUUID());
            ServerPlayer leader = player.server.getPlayerList().getPlayer(leaderId);
            inviterName = leader != null ? leader.getName().getString() : "?";
        }

        // Invitable players: online players not in any group, not already invited
        List<String> invitable = List.of();
        if (group != null && group.isLeader(player.getUUID()) && !group.isFull()) {
            invitable = player.server.getPlayerList().getPlayers().stream()
                .filter(sp -> !sp.getUUID().equals(player.getUUID()))
                .filter(sp -> !playerToGroup.containsKey(sp.getUUID()))
                .filter(sp -> !pendingInvites.containsKey(sp.getUUID()))
                .map(sp -> sp.getName().getString())
                .collect(Collectors.toList());
        }

        RaidSyncPacket packet;
        if (group == null) {
            packet = new RaidSyncPacket(false,
                history.containsKey(player.getUUID()),
                hasPending, inviterName, invitable,
                "", false, List.of(),
                -1, 0, 0, 0);
        } else {
            List<MemberInfo> members = group.getMembers().stream()
                .map(uuid -> {
                    ServerPlayer sp = player.server.getPlayerList().getPlayer(uuid);
                    String name = sp != null ? sp.getName().getString() : uuid.toString().substring(0, 8);
                    int level = sp != null
                        ? sp.getData(neoforge.donjonmc.player.ModAttachments.PLAYER_DATA).getLevel()
                        : 0;
                    return new MemberInfo(name, level, group.getRole(uuid).ordinal());
                })
                .collect(Collectors.toList());

            String leaderName = group.isLeader(player.getUUID()) ? "you" :
                Optional.ofNullable(player.server.getPlayerList().getPlayer(group.getLeaderId()))
                    .map(sp -> sp.getName().getString()).orElse("?");

            packet = new RaidSyncPacket(true,
                history.containsKey(group.getLeaderId()),
                hasPending, inviterName, invitable,
                leaderName, group.isLeader(player.getUUID()), members,
                group.getPortalRankOrdinal(), group.getPortalX(), group.getPortalY(), group.getPortalZ());
        }
        PacketDistributor.sendToPlayer(player, packet);
    }
}
