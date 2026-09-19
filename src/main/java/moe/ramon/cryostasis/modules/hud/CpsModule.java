package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.hud.HudLine;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import moe.ramon.cryostasis.service.ClickTracker;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.ModeSetting;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Clicks per second readout. Reads from the shared {@link ClickTracker}, which is fed
 * by the mouse Mixin, so it reflects real GLFW clicks rather than in game attack ticks.
 */
public final class CpsModule extends HudModule {
	private final ModeSetting button = register(new ModeSetting("Button", "Left", List.of("Left", "Right", "Both")));
	private final BooleanSetting showLabel = register(new BooleanSetting("Label", true));

	public CpsModule() {
		// Default to the top left, matching the original client's HUD panel corner.
		super("CPS", "Shows your clicks per second.", 0.01, 0.02);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		ClickTracker tracker = Cryostasis.get().getClickTracker();
		long now = System.currentTimeMillis();

		String value;
		if (button.is("Both")) {
			value = tracker.cps(ClickTracker.LEFT, now) + " \u00b7 " + tracker.cps(ClickTracker.RIGHT, now);
		} else {
			int which = button.is("Right") ? ClickTracker.RIGHT : ClickTracker.LEFT;
			value = Integer.toString(tracker.cps(which, now));
		}
		HudText.draw(this, context, HudLine.of(showLabel.get() ? "CPS" : "", value));
	}
}
