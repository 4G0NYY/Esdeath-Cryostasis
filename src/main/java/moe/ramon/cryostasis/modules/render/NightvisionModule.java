package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Keeps the world lit as though the night vision potion were always active, and ignores the
 * darkness effect that would otherwise pulse the screen dark. The behavior lives across the
 * render Mixins:
 *
 * <ul>
 *   <li>{@code LightTextureMixin} feeds the lightmap full night-vision brightness and zeroes the
 *       darkness blend, so the view stays bright.</li>
 *   <li>{@code GameRendererMixin} pins the night-vision scale to full so brightness never dips.</li>
 *   <li>{@code MobEffectFogEnvironmentMixin} suppresses the darkness fog environment.</li>
 *   <li>{@code FogRendererMixin} sweeps the horizon through the rainbow while Rainbow mode is on,
 *       so the sky matches the HUD theme.</li>
 * </ul>
 *
 * Client side only: nothing is sent to the server, this only changes what is drawn.
 */
public final class NightvisionModule extends Module {
	public NightvisionModule() {
		super("Nightvision", "Keep the world bright and ignore darkness, like the night vision potion.", Category.RENDER);
	}
}
