package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.render.NightvisionModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drives Nightvision's brightness from where the lightmap is built. Two of the local player's
 * effect reads inside {@code updateLightTexture} are wrapped:
 *
 * <ul>
 *   <li>the night-vision presence check is forced true, so the lightmap takes the bright
 *       night-vision path (paired with {@code GameRendererMixin} pinning the scale to full);</li>
 *   <li>the darkness blend read is forced to zero, so the darkness effect cannot dim the view.</li>
 * </ul>
 *
 * Wrapping the specific calls keeps the change confined to the lightmap: gameplay reads of the
 * same effects elsewhere are untouched.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
	@WrapOperation(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
	private boolean cryostasis$forceNightVision(LocalPlayer player, Holder<MobEffect> effect, Operation<Boolean> original) {
		if (effect == MobEffects.NIGHT_VISION && cryostasis$nightvisionOn()) {
			return true;
		}
		return original.call(player, effect);
	}

	@WrapOperation(method = "updateLightTexture",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float cryostasis$ignoreDarkness(LocalPlayer player, Holder<MobEffect> effect, float partial, Operation<Float> original) {
		if (effect == MobEffects.DARKNESS && cryostasis$nightvisionOn()) {
			return 0.0f;
		}
		return original.call(player, effect, partial);
	}

	private boolean cryostasis$nightvisionOn() {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return false;
		}
		NightvisionModule nightvision = cryostasis.getModuleManager().get(NightvisionModule.class);
		return nightvision != null && nightvision.isEnabled();
	}
}
