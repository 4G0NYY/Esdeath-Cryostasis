package moe.ramon.cryostasis.hud;

/**
 * Central color source for HUD text. Returns solid white normally, or a wall-clock driven
 * rainbow sweep when rainbow mode is on. Reading through here keeps every HUD element in
 * step and lets the Rainbow module flip a single flag instead of each module tracking its
 * own state.
 *
 * The sweep is derived from the system clock rather than a frame counter, so it advances at
 * a steady rate independent of frame rate and needs no tick hook to animate.
 */
public final class HudColors {
	private static volatile boolean rainbow;
	private static volatile float speed = 1.0f;

	private HudColors() {
	}

	public static void setRainbow(boolean value) {
		rainbow = value;
	}

	public static boolean isRainbow() {
		return rainbow;
	}

	public static void setSpeed(float value) {
		speed = Math.max(0.1f, value);
	}

	/** The default HUD text color for this frame. */
	public static int primary() {
		return rainbow ? rainbow(0.0f) : 0xFFFFFFFF;
	}

	/**
	 * A rainbow color for the given phase offset in the range 0..1. Offsetting per line or
	 * per list entry spreads the sweep into a gradient across a block of text.
	 */
	public static int rainbow(float offset) {
		float period = 5000.0f / speed;
		float hue = ((System.currentTimeMillis() % (long) period) / period + offset) % 1.0f;
		return hsvToArgb(hue, 0.75f, 1.0f);
	}

	private static int hsvToArgb(float h, float s, float v) {
		int sextant = (int) (h * 6.0f) % 6;
		float f = h * 6.0f - (float) Math.floor(h * 6.0f);
		float p = v * (1.0f - s);
		float q = v * (1.0f - f * s);
		float t = v * (1.0f - (1.0f - f) * s);
		float r;
		float g;
		float b;
		switch (sextant) {
			case 0 -> {
				r = v;
				g = t;
				b = p;
			}
			case 1 -> {
				r = q;
				g = v;
				b = p;
			}
			case 2 -> {
				r = p;
				g = v;
				b = t;
			}
			case 3 -> {
				r = p;
				g = q;
				b = v;
			}
			case 4 -> {
				r = t;
				g = p;
				b = v;
			}
			default -> {
				r = v;
				g = p;
				b = q;
			}
		}
		int ri = (int) (r * 255.0f) & 0xFF;
		int gi = (int) (g * 255.0f) & 0xFF;
		int bi = (int) (b * 255.0f) & 0xFF;
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}
}
