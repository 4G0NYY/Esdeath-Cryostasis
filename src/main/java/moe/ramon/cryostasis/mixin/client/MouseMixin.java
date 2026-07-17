package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.service.ClickTracker;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the {@link ClickTracker} from raw mouse button presses. Injected at the head of
 * the mouse press handler so a click is counted the moment GLFW reports it, independent
 * of whether the game consumes it (in a GUI, over a slot, and so on). Validates the
 * Mixin pipeline that later world-render modules in Group B depend on.
 */
@Mixin(MouseHandler.class)
public class MouseMixin {
	@Inject(method = "onPress", at = @At("HEAD"))
	private void cryostasis$trackClicks(long window, int button, int action, int mods, CallbackInfo ci) {
		if (action != GLFW.GLFW_PRESS) {
			return;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			cryostasis.getClickTracker().onClick(ClickTracker.LEFT, System.currentTimeMillis());
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			cryostasis.getClickTracker().onClick(ClickTracker.RIGHT, System.currentTimeMillis());
		}
	}
}
