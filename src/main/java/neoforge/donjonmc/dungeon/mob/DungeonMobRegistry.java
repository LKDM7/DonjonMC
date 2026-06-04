package neoforge.donjonmc.dungeon.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.mob.entity.*;
import neoforge.donjonmc.punishment.SandWormEntity;

import java.util.ArrayList;
import java.util.List;

public final class DungeonMobRegistry {

    private DungeonMobRegistry() {}

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, Donjonmc.MODID);

    // ── Normal mobs ─────────────────────────────────────────────────────────────

    public static final DeferredHolder<EntityType<?>, EntityType<GoblinEntity>> GOBLIN =
            reg("goblin",          GoblinEntity::new,          0.6f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<NewGoblinEntity>> NEW_GOBLIN =
            reg("new_goblin",      NewGoblinEntity::new,       0.6f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<SkullEntity>> SKULL =
            reg("skull",           SkullEntity::new,           0.6f, 0.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<GigaGoblinEntity>> GIGA_GOBLIN =
            reg("giga_goblin",     GigaGoblinEntity::new,      1.0f, 2.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<HobgoblinClubEntity>> HOBGOBLIN_CLUB =
            reg("hobgoblin_club",  HobgoblinClubEntity::new,   0.8f, 1.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<HobgoblinBomberEntity>> HOBGOBLIN_BOMBER =
            reg("hobgoblin_bomber",HobgoblinBomberEntity::new, 0.8f, 1.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<BossGolemEntity>> BOSS_GOLEM =
            reg("boss_golem",      BossGolemEntity::new,       1.4f, 2.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<SpiderBossEntity>> SPIDER_BOSS =
            reg("spider_boss",     SpiderBossEntity::new,      1.4f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<UndeadEntity>> UNDEAD =
            reg("undead",          UndeadEntity::new,          0.8f, 1.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<OrcEntity>> ORC =
            reg("orc",             OrcEntity::new,             1.0f, 2.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowSoldierEntity>> SHADOW_SOLDIER =
            reg("shadow_soldier",  ShadowSoldierEntity::new,   0.6f, 1.6f);
    public static final DeferredHolder<EntityType<?>, EntityType<DemonGuardEntity>> DEMON_GUARD =
            reg("demon_guard",     DemonGuardEntity::new,      0.8f, 2.2f);
    public static final DeferredHolder<EntityType<?>, EntityType<TuskEntity>> TUSK =
            reg("tusk",            TuskEntity::new,            1.2f, 2.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<IceBearEntity>> ICE_BEAR =
            reg("ice_bear",        IceBearEntity::new,         1.2f, 1.6f);
    public static final DeferredHolder<EntityType<?>, EntityType<IceElfEntity>> ICE_ELF =
            reg("ice_elf",         IceElfEntity::new,          0.8f, 1.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<DemonBossEntity>> DEMON_BOSS =
            reg("demon_boss",      DemonBossEntity::new,       1.0f, 2.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<AntEntity>> ANT =
            reg("ant",             AntEntity::new,             0.6f, 0.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<WildDemonEntity>> WILD_DEMON =
            reg("wild_demon",      WildDemonEntity::new,       0.8f, 2.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<SteelFangEntity>> STEEL_FANG =
            reg("steel_fang",      SteelFangEntity::new,       0.8f, 1.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<SerpentEntity>> SERPENT =
            reg("serpent",         SerpentEntity::new,         0.8f, 1.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<CentipieEntity>> CENTIPIE =
            reg("centipie",        CentipieEntity::new,        1.2f, 1.0f);

    // ── Punishment mob ───────────────────────────────────────────────────────────

    public static final DeferredHolder<EntityType<?>, EntityType<SandWormEntity>> SAND_WORM =
            reg("sand_worm", SandWormEntity::new, 1.4f, 0.9f);

    // ── Bosses ───────────────────────────────────────────────────────────────────

    public static final DeferredHolder<EntityType<?>, EntityType<IceMonarchEntity>> ICE_MONARCH =
            reg("ice_monarch",     IceMonarchEntity::new,      1.2f, 2.8f);
    public static final DeferredHolder<EntityType<?>, EntityType<BaranEntity>> BARAN =
            reg("baran",           BaranEntity::new,           1.4f, 3.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<IgrisEntity>> IGRIS =
            reg("igris",           IgrisEntity::new,           1.0f, 2.4f);
    public static final DeferredHolder<EntityType<?>, EntityType<MonarchOfGiantsEntity>> MONARCH_OF_GIANTS =
            reg("monarch_of_giants", MonarchOfGiantsEntity::new, 1.8f, 4.0f);
    public static final DeferredHolder<EntityType<?>, EntityType<KamishEntity>> KAMISH =
            reg("kamish",          KamishEntity::new,          2.0f, 4.0f);

    // ── All holders (for iteration) ───────────────────────────────────────────

    public static final List<DeferredHolder<EntityType<?>, ?>> ALL = new ArrayList<>();

    static {
        ALL.add(GOBLIN); ALL.add(NEW_GOBLIN); ALL.add(SKULL); ALL.add(GIGA_GOBLIN);
        ALL.add(HOBGOBLIN_CLUB); ALL.add(HOBGOBLIN_BOMBER); ALL.add(BOSS_GOLEM);
        ALL.add(SPIDER_BOSS); ALL.add(UNDEAD); ALL.add(ORC); ALL.add(SHADOW_SOLDIER);
        ALL.add(DEMON_GUARD); ALL.add(TUSK); ALL.add(ICE_BEAR); ALL.add(ICE_ELF);
        ALL.add(DEMON_BOSS); ALL.add(ANT); ALL.add(WILD_DEMON); ALL.add(STEEL_FANG);
        ALL.add(SERPENT); ALL.add(CENTIPIE); ALL.add(SAND_WORM); ALL.add(ICE_MONARCH); ALL.add(BARAN);
        ALL.add(IGRIS); ALL.add(MONARCH_OF_GIANTS); ALL.add(KAMISH);
    }

    // ── Attribute registration ─────────────────────────────────────────────────

    public static void registerAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(GOBLIN.get(),            GoblinEntity.createAttributes().build());
        event.put(NEW_GOBLIN.get(),        NewGoblinEntity.createAttributes().build());
        event.put(SKULL.get(),             SkullEntity.createAttributes().build());
        event.put(GIGA_GOBLIN.get(),       GigaGoblinEntity.createAttributes().build());
        event.put(HOBGOBLIN_CLUB.get(),    HobgoblinClubEntity.createAttributes().build());
        event.put(HOBGOBLIN_BOMBER.get(),  HobgoblinBomberEntity.createAttributes().build());
        event.put(BOSS_GOLEM.get(),        BossGolemEntity.createAttributes().build());
        event.put(SPIDER_BOSS.get(),       SpiderBossEntity.createAttributes().build());
        event.put(UNDEAD.get(),            UndeadEntity.createAttributes().build());
        event.put(ORC.get(),               OrcEntity.createAttributes().build());
        event.put(SHADOW_SOLDIER.get(),    ShadowSoldierEntity.createAttributes().build());
        event.put(DEMON_GUARD.get(),       DemonGuardEntity.createAttributes().build());
        event.put(TUSK.get(),              TuskEntity.createAttributes().build());
        event.put(ICE_BEAR.get(),          IceBearEntity.createAttributes().build());
        event.put(ICE_ELF.get(),           IceElfEntity.createAttributes().build());
        event.put(DEMON_BOSS.get(),        DemonBossEntity.createAttributes().build());
        event.put(ANT.get(),               AntEntity.createAttributes().build());
        event.put(WILD_DEMON.get(),        WildDemonEntity.createAttributes().build());
        event.put(STEEL_FANG.get(),        SteelFangEntity.createAttributes().build());
        event.put(SERPENT.get(),           SerpentEntity.createAttributes().build());
        event.put(CENTIPIE.get(),          CentipieEntity.createAttributes().build());
        event.put(SAND_WORM.get(),         SandWormEntity.createAttributes().build());
        event.put(ICE_MONARCH.get(),       IceMonarchEntity.createAttributes().build());
        event.put(BARAN.get(),             BaranEntity.createAttributes().build());
        event.put(IGRIS.get(),             IgrisEntity.createAttributes().build());
        event.put(MONARCH_OF_GIANTS.get(), MonarchOfGiantsEntity.createAttributes().build());
        event.put(KAMISH.get(),            KamishEntity.createAttributes().build());
    }

    // ── Renderer registration (client-side) ───────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void registerRenderers(
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        for (DeferredHolder<EntityType<?>, ?> h : ALL) {
            event.registerEntityRenderer(
                    (EntityType) h.get(),
                    neoforge.donjonmc.client.renderer.DungeonMobRenderer::new);
        }
    }

    // ── Factory helper ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T extends DungeonMob> DeferredHolder<EntityType<?>, EntityType<T>> reg(
            String name, EntityType.EntityFactory<T> factory, float width, float height) {
        return (DeferredHolder<EntityType<?>, EntityType<T>>) (DeferredHolder<?, ?>) ENTITY_TYPES.register(name, () ->
                EntityType.Builder.<T>of(factory, MobCategory.MONSTER)
                        .sized(width, height)
                        .clientTrackingRange(8)
                        .build("donjonmc:" + name));
    }
}
