package moe.ramon.cryostasis.modules.player;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Keeps the player sprinting while moving forward, so sprint does not have to be held or
 * double tapped. Client side only: it sets the local sprint flag, which vanilla already
 * syncs to the server the same way a normal sprint does.
 */
public final class ToggleSprintModule extends Module {
	public ToggleSprintModule() {
		super("ToggleSprint", "Automatically sprints while moving forward.", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.options == null) {
			return;
		}
		// Only sprint when actually moving forward and able to: mirrors vanilla's own
		// sprint preconditions closely enough without fighting the hunger/collision checks.
		if (mc.options.keyUp.isDown() && !mc.player.horizontalCollision && !mc.player.isShiftKeyDown()) {
			mc.player.setSprinting(true);
		}
	}

	@Override
	public void onDisable() {
		if (mc.player != null) {
			mc.player.setSprinting(false);
		}
	}
}
