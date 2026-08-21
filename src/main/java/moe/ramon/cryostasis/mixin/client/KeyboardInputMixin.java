package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.render.FreecamModule;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the body still while Freecam has the camera out. The movement keys are read once per
 * tick here and turned into the impulse the player walks on, so clearing the result afterwards
 * hands the player the same input they would get from someone with their hands off the keyboard.
 * That covers the sprint and sneak flags too, and it is what the client sends the server, so
 * there is nothing to reconcile when the camera comes back.
 *
 * Clearing the input rather than blocking the key reads is deliberate: the keys stay live for
 * the module itself, which flies the camera on them.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("RETURN"))
	private void cryostasis$freezeBody(CallbackInfo ci) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		FreecamModule freecam = cryostasis.getModuleManager().get(FreecamModule.class);
		if (freecam != null && freecam.isDetached()) {
			this.keyPresses = Input.EMPTY;
			this.moveVector = Vec2.ZERO;
		}
	}
}
