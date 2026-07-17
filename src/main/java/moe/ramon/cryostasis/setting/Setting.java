package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;

/**
 * Base type for a single configurable value on a module.
 *
 * Settings serialize themselves to and from JSON so the config layer never needs
 * reflection or per-type knowledge: it walks a module's settings and calls
 * {@link #write()} / {@link #read(JsonElement)} on each. Keep the name stable once
 * shipped, it is the JSON key used for persistence.
 */
public abstract class Setting<T> {
	private final String name;
	protected T value;

	protected Setting(String name, T defaultValue) {
		this.name = name;
		this.value = defaultValue;
	}

	public String getName() {
		return name;
	}

	public T get() {
		return value;
	}

	public void set(T value) {
		this.value = value;
	}

	/** Serialize the current value for config persistence. */
	public abstract JsonElement write();

	/** Restore from a previously serialized value. Must tolerate malformed input. */
	public abstract void read(JsonElement element);
}
