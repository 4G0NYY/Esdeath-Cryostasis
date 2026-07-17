package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards raw key presses to the input handler so module hotkeys and the click GUI key
 * work everywhere the game reads the keyboard. Only rising edges (GLFW_PRESS) are
 * forwarded; repeats and releases are ignored so a held key toggles once.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void cryostasis$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		if (action != GLFW.GLFW_PRESS) {
			return;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis != null) {
			cryostasis.getInputHandler().onKeyPress(key);
		}
	}
}
