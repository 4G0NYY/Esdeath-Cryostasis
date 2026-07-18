package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Cancels fall damage. The work lives in {@code ServerboundMovePlayerPacketMixin}, which reports
 * the player as on the ground in every movement packet while this is on. The server tracks a
 * fall from the run of not-on-ground packets and applies the damage on landing; a player that is
 * always on the ground never accrues a fall, so no damage is dealt. This works the same in
 * singleplayer and multiplayer because both drive fall damage from those packets. This module is
 * just the gate the Mixin reads.
 */
public final class ZootModule extends Module {
	public ZootModule() {
		super("Zoot", "Take no fall damage.", Category.MOVEMENT);
	}
}
