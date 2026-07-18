package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Cancels the walking slowdown from soul sand, so the player crosses it at normal speed.
 * The behavior lives in {@code EntityMixin}, which forces the block speed factor back to
 * 1.0 for the local player while soul sand is the block that would slow it. This module is
 * just the gate the Mixin reads.
 */
public final class NoSoulsandModule extends Module {
	public NoSoulsandModule() {
		super("NoSoulsand", "Ignore the slowdown from soul sand.", Category.MOVEMENT);
	}
}
