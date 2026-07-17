package moe.ramon.cryostasis.gui;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Paints the themed panel that stands in for a container screen's background texture.
 *
 * Container screens do far more inside renderBg than blit their background: the survival
 * inventory draws the player model there, the furnace its burn arrows, the enchanting table
 * its level names and lapis costs, the creative screen its whole tab strip. So the skin
 * cannot replace renderBg wholesale. Instead renderBg is allowed to run and only its
 * background-texture blit is swapped for this panel, which is the one call every container
 * screen makes and nothing else does: sprites, text, items, and entities all reach
 * GuiGraphics through other methods.
 *
 * The swap happens at the exact point the texture would have been drawn, so whatever a
 * screen layers under or over its background keeps its original z-order.
 *
 * State is static rather than passed down because the interception point is inside
 * GuiGraphics, several frames removed from the screen. That is safe here: this is only ever
 * touched from the render thread, between begin and end of a single renderBg call.
 */
public final class ContainerSkin {
	private ContainerSkin() {
	}

	private static final int PANEL = 0xF01A2433;
	private static final int FIELD = 0xFF10161F;
	private static final int CELL = 0xFF0B0F16;
	private static final int CELL_BORDER = 0xFF2A3648;

	private static boolean active;
	private static boolean painted;
	private static int leftPos;
	private static int topPos;
	private static int imageWidth;
	private static int imageHeight;
	private static List<Slot> slots = List.of();

	/** Arms the skin for one renderBg call, capturing the geometry the panel needs. */
	public static void begin(int left, int top, int width, int height, List<Slot> menuSlots) {
		active = true;
		painted = false;
		leftPos = left;
		topPos = top;
		imageWidth = width;
		imageHeight = height;
		slots = menuSlots;
	}

	public static void end() {
		active = false;
		// Dropped so a closed screen's slots are not held alive until the next container opens.
		slots = List.of();
	}

	public static boolean isActive() {
		return active;
	}

	/**
	 * Paints the panel in place of the first background blit and swallows the rest. Screens
	 * such as the chest blit their background in several pieces, and one panel already covers
	 * the whole body.
	 */
	public static void paintOnce(GuiGraphics graphics) {
		if (painted) {
			return;
		}
		painted = true;

		int x0 = leftPos;
		int y0 = topPos;
		int x1 = leftPos + imageWidth;
		int y1 = topPos + imageHeight;

		graphics.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, PANEL);
		border(graphics, x0 - 3, y0 - 3, x1 + 3, y1 + 3, Theme.ACCENT);
		graphics.fill(x0, y0, x1, y1, FIELD);

		// Cells come from the menu's own slot list, so every container gets a grid that matches
		// its real layout without a texture or a hand-placed rect per screen.
		for (Slot slot : slots) {
			int sx = leftPos + slot.x;
			int sy = topPos + slot.y;
			graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, CELL);
			border(graphics, sx - 1, sy - 1, sx + 17, sy + 17, CELL_BORDER);
		}
	}

	private static void border(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}
}
