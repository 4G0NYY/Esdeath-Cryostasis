package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.modules.combat.MoreParticlesModule;
import moe.ramon.cryostasis.modules.combat.SharpnessModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spawns extra crit particles when the player attacks, honoring the MoreParticles and
 * Sharpness gating recovered from the original client. Purely visual: it calls the same
 * {@code crit} the game uses for a natural critical hit, so no damage or hit logic
 * changes and nothing is sent that a server would not already see from a real crit.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void cryostasis$critParticles(Player player, Entity target, CallbackInfo ci) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null || !(target instanceof LivingEntity)) {
			return;
		}
		ModuleManager modules = cryostasis.getModuleManager();
		MoreParticlesModule more = modules.get(MoreParticlesModule.class);
		SharpnessModule sharp = modules.get(SharpnessModule.class);
		boolean moreOn = more != null && more.isEnabled();
		boolean sharpOn = sharp != null && sharp.isEnabled();

		if (moreOn) {
			// Triple burst, but only on a real crit unless Sharpness overrides the gate.
			if (sharpOn || isLegitCrit(player)) {
				player.crit(target);
				player.crit(target);
				player.crit(target);
			}
		} else if (sharpOn) {
			player.crit(target);
		}
	}

	private static boolean isLegitCrit(Player player) {
		return player.fallDistance > 0
				&& !player.onGround()
				&& !player.isInWater()
				&& !player.onClimbable()
				&& !player.isPassenger();
	}
}
