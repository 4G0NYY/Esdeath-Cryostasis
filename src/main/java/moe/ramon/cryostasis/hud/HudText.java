package moe.ramon.cryostasis.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Small helper for the common HUD case of drawing one or more anchored text lines over a
 * translucent backdrop and reporting the bounds back to a {@link HudModule}. Keeps each
 * module's render method to the logic that is actually unique to it.
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

	private HudText() {
	}

	/** Draw a single line at the module's anchor in the default color. */
	public static void drawLine(HudModule module, GuiGraphics context, String text) {
		drawLine(module, context, text, WHITE);
	}

	/** Draw a single line at the module's anchor and record its bounds. */
	public static void drawLine(HudModule module, GuiGraphics context, String text, int color) {
		Font font = Minecraft.getInstance().font;
		int width = font.width(text);
		int height = font.lineHeight;
		int x = module.resolveX(context.guiWidth(), width);
		int y = module.resolveY(context.guiHeight(), height);

		context.fill(x - PAD_X, y - 1, x + width + PAD_X, y + height, BACKGROUND);
		context.drawString(font, text, x, y, HudColors.isRainbow() ? HudColors.rainbow(0.0f) : color);
		module.setBounds(x - PAD_X, y - 1, width + PAD_X * 2, height + 1);
	}

	/** Draw a stack of lines at the module's anchor in the default color. */
	public static void drawLines(HudModule module, GuiGraphics context, List<String> lines) {
		drawLines(module, context, lines, WHITE);
	}

	/** Draw a stack of lines at the module's anchor and record the combined bounds. */
	public static void drawLines(HudModule module, GuiGraphics context, List<String> lines, int color) {
		Font font = Minecraft.getInstance().font;
		int lineHeight = font.lineHeight + 1;
		int widest = 0;
		for (String line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		int blockHeight = lineHeight * lines.size();
		int x = module.resolveX(context.guiWidth(), widest);
		int y = module.resolveY(context.guiHeight(), blockHeight);

		context.fill(x - PAD_X, y - 1, x + widest + PAD_X, y + blockHeight, BACKGROUND);
		int cursor = y;
		for (int i = 0; i < lines.size(); i++) {
			int lineColor = HudColors.isRainbow() ? HudColors.rainbow(i * 0.08f) : color;
			context.drawString(font, lines.get(i), x, cursor, lineColor);
			cursor += lineHeight;
		}
		module.setBounds(x - PAD_X, y - 1, widest + PAD_X * 2, blockHeight + 1);
	}
}
