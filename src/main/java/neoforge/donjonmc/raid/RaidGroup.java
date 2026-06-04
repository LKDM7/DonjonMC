package neoforge.donjonmc.raid;

import java.util.*;

public class RaidGroup {

    public static final int MAX_SIZE = 8;

    private final UUID id = UUID.randomUUID();
    private UUID leaderId;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, RaidRole> roles = new HashMap<>();

    // ── Portal tracking ───────────────────────────────────────────────────────
    private int portalRankOrdinal = -1;  // -1 = no portal tracked
    private int portalX, portalY, portalZ;

    public RaidGroup(UUID leaderId) {
        this.leaderId = leaderId;
    }

    public UUID getId()       { return id; }
    public UUID getLeaderId() { return leaderId; }
    public void setLeaderId(UUID uuid) { this.leaderId = uuid; }

    public boolean isLeader(UUID uuid)  { return uuid.equals(leaderId); }
    public boolean isFull()             { return members.size() >= MAX_SIZE; }
    public boolean isEmpty()            { return members.isEmpty(); }
    public boolean contains(UUID uuid)  { return members.contains(uuid); }

    public List<UUID>            getMembers()  { return Collections.unmodifiableList(members); }
    public Map<UUID, RaidRole>   getRolesMap() { return Collections.unmodifiableMap(roles); }

    public RaidRole getRole(UUID uuid) {
        return roles.getOrDefault(uuid, RaidRole.NONE);
    }

    public boolean addMember(UUID uuid) {
        if (isFull() || contains(uuid)) return false;
        members.add(uuid);
        roles.put(uuid, RaidRole.NONE);
        return true;
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        roles.remove(uuid);
    }

    public void setRole(UUID uuid, RaidRole role) {
        if (contains(uuid)) roles.put(uuid, role);
    }

    // ── Portal accessors ──────────────────────────────────────────────────────
    public boolean hasPortal()            { return portalRankOrdinal >= 0; }
    public int getPortalRankOrdinal()     { return portalRankOrdinal; }
    public int getPortalX()              { return portalX; }
    public int getPortalY()              { return portalY; }
    public int getPortalZ()              { return portalZ; }

    public void setPortal(int rankOrdinal, int x, int y, int z) {
        this.portalRankOrdinal = rankOrdinal;
        this.portalX = x; this.portalY = y; this.portalZ = z;
    }

    public void clearPortal() { this.portalRankOrdinal = -1; }
}
