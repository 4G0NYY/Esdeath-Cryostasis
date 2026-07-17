package moe.ramon.cryostasis.modules.misc;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.gui.Theme;
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
	private static final int X = 4;
	private static final int Y = 24;
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

		int y = Y;
		for (int i = 0; i < categories.length; i++) {
			boolean selected = i == categoryIndex && !inModules;
			boolean active = i == categoryIndex;
			drawRow(context, font, X, y, categories[i].getDisplayName(),
					selected ? Theme.ACCENT : Theme.HEADER, active ? Theme.TEXT : Theme.SUBTEXT);
			y += ROW_HEIGHT;
		}

		if (inModules) {
			List<Module> modules = currentModules();
			int moduleX = X + COLUMN_WIDTH + 2;
			int moduleY = Y + categoryIndex * ROW_HEIGHT;
			for (int i = 0; i < modules.size(); i++) {
				Module module = modules.get(i);
				boolean selected = i == moduleIndex;
				int textColor = module.isEnabled() ? Theme.ACCENT : Theme.TEXT;
				drawRow(context, font, moduleX, moduleY, module.getName(),
						selected ? Theme.ROW_HOVER : Theme.ROW, textColor);
				moduleY += ROW_HEIGHT;
			}
		}
	}

	private void drawRow(GuiGraphics context, Font font, int x, int y, String label, int background, int textColor) {
		context.fill(x, y, x + COLUMN_WIDTH, y + ROW_HEIGHT, background);
		context.drawString(font, label, x + 4, y + 2, textColor, false);
	}
}
