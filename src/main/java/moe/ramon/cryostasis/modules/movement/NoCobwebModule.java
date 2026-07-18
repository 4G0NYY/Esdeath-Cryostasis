package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Cancels the movement slowdown from cobwebs, so the player walks through them at normal
 * speed. The behavior lives in {@code EntityMixin}, which drops the {@code makeStuckInBlock}
 * call for the local player when the block is a cobweb. This module is just the gate the
 * Mixin reads.
 */
public final class NoCobwebModule extends Module {
	public NoCobwebModule() {
		super("NoCobweb", "Ignore the slowdown from cobwebs.", Category.MOVEMENT);
	}
}
