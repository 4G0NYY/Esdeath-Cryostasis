package moe.ramon.cryostasis.modules.player;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.NumberSetting;

/**
 * Speeds up block breaking by multiplying the player's mining speed. The multiply happens in
 * {@code PlayerMixin} on {@code getDestroySpeed}, so it feeds the same value the game uses for
 * every hardness and tool. This module is the gate the Mixin reads plus the tunable factor.
 *
 * The factor is applied to both the client-predicted break (so mining feels instant) and, in
 * singleplayer, the integrated server's own check, because the Mixin matches on the player UUID
 * rather than the client entity; that keeps the two sides in step so blocks do not snap back.
 */
public final class FastBreakModule extends Module {
	private final NumberSetting multiplier = register(new NumberSetting("Multiplier", 3.0, 1.0, 10.0, 0.1));

	public FastBreakModule() {
		super("FastBreak", "Mine and break blocks faster.", Category.PLAYER);
	}

	/** The mining-speed multiplier the Mixin folds into getDestroySpeed. */
	public float multiplier() {
		return multiplier.getFloat();
	}
}
