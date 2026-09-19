package moe.ramon.cryostasis.hud;

import moe.ramon.cryostasis.gui.Skin;
import moe.ramon.cryostasis.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Draws a HUD readout as a small panel in the client's own style: the clipped plate and drop
 * shadow the TabGui sits on, an accent stripe down the left edge, and each line split into a quiet
 * label and a bright value, so the eye lands on the number rather than on the word naming it.
 * Values are right-aligned, so a column of numbers lines up on its last digit.
 *
 * The anchor resolves against the whole plate rather than against the text inside it, so the
 * rectangle the editor drags is the same rectangle the position is measured from. Resolving one
 * and reporting the other makes every drag creep by the padding.
 */
public final class HudText {
	private static final int STRIPE = 2;
	private static final int PAD_LEFT = STRIPE + 4;
	private static final int PAD_RIGHT = 4;
	private static final int PAD_Y = 3;
	private static final int LINE_GAP = 2;
	private static final int COLUMN_GAP = 6;

	private HudText() {
	}

	public static void draw(HudModule module, GuiGraphics context, HudLine line) {
		draw(module, context, List.of(line), 1.0f);
	}

	public static void draw(HudModule module, GuiGraphics context, List<HudLine> lines) {
		draw(module, context, lines, 1.0f);
	}

	/** Draw the readout at {@code alpha} opacity, for an element that fades rather than blinks out. */
	public static void draw(HudModule module, GuiGraphics context, List<HudLine> lines, float alpha) {
		Font font = Minecraft.getInstance().font;
		int labelWidth = 0;
		int valueWidth = 0;
		for (HudLine line : lines) {
			labelWidth = Math.max(labelWidth, font.width(line.label()));
			valueWidth = Math.max(valueWidth, font.width(line.value()));
		}
		int gap = labelWidth > 0 && valueWidth > 0 ? COLUMN_GAP : 0;
		int width = PAD_LEFT + labelWidth + gap + valueWidth + PAD_RIGHT;
		int lineHeight = font.lineHeight + LINE_GAP;
		int height = PAD_Y * 2 + lines.size() * lineHeight - LINE_GAP;
		int x = module.resolveX(context.guiWidth(), width);
		int y = module.resolveY(context.guiHeight(), height);

		Skin.shadow(context, x, y, width, height, alpha);
		Skin.panel(context, x, y, width, height, Skin.fade(Theme.PANEL, alpha));
		// Inset by a pixel top and bottom so the panel keeps its clipped corners.
		context.fill(x, y + 1, x + STRIPE, y + height - 1, Skin.fade(HudColors.accent(0.0f), alpha));

		int rowY = y + PAD_Y;
		for (int i = 0; i < lines.size(); i++) {
			HudLine line = lines.get(i);
			context.drawString(font, line.label(), x + PAD_LEFT, rowY, Skin.fade(Theme.SUBTEXT, alpha), false);
			int valueX = x + width - PAD_RIGHT - font.width(line.value());
			context.drawString(font, line.value(), valueX, rowY, Skin.fade(valueColor(line, i), alpha), false);
			rowY += lineHeight;
		}
		module.setBounds(x, y, width, height);
	}

	/** A value with a colour of its own keeps it in rainbow mode, since that colour is the reading. */
	private static int valueColor(HudLine line, int index) {
		if (line.color() == Theme.TEXT && HudColors.isRainbow()) {
			return HudColors.rainbow(index * 0.08f);
		}
		return line.color();
	}
}
