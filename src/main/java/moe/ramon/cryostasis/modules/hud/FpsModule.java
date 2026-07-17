package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import moe.ramon.cryostasis.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Frames per second readout. The trivial end to end module that proves the pipeline:
 * config, HUD anchoring, and the click GUI all exercise this with no game hooks.
 */
public final class FpsModule extends HudModule {
	private final BooleanSetting showLabel = register(new BooleanSetting("Label", true));

	public FpsModule() {
		super("FPS", "Shows your current framerate.", 0.01, 0.05);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		String text = Integer.toString(mc.getFps());
		if (showLabel.get()) {
			text = text + " FPS";
		}
		HudText.drawLine(this, context, text);
	}
}
