package moe.ramon.cryostasis.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * One draggable column of the click GUI: a header that moves the column and folds it away, and a
 * body below it that the subclass lays out.
 *
 * Panels outlive the screen that built them, so position and folding survive closing the menu.
 * They hold layout state only and take the screen currently showing them as a parameter; holding a
 * screen instead would pin the first one forever, and every menu opened after it would write its
 * state into a screen no longer on display.
 */
abstract class ClickPanel {
	static final int WIDTH = 112;
	static final int HEADER_HEIGHT = 14;
	static final int ROW_HEIGHT = 13;

	private final String title;
	int x;
	int y;
	boolean collapsed;

	ClickPanel(String title) {
		this.title = title;
	}

	/** The body's height with nothing expanded, which is what the first layout wraps rows by. */
	abstract int bodyHeight();

	abstract void renderBody(ClickGuiScreen screen, GuiGraphics context, int mouseX, int mouseY);

	/** Handle a click on the body. Only called while the panel is unfolded. */
	abstract boolean handleClick(ClickGuiScreen screen, double mouseX, double mouseY, int button);

	boolean handleScroll(double mouseX, double mouseY, double vertical) {
		return false;
	}

	final void render(ClickGuiScreen screen, GuiGraphics context, int mouseX, int mouseY) {
		context.fill(x, y, x + WIDTH, y + HEADER_HEIGHT, Theme.HEADER);
		// Accent underline on the header, echoing the title screen's panel seam.
		context.fill(x, y + HEADER_HEIGHT - 1, x + WIDTH, y + HEADER_HEIGHT, Theme.ACCENT);
		context.drawString(screen.getFont(), title, x + 4, y + 3, Theme.TEXT);
		if (!collapsed) {
			renderBody(screen, context, mouseX, mouseY);
		}
	}

	boolean inHeader(double mouseX, double mouseY) {
		return inColumn(mouseX) && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
	}

	boolean inColumn(double mouseX) {
		return mouseX >= x && mouseX <= x + WIDTH;
	}
}
