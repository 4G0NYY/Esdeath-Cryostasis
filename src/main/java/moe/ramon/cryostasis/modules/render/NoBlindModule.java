package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Ignores the blindness effect: the thick fog and darkening blindness normally imposes are
 * suppressed so vision stays clear. The behavior lives in {@code MobEffectFogEnvironmentMixin},
 * which reports the blindness fog environment as not applicable for the local view while this
 * is on. Client side only: it changes nothing the server sees, only what is drawn.
 */
public final class NoBlindModule extends Module {
	public NoBlindModule() {
		super("NoBlind", "Ignore the blindness effect.", Category.RENDER);
	}
}
