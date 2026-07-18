package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.render.NightvisionModule;
import moe.ramon.cryostasis.modules.render.ZoomModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the Zoom module by dividing the computed field of view. Uses a return-value
 * modifier so it composes cleanly with vanilla's own FOV math (sprint, speed effects,
 * spyglass) instead of overwriting it. Also pins the night-vision scale to full for
 * Nightvision.
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

	/**
	 * Nightvision: return full night-vision brightness for the local player. This runs at HEAD
	 * with an early return rather than a return-value modifier because the vanilla body reads the
	 * effect instance first and would throw when the player does not actually have night vision
	 * (which is exactly the case Nightvision creates through the lightmap hook).
	 */
	@Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
	private static void cryostasis$forceNightVision(LivingEntity entity, float partialTick,
			CallbackInfoReturnable<Float> cir) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null || entity != Minecraft.getInstance().player) {
			return;
		}
		NightvisionModule nightvision = cryostasis.getModuleManager().get(NightvisionModule.class);
		if (nightvision != null && nightvision.isEnabled()) {
			cir.setReturnValue(1.0f);
		}
	}
}
