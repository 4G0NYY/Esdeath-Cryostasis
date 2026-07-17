package moe.ramon.cryostasis.modules.combat;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Spawns a single crit particle burst on every hit. Defers entirely to
 * {@link MoreParticlesModule} when that is enabled, so the two never double up. Despite
 * the combat-sounding name it only affects particles, never damage. The trigger lives in
 * {@code MultiPlayerGameModeMixin}.
 */
public final class SharpnessModule extends Module {
	public SharpnessModule() {
		super("Sharpness", "Shows crit particles on every hit.", Category.COMBAT);
	}
}
