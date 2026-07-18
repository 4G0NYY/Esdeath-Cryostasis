package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.movement.SafeWalkModule;
import moe.ramon.cryostasis.modules.player.FastBreakModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Local-player hooks that hang off {@link Player}. The Mixin applies to every Player instance, so
 * each hook guards itself to the client's own player before acting.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	/**
	 * SafeWalk: force the edge back-off gate on. Vanilla only backs a player off a ledge when
	 * {@code isStayingOnGroundSurface} is true (which crouching sets); returning true here
	 * reproduces that without the crouch.
	 */
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

	/**
	 * FastBreak: scale the mining speed. Guarded by UUID rather than entity identity so that in
	 * singleplayer it catches both the client's LocalPlayer (the break the player sees) and the
	 * integrated server's ServerPlayer (the authority that would otherwise reject a too-fast
	 * break); the two share a UUID, so the speeds stay in agreement and blocks do not snap back.
	 */
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void cryostasis$fastBreak(BlockState state, CallbackInfoReturnable<Float> cir) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		Player self = (Player) (Object) this;
		Player local = Minecraft.getInstance().player;
		if (local == null || !self.getUUID().equals(local.getUUID())) {
			return;
		}
		FastBreakModule fastBreak = cryostasis.getModuleManager().get(FastBreakModule.class);
		if (fastBreak != null && fastBreak.isEnabled()) {
			// Zero stays zero: an unbreakable block must remain unbreakable.
			cir.setReturnValue(cir.getReturnValueF() * fastBreak.multiplier());
		}
	}
}
