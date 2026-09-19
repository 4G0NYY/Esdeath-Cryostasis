package moe.ramon.cryostasis.hud;

import moe.ramon.cryostasis.gui.Theme;

/**
 * One row of a HUD readout: a quiet label and the value it names. {@code color} is the value's,
 * and anything other than {@link Theme#TEXT} is taken to mean something, so rainbow mode leaves
 * it alone.
 */
public record HudLine(String label, String value, int color) {
	public static HudLine of(String label, String value) {
		return new HudLine(label, value, Theme.TEXT);
	}
}
