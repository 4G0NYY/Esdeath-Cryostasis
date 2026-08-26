package moe.ramon.cryostasis.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Small helper for the common HUD case of drawing one or more anchored text lines over a
 * translucent backdrop and reporting the bounds back to a {@link HudModule}. Keeps each
 * module's render method to the logic that is actually unique to it.
 *
 * The anchor resolves against the whole plate rather than against the text inside it, so the
 * rectangle the editor drags is the same rectangle the position is measured from. Resolving one
 * and reporting the other makes every drag creep by the padding.
 *
 * Color is resolved through {@link HudColors}: when rainbow mode is on the passed color is
 * overridden with the sweep so every element cycles together, with a small per-line phase
 * offset so stacked lines read as a gradient.
 */
public final class HudText {
	public static final int WHITE = 0xFFFFFFFF;

	// Themed translucent plate behind text, matching the navy click GUI rows.
	private static final int BACKGROUND = 0x900A111B;
	private static final int PAD_X = 2;
	private static final int PAD_Y = 1;

	private HudText() {
	}

	/** Draw a single line at the module's anchor in the default color. */
	public static void drawLine(HudModule module, GuiGraphics context, String text) {
		drawLine(module, context, text, WHITE);
	}

	/** Draw a single line at the module's anchor and record its bounds. */
	public static void drawLine(HudModule module, GuiGraphics context, String text, int color) {
		drawLines(module, context, List.of(text), color);
	}

	/** Draw a stack of lines at the module's anchor in the default color. */
	public static void drawLines(HudModule module, GuiGraphics context, List<String> lines) {
		drawLines(module, context, lines, WHITE);
	}

	/** Draw a stack of lines at the module's anchor and record the combined bounds. */
	public static void drawLines(HudModule module, GuiGraphics context, List<String> lines, int color) {
		draw(module, context, lines, index -> color);
	}

	/**
	 * Draw a stack of lines, each in its own colour, and record the combined bounds. For elements
	 * where the colour says something about the line it is on rather than styling the block as a
	 * whole. {@code colors} must hold at least one entry per line.
	 */
	public static void drawLines(HudModule module, GuiGraphics context, List<String> lines, int[] colors) {
		draw(module, context, lines, index -> colors[index]);
	}

	private static void draw(HudModule module, GuiGraphics context, List<String> lines,
			IntUnaryOperator colorAt) {
		Font font = Minecraft.getInstance().font;
		int lineHeight = font.lineHeight + 1;
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		int width = widest + PAD_X * 2;
		int height = lineHeight * lines.size() + PAD_Y * 2;
		int x = module.resolveX(context.guiWidth(), width);
		int y = module.resolveY(context.guiHeight(), height);

		context.fill(x, y, x + width, y + height, BACKGROUND);
		int cursor = y + PAD_Y;
		for (int i = 0; i < lines.size(); i++) {
			int lineColor = HudColors.isRainbow() ? HudColors.rainbow(i * 0.08f) : colorAt.applyAsInt(i);
			context.drawString(font, lines.get(i), x + PAD_X, cursor, lineColor);
			cursor += lineHeight;
		}
		module.setBounds(x, y, width, height);
	}
}
