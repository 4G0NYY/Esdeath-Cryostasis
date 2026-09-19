package moe.ramon.cryostasis.hud;

/**
 * The clock behind the HUD's slides and fades. Driven by the wall clock rather than by a tick, so
 * an animation runs at the same speed at any frame rate and needs no hook of its own.
 */
public final class Easing {
	/** A stall longer than this is treated as this long, so the next frame does not jump. */
	private static final float MAX_DELTA = 0.1f;

	private long lastFrame;

	/**
	 * The share of the remaining distance a value should cover this frame, when it closes the gap
	 * at {@code rate} per second. Call once per frame and use the result for every value that
	 * frame moves.
	 */
	public float step(float rate) {
		long now = System.currentTimeMillis();
		float delta = lastFrame == 0 ? 0.0f : Math.min(MAX_DELTA, (now - lastFrame) / 1000.0f);
		lastFrame = now;
		return 1.0f - (float) Math.exp(-rate * delta);
	}
}
