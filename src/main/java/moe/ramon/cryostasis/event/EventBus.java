package moe.ramon.cryostasis.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A tiny typed publish/subscribe bus for internal events that Fabric does not already
 * expose (for example a zoom FOV hook driven by a Mixin). Listeners are keyed by event
 * class, so dispatch is a single map lookup and an indexed loop, no reflection and no
 * per-event scanning of annotated methods like the original reflection based EventAPI.
 *
 * Events that need to carry a result or be cancellable should extend
 * {@link CancellableEvent}; the publisher inspects the event after {@link #post}.
 */
public final class EventBus {
	private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners = new ConcurrentHashMap<>();

	public <T> void subscribe(Class<T> type, Consumer<T> listener) {
		listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
	}

	public <T> void unsubscribe(Class<T> type, Consumer<T> listener) {
		List<Consumer<?>> list = listeners.get(type);
		if (list != null) {
			list.remove(listener);
		}
	}

	/**
	 * Dispatch an event to its listeners and return it, so callers can read back any
	 * mutation the listeners applied.
	 */
	@SuppressWarnings("unchecked")
	public <T> T post(T event) {
		List<Consumer<?>> list = listeners.get(event.getClass());
		if (list == null) {
			return event;
		}
		for (int i = 0; i < list.size(); i++) {
			((Consumer<T>) list.get(i)).accept(event);
		}
		return event;
	}

	public boolean hasListeners(Class<?> type) {
		List<Consumer<?>> list = listeners.get(type);
		return list != null && !list.isEmpty();
	}
}
