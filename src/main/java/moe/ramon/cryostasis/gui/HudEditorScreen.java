package moe.ramon.cryostasis.gui;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.hud.HudModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag the HUD around. Every enabled HUD element draws exactly where it normally does, and any of
 * them can be picked up and dropped anywhere on the screen.
 *
 * Positions are anchors rather than pixels (see {@link HudModule}), so an element dropped against
 * the right edge stays against it at another window size. Picking an element up also takes it out
 * of the manager's auto-stacked left column, which is why the column closes up behind the first
 * element dragged out of it.
 *
 * Dropping snaps to the screen edges, the screen centre, and to the edges and centres of every
 * other element, with a guide drawn along whichever line caught it. That is what makes two
 * elements line up exactly rather than nearly, which is the whole difficulty with placing a HUD by
 * hand.
 */
public final class HudEditorScreen extends Screen {
	/** How close a drop has to come to a line before it is taken to mean that line. */
	private static final int SNAP = 4;
	/** The inset the edge guides sit at, matching the auto-stacked column's own margin. */
	private static final int MARGIN = 2;

	private static final int OUTLINE_IDLE = 0x60FFFFFF;
	private static final int GUIDE = 0xFF5A8FC7;

	private HudModule dragging;
	private HudModule selected;
	private int grabOffsetX;
	private int grabOffsetY;

	// The lines the current drag snapped to, in screen coordinates, or absent when it snapped to
	// neither. Recomputed on every drag event and drawn on the frame that follows.
	private int guideX = Integer.MIN_VALUE;
	private int guideY = Integer.MIN_VALUE;

	public HudEditorScreen() {
		super(Component.literal("HUD Editor"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// A flat dim instead of Screen.renderBackground, matching the click GUI: the blurred
		// background may only be requested once per frame and this frame has already spent it.
		context.fill(0, 0, width, height, Theme.DIM);

		Cryostasis.get().getHudManager().renderElements(context, delta);
		// Anything that drew nothing this frame gets a chip standing in for it, so an element
		// only visible now and then can still be placed.
		for (HudModule hud : Cryostasis.get().getModuleManager().getHudModules()) {
			if (hud.isEnabled() && !hud.hasBounds()) {
				hud.renderPlaceholder(context);
			}
		}

		HudModule hovered = dragging != null ? dragging : elementAt(mouseX, mouseY);
		for (HudModule hud : elements()) {
			boolean active = hud == hovered || hud == selected;
			Skin.border(context, hud.getLastX(), hud.getLastY(),
					hud.getLastX() + hud.getLastWidth(), hud.getLastY() + hud.getLastHeight(),
					active ? Theme.ACCENT : OUTLINE_IDLE);
		}
		if (hovered != null) {
			label(context, hovered);
		}
		if (dragging != null) {
			if (guideX != Integer.MIN_VALUE) {
				context.fill(guideX, 0, guideX + 1, height, GUIDE);
			}
			if (guideY != Integer.MIN_VALUE) {
				context.fill(0, guideY, width, guideY + 1, GUIDE);
			}
		}

		footer(context);
		super.render(context, mouseX, mouseY, delta);
	}

	/** The element's name over its top-left corner, kept on screen when it is against an edge. */
	private void label(GuiGraphics context, HudModule hud) {
		String name = hud.getName();
		int textWidth = font.width(name);
		int x = Math.min(hud.getLastX(), width - textWidth - 4);
		int y = hud.getLastY() - font.lineHeight - 2;
		if (y < 0) {
			y = hud.getLastY() + hud.getLastHeight() + 2;
		}
		Skin.panel(context, x - 2, y - 1, textWidth + 4, font.lineHeight + 2, Theme.PANEL_SOLID);
		context.drawString(font, name, x, y, Theme.TEXT, false);
	}

	private void footer(GuiGraphics context) {
		String hint = elements().isEmpty()
				? "No HUD elements are on. Turn some on in the click GUI first."
				: "Drag to move. Arrow keys nudge. Right click resets one, Backspace resets all.";
		context.drawCenteredString(font, hint, width / 2, height - 14, Theme.SUBTEXT);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		HudModule hit = elementAt((int) mouseX, (int) mouseY);
		if (hit == null) {
			selected = null;
			return super.mouseClicked(mouseX, mouseY, button);
		}
		selected = hit;
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			hit.resetPosition();
			return true;
		}
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			// Taking it out of the column reads its anchor back out of where it is drawn now, so
			// it does not jump out from under the cursor on the first pixel of the drag.
			hit.detach(width, height);
			dragging = hit;
			grabOffsetX = (int) mouseX - hit.getLastX();
			grabOffsetY = (int) mouseY - hit.getLastY();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = null;
		guideX = Integer.MIN_VALUE;
		guideY = Integer.MIN_VALUE;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(mouseX, mouseY, button, dx, dy);
		}
		int wantX = (int) mouseX - grabOffsetX;
		int wantY = (int) mouseY - grabOffsetY;

		int[] guide = new int[1];
		int snappedX = snap(wantX, dragging.getLastWidth(), width, false, guide);
		guideX = guide[0];
		int snappedY = snap(wantY, dragging.getLastHeight(), height, true, guide);
		guideY = guide[0];

		dragging.nudge(snappedX - dragging.getLastX(), snappedY - dragging.getLastY(), width, height);
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			for (HudModule hud : Cryostasis.get().getModuleManager().getHudModules()) {
				hud.resetPosition();
			}
			return true;
		}
		if (selected != null) {
			int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -1 : keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : 0;
			int dy = keyCode == GLFW.GLFW_KEY_UP ? -1 : keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 0;
			if (dx != 0 || dy != 0) {
				selected.detach(width, height);
				selected.nudge(dx, dy, width, height);
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		Cryostasis.get().getConfigManager().save();
		super.onClose();
	}

	/**
	 * Where along one axis the dragged element should actually land. Each candidate line is tried
	 * against the element's leading edge, trailing edge, and centre, and the nearest match inside
	 * {@link #SNAP} wins; nothing in range leaves the requested position alone.
	 */
	private int snap(int want, int size, int screenSize, boolean vertical, int[] guideOut) {
		guideOut[0] = Integer.MIN_VALUE;
		int best = want;
		int bestDistance = SNAP + 1;

		for (int line : lines(screenSize, vertical)) {
			int[] options = {line, line - size, line - size / 2};
			for (int option : options) {
				int distance = Math.abs(option - want);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = option;
					guideOut[0] = line;
				}
			}
		}
		return best;
	}

