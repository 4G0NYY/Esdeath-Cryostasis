package moe.ramon.cryostasis.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;

/**
 * Repaints the vanilla GUI widgets in the client theme.
 *
 * Every widget vanilla draws (button, tab, slider, text field, checkbox, scrollbar, tooltip)
 * gets its look from one blitSprite call naming a sprite such as widget/button. Both simpler
 * blitSprite overloads funnel into the same one, so dispatching on the sprite name at that
 * single point themes every widget on every screen: options, controls, video settings, the
 * pause menu, world and server select, world creation, and any mod screen built from the same
 * widgets. That is the whole reason the skin lives here rather than in a mixin per widget
 * class: there is no per-screen or per-widget special case to maintain.
 *
 * Sprites that carry meaning rather than a frame are deliberately left alone, since flattening
 * them into a plate would destroy the icon: the world creation lock button (widget/locked_button
 * and widget/unlocked_button) and the book page arrows (widget/page_forward and
 * widget/page_backward).
 *
 * The caller's color is folded in with ARGB.multiply rather than dropped. Vanilla passes white
 * scaled by the widget's alpha, so multiplying keeps the theme color while preserving the fade
 * a widget applies while a screen is appearing; for the usual opaque white it is the identity.
 */
public final class WidgetSkin {
	private WidgetSkin() {
	}

	/** Width of the accent stripe marking a hovered or focused widget, matching the click GUI. */
	private static final int STRIPE = 2;
	/** Inset of a checkbox's filled tick from its box edge. */
	private static final int TICK_INSET = 4;

	/**
	 * Paints the themed stand-in for a vanilla widget sprite.
	 *
	 * @return true if this sprite is themed and the vanilla blit should be skipped, false to let
	 *         it draw as usual.
	 */
	public static boolean paint(GuiGraphics graphics, ResourceLocation sprite,
			int x, int y, int width, int height, int color) {
		// Only vanilla's own widgets are themed. A mod shipping its own sprites keeps its look.
		if (!ResourceLocation.DEFAULT_NAMESPACE.equals(sprite.getNamespace())) {
			return false;
		}

		switch (sprite.getPath()) {
			// Buttons. Hover adds the same left accent stripe the click GUI rows use.
			case "widget/button" -> plate(graphics, x, y, width, height, Theme.ROW, Theme.ACCENT_DIM, color);
			case "widget/button_highlighted" -> {
				plate(graphics, x, y, width, height, Theme.ROW_HOVER, Theme.ACCENT, color);
				fill(graphics, x, y, STRIPE, height, Theme.ACCENT, color);
			}
			case "widget/button_disabled" ->
					plate(graphics, x, y, width, height, Theme.SETTING_ROW, Theme.ACCENT_DIM, color);

			// Tabs across the top of the options screens. The selected one is marked with an
			// accent underline, since vanilla carries that distinction in the sprite art.
			case "widget/tab" -> plate(graphics, x, y, width, height, Theme.SETTING_ROW, Theme.ACCENT_DIM, color);
			case "widget/tab_highlighted" ->
					plate(graphics, x, y, width, height, Theme.ROW_HOVER, Theme.ACCENT_DIM, color);
			case "widget/tab_selected", "widget/tab_selected_highlighted" -> {
				plate(graphics, x, y, width, height, Theme.HEADER, Theme.ACCENT, color);
				fill(graphics, x, y + height - STRIPE, width, STRIPE, Theme.ACCENT, color);
			}

			// Sliders: a sunken track with an accent handle riding in it.
			case "widget/slider" -> plate(graphics, x, y, width, height, Theme.CELL, Theme.CELL_BORDER, color);
			case "widget/slider_highlighted" -> plate(graphics, x, y, width, height, Theme.CELL, Theme.ACCENT, color);
			case "widget/slider_handle" -> plate(graphics, x, y, width, height, Theme.ACCENT_DIM, Theme.ACCENT, color);
			case "widget/slider_handle_highlighted" ->
					plate(graphics, x, y, width, height, Theme.ACCENT, Theme.TEXT, color);

			// Text fields, including the multi-line text areas.
			case "widget/text_field" -> plate(graphics, x, y, width, height, Theme.CELL, Theme.CELL_BORDER, color);
			case "widget/text_field_highlighted" ->
					plate(graphics, x, y, width, height, Theme.CELL, Theme.ACCENT, color);

			// Checkboxes. The tick is a filled inset square rather than a glyph, so it stays
			// legible at the sizes vanilla uses without needing a texture.
			case "widget/checkbox" -> plate(graphics, x, y, width, height, Theme.CELL, Theme.CELL_BORDER, color);
			case "widget/checkbox_highlighted" ->
					plate(graphics, x, y, width, height, Theme.CELL, Theme.ACCENT, color);
			case "widget/checkbox_selected" -> {
				plate(graphics, x, y, width, height, Theme.CELL, Theme.CELL_BORDER, color);
				tick(graphics, x, y, width, height, Theme.ACCENT, color);
			}
			case "widget/checkbox_selected_highlighted" -> {
				plate(graphics, x, y, width, height, Theme.CELL, Theme.ACCENT, color);
				tick(graphics, x, y, width, height, Theme.TEXT, color);
			}

			// Scrollbars on every scrolling list.
			case "widget/scroller_background" -> fill(graphics, x, y, width, height, Theme.CELL, color);
			case "widget/scroller" -> plate(graphics, x, y, width, height, Theme.ACCENT_DIM, Theme.ACCENT, color);

			// Tooltips, drawn as a background pass followed by a frame pass over the same box.
			case "tooltip/background" -> fill(graphics, x, y, width, height, Theme.PANEL_SOLID, color);
			case "tooltip/frame" -> Skin.border(graphics, x, y, x + width, y + height, tint(Theme.ACCENT, color));

			default -> {
				return false;
			}
		}
		return true;
	}

	private static void plate(GuiGraphics graphics, int x, int y, int width, int height,
			int fill, int border, int color) {
		Skin.plate(graphics, x, y, width, height, tint(fill, color), tint(border, color));
	}

	private static void fill(GuiGraphics graphics, int x, int y, int width, int height, int color, int tint) {
		graphics.fill(x, y, x + width, y + height, tint(color, tint));
	}

	private static void tick(GuiGraphics graphics, int x, int y, int width, int height, int color, int tint) {
		graphics.fill(x + TICK_INSET, y + TICK_INSET, x + width - TICK_INSET, y + height - TICK_INSET,
				tint(color, tint));
	}

	private static int tint(int base, int color) {
		return ARGB.multiply(base, color);
	}
}
