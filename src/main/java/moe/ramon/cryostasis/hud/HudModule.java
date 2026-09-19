package moe.ramon.cryostasis.hud;

import moe.ramon.cryostasis.gui.Skin;
import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A module that draws to the in game overlay. Adds a draggable, resolution independent screen
 * position on top of the base module lifecycle.
 *
 * Position is stored as a fractional anchor (0..1 of the screen in each axis) plus the element's
 * own size, so an element pinned to the top right stays there when the window is resized. The HUD
 * editor mutates the anchor through {@link #nudge}; rendering reads {@link #resolveX} and
 * {@link #resolveY}.
 *
 * An element starts life in the manager's auto-stacked column and leaves it the moment the player
 * drags it, which is what {@link #isDetached} records. Detaching reads the anchor back out of
 * wherever the element was last drawn, so it does not jump out from under the cursor on the first
 * pixel of the drag.
 */
public abstract class HudModule extends Module {
	private final double defaultAnchorX;
	private final double defaultAnchorY;

	private double anchorX;
	private double anchorY;
	private boolean detached;

	// Cached last drawn bounds, used by the editor for hit testing and drag. These are
	// written every frame in render() so the editor never has to guess element size.
	private int lastX;
	private int lastY;
	private int lastWidth;
	private int lastHeight;

	// When the manager stacks this element, resolveX/resolveY return the assigned slot
	// instead of the anchor, so the left column never overlaps regardless of GUI scale.
	private boolean stacked;
	private int stackX;
	private int stackY;

	protected HudModule(String name, String description, double defaultAnchorX, double defaultAnchorY) {
		this(name, description, Category.HUD, defaultAnchorX, defaultAnchorY);
	}

	protected HudModule(String name, String description, Category category,
			double defaultAnchorX, double defaultAnchorY) {
		super(name, description, category);
		// Every HUD element only reads what the client already knows and draws it.
		markQol();
		this.defaultAnchorX = defaultAnchorX;
		this.defaultAnchorY = defaultAnchorY;
		this.anchorX = defaultAnchorX;
		this.anchorY = defaultAnchorY;
	}

	/**
	 * Draw the element. Implementations must call {@link #setBounds} with the region they
	 * occupied, using the same origin {@link #resolveX} and {@link #resolveY} returned, so the
	 * editor can select and drag them without the position creeping on every drag.
	 */
	public abstract void render(GuiGraphics context, float tickDelta);

	protected final void setBounds(int x, int y, int width, int height) {
		this.lastX = x;
		this.lastY = y;
		this.lastWidth = width;
		this.lastHeight = height;
	}

	public final int resolveX(int screenWidth, int elementWidth) {
		if (stacked) {
			return stackX;
		}
		return (int) Math.round(anchorX * (screenWidth - elementWidth));
	}

	public final int resolveY(int screenHeight, int elementHeight) {
		if (stacked) {
			return stackY;
		}
		return (int) Math.round(anchorY * (screenHeight - elementHeight));
	}

	/**
	 * Whether this element belongs in the manager's auto-stacked top-left column. Elements that
	 * own their corner (the ArrayList, the bottom-left MLG cue) opt out.
	 */
	public boolean isAutoStacked() {
		return true;
	}

	/** Whether the manager should place this element this frame. */
	public final boolean usesStack() {
		return isAutoStacked() && !detached;
	}

	/** Pin this element to a manager-assigned slot for the current frame. */
	public final void beginStack(int x, int y) {
		this.stacked = true;
		this.stackX = x;
		this.stackY = y;
	}

	/** Release the manager slot so the element falls back to its own anchor. */
	public final void endStack() {
		this.stacked = false;
	}

	public final boolean isDetached() {
		return detached;
	}

	public final void setDetached(boolean value) {
		this.detached = value;
	}

	/**
	 * Take this element out of the auto-stacked column, keeping it exactly where it is drawn now.
	 * Called when the editor first picks it up.
	 */
	public final void detach(int screenWidth, int screenHeight) {
		if (detached) {
			return;
		}
		detached = true;
		anchorX = fraction(lastX, screenWidth, lastWidth);
		anchorY = fraction(lastY, screenHeight, lastHeight);
	}

	/** Move the element by a pixel delta, reprojected back onto its anchor. */
	public final void nudge(int dx, int dy, int screenWidth, int screenHeight) {
		anchorX = fraction(lastX + dx, screenWidth, lastWidth);
		anchorY = fraction(lastY + dy, screenHeight, lastHeight);
	}

	/** Put the element back in the column and at the position it shipped with. */
	public final void resetPosition() {
		detached = false;
		anchorX = defaultAnchorX;
		anchorY = defaultAnchorY;
	}

	private static double fraction(int pixels, int screenSpan, int elementSpan) {
		int span = Math.max(1, screenSpan - elementSpan);
		return clamp01((double) pixels / span);
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}

	public double getAnchorX() {
		return anchorX;
	}

	public double getAnchorY() {
		return anchorY;
	}

	public void setAnchor(double x, double y) {
		this.anchorX = clamp01(x);
		this.anchorY = clamp01(y);
	}

	public int getLastX() {
		return lastX;
	}

	public int getLastY() {
		return lastY;
	}

	public int getLastWidth() {
		return lastWidth;
	}

	public int getLastHeight() {
		return lastHeight;
	}

	/** Whether the element drew anything worth grabbing on the last frame. */
	public final boolean hasBounds() {
		return lastWidth > 0 && lastHeight > 0;
	}

	/**
	 * Draw a stand-in chip where this element would sit, and claim those bounds. Several elements
	 * show nothing most of the time (the reach readout between swings, the MLG cue when not
	 * sneaking), and without this the editor would offer no way at all to place them.
	 */
	public final void renderPlaceholder(GuiGraphics context) {
		Font font = Minecraft.getInstance().font;
		String label = getName();
		int width = font.width(label) + 10;
		int height = font.lineHeight + 6;
		int x = resolveX(context.guiWidth(), width);
		int y = resolveY(context.guiHeight(), height);
		// Half the opacity of a live readout, so a stand-in never passes for one.
		Skin.panel(context, x, y, width, height, Skin.fade(Theme.PANEL, 0.5f));
		Skin.border(context, x, y, x + width, y + height, Theme.ACCENT_DIM);
		context.drawString(font, label, x + 5, y + 3, Theme.SUBTEXT, false);
		setBounds(x, y, width, height);
	}
}
