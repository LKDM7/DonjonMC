package neoforge.donjonmc.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.raid.RaidManager;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class DungeonPortalEntity extends Entity implements GeoEntity {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Integer>  RANK_ORD  =
        SynchedEntityData.defineId(DungeonPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean>  KEY_GIVEN =
        SynchedEntityData.defineId(DungeonPortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>  EXIT_MODE =
        SynchedEntityData.defineId(DungeonPortalEntity.class, EntityDataSerializers.BOOLEAN);

    /** Halo : contour lumineux quand un joueur est à moins de 120 blocs. */
    private static final double HALO_RANGE = 120.0;

    /** Lifespan in ticks: 30 minutes for portal, 60 seconds for exit. */
    private int ticksLeft;
    private int instanceId = -1;

    public DungeonPortalEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.ticksLeft = 20 * 60 * 30;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RANK_ORD,  0);
        builder.define(KEY_GIVEN, false);
        builder.define(EXIT_MODE, false);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public DungeonRank getRank()    { return DungeonRank.fromOrdinal(entityData.get(RANK_ORD)); }
    public boolean isKeyGiven()     { return entityData.get(KEY_GIVEN); }
    public boolean isExitPortal()   { return entityData.get(EXIT_MODE); }

    public void init(DungeonRank rank) {
        entityData.set(RANK_ORD, rank.ordinal());
        entityData.set(KEY_GIVEN, false);
        entityData.set(EXIT_MODE, false);
    }

    public void setAsExit(int instanceId) {
        entityData.set(EXIT_MODE, true);
        entityData.set(KEY_GIVEN, true);
        this.instanceId = instanceId;
        this.ticksLeft  = 20 * 60 * 5; // 5 minutes to exit
    }

    public void setAsEntranceExit(int instanceId) {
        entityData.set(EXIT_MODE, true);
        entityData.set(KEY_GIVEN, true);
        this.instanceId = instanceId;
        this.ticksLeft  = 20 * 60 * 120; // 2 hours (durée max du donjon)
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        if (--ticksLeft <= 0) { discard(); return; }

        // Halo de perception : même principe que la Perception sur les mobs,
        // mais déclenché par la proximité de n'importe quel joueur (1×/s).
        if (tickCount % 20 == 0) {
            boolean playerNearby = level().getNearestPlayer(this, HALO_RANGE) != null;
            if (hasGlowingTag() != playerNearby) {
                setGlowingTag(playerNearby);
            }
        }
    }

    // ── Right-click interaction ───────────────────────────────────────────────

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }

        if (isExitPortal()) {
            DungeonManager.getInstance().exitPlayerViaPortal(sp, instanceId);
            return InteractionResult.CONSUME;
        }

        // Enregistre le portail dans le HUD Raid de tous les membres du groupe
        BlockPos pos = blockPosition();
        RaidManager.getInstance().setGroupPortal(
            sp.getUUID(), getRank().ordinal(),
            pos.getX(), pos.getY(), pos.getZ(),
            sp.server);

        // Tente d'activer le donjon
        DungeonManager.getInstance().tryActivate(sp, this);
        return InteractionResult.CONSUME;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(RANK_ORD,  tag.getInt("rank"));
        entityData.set(KEY_GIVEN, tag.getBoolean("keyGiven"));
        entityData.set(EXIT_MODE, tag.getBoolean("exitMode"));
        ticksLeft  = tag.getInt("ticksLeft");
        instanceId = tag.getInt("instanceId");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("rank",       entityData.get(RANK_ORD));
        tag.putBoolean("keyGiven", entityData.get(KEY_GIVEN));
        tag.putBoolean("exitMode", entityData.get(EXIT_MODE));
        tag.putInt("ticksLeft",  ticksLeft);
        tag.putInt("instanceId", instanceId);
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }

    // ── GeoEntity ─────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0,
            state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