	/** Every line worth snapping to on one axis: the screen's, then every other element's. */
	private List<Integer> lines(int screenSize, boolean vertical) {
		List<Integer> lines = new ArrayList<>();
		lines.add(0);
		lines.add(MARGIN);
		lines.add(screenSize / 2);
		lines.add(screenSize - MARGIN);
		lines.add(screenSize);
		for (HudModule hud : elements()) {
			if (hud == dragging) {
				continue;
			}
			int start = vertical ? hud.getLastY() : hud.getLastX();
			int span = vertical ? hud.getLastHeight() : hud.getLastWidth();
			lines.add(start);
			lines.add(start + span / 2);
			lines.add(start + span);
		}
		return lines;
	}

	/** The elements the editor can act on: enabled, and having actually drawn something. */
	private List<HudModule> elements() {
		List<HudModule> found = new ArrayList<>();
		for (HudModule hud : Cryostasis.get().getModuleManager().getHudModules()) {
			if (hud.isEnabled() && hud.hasBounds()) {
				found.add(hud);
			}
		}
		return found;
	}

	/** The topmost element under the cursor, last drawn winning, as the eye reads them. */
	private HudModule elementAt(int mouseX, int mouseY) {
		List<HudModule> found = elements();
		for (int i = found.size() - 1; i >= 0; i--) {
			HudModule hud = found.get(i);
			if (mouseX >= hud.getLastX() && mouseX < hud.getLastX() + hud.getLastWidth()
					&& mouseY >= hud.getLastY() && mouseY < hud.getLastY() + hud.getLastHeight()) {
				return hud;
			}
		}
		return null;
	}
}
