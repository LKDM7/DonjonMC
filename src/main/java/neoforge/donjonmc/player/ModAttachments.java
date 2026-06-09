package neoforge.donjonmc.player;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.dungeon.DungeonSaveData;
import neoforge.donjonmc.punishment.PunishmentData;
import neoforge.donjonmc.quest.DailyQuestData;
import neoforge.donjonmc.quest.UniqueQuestData;

public final class ModAttachments {

    private ModAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Donjonmc.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerData>> PLAYER_DATA =
        ATTACHMENT_TYPES.register("player_data", () ->
            AttachmentType.builder(PlayerData::new)
                .serialize(PlayerData.CODEC)
                .copyOnDeath()
                .build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PunishmentData>> PUNISHMENT_DATA =
        ATTACHMENT_TYPES.register("punishment_data", () ->
            AttachmentType.builder(PunishmentData::new)
                .serialize(PunishmentData.CODEC)
                .copyOnDeath()
                .build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DailyQuestData>> DAILY_QUEST =
        ATTACHMENT_TYPES.register("daily_quest", () ->
            AttachmentType.builder(DailyQuestData::new)
                .serialize(DailyQuestData.CODEC)
                .copyOnDeath()
                .build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<UniqueQuestData>> UNIQUE_QUEST =
        ATTACHMENT_TYPES.register("unique_quest", () ->
            AttachmentType.builder(UniqueQuestData::new)
                .serialize(UniqueQuestData.CODEC)
                .copyOnDeath()
                .build()
        );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DungeonSaveData>> DUNGEON_SAVE =
        ATTACHMENT_TYPES.register("dungeon_save", () ->
            AttachmentType.builder(DungeonSaveData::new)
                .serialize(DungeonSaveData.CODEC)
                .build()
        );
}
