package neoforge.donjonmc.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import neoforge.donjonmc.Donjonmc;
import neoforge.donjonmc.client.hud.DailyQuestHud;
import neoforge.donjonmc.dungeon.mob.DungeonMobRegistry;
import neoforge.donjonmc.client.hud.DungeonHud;
import neoforge.donjonmc.client.hud.LevelUpPopupHud;
import neoforge.donjonmc.client.hud.PunishmentTimerHud;
import neoforge.donjonmc.client.hud.RankBadgeHud;
import neoforge.donjonmc.client.screen.HunterScreen;

public final class ClientEventHandler {

    private ClientEventHandler() {}

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Donjonmc.PORTAL_ENTITY.get(), DungeonPortalRenderer::new);
        DungeonMobRegistry.registerRenderers(event);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_HUNTER_SCREEN);
        event.register(KeyBindings.TOGGLE_QUEST_HUD_SIDE);
    }

}

@EventBusSubscriber(modid = Donjonmc.MODID, value = Dist.CLIENT)
class ClientGameEventHandler {

    private ClientGameEventHandler() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        while (KeyBindings.OPEN_HUNTER_SCREEN.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new HunterScreen());
        }

        while (KeyBindings.TOGGLE_QUEST_HUD_SIDE.consumeClick()) {
            ClientDailyQuestCache.questHudLeft = !ClientDailyQuestCache.questHudLeft;
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        RankBadgeHud.render(event.getGuiGraphics(), w, h);
        PunishmentTimerHud.render(event.getGuiGraphics(), w, h);
        DailyQuestHud.render(event.getGuiGraphics(), w, h);
        DungeonHud.render(event.getGuiGraphics(), w, h);
        LevelUpPopupHud.render(event.getGuiGraphics(), w, h);
    }
}
