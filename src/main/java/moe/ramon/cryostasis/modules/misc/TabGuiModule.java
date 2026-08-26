package moe.ramon.cryostasis.modules.misc;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.gui.HudEditorScreen;
import moe.ramon.cryostasis.gui.Skin;
import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.hud.HudModule;
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
 * The category column is a fixed size and is the only part that carries the menu's position, so
 * opening and closing a category cannot move the menu. The older layout sized the whole block to
 * whichever column was taller and hung it off the bottom of the screen, which meant every step
 * into a category shifted the categories themselves out from under the eye. The module column now
 * opens beside the row it belongs to and slides in rather than appearing, so it reads as that
 * row's submenu.
 *
 * It is a HUD element, so the editor moves it with everything else and its position is saved.
 * Key routing is done in {@link moe.ramon.cryostasis.input.InputHandler}, which forwards
 * navigation keys here and lets everything else fall through to normal module hotkeys.
 */
public final class TabGuiModule extends HudModule {
	private static final int ROW_HEIGHT = 12;
	private static final int HEADER_HEIGHT = 13;
	private static final int CATEGORY_WIDTH = 78;
	private static final int MODULE_WIDTH = 92;
	private static final int COLUMN_GAP = 3;
	private static final int PAD_X = 6;
	private static final int PILL_WIDTH = 12;
	private static final int PILL_HEIGHT = 4;

	/** Easing rate of the sliding highlights, per second. Higher settles faster. */
	private static final float EASE_RATE = 18.0f;
	/** Below this the module column is not worth drawing, and closed is exactly zero. */
	private static final float VISIBLE = 0.01f;

	private int categoryIndex;
	private int moduleIndex;
	private boolean inModules;

	// Eased display state. These chase the indices above rather than tracking them, which is what
	// turns a step through the menu into a slide instead of a jump.
	private float categorySlide;
	private float moduleSlide;
	private float openAmount;
	private long lastFrame;

	public TabGuiModule() {
		super("TabGui", "Arrow-key on-screen menu with the same toggles as the click GUI.",
				Category.MISC, 0.012, 0.35);
	}

