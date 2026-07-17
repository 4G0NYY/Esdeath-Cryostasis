package moe.ramon.cryostasis.input;

import moe.ramon.cryostasis.gui.ClickGuiScreen;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.module.ModuleManager;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Routes raw key presses to module toggles and the click GUI. Fed by a keyboard Mixin
 * rather than a per-tick poll, so a tap registers exactly once with no edge-detection
 * bookkeeping.
 *
 * Presses are ignored while any screen is open so that typing in chat or the GUI never
 * toggles a module. The GUI open key is deliberately checked from within the GUI too,
 * which is handled by the screen itself (Escape closes it).
 */
public final class InputHandler {
	private final ModuleManager modules;
	private int openGuiKey = GLFW.GLFW_KEY_RIGHT_SHIFT;

	public InputHandler(ModuleManager modules) {
		this.modules = modules;
	}

	public void onKeyPress(int key) {
		if (key == GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null) {
			return;
		}
		if (key == openGuiKey) {
			mc.setScreen(new ClickGuiScreen());
			return;
		}
		List<Module> all = modules.getModules();
		for (int i = 0; i < all.size(); i++) {
			Module module = all.get(i);
			if (module.getKeyCode() == key) {
				module.toggle();
			}
		}
	}

	public int getOpenGuiKey() {
		return openGuiKey;
	}

	public void setOpenGuiKey(int key) {
		this.openGuiKey = key;
	}
}
