package neoforge.donjonmc.raid;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Holder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.player.ModAttachments;
import neoforge.donjonmc.player.PlayerEventHandler;

@EventBusSubscriber(modid = Donjonmc.MODID)
public final class RaidEventHandler {

    private RaidEventHandler() {}

    private static final ResourceLocation STRIKER_DAMAGE    =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_striker_damage");
    private static final ResourceLocation VANGUARD_ARMOR    =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_vanguard_armor");
    private static final ResourceLocation SUPPORT_SPEED     =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_support_speed");
    private static final ResourceLocation PORTER_KNOCKBACK  =
        ResourceLocation.fromNamespaceAndPath(Donjonmc.MODID, "raid_porter_knockback");

    // ── Role modifiers ────────────────────────────────────────────────────────

    public static void applyRoleModifiers(Player player, RaidRole role) {
        // Retire tous les buffs de rôle
        removeMod(player, Attributes.ATTACK_DAMAGE,        STRIKER_DAMAGE);
        removeMod(player, Attributes.ARMOR,                VANGUARD_ARMOR);
        removeMod(player, Attributes.MOVEMENT_SPEED,       SUPPORT_SPEED);
        removeMod(player, Attributes.KNOCKBACK_RESISTANCE, PORTER_KNOCKBACK);

        switch (role) {
            case STRIKER  -> addMod(player, Attributes.ATTACK_DAMAGE,        STRIKER_DAMAGE,   0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            case VANGUARD -> addMod(player, Attributes.ARMOR,                VANGUARD_ARMOR,   4.0,  AttributeModifier.Operation.ADD_VALUE);
            case SUPPORT  -> addMod(player, Attributes.MOVEMENT_SPEED,       SUPPORT_SPEED,    0.01, AttributeModifier.Operation.ADD_VALUE);
            case PORTER   -> addMod(player, Attributes.KNOCKBACK_RESISTANCE, PORTER_KNOCKBACK, 0.3,  AttributeModifier.Operation.ADD_VALUE);
            default -> {}
        }
    }

    private static void removeMod(Player player, Holder<Attribute> attr, ResourceLocation id) {
        var inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    private static void addMod(Player player, Holder<Attribute> attr, ResourceLocation id,
                                double value, AttributeModifier.Operation op) {
        var inst = player.getAttribute(attr);
        if (inst != null) inst.addTransientModifier(new AttributeModifier(id, value, op));
    }

    // ── Tick: anti-AFK, Vanguard aggro redirect, Support regen ───────────────

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        RaidGroup group = RaidManager.getInstance().getGroup(player.getUUID()).orElse(null);
        if (group == null) return;

        // Anti-AFK position tracking (every second)
        if (player.tickCount % 20 == 0) {
            RaidManager.getInstance().updatePosition(player);
        }

        // Vanguard aggro redirect (every 2 s)
        if (player.tickCount % 40 == 0 && group.getRole(player.getUUID()) == RaidRole.VANGUARD) {
            AABB box = player.getBoundingBox().inflate(20.0);
            player.level().getEntitiesOfClass(Mob.class, box, mob -> {
                if (!(mob.getTarget() instanceof ServerPlayer target)) return false;
                return group.contains(target.getUUID())
                    && group.getRole(target.getUUID()) != RaidRole.VANGUARD;
            }).forEach(mob -> mob.setTarget(player));
        }

        // Support bonus regen for allies (every second)
        if (player.tickCount % 20 == 0 && group.getRole(player.getUUID()) != RaidRole.SUPPORT) {
            boolean nearSupport = group.getMembers().stream()
                .filter(uuid -> group.getRole(uuid) == RaidRole.SUPPORT)
                .map(uuid -> player.server.getPlayerList().getPlayer(uuid))
                .filter(java.util.Objects::nonNull)
                .anyMatch(s -> s.distanceTo(player) <= 20.0);

            if (nearSupport) {
                MagicData magicData = MagicData.getPlayerMagicData(player);
                float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
                if (magicData.getMana() < maxMana) {
                    magicData.addMana(1.0f);
                }
            }
        }
    }

    // ── Invite timeout cleanup (every 5 s) ────────────────────────────────────

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 != 0) return;
        RaidManager.getInstance().cleanupExpiredInvites(event.getServer());
    }

    // ── Disconnect cleanup ────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            RaidManager.getInstance().handleDisconnect(sp);
        }
    }
}
