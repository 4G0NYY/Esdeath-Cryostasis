package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A simple on/off toggle. */
public final class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String name, boolean defaultValue) {
		super(name, defaultValue);
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsBoolean());
		}
	}
}
