package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.NumberSetting;

/**
 * Narrows the field of view while enabled, giving a spyglass-style zoom. The FOV change
 * itself is applied by {@code GameRendererMixin}; this module only carries the toggle and
 * the zoom factor. Bind a key in the click GUI to flick it on and off.
 */
public final class ZoomModule extends Module {
	private final NumberSetting factor = register(new NumberSetting("Factor", 4.0, 1.5, 10.0, 0.5));

	public ZoomModule() {
		super("Zoom", "Zooms in by narrowing your field of view.", Category.RENDER);
	}

	public float getFactor() {
		return factor.getFloat();
	}
}
