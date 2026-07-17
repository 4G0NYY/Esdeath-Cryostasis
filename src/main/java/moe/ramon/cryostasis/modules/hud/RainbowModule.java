package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.NumberSetting;

/**
 * Toggles the client-wide rainbow sweep on the HUD text. It draws nothing itself; enabling
 * it flips the flag in {@link HudColors} that every HUD element reads its color through, so
 * the FPS, CPS, coordinate, and ArrayList text all cycle together.
 */
public final class RainbowModule extends Module {
	private final NumberSetting speed = register(new NumberSetting("Speed", 1.0, 0.2, 5.0, 0.1));

	public RainbowModule() {
		super("Rainbow", "Cycles the HUD text through the color spectrum.", Category.HUD);
	}

	@Override
	public void onEnable() {
		HudColors.setSpeed(speed.get().floatValue());
		HudColors.setRainbow(true);
	}

	@Override
	public void onDisable() {
		HudColors.setRainbow(false);
	}

	@Override
	public void onTick() {
		// Pick up live speed edits from the click GUI while the sweep is running.
		HudColors.setSpeed(speed.get().floatValue());
	}
}
