package moe.ramon.cryostasis.modules.combat;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.NumberSetting;

/**
 * Extends how far the player can hit and touch. Vanilla keeps both distances as attributes
 * (3.0 blocks to an entity, 4.5 to a block, more in creative), and everything downstream reads
 * them: the crosshair ray that picks a target, and the check that decides whether the swing
 * counts. {@code PlayerMixin} raises what those two attributes report, so the longer reach
 * applies to aiming and to hitting together and there is no separate targeting code.
 *
 * Neither setting ever shortens anything. The Mixin takes the larger of the vanilla value and
 * the one set here, so creative keeps the longer reach it already had, and a setting left at the
 * bottom of its range is simply inert.
 *
 * The Mixin is keyed on the player's UUID rather than on the client's own player object, so in
 * singleplayer the integrated server's copy of the player gets the same answer and agrees that
 * the hit was in range. A fair-play multiplayer server runs its own check with its own numbers,
 * and beyond about 3 blocks of entity reach it will start throwing the hits away.
 */
public final class ReachModule extends Module {
	private final NumberSetting entities = register(new NumberSetting("Entities", 4.5, 3.0, 12.0, 0.1));
	private final NumberSetting blocks = register(new NumberSetting("Blocks", 5.0, 4.5, 12.0, 0.1));

	public ReachModule() {
		super("Reach", "Hit and touch things further away.", Category.COMBAT);
	}

	public double entityRange() {
		return entities.get();
	}

	public double blockRange() {
		return blocks.get();
	}
}
