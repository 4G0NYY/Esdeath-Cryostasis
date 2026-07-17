package moe.ramon.cryostasis.hud;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A module that draws to the in game overlay. Adds a draggable, resolution
 * independent screen position on top of the base module lifecycle.
 *
 * Position is stored as a fractional anchor (0..1 of the screen in each axis) plus a
 * pixel offset, so an element pinned to the top right stays there when the window is
 * resized. The HUD editor mutates {@link #anchorX}/{@link #anchorY}; rendering reads
 * {@link #resolveX}/{@link #resolveY}.
 */
public abstract class HudModule extends Module {
	private double anchorX;
	private double anchorY;

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
		super(name, description, Category.HUD);
		this.anchorX = defaultAnchorX;
		this.anchorY = defaultAnchorY;
	}

	/**
	 * Draw the element. Implementations must call {@link #setBounds} with the region
	 * they occupied so the editor can select and drag them.
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
	 * Whether the manager should place this element in the auto-stacked top-left column.
	 * Elements that own their corner (the ArrayList, the bottom-left MLG cue) opt out.
	 */
	public boolean isAutoStacked() {
		return true;
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

	/** Move the element to an absolute pixel position, reprojected back to an anchor. */
	public final void moveTo(int x, int y, int screenWidth, int screenHeight) {
		int spanX = Math.max(1, screenWidth - lastWidth);
		int spanY = Math.max(1, screenHeight - lastHeight);
		this.anchorX = clamp01((double) x / spanX);
		this.anchorY = clamp01((double) y / spanY);
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
}
