package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * An ARGB color stored as a packed int. Serialized as a hex string ("#AARRGGBB")
 * so configs are human editable.
 */
public final class ColorSetting extends Setting<Integer> {
	public ColorSetting(String name, int defaultArgb) {
		super(name, defaultArgb);
	}

	public int rgb() {
		return get();
	}

	public int alpha() {
		return (get() >> 24) & 0xFF;
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(String.format("#%08X", get()));
	}

	@Override
	public void read(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		try {
			String raw = element.getAsString().trim();
			if (raw.startsWith("#")) {
				raw = raw.substring(1);
			}
			// parseLong tolerates the high bit being set, unlike parseInt on 8 hex digits.
			set((int) Long.parseLong(raw, 16));
		} catch (NumberFormatException ignored) {
			// Leave the current value in place on malformed input.
		}
	}
}
