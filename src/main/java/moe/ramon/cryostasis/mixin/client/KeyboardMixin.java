package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards raw key edges to the input handler so module hotkeys and the click GUI key work
 * everywhere the game reads the keyboard. Repeats are dropped, so a held key toggles once;
 * releases are forwarded because a hold-to-activate module needs to know when the key goes back
 * up, wherever the player happens to be when it does.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void cryostasis$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		if (action == GLFW.GLFW_PRESS) {
			cryostasis.getInputHandler().onKeyPress(key);
		} else if (action == GLFW.GLFW_RELEASE) {
			cryostasis.getInputHandler().onKeyRelease(key);
		}
	}
}
