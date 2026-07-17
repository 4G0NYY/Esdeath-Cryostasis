package moe.ramon.cryostasis.event;

/** Base for internal events whose publisher acts on a cancel flag after dispatch. */
public abstract class CancellableEvent {
	private boolean cancelled;

	public void cancel() {
		this.cancelled = true;
	}

	public boolean isCancelled() {
		return cancelled;
	}
}
