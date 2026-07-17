package moe.ramon.cryostasis.modules.misc;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Adds a small "Take All" button to chest-like containers that empties them into the player
 * inventory in one click. The button and click handling live in {@code TakeAllMixin}; this
 * module is the gate it reads, so the feature can be toggled from the menu like any other.
 */
public final class TakeAllModule extends Module {
	public TakeAllModule() {
		super("TakeAll", "Adds a one-click Take All button to chests.", Category.MISC);
		// On by default: this is a convenience with no gameplay downside.
		setEnabled(true);
	}
}