	@Override
	public void onDisable() {
		// Reset navigation so it always opens at the top next time.
		categoryIndex = 0;
		moduleIndex = 0;
		inModules = false;
		categorySlide = 0.0f;
		moduleSlide = 0.0f;
		openAmount = 0.0f;
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

	@Override
	public boolean isAutoStacked() {
		// It is a menu rather than a readout, and it carries its own position.
		return false;
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		// The overlay draws behind open screens, and a menu driven by keys the screen has taken
		// should not be one of the things showing through. The editor is the exception: it is the
		// screen for moving this, so it has to be able to see it.
		if (mc.screen != null && !(mc.screen instanceof HudEditorScreen)) {
			return;
		}
		Font font = mc.font;
		Category[] categories = Category.values();

		int width = CATEGORY_WIDTH;
		int height = HEADER_HEIGHT + categories.length * ROW_HEIGHT + 2;
		int x = resolveX(context.guiWidth(), width);
		int y = resolveY(context.guiHeight(), height);
		setBounds(x, y, width, height);

		ease();

		Skin.shadow(context, x, y, width, height);
		Skin.panel(context, x, y, width, height, Theme.PANEL);
		context.fill(x, y, x + width, y + HEADER_HEIGHT, Theme.HEADER);
		context.fill(x, y + HEADER_HEIGHT - 1, x + width, y + HEADER_HEIGHT, accent(0));
		context.drawString(font, "Cryostasis", x + PAD_X, y + 3, Theme.TEXT, false);

		int rowsTop = y + HEADER_HEIGHT + 1;
		// One moving plate rather than a fill per row, which is what lets the highlight travel
		// between rows instead of blinking from one to the next.
		int slideY = rowsTop + Math.round(categorySlide * ROW_HEIGHT);
		context.fill(x, slideY, x + width, slideY + ROW_HEIGHT, Theme.ROW_HOVER);
		context.fill(x, slideY, x + 2, slideY + ROW_HEIGHT, accent(0));

		for (int i = 0; i < categories.length; i++) {
			int rowY = rowsTop + i * ROW_HEIGHT;
			boolean selected = i == categoryIndex;
			int count = Cryostasis.get().getModuleManager().getByCategory(categories[i]).size();
			context.drawString(font, categories[i].getDisplayName(), x + PAD_X, rowY + 2,
					selected ? Theme.TEXT : Theme.SUBTEXT, false);
			String tally = Integer.toString(count);
			context.drawString(font, tally, x + width - PAD_X - font.width(tally), rowY + 2,
					selected ? accent(i) : Theme.ACCENT_DIM, false);
		}

		if (openAmount > VISIBLE) {
			renderModules(context, font, x + width + COLUMN_GAP, rowsTop);
		}
	}

	/**
	 * The selected category's modules, hung off the row that owns them and slid in from behind
	 * the category column. Clamped when the list would otherwise run off the bottom, which is the
	 * one case where it cannot simply start at its row.
	 */
	private void renderModules(GuiGraphics context, Font font, int columnX, int rowsTop) {
		List<Module> modules = currentModules();
		if (modules.isEmpty()) {
			return;
		}
		int height = modules.size() * ROW_HEIGHT + 2;
		int top = rowsTop + Math.round(categorySlide * ROW_HEIGHT) - 1;
		top = Math.min(top, context.guiHeight() - height - 2);
		top = Math.max(top, 2);

		// Slide the column in from behind the categories and fade it with the same number, so a
		// category stepped past quickly never leaves a hard edge behind.
		int x = columnX - Math.round((1.0f - openAmount) * (COLUMN_GAP + 6));
		float alpha = openAmount;

		Skin.shadow(context, x, top, MODULE_WIDTH, height, alpha);
		Skin.panel(context, x, top, MODULE_WIDTH, height, Skin.fade(Theme.PANEL, alpha));

		int slideY = top + 1 + Math.round(moduleSlide * ROW_HEIGHT);
		if (inModules) {
			context.fill(x, slideY, x + MODULE_WIDTH, slideY + ROW_HEIGHT, Skin.fade(Theme.ROW_HOVER, alpha));
			context.fill(x, slideY, x + 2, slideY + ROW_HEIGHT, Skin.fade(accent(moduleIndex), alpha));
		}

		for (int i = 0; i < modules.size(); i++) {
			Module module = modules.get(i);
			int rowY = top + 1 + i * ROW_HEIGHT;
			boolean on = module.isEnabled();
			boolean selected = inModules && i == moduleIndex;
			int color = on ? accent(i) : selected ? Theme.TEXT : Theme.SUBTEXT;
			context.drawString(font, module.getName(), x + PAD_X, rowY + 2, Skin.fade(color, alpha), false);

			// A pill rather than a coloured name alone, so the state survives rainbow mode and
			// reads at a glance down a column of ten.
			int pillX = x + MODULE_WIDTH - PAD_X - PILL_WIDTH;
			int pillY = rowY + (ROW_HEIGHT - PILL_HEIGHT) / 2;
			context.fill(pillX, pillY, pillX + PILL_WIDTH, pillY + PILL_HEIGHT,
					Skin.fade(on ? accent(i) : Theme.CELL, alpha));
		}
	}

	/**
	 * Advance the eased values towards where the keys have put the selection. Driven by the wall
	 * clock rather than by a tick, so the slide runs at the same speed at any frame rate and needs
	 * no hook of its own.
	 */
	private void ease() {
		long now = System.currentTimeMillis();
		float delta = lastFrame == 0 ? 0.0f : Math.min(0.1f, (now - lastFrame) / 1000.0f);
		lastFrame = now;
		float step = 1.0f - (float) Math.exp(-EASE_RATE * delta);

		categorySlide += (categoryIndex - categorySlide) * step;
		moduleSlide += (moduleIndex - moduleSlide) * step;
		openAmount += ((inModules ? 1.0f : 0.0f) - openAmount) * step;
		if (!inModules && openAmount < VISIBLE) {
			// Snap the last sliver away, so a closed column is closed rather than a faint line.
			openAmount = 0.0f;
			moduleSlide = 0.0f;
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
}
