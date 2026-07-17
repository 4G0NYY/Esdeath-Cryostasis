package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A free-text value, used by modules such as AutoText for their message payload. */
public final class StringSetting extends Setting<String> {
	public StringSetting(String name, String defaultValue) {
		super(name, defaultValue);
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsString());
		}
	}
}
