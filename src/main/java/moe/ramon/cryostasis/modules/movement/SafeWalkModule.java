package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Stops the player from walking off the edge of a block, the way sneaking does but without
 * actually crouching. The behavior lives in {@code PlayerMixin}, which forces the edge
 * back-off check on for the local player while this is enabled. This module is just the gate
 * the Mixin reads.
 *
 * It is designed to sit alongside AutoPath: SafeWalk is the safety net, AutoPath is the
 * bridge builder. AutoPath places a block into the space ahead before the player reaches it,
 * so on a normal step there is ground to walk onto and SafeWalk never triggers; when AutoPath
 * cannot place (no blocks, no support), SafeWalk still catches the fall.
 */
public final class SafeWalkModule extends Module {
	public SafeWalkModule() {
		super("SafeWalk", "Never walk off the edge of a block.", Category.MOVEMENT);
	}
}
