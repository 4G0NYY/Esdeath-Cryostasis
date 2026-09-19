package moe.ramon.cryostasis.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A saved setup: which modules are on and how each is set, keyed by lower-cased module name in the
 * same shape the config file uses, as {@code {"fps": {"enabled": true, "settings": {...}}}}.
 *
 * {@code modules} is never mutated after construction. Equality is by content, which is what lets
 * the sync tell whether the preset it just uploaded is still the one the player has.
 */
public record Preset(String name, JsonObject modules) {
	/** Whether the preset switches the module with this lower-cased name on. Absent means off. */
	public boolean enables(String module) {
		JsonElement state = modules.get(module);
		if (state == null || !state.isJsonObject()) {
			return false;
		}
		JsonElement enabled = state.getAsJsonObject().get("enabled");
		return enabled != null && enabled.isJsonPrimitive() && enabled.getAsBoolean();
	}

	public int enabledCount() {
		int count = 0;
		for (String key : modules.keySet()) {
			if (enables(key)) {
				count++;
			}
		}
		return count;
	}
}
