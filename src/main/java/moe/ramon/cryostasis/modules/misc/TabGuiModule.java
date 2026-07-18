package moe.ramon.cryostasis.modules.misc;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * An on-screen menu navigated with the arrow keys, exposing the same category and module
 * toggles as the right-shift click GUI but without opening a screen. Left and right move
 * between the category column and the module column; up and down move within a column; enter
 * or right activates (enter a category, or toggle a module); left steps back.
 *
 * This is opt-in: it only draws and only consumes arrow keys while enabled. Key routing is
 * done in {@link moe.ramon.cryostasis.input.InputHandler}, which forwards navigation keys here
 * and lets everything else fall through to normal module hotkeys.
 */
public final class TabGuiModule extends Module {
	private static final int MARGIN = 4;
	private static final int ROW_HEIGHT = 11;
	private static final int COLUMN_WIDTH = 74;

	private int categoryIndex;
	private int moduleIndex;
	private boolean inModules;

	public TabGuiModule() {
		super("TabGui", "Arrow-key on-screen menu with the same toggles as the click GUI.", Category.MISC);
	}

	@Override
	public void onDisable() {
		// Reset navigation so it always opens at the top next time.
		categoryIndex = 0;
		moduleIndex = 0;
		inModules = false;
	}

	/** Handle a navigation key. Returns true when the key was consumed by the menu. */
	public boolean handleKey(int key) {
		switch (key) {
			case GLFW.GLFW_KEY_UP -> move(-1);
			case GLFW.GLFW_KEY_DOWN -> move(1);
			case GLFW.GLFW_KEY_LEFT -> back();
			case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> forward();
			default -> {
				return false;
			}
		}
		return true;
	}

	private void move(int delta) {
		if (inModules) {
			List<Module> modules = currentModules();
			if (!modules.isEmpty()) {
				moduleIndex = Math.floorMod(moduleIndex + delta, modules.size());
			}
		} else {
			Category[] categories = Category.values();
			categoryIndex = Math.floorMod(categoryIndex + delta, categories.length);
		}
	}

	private void forward() {
		if (inModules) {
			List<Module> modules = currentModules();
			if (moduleIndex < modules.size()) {
				modules.get(moduleIndex).toggle();
			}
		} else if (!currentModules().isEmpty()) {
			inModules = true;
			moduleIndex = 0;
		}
	}

	private void back() {
		inModules = false;
	}

	private List<Module> currentModules() {
		Category category = Category.values()[categoryIndex];
		return Cryostasis.get().getModuleManager().getByCategory(category);
	}

	public void render(GuiGraphics context) {
		Font font = mc.font;
		Category[] categories = Category.values();
		List<Module> modules = inModules ? currentModules() : null;

		// Anchor to the bottom-right corner so the menu never covers the top-left HUD stack (FPS,
		// CPS, coordinates). The category column is the rightmost; the module column, when open,
		// grows to its left, so both stay on screen. Both columns share a top edge chosen so the
		// taller of the two just reaches the bottom margin, which lets the block grow upward
		// instead of spilling off the bottom.
		int screenWidth = mc.getWindow().getGuiScaledWidth();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		int moduleRows = modules != null ? modules.size() : 0;
		int maxRows = Math.max(categories.length, moduleRows);
		int topY = screenHeight - MARGIN - maxRows * ROW_HEIGHT;
		int categoryX = screenWidth - MARGIN - COLUMN_WIDTH;

		int y = topY;
		for (int i = 0; i < categories.length; i++) {
			boolean selected = i == categoryIndex && !inModules;
			boolean active = i == categoryIndex;
			drawRow(context, font, categoryX, y, categories[i].getDisplayName(),
					selected ? accent(i) : Theme.HEADER, active ? Theme.TEXT : Theme.SUBTEXT);
			y += ROW_HEIGHT;
		}

		if (modules != null) {
			int moduleX = categoryX - 2 - COLUMN_WIDTH;
			int moduleY = topY;
			for (int i = 0; i < modules.size(); i++) {
				Module module = modules.get(i);
				boolean selected = i == moduleIndex;
				int textColor = module.isEnabled() ? accent(i) : Theme.TEXT;
				drawRow(context, font, moduleX, moduleY, module.getName(),
						selected ? Theme.ROW_HOVER : Theme.ROW, textColor);
				moduleY += ROW_HEIGHT;
			}
		}
	}

	/**
	 * The highlight color for row {@code index}. Normally the steel accent, but when Rainbow mode
	 * is on it reads through {@link HudColors} so the menu sweeps in step with the rest of the
	 * HUD; the per-row offset spreads the sweep into a gradient down the column.
	 */
	private int accent(int index) {
		return HudColors.isRainbow() ? HudColors.rainbow(index * 0.06f) : Theme.ACCENT;
	}

	private void drawRow(GuiGraphics context, Font font, int x, int y, String label, int background, int textColor) {
		context.fill(x, y, x + COLUMN_WIDTH, y + ROW_HEIGHT, background);
		context.drawString(font, label, x + 4, y + 2, textColor, false);
	}
}
