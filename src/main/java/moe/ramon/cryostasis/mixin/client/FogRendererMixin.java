package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.modules.render.NightvisionModule;
import moe.ramon.cryostasis.render.RenderUtil;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sweeps the horizon through the rainbow while Nightvision and Rainbow mode are both on, so the
 * sky matches the HUD theme. {@code setupFog} returns the fog color the sky blends into at the
 * horizon; recoloring it there tints the distance without touching the rest of the sky. The
 * alpha is left as vanilla set it so fog density is unchanged, only the hue moves.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@ModifyReturnValue(method = "setupFog", at = @At("RETURN"))
	private Vector4f cryostasis$rainbowHorizon(Vector4f color) {
		if (color == null || !HudColors.isRainbow()) {
			return color;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return color;
		}
		NightvisionModule nightvision = cryostasis.getModuleManager().get(NightvisionModule.class);
		if (nightvision != null && nightvision.isEnabled()) {
			float[] c = RenderUtil.argbToFloats(HudColors.rainbow(0.0f));
			color.set(c[0], c[1], c[2], color.w);
		}
		return color;
	}
}
