package moe.ramon.cryostasis.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The drawing primitives every themed surface is built from. Shared so the click GUI, the
 * container skin, and the vanilla widget skin all produce the same plate: one fill and a one
 * pixel border, no nine-slice and no texture.
 */
public final class Skin {
	private Skin() {
	}

	/** A one pixel border drawn inside the given bounds. */
	public static void border(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}

	/** A filled, bordered plate: the shape every button, field, and panel in the client uses. */
	public static void plate(GuiGraphics graphics, int x, int y, int width, int height, int fill, int border) {
		graphics.fill(x, y, x + width, y + height, fill);
		border(graphics, x, y, x + width, y + height, border);
	}
}
