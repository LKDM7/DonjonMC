package neoforge.donjonmc.raid;

import java.util.*;

public class RaidHistory {

    private final List<UUID>          members;
    private final Map<UUID, RaidRole> roles;

    public RaidHistory(List<UUID> members, Map<UUID, RaidRole> roles) {
        this.members = new ArrayList<>(members);
        this.roles   = new HashMap<>(roles);
    }

    public List<UUID> getMembers()           { return Collections.unmodifiableList(members); }
    public RaidRole   getRole(UUID uuid)     { return roles.getOrDefault(uuid, RaidRole.NONE); }
    public Map<UUID, RaidRole> getRolesMap() { return Collections.unmodifiableMap(roles); }
}
