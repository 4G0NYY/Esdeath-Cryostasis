package moe.ramon.cryostasis.modules.combat;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Triples the crit particle burst on hit. Gated against {@link SharpnessModule} exactly
 * as the original: with Sharpness off it only fires on a legitimate crit; with Sharpness
 * on it fires on every hit. The trigger lives in {@code MultiPlayerGameModeMixin}.
 */
public final class MoreParticlesModule extends Module {
	public MoreParticlesModule() {
		super("MoreParticles", "Triples crit particles, on legit crits unless Sharpness is on.", Category.COMBAT);
	}
}
