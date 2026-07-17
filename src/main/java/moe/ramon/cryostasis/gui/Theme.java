package moe.ramon.cryostasis.gui;

/**
 * Shared color palette for every screen the client themes. Tuned to the original Esdeath navy
 * and steel look: deep blue background, translucent slate panels, a single steel-blue accent.
 * Kept in one place so the title screen, the click GUI, the vanilla menus, and the container
 * screens stay visually consistent and a future retheme is a single-file edit.
 *
 * All values are packed ARGB ints, matching what GuiGraphics fill and text draws expect.
 */
public final class Theme {
	private Theme() {
	}

	// Backdrops.
	public static final int BG_TOP = 0xFF0E1B2B;
	public static final int BG_BOTTOM = 0xFF05090F;
	public static final int DIM = 0xC00A111B;
	/** List bodies, which sit over a backdrop and so stay translucent. */
	public static final int LIST = 0xC00A111B;

	// Panels and rows.
	public static final int PANEL = 0xC0161F2C;
	/** Panels that must fully hide what is behind them: container bodies and tooltips. */
	public static final int PANEL_SOLID = 0xF01A2433;
	public static final int HEADER = 0xFF14202F;
	public static final int ROW = 0xC0121A25;
	public static final int ROW_HOVER = 0xD01F2C3E;
	public static final int SETTING_ROW = 0xB00C121B;

	// Inputs and cells: text fields, slider tracks, scrollbar troughs, inventory slots.
	public static final int FIELD = 0xFF10161F;
	public static final int CELL = 0xFF0B0F16;
	public static final int CELL_BORDER = 0xFF2A3648;

	// Accent and text.
	public static final int ACCENT = 0xFF5A8FC7;
	public static final int ACCENT_DIM = 0xFF2C4A63;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int SUBTEXT = 0xFF9AA7B8;
}
