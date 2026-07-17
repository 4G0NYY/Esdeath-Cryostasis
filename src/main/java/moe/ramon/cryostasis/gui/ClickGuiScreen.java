package moe.ramon.cryostasis.gui;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.ColorSetting;
import moe.ramon.cryostasis.setting.KeybindSetting;
import moe.ramon.cryostasis.setting.ModeSetting;
import moe.ramon.cryostasis.setting.NumberSetting;
import moe.ramon.cryostasis.setting.Setting;
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
 * inline: booleans toggle, modes cycle, numbers respond to scroll, keybinds capture the
 * next key, colors cycle a small preset palette.
 *
 * Layout math is intentionally simple and computed per frame; a config menu is not a hot
 * path, so clarity beats caching here.
 */
public final class ClickGuiScreen extends Screen {
	private static final int PANEL_WIDTH = 96;
	private static final int ROW_HEIGHT = 13;
	private static final int HEADER_HEIGHT = 14;

	private static final int COLOR_HEADER = Theme.HEADER;
	private static final int COLOR_PANEL = Theme.ROW;
	private static final int COLOR_ENABLED = Theme.ACCENT;
	private static final int COLOR_TEXT = Theme.TEXT;
	private static final int COLOR_SUBTEXT = Theme.SUBTEXT;

	private static final int[] COLOR_PALETTE = {
			0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFFFFF
	};

	// Persist panel positions and expansion across reopenings within a session.
	private static final List<Panel> PANELS = new ArrayList<>();
	private static boolean initialized;

	private Panel dragging;
	private int dragOffsetX;
	private int dragOffsetY;
	private KeybindSetting bindingCapture;
	private Module bindingModule;

	public ClickGuiScreen() {
		super(Component.literal("Cryostasis"));
		if (!initialized) {
			int x = 6;
			for (Category category : Category.values()) {
				PANELS.add(new Panel(category, x, 6));
				x += PANEL_WIDTH + 4;
			}
			initialized = true;
		}
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
			panel.render(context, mouseX, mouseY);
		}
		if (bindingCapture != null || bindingModule != null) {
			context.drawCenteredString(font,
					"Press a key to bind, Escape to clear", width / 2, height - 14, COLOR_TEXT);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
			if (panel.handleClick(mouseX, mouseY, button)) {
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = null;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
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

	/** A single category column. */
	private final class Panel {
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

		void render(GuiGraphics context, int mouseX, int mouseY) {
			context.fill(x, y, x + PANEL_WIDTH, y + HEADER_HEIGHT, COLOR_HEADER);
			// Accent underline on the header, echoing the title screen's panel seam.
			context.fill(x, y + HEADER_HEIGHT - 1, x + PANEL_WIDTH, y + HEADER_HEIGHT, Theme.ACCENT);
			context.drawString(font, category.getDisplayName(), x + 4, y + 3, COLOR_TEXT);
			if (collapsed) {
				return;
			}
			int rowY = y + HEADER_HEIGHT;
			for (Module module : modules()) {
				boolean hovered = mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
				context.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW_HEIGHT, hovered ? Theme.ROW_HOVER : COLOR_PANEL);
				// A left accent stripe marks the enabled modules at a glance.
				if (module.isEnabled()) {
					context.fill(x, rowY, x + 2, rowY + ROW_HEIGHT, Theme.ACCENT);
				}
				int color = module.isEnabled() ? COLOR_ENABLED : COLOR_TEXT;
				context.drawString(font, module.getName(), x + 6, rowY + 3, color);
				// Show the toggle key on the row so a bind is visible at a glance.
				if (module.hasKeybind()) {
					String key = keyName(module.getKeyCode());
					context.drawString(font, key, x + PANEL_WIDTH - font.width(key) - 4, rowY + 3, COLOR_SUBTEXT);
				}
				rowY += ROW_HEIGHT;

				if (expanded.contains(module)) {
					// Toggle-key bind row, first under the module so it is easy to find.
					context.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW_HEIGHT, Theme.SETTING_ROW);
					int bindColor = bindingModule == module ? Theme.ACCENT : COLOR_SUBTEXT;
					context.drawString(font, "Bind: " + keyName(module.getKeyCode()), x + 8, rowY + 3, bindColor);
					rowY += ROW_HEIGHT;

					for (Setting<?> setting : module.getSettings()) {
						context.fill(x, rowY, x + PANEL_WIDTH, rowY + ROW_HEIGHT, Theme.SETTING_ROW);
						context.drawString(font, describe(setting), x + 8, rowY + 3, COLOR_SUBTEXT);
						rowY += ROW_HEIGHT;
					}
				}
			}
		}

		boolean handleClick(double mx, double my, int button) {
			if (mx < x || mx > x + PANEL_WIDTH) {
				return false;
			}
			int rowY = y + HEADER_HEIGHT;
			for (Module module : modules()) {
				if (my >= rowY && my <= rowY + ROW_HEIGHT) {
					if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
						module.toggle();
					} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
						if (expanded.contains(module)) {
							expanded.remove(module);
						} else {
							expanded.add(module);
						}
					}
					return true;
				}
				rowY += ROW_HEIGHT;
				if (expanded.contains(module)) {
					// Bind row: clicking it starts key capture for this module's toggle.
					if (my >= rowY && my <= rowY + ROW_HEIGHT) {
						bindingModule = module;
						bindingCapture = null;
						return true;
					}
					rowY += ROW_HEIGHT;

					for (Setting<?> setting : module.getSettings()) {
						if (my >= rowY && my <= rowY + ROW_HEIGHT) {
							clickSetting(setting, button);
							return true;
						}
						rowY += ROW_HEIGHT;
					}
				}
			}
			return false;
		}

		boolean handleScroll(double mx, double my, double vertical) {
			if (mx < x || mx > x + PANEL_WIDTH || vertical == 0) {
				return false;
			}
			int rowY = y + HEADER_HEIGHT;
			for (Module module : modules()) {
				rowY += ROW_HEIGHT;
				if (expanded.contains(module)) {
					// Skip the bind row that render and handleClick draw before the settings.
					rowY += ROW_HEIGHT;
					for (Setting<?> setting : module.getSettings()) {
						if (my >= rowY && my <= rowY + ROW_HEIGHT && setting instanceof NumberSetting number) {
							number.set(number.get() + Math.signum(vertical) * number.getStep());
							return true;
						}
						rowY += ROW_HEIGHT;
					}
				}
			}
			return false;
		}

		private void clickSetting(Setting<?> setting, int button) {
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

		private void cyclePalette(ColorSetting color) {
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

		private String describe(Setting<?> setting) {
			if (setting instanceof BooleanSetting bool) {
				return setting.getName() + ": " + (bool.get() ? "on" : "off");
			} else if (setting instanceof ModeSetting mode) {
				return setting.getName() + ": " + mode.get();
			} else if (setting instanceof NumberSetting number) {
				return setting.getName() + ": " + trim(number.get());
			} else if (setting instanceof KeybindSetting keybind) {
				return setting.getName() + ": " + keyName(keybind.get());
			} else if (setting instanceof ColorSetting) {
				return setting.getName();
			}
			return setting.getName();
		}

		private String trim(double value) {
			if (value == Math.rint(value)) {
				return Integer.toString((int) value);
			}
			return String.format("%.2f", value);
		}

		private String keyName(int key) {
			if (key == GLFW.GLFW_KEY_UNKNOWN) {
				return "none";
			}
			String name = GLFW.glfwGetKeyName(key, 0);
			return name != null ? name.toUpperCase() : "key " + key;
		}
	}
}
