package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.movement.NoCobwebModule;
import moe.ramon.cryostasis.modules.movement.NoSoulsandModule;
import moe.ramon.cryostasis.modules.render.FreecamModule;
import moe.ramon.cryostasis.modules.render.FreelookModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Local-player hooks that hang off {@link Entity}. The Mixin applies to every entity, so each
 * hook guards itself to the client's own player first. The physics hooks guard by UUID rather
 * than instance identity so that in singleplayer they catch both the client LocalPlayer (the
 * movement the player feels) and the integrated server's ServerPlayer (the authority that would
 * otherwise re-apply the slowdown and rubber-band the player back), matching the FastBreak
 * approach.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
	/**
	 * NoCobweb: skip the cobweb slowdown. Cobwebs slow entities by calling
	 * {@code makeStuckInBlock}; dropping that call for the local player while the block is a
	 * cobweb leaves normal movement intact. Guarded to cobwebs only so powder snow, sweet berry
	 * bushes, and anything else that also uses this path keep working.
	 */
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void cryostasis$noCobweb(BlockState state, Vec3 multiplier, CallbackInfo ci) {
		if (!cryostasis$isLocalPlayer()) {
			return;
		}
		if (!state.is(Blocks.COBWEB)) {
			return;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		NoCobwebModule module = cryostasis.getModuleManager().get(NoCobwebModule.class);
		if (module != null && module.isEnabled()) {
			ci.cancel();
		}
	}

	/**
	 * NoSoulsand: cancel the soul sand walking slowdown by returning the block speed factor to
	 * 1.0 when soul sand is the block that would slow the local player. Other slow blocks such as
	 * honey are left untouched because the fix only fires when soul sand is present.
	 */
	@ModifyReturnValue(method = "getBlockSpeedFactor", at = @At("RETURN"))
	private float cryostasis$noSoulsand(float original) {
		if (original == 1.0f || !cryostasis$isLocalPlayer()) {
			return original;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return original;
		}
		NoSoulsandModule module = cryostasis.getModuleManager().get(NoSoulsandModule.class);
		if (module == null || !module.isEnabled()) {
			return original;
		}
		Entity self = (Entity) (Object) this;
		Level level = self.level();
		if (level == null) {
			return original;
		}
		// Vanilla reads the block at the feet first and the block below when that is neutral, so
		// check both to know soul sand is the cause before overriding.
		if (level.getBlockState(self.blockPosition()).is(Blocks.SOUL_SAND)
				|| level.getBlockState(self.getBlockPosBelowThatAffectsMyMovement()).is(Blocks.SOUL_SAND)) {
			return 1.0f;
		}
		return original;
	}

	/**
	 * Freecam and Freelook: steer the free camera with the mouse instead of the body, leaving the
	 * head pointing where it was left. Identity against the client's player rather than the UUID
	 * guard the physics hooks use, because the mouse only ever turns that one entity, and a
	 * rotation the server made should still land. Freecam is asked first: it has the camera off
	 * the body entirely, so a glance underneath it would have nothing to aim.
	 */
	@Inject(method = "turn", at = @At("HEAD"), cancellable = true)
	private void cryostasis$freeView(double yRot, double xRot, CallbackInfo ci) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		FreecamModule freecam = cryostasis.getModuleManager().get(FreecamModule.class);
		if (freecam != null && freecam.isDetached()) {
			freecam.turn(yRot, xRot);
			ci.cancel();
			return;
		}
		FreelookModule freelook = cryostasis.getModuleManager().get(FreelookModule.class);
		if (freelook != null && freelook.isLooking()) {
			freelook.turn(yRot, xRot);
			ci.cancel();
		}
	}

	private boolean cryostasis$isLocalPlayer() {
		Player local = Minecraft.getInstance().player;
		if (local == null) {
			return false;
		}
		Entity self = (Entity) (Object) this;
		return self.getUUID().equals(local.getUUID());
	}
}
