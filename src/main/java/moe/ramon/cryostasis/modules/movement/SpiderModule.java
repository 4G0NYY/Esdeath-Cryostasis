package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Lets the player climb straight walls like a spider. The behavior lives in
 * {@code LivingEntityMixin}, which forces {@code onClimbable} true for the local player while it
 * is pressed against a wall, so the game applies ladder physics: hold forward into the wall to
 * go up, sneak to hold position. This module is just the gate the Mixin reads.
 */
public final class SpiderModule extends Module {
	public SpiderModule() {
		super("Spider", "Climb straight up walls like a spider.", Category.MOVEMENT);
	}
}
