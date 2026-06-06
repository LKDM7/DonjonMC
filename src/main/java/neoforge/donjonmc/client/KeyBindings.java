package neoforge.donjonmc.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public final class KeyBindings {

    private KeyBindings() {}

    private static final String CATEGORY = "key.categories.donjonmc";

    public static final KeyMapping OPEN_HUNTER_SCREEN       = key("open_hunter_screen",       GLFW.GLFW_KEY_H);
    public static final KeyMapping TOGGLE_QUEST_HUD_SIDE    = key("toggle_quest_hud_side",    GLFW.GLFW_KEY_UNKNOWN);

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping(
            "key.donjonmc." + name,
            InputConstants.Type.KEYSYM,
            glfwKey,
            CATEGORY
        );
    }
}
