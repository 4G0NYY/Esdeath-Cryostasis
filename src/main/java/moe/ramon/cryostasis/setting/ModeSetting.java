package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

/**
 * A choice among a fixed list of named modes, stored by its string label so the
 * config stays readable and survives reordering of the options.
 */
public final class ModeSetting extends Setting<String> {
	private final List<String> options;

	public ModeSetting(String name, String defaultValue, List<String> options) {
		super(name, defaultValue);
		this.options = List.copyOf(options);
		if (!this.options.contains(defaultValue)) {
			throw new IllegalArgumentException("Default mode '" + defaultValue + "' is not among options for setting '" + name + "'");
		}
	}

	public List<String> getOptions() {
		return options;
	}

	public boolean is(String mode) {
		return get().equals(mode);
	}

	/** Advance to the next mode, wrapping around. Used by click-to-cycle UI. */
	public void cycle() {
		int index = options.indexOf(get());
		set(options.get((index + 1) % options.size()));
	}

	@Override
	public void set(String value) {
		if (options.contains(value)) {
			super.set(value);
		}
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
