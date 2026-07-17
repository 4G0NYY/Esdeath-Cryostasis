package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.movement.SafeWalkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements SafeWalk by forcing the edge back-off gate on for the local player. Vanilla only
 * backs a player off a ledge when {@code isStayingOnGroundSurface} is true (which crouching
 * sets); returning true here reproduces that without the crouch. The Mixin applies to every
 * Player, so it is guarded to the client's own player only.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
	private void cryostasis$safeWalk(CallbackInfoReturnable<Boolean> cir) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		SafeWalkModule safeWalk = cryostasis.getModuleManager().get(SafeWalkModule.class);
		if (safeWalk != null && safeWalk.isEnabled()) {
			cir.setReturnValue(true);
		}
	}
}
