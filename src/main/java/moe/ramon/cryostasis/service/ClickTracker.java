package moe.ramon.cryostasis.service;

/**
 * Tracks recent mouse clicks so modules can report clicks per second. Fed by a Mixin
 * on the mouse handler; read by the CPS HUD module.
 *
 * Uses two fixed-size ring buffers of click timestamps (one per button) so counting is
 * allocation free and bounded: a burst of clicks cannot grow memory without limit, and
 * {@link #cps} simply counts entries newer than one second.
 */
public final class ClickTracker {
	public static final int LEFT = 0;
	public static final int RIGHT = 1;

	private static final int WINDOW_MS = 1000;
	private static final int CAPACITY = 64;

	private final long[][] timestamps = new long[2][CAPACITY];
	private final int[] head = new int[2];

	/** Record a click for the given button (LEFT or RIGHT) at the given wall clock time. */
	public void onClick(int button, long nowMs) {
		if (button != LEFT && button != RIGHT) {
			return;
		}
		int index = head[button];
		timestamps[button][index] = nowMs;
		head[button] = (index + 1) % CAPACITY;
	}

	/** Clicks in the last second for the given button. */
	public int cps(int button, long nowMs) {
		if (button != LEFT && button != RIGHT) {
			return 0;
		}
		long cutoff = nowMs - WINDOW_MS;
		int count = 0;
		long[] buffer = timestamps[button];
		for (int i = 0; i < CAPACITY; i++) {
			if (buffer[i] > cutoff) {
				count++;
			}
		}
		return count;
	}
}
