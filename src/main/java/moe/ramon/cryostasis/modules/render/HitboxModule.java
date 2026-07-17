package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.ColorSetting;
import moe.ramon.cryostasis.setting.NumberSetting;

/**
 * Draws expanded outlines around entity hitboxes. This module only holds state and
 * settings; the actual drawing is done from a world-render hook (see WorldRenderHooks)
 * so it participates in the normal render pass rather than needing its own Mixin.
 */
public final class HitboxModule extends Module {
	private final ColorSetting color = register(new ColorSetting("Color", 0xFFFF5555));
	private final NumberSetting expand = register(new NumberSetting("Expand", 0.1, 0.0, 0.5, 0.05));

	public HitboxModule() {
		super("Hitbox", "Outlines entity hitboxes.", Category.RENDER);
	}

	public int getColor() {
		return color.get();
	}

	public double getExpand() {
		return expand.get();
	}
}
