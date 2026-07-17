package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.render.ZoomModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the Zoom module by dividing the computed field of view. Uses a return-value
 * modifier so it composes cleanly with vanilla's own FOV math (sprint, speed effects,
 * spyglass) instead of overwriting it.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
	@ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float cryostasis$applyZoom(float original) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return original;
		}
		ZoomModule zoom = cryostasis.getModuleManager().get(ZoomModule.class);
		if (zoom != null && zoom.isEnabled()) {
			return original / zoom.getFactor();
		}
		return original;
	}
}
