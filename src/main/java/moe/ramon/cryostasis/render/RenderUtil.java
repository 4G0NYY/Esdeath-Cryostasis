package moe.ramon.cryostasis.render;

/** Small helpers shared by the world-render modules. */
public final class RenderUtil {
	private RenderUtil() {
	}

	/**
	 * Unpack a packed ARGB int into normalized r, g, b, a floats in that order. Reused
	 * across line-box draws so each module does not repeat the bit math.
	 */
	public static float[] argbToFloats(int argb) {
		float a = ((argb >> 24) & 0xFF) / 255.0f;
		float r = ((argb >> 16) & 0xFF) / 255.0f;
		float g = ((argb >> 8) & 0xFF) / 255.0f;
		float b = (argb & 0xFF) / 255.0f;
		return new float[] { r, g, b, a };
	}
}
