package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.ColorSetting;

/**
 * Recolors the block selection outline. State and color only; the draw and the
 * suppression of the vanilla outline happen in the world-render hook.
 */
public final class BlockOutlineModule extends Module {
	private final ColorSetting color = register(new ColorSetting("Color", 0xFF64D2FF));

	public BlockOutlineModule() {
		super("BlockOutline", "Recolors the block selection outline.", Category.RENDER);
	}

	public int getColor() {
		return color.get();
	}
}
