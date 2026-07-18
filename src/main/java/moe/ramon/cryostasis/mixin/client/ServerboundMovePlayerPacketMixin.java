package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.movement.ZootModule;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Implements Zoot (NoFall) by reporting the player as on the ground in outgoing movement packets
 * while they are airborne. Every position packet the client sends flows through this abstract
 * base constructor (each subtype calls it), where the onGround flag is stored, so one hook on the
 * first boolean argument covers Pos, PosRot, Rot, and StatusOnly alike. The server derives fall
 * damage from the run of not-on-ground packets, so a player that always claims to be grounded
 * never accrues a fall to be hurt by.
 */
@Mixin(ServerboundMovePlayerPacket.class)
public abstract class ServerboundMovePlayerPacketMixin {
	// Must be static: this handler injects at HEAD of the constructor, before the super() call,
	// where `this` is not yet constructed. Mixin rejects a non-static @ModifyVariable in that
	// position, and the failure cascades into Fabric's VanillaPacketTypes.<clinit>, breaking all
	// networking (world join/create) with NoClassDefFoundError. The body uses only static
	// accessors, so static is a clean fit.
	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private static boolean cryostasis$noFall(boolean onGround) {
		if (onGround) {
			// Already grounded: nothing to spoof.
			return true;
		}
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return onGround;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return onGround;
		}
		ZootModule zoot = cryostasis.getModuleManager().get(ZootModule.class);
		if (zoot != null && zoot.isEnabled()) {
			return true;
		}
		return onGround;
	}
}
