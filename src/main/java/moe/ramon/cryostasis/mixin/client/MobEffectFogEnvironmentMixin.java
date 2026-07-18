package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.modules.render.NightvisionModule;
import moe.ramon.cryostasis.modules.render.NoBlindModule;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Suppresses the fog effects tied to mob effects. In 1.21.8 each such effect (blindness,
 * darkness) is a {@link MobEffectFogEnvironment} that opts in through {@code isApplicable}; if
 * that reports false the environment is never selected, so both its fog and its darkening are
 * skipped. NoBlind hides blindness, Nightvision hides darkness. Both subclasses inherit this
 * one {@code isApplicable}, and {@code getMobEffect} tells them apart at runtime.
 */
@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {
	@ModifyReturnValue(method = "isApplicable", at = @At("RETURN"))
	private boolean cryostasis$suppress(boolean applicable) {
		if (!applicable) {
			return false;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return true;
		}
		Holder<MobEffect> effect = ((MobEffectFogEnvironment) (Object) this).getMobEffect();
		ModuleManager modules = cryostasis.getModuleManager();
		if (effect == MobEffects.BLINDNESS) {
			NoBlindModule noBlind = modules.get(NoBlindModule.class);
			if (noBlind != null && noBlind.isEnabled()) {
				return false;
			}
		} else if (effect == MobEffects.DARKNESS) {
			NightvisionModule nightvision = modules.get(NightvisionModule.class);
			if (nightvision != null && nightvision.isEnabled()) {
				return false;
			}
		}
		return true;
	}
}
