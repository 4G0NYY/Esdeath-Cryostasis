package moe.ramon.cryostasis.gui;

import com.mojang.blaze3d.platform.InputConstants;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.input.InputHandler;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.ColorSetting;
import moe.ramon.cryostasis.setting.KeybindSetting;
import moe.ramon.cryostasis.setting.ModeSetting;
import moe.ramon.cryostasis.setting.NumberSetting;
import moe.ramon.cryostasis.setting.Setting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The module configuration menu. One draggable panel per category; each panel lists its
 * modules. Left click toggles a module, right click expands its settings. Settings edit
 * inline: booleans toggle, modes cycle, numbers ride a slider the mouse drags, keybinds
 * capture the next key, colors cycle a small preset palette. Right clicking any setting
 * restores the value it shipped with.
 *
 * Every row a panel can draw is produced once by {@link Panel#layout()}, and rendering, hit
 * testing, dragging, and scrolling all walk that same list. The three used to recompute the
 * running y themselves and had to be kept in step by hand, which was survivable while every
 * row was one height and stopped being so the moment sliders became taller than the rest.
 */
public final class ClickGuiScreen extends Screen {
	private static final int PANEL_WIDTH = 112;
	private static final int PANEL_GAP = 4;
	private static final int MARGIN = 6;
	private static final int ROW_HEIGHT = 13;
	private static final int SLIDER_HEIGHT = 21;
	private static final int HEADER_HEIGHT = 14;

	/** Horizontal inset of a slider track from the panel edge. */
	private static final int TRACK_INSET = 7;
	private static final int TRACK_HEIGHT = 4;
	/** Distance from the top of a slider row down to the top of its track. */
	private static final int TRACK_OFFSET = 13;
	private static final int HANDLE_WIDTH = 4;
	/** How far the handle stands proud of the track, above and below. */
	private static final int HANDLE_GROW = 3;

	private static final int COLOR_HEADER = Theme.HEADER;
	private static final int COLOR_PANEL = Theme.ROW;
	private static final int COLOR_ENABLED = Theme.ACCENT;
	private static final int COLOR_TEXT = Theme.TEXT;
	private static final int COLOR_SUBTEXT = Theme.SUBTEXT;

	private static final int[] COLOR_PALETTE = {
			0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF
	};

	// Persist panel positions and expansion across reopenings within a session. The panels
	// outlive the screen that built them, so they hold layout state only and take the screen
	// that is currently showing them as a parameter. An inner class would instead capture the
	// first screen forever, and every menu opened after that one would write its bind state
	// into a screen that is no longer on display.
	private static final List<Panel> PANELS = new ArrayList<>();
	private static boolean initialized;

	private Panel dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private KeybindSetting bindingCapture;
	private Module bindingModule;

	// The slider the mouse is currently holding, and the track it was grabbed on. The track is
	// captured rather than looked up per drag event so a slider keeps following the cursor once
	// grabbed, even when the pointer leaves the panel.
	private NumberSetting slider;
	private int sliderTrackX;
	private int sliderTrackWidth;

	public ClickGuiScreen() {
		super(Component.literal("Cryostasis"));
	}

	@Override
	protected void init() {
		if (initialized) {
			return;
		}
		// Laid out here rather than in the constructor because it needs the screen size, which a
		// constructor does not have. Six panels in one row is over 690 units wide, and at a high
		// GUI scale the usable width is as little as 320, so a single row would start most
		// categories off screen with no way to know they were there. They are draggable after
		// this; the wrap only decides where they begin.
		int x = MARGIN;
		int y = MARGIN;
		int rowHeight = 0;
		for (Category category : Category.values()) {
			if (x > MARGIN && x + PANEL_WIDTH > width) {
				x = MARGIN;
				y += rowHeight + MARGIN;
				rowHeight = 0;
			}
			PANELS.add(new Panel(category, x, y));
			x += PANEL_WIDTH + PANEL_GAP;
			// The collapsed height, since a panel starts with its modules listed and its settings
			// folded away.
			int modules = Cryostasis.get().getModuleManager().getByCategory(category).size();
			rowHeight = Math.max(rowHeight, HEADER_HEIGHT + modules * ROW_HEIGHT);
		}
		initialized = true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// A flat dim instead of Screen.renderBackground: since 1.21.6 the blurred
		// background may only be requested once per frame, and this non-pausing screen
		// already sits over a frame that requested it, so calling renderBackground here
		// throws "Can only blur once per frame".
		context.fill(0, 0, width, height, Theme.DIM);
		for (Panel panel : PANELS) {
			panel.render(this, context, mouseX, mouseY);
		}
		if (bindingModule != null || bindingCapture != null) {
			context.drawCenteredString(font,
					"Press a key to bind, Escape to clear", width / 2, height - 14, COLOR_TEXT);
		} else {
			// The other two menus have no button anywhere, so the only place their keys can be
			// discovered is the menu a new player does find.
			InputHandler input = Cryostasis.get().getInputHandler();
			context.drawCenteredString(font,
					keyName(input.getOpenCosmeticsKey()) + " cosmetics    "
							+ keyName(input.getOpenHudEditorKey()) + " move the HUD",
					width / 2, height - 14, COLOR_SUBTEXT);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// A pending capture belongs to the row that started it, so any other click cancels it.
		// Without this the next key press lands on a bind the user has already moved away from.
		bindingModule = null;
		bindingCapture = null;
		for (Panel panel : PANELS) {
			// Header: left drag to move, right click to collapse the whole panel.
			if (panel.inHeader(mouseX, mouseY)) {
				if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
					dragging = panel;
					dragOffsetX = (int) mouseX - panel.x;
					dragOffsetY = (int) mouseY - panel.y;
				} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
					panel.collapsed = !panel.collapsed;
				}
				return true;
			}
			if (panel.collapsed) {
				continue;
			}
			if (panel.handleClick(this, mouseX, mouseY, button)) {
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = null;
		slider = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (slider != null) {
			slideTo(slider, mouseX, sliderTrackX, sliderTrackWidth);
			return true;
		}
		if (dragging != null) {
			dragging.x = (int) mouseX - dragOffsetX;
			dragging.y = (int) mouseY - dragOffsetY;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		for (Panel panel : PANELS) {
			if (!panel.collapsed && panel.handleScroll(mouseX, mouseY, vertical)) {
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (bindingModule != null) {
			// Escape clears the module's toggle key; any other key becomes the new bind.
			bindingModule.setKeyCode(keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
			bindingModule = null;
			return true;
		}
		if (bindingCapture != null) {
			bindingCapture.set(keyCode == GLFW.GLFW_KEY_ESCAPE ? GLFW.GLFW_KEY_UNKNOWN : keyCode);
			bindingCapture = null;
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		// Persist the whole config when the menu is dismissed, so toggles and edits stick.
		Cryostasis.get().getConfigManager().save();
		super.onClose();
	}

	/** Move a slider to wherever along its track the cursor is, clamped to the ends. */
	private static void slideTo(NumberSetting number, double mouseX, int trackX, int trackWidth) {
		double fraction = Math.max(0.0, Math.min(1.0, (mouseX - trackX) / trackWidth));
		number.set(number.getMin() + fraction * (number.getMax() - number.getMin()));
	}

	/** Where along its track a value sits, as 0..1. A zero-width range pins to the left. */
	private static double fractionOf(NumberSetting number) {
		double span = number.getMax() - number.getMin();
		return span <= 0.0 ? 0.0 : (number.get() - number.getMin()) / span;
	}

	private void clickSetting(Setting<?> setting, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			setting.reset();
			return;
		}
		if (setting instanceof BooleanSetting bool) {
			bool.toggle();
		} else if (setting instanceof ModeSetting mode) {
			mode.cycle();
		} else if (setting instanceof KeybindSetting keybind) {
			bindingCapture = keybind;
		} else if (setting instanceof ColorSetting color) {
			cyclePalette(color);
		}
	}

	private static void cyclePalette(ColorSetting color) {
		int current = color.get();
		int index = 0;
		for (int i = 0; i < COLOR_PALETTE.length; i++) {
			if (COLOR_PALETTE[i] == current) {
				index = i + 1;
				break;
			}
		}
		color.set(COLOR_PALETTE[index % COLOR_PALETTE.length]);
	}

	private static String describe(Setting<?> setting) {
		if (setting instanceof BooleanSetting bool) {
			return setting.getName() + ": " + (bool.get() ? "on" : "off");
		} else if (setting instanceof ModeSetting mode) {
			return setting.getName() + ": " + mode.get();
		} else if (setting instanceof KeybindSetting keybind) {
			return setting.getName() + ": " + keyName(keybind.get());
		}
		return setting.getName();
	}

	static String trim(double value) {
		if (value == Math.rint(value)) {
			return Integer.toString((int) value);
		}
		return String.format("%.2f", value);
	}

	/**
	 * The label for a bound key. Goes through the game's own key names rather than
	 * {@code glfwGetKeyName}, which answers null for every key that prints no character, so
	 * F-keys, arrows, and the modifiers read as themselves instead of as a raw code.
	 */
	private static String keyName(int key) {
		if (key == GLFW.GLFW_KEY_UNKNOWN) {
			return "none";
		}
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase();
	}

	/** What a panel draws at one vertical position. */
	private enum Kind {
		MODULE,
		/** The module's own toggle key, drawn first under an expanded module. */
		BIND,
		SETTING
	}

	private record Row(Kind kind, Module module, Setting<?> setting, int y, int height) {
		boolean contains(double my) {
			return my >= y && my < y + height;
		}
	}

	/** A single category column. */
	private static final class Panel {
		private final Category category;
		private int x;
		private int y;
		private boolean collapsed;
		private final Set<Module> expanded = new HashSet<>();

		Panel(Category category, int x, int y) {
			this.category = category;
			this.x = x;
			this.y = y;
		}

		private List<Module> modules() {
			return Cryostasis.get().getModuleManager().getByCategory(category);
		}

		boolean inHeader(double mx, double my) {
			return mx >= x && mx <= x + PANEL_WIDTH && my >= y && my <= y + HEADER_HEIGHT;
		}

		private boolean inColumn(double mx) {
			return mx >= x && mx <= x + PANEL_WIDTH;
		}

		private int trackX() {
			return x + TRACK_INSET;
		}

		private int trackWidth() {
			return PANEL_WIDTH - TRACK_INSET * 2;
		}

		/**
		 * Every row this panel currently shows, top to bottom. The single source of truth for
		 * where a row sits, so a change to any row's height is picked up by drawing and by hit
		 * testing at once.
		 */
		private List<Row> layout() {
			List<Row> rows = new ArrayList<>();
			if (collapsed) {
				return rows;
			}
			int rowY = y + HEADER_HEIGHT;
			for (Module module : modules()) {
				rows.add(new Row(Kind.MODULE, module, null, rowY, ROW_HEIGHT));
				rowY += ROW_HEIGHT;
				if (!expanded.contains(module)) {
					continue;
				}
				rows.add(new Row(Kind.BIND, module, null, rowY, ROW_HEIGHT));
				rowY += ROW_HEIGHT;
				for (Setting<?> setting : module.getSettings()) {
					int height = setting instanceof NumberSetting ? SLIDER_HEIGHT : ROW_HEIGHT;
					rows.add(new Row(Kind.SETTING, module, setting, rowY, height));
					rowY += height;
				}
			}
			return rows;
		}

		void render(ClickGuiScreen screen, GuiGraphics context, int mouseX, int mouseY) {
			Font font = screen.font;
			context.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, COLOR_HEADER);
			// Accent underline on the header, echoing the title screen's panel seam.
			context.fill(x, y + HEADER_HEIGHT - 1, x + PANEL_WIDTH, y + HEADER_HEIGHT, Theme.ACCENT);
			context.drawString(font, category.getDisplayName(), x + 4, y + 3, COLOR_TEXT);

			for (Row row : layout()) {
				boolean hovered = inColumn(mouseX) && row.contains(mouseY);
				switch (row.kind()) {
					case MODULE -> renderModule(context, font, row, hovered);
					case BIND -> {
						context.fill(x, row.y(), x + PANEL_WIDTH, row.y() + row.height(), Theme.SETTING_ROW);
						int color = screen.bindingModule == row.module() ? Theme.ACCENT : COLOR_SUBTEXT;
						context.drawString(font, "Bind: " + keyName(row.module().getKeyCode()),
								x + 8, row.y() + 3, color);
					}
					case SETTING -> renderSetting(screen, context, font, row, hovered, mouseX);
				}
			}
		}

		private void renderModule(GuiGraphics context, Font font, Row row, boolean hovered) {
			Module module = row.module();
			context.fill(x, row.y(), x + PANEL_WIDTH, row.y() + row.height(),
					hovered ? Theme.ROW_HOVER : COLOR_PANEL);
			// A left accent stripe marks the enabled modules at a glance.
			if (module.isEnabled()) {
				context.fill(x, row.y(), x + 2, row.y() + row.height(), Theme.ACCENT);
			}
			context.drawString(font, module.getName(), x + 6, row.y() + 3,
					module.isEnabled() ? COLOR_ENABLED : COLOR_TEXT);
			// Show the toggle key on the row so a bind is visible at a glance.
			if (module.hasKeybind()) {
				String key = keyName(module.getKeyCode());
				context.drawString(font, key, x + PANEL_WIDTH - font.width(key) - 4, row.y() + 3, COLOR_SUBTEXT);
			}
		}

		private void renderSetting(ClickGuiScreen screen, GuiGraphics context, Font font, Row row,
				boolean hovered, int mouseX) {
			Setting<?> setting = row.setting();
			context.fill(x, row.y(), x + PANEL_WIDTH, row.y() + row.height(),
					hovered ? Theme.ROW_HOVER : Theme.SETTING_ROW);

			if (setting instanceof NumberSetting number) {
				renderSlider(screen, context, font, number, row, hovered, mouseX);
				return;
			}
			int color = screen.bindingCapture == setting ? Theme.ACCENT : COLOR_SUBTEXT;
			context.drawString(font, describe(setting), x + 8, row.y() + 3, color);
			// A colour is the one value whose name says nothing about it, so the row carries a
			// swatch of what it is actually set to.
			if (setting instanceof ColorSetting swatch) {
				Skin.plate(context, x + PANEL_WIDTH - 14, row.y() + 3, 10, 7,
						swatch.get(), Theme.CELL_BORDER);
			}
		}

		private void renderSlider(ClickGuiScreen screen, GuiGraphics context, Font font,
				NumberSetting number, Row row, boolean hovered, int mouseX) {
			context.drawString(font, number.getName(), x + 7, row.y() + 2, COLOR_SUBTEXT);
			String value = trim(number.get());
			context.drawString(font, value, x + PANEL_WIDTH - font.width(value) - 7, row.y() + 2,
					Theme.ACCENT);

			int trackX = trackX();
			int trackWidth = trackWidth();
			int trackY = row.y() + TRACK_OFFSET;
			Skin.plate(context, trackX, trackY, trackWidth, TRACK_HEIGHT, Theme.CELL, Theme.CELL_BORDER);

			int filled = (int) Math.round(fractionOf(number) * trackWidth);
			if (filled > 0) {
				context.fill(trackX, trackY, trackX + filled, trackY + TRACK_HEIGHT, Theme.ACCENT_DIM);
			}

			// The handle is kept inside the track at both ends, so the maximum reads as full
			// rather than as a handle hanging off the right edge.
			boolean active = screen.slider == number
					|| (hovered && mouseX >= trackX - HANDLE_WIDTH && mouseX <= trackX + trackWidth + HANDLE_WIDTH);
			int handleX = trackX + Math.min(filled, trackWidth - HANDLE_WIDTH);
			Skin.plate(context, handleX, trackY - HANDLE_GROW, HANDLE_WIDTH, TRACK_HEIGHT + HANDLE_GROW * 2,
					active ? Theme.ACCENT : Theme.ACCENT_DIM, active ? Theme.TEXT : Theme.ACCENT);
		}

		boolean handleClick(ClickGuiScreen screen, double mx, double my, int button) {
			if (!inColumn(mx)) {
				return false;
			}
			for (Row row : layout()) {
				if (!row.contains(my)) {
					continue;
				}
				switch (row.kind()) {
					case MODULE -> {
						if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
							row.module().toggle();
						} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
							if (!expanded.remove(row.module())) {
								expanded.add(row.module());
							}
						}
					}
					// Clicking it starts key capture for this module's toggle.
					case BIND -> screen.bindingModule = row.module();
					case SETTING -> {
						if (row.setting() instanceof NumberSetting number
								&& button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
							// Grab the slider and jump to the click, so a click anywhere on the
							// track sets the value and the same gesture continues as a drag.
							screen.slider = number;
							screen.sliderTrackX = trackX();
							screen.sliderTrackWidth = trackWidth();
							slideTo(number, mx, screen.sliderTrackX, screen.sliderTrackWidth);
						} else {
							screen.clickSetting(row.setting(), button);
						}
					}
				}
				return true;
			}
			return false;
		}

		boolean handleScroll(double mx, double my, double vertical) {
			if (!inColumn(mx) || vertical == 0) {
				return false;
			}
			for (Row row : layout()) {
				if (row.contains(my) && row.setting() instanceof NumberSetting number) {
					// The wheel still nudges by one step, which is the only way to land on an
					// exact value the track is too short to single out.
					number.set(number.get() + Math.signum(vertical) * number.getStep());
					return true;
				}
			}
			return false;
		}
	}
}
