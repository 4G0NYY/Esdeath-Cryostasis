package moe.ramon.cryostasis.module;

import moe.ramon.cryostasis.setting.KeybindSetting;
import moe.ramon.cryostasis.setting.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every feature. A module is self contained: it owns its settings,
 * its enabled state, and its lifecycle hooks. The manager dispatches to the hooks by
 * direct virtual call, so there is no reflection on the tick or render path.
 *
 * Subclasses override only the hooks they need. Default implementations are empty so
 * a HUD-only module never pays for a tick hook, and vice versa.
 */
public abstract class Module {
	protected final Minecraft mc = Minecraft.getInstance();

	private final String name;
	private final String description;
	private final Category category;
	private final List<Setting<?>> settings = new ArrayList<>();

	private int keyCode = GLFW.GLFW_KEY_UNKNOWN;
	private boolean enabled;

	protected Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	// Lifecycle hooks. Override as needed.

	/** Called once when the module transitions from disabled to enabled. */
	public void onEnable() {
	}

	/** Called once when the module transitions from enabled to disabled. */
	public void onDisable() {
	}

	/** Called every client tick while enabled. Keep allocation free. */
	public void onTick() {
	}

	// State management.

	public final void toggle() {
		setEnabled(!enabled);
	}

	public final void setEnabled(boolean value) {
		if (this.enabled == value) {
			return;
		}
		this.enabled = value;
		if (value) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public final boolean isEnabled() {
		return enabled;
	}

	// Settings registration. Called from subclass constructors.

	protected final <S extends Setting<?>> S register(S setting) {
		settings.add(setting);
		return setting;
	}

	public final List<Setting<?>> getSettings() {
		return settings;
	}

	// Keybind.

	protected final void setDefaultKey(int keyCode) {
		this.keyCode = keyCode;
	}

	public final int getKeyCode() {
		return keyCode;
	}

	public final void setKeyCode(int keyCode) {
		this.keyCode = keyCode;
	}

	public final boolean hasKeybind() {
		return keyCode != GLFW.GLFW_KEY_UNKNOWN;
	}

	// Identity.

	public final String getName() {
		return name;
	}

	public final String getDescription() {
		return description;
	}

	public final Category getCategory() {
		return category;
	}

	/**
	 * The text shown in the ArrayList HUD. Defaults to the name, but a module can
	 * append live state (a mode, a value) by overriding this.
	 */
	public String getHudLabel() {
		return name;
	}

	/** Convenience for subclasses that expose an extra hotkey as a setting. */
	protected final KeybindSetting keybindSetting(String settingName, int defaultKey) {
		return register(new KeybindSetting(settingName, defaultKey));
	}
}
