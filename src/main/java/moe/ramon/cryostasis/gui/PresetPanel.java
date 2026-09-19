package moe.ramon.cryostasis.gui;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.preset.Preset;
import moe.ramon.cryostasis.preset.PresetManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * The click GUI's preset column: the built-in QoL preset, the player's own presets, and a field to
 * save the current setup under a name.
 *
 * A row's accent stripe marks a preset that describes what is on right now: for QoL, that nothing
 * unsafe is on; for a user preset, that exactly its modules are on. A preset the backend has not
 * confirmed yet reads "local" in amber, so a player can tell one that will follow them to another
 * machine from one that so far only exists on this one.
 *
 * What a click did is reported in the screen's footer rather than in the column, because the
 * column is too narrow to explain why a name was refused.
 */
final class PresetPanel extends ClickPanel {
	private static final int FIELD_HEIGHT = 16;
	private static final long NOTICE_MS = 3000L;
	private static final String HINT =
			"Click a preset to apply it. Right click overwrites it, Shift + right click deletes it.";

	private String notice = "";
	private int noticeColor;
	private long noticeUntil;

	PresetPanel() {
		super("Presets");
	}

	private static PresetManager manager() {
		return Cryostasis.get().getPresetManager();
	}

	@Override
	int bodyHeight() {
		return (1 + manager().presets().size()) * ROW_HEIGHT + FIELD_HEIGHT + ROW_HEIGHT;
	}

	@Override
	void renderBody(ClickGuiScreen screen, GuiGraphics context, int mouseX, int mouseY) {
		Font font = screen.getFont();
		PresetManager manager = manager();
		boolean inColumn = inColumn(mouseX);
		int rowY = y + HEADER_HEIGHT;

		renderRow(context, font, PresetManager.QOL, "system", Theme.SUBTEXT, manager.isQolActive(),
				inColumn && within(mouseY, rowY, ROW_HEIGHT), rowY);
		rowY += ROW_HEIGHT;
		for (Preset preset : manager.presets()) {
			boolean unsynced = manager.isUnsynced(preset);
			String tag = unsynced ? "local" : Integer.toString(preset.enabledCount());
			renderRow(context, font, preset.name(), tag, unsynced ? Theme.WARN : Theme.SUBTEXT,
					manager.matches(preset), inColumn && within(mouseY, rowY, ROW_HEIGHT), rowY);
			rowY += ROW_HEIGHT;
		}

		context.fill(x, rowY, x + WIDTH, rowY + FIELD_HEIGHT, Theme.SETTING_ROW);
		EditBox field = screen.presetName;
		field.setX(x + 3);
		field.setY(rowY + 2);
		field.setWidth(WIDTH - 6);
		field.visible = true;
		rowY += FIELD_HEIGHT;

		boolean hovered = inColumn && within(mouseY, rowY, ROW_HEIGHT);
		context.fill(x, rowY, x + WIDTH, rowY + ROW_HEIGHT, hovered ? Theme.ROW_HOVER : Theme.ROW);
		context.drawString(font, "+ Save current", x + 6, rowY + 3, hovered ? Theme.ACCENT : Theme.TEXT);
	}

	private void renderRow(GuiGraphics context, Font font, String name, String tag, int tagColor,
			boolean active, boolean hovered, int rowY) {
		context.fill(x, rowY, x + WIDTH, rowY + ROW_HEIGHT, hovered ? Theme.ROW_HOVER : Theme.ROW);
		if (active) {
			context.fill(x, rowY, x + 2, rowY + ROW_HEIGHT, Theme.ACCENT);
		}
		int tagX = x + WIDTH - font.width(tag) - 4;
		context.drawString(font, tag, tagX, rowY + 3, tagColor);
		// A name may be wider than the column, so it is cut short of the tag rather than under it.
		String shown = font.plainSubstrByWidth(name, tagX - x - 10);
		context.drawString(font, shown, x + 6, rowY + 3, active ? Theme.ACCENT : Theme.TEXT);
	}

	@Override
	boolean handleClick(ClickGuiScreen screen, double mouseX, double mouseY, int button) {
		if (!inColumn(mouseX)) {
			return false;
		}
		PresetManager manager = manager();
		int rowY = y + HEADER_HEIGHT;
		if (within(mouseY, rowY, ROW_HEIGHT)) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				int switchedOff = manager.applyQol();
				notify(switchedOff == 0 ? "Nothing unsafe was on" : "QoL: switched off " + switchedOff
						+ (switchedOff == 1 ? " module" : " modules"), Theme.GOOD);
			}
			return true;
		}
		rowY += ROW_HEIGHT;
		for (Preset preset : manager.presets()) {
			if (within(mouseY, rowY, ROW_HEIGHT)) {
				clickPreset(manager, preset, button);
				return true;
			}
			rowY += ROW_HEIGHT;
		}
		// The field is a widget, so its click is left for the screen to route to it.
		if (within(mouseY, rowY, FIELD_HEIGHT)) {
			return false;
		}
		rowY += FIELD_HEIGHT;
		if (within(mouseY, rowY, ROW_HEIGHT)) {
			if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
				saveFrom(screen.presetName);
			}
			return true;
		}
		return false;
	}

	private void clickPreset(PresetManager manager, Preset preset, int button) {
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			manager.apply(preset);
			notify("Applied " + preset.name(), Theme.GOOD);
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && Screen.hasShiftDown()) {
			manager.delete(preset.name());
			notify("Deleted " + preset.name(), Theme.SUBTEXT);
		} else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			save(preset.name(), "Updated ");
		}
	}

	/** Save the current setup under whatever the field holds, and empty it on success. */
	void saveFrom(EditBox field) {
		if (save(field.getValue(), "Saved ")) {
			field.setValue("");
		}
	}

	private boolean save(String name, String verb) {
		try {
			notify(verb + manager().save(name), Theme.GOOD);
			return true;
		} catch (IllegalArgumentException refused) {
			notify(refused.getMessage(), Theme.BAD);
			return false;
		}
	}

	private void notify(String text, int color) {
		notice = text;
		noticeColor = color;
		noticeUntil = System.currentTimeMillis() + NOTICE_MS;
	}

	/** What the footer should say for this panel right now, or null to leave it to the screen. */
	String footer(int mouseX, int mouseY) {
		if (System.currentTimeMillis() < noticeUntil) {
			return notice;
		}
		boolean over = inColumn(mouseX) && mouseY >= y && mouseY < y + HEADER_HEIGHT + (collapsed ? 0 : bodyHeight());
		return over ? HINT : null;
	}

	int footerColor() {
		return System.currentTimeMillis() < noticeUntil ? noticeColor : Theme.SUBTEXT;
	}

	private static boolean within(double mouseY, int top, int height) {
		return mouseY >= top && mouseY < top + height;
	}
}
