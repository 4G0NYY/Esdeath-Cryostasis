package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

/**
 * A single key binding stored as a raw GLFW key code. {@link GLFW#GLFW_KEY_UNKNOWN}
 * (-1) means unbound. Kept as a plain int rather than a Minecraft KeyBinding so a
 * module can own extra hotkeys without registering them in the vanilla controls menu.
 */
public final class KeybindSetting extends Setting<Integer> {
	public KeybindSetting(String name, int defaultKey) {
		super(name, defaultKey);
	}

	public boolean isBound() {
		return get() != GLFW.GLFW_KEY_UNKNOWN;
	}

	public boolean matches(int key) {
		return isBound() && get() == key;
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsInt());
		}
	}
}
