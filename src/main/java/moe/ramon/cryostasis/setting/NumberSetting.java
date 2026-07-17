package moe.ramon.cryostasis.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * A numeric slider bounded by [min, max] and quantized to a step.
 *
 * Stored as a double so a single type covers both integer counters and fractional
 * multipliers. Callers that need an int use {@link #getInt()}.
 */
public final class NumberSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;

	public NumberSetting(String name, double defaultValue, double min, double max, double step) {
		super(name, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
	}

	public double getMin() {
		return min;
	}

	public double getMax() {
		return max;
	}

	public double getStep() {
		return step;
	}

	public int getInt() {
		return (int) Math.round(get());
	}

	public float getFloat() {
		return get().floatValue();
	}

	@Override
	public void set(Double value) {
		super.set(clamp(value));
	}

	private double clamp(double raw) {
		double clamped = Math.max(min, Math.min(max, raw));
		if (step > 0.0) {
			// Snap to the nearest step relative to min so sliders land on clean values.
			clamped = min + Math.round((clamped - min) / step) * step;
			clamped = Math.max(min, Math.min(max, clamped));
		}
		return clamped;
	}

	@Override
	public JsonElement write() {
		return new JsonPrimitive(get());
	}

	@Override
	public void read(JsonElement element) {
		if (element != null && element.isJsonPrimitive()) {
			set(element.getAsDouble());
		}
	}
}
