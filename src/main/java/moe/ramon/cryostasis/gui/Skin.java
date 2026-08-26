package moe.ramon.cryostasis.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The drawing primitives every themed surface is built from. Shared so the click GUI, the
 * container skin, and the vanilla widget skin all produce the same plate: one fill and a one
 * pixel border, no nine-slice and no texture.
 */
public final class Skin {
	private static final int SHADOW = 0x60000000;

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

	/**
	 * A plate with its four corner pixels clipped off, which is as round as a surface built from
	 * axis-aligned fills gets. Used by the overlay panels, where a hard corner against the world
	 * behind it reads as a debug rectangle rather than as part of the client.
	 */
	public static void panel(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
		graphics.fill(x + 1, y, x + width - 1, y + 1, fill);
		graphics.fill(x, y + 1, x + width, y + height - 1, fill);
		graphics.fill(x + 1, y + height - 1, x + width - 1, y + height, fill);
	}

	/** The same shape offset by a pixel in translucent black, to lift a panel off the world. */
	public static void shadow(GuiGraphics graphics, int x, int y, int width, int height) {
		shadow(graphics, x, y, width, height, 1.0f);
	}

	/** A shadow for a panel that is itself fading, so the two arrive and leave together. */
	public static void shadow(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
		panel(graphics, x + 1, y + 1, width, height, fade(SHADOW, alpha));
	}

	/** Rescale a packed ARGB colour's alpha, for anything that fades in or out. */
	public static int fade(int argb, float alpha) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
