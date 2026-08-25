package moe.ramon.cryostasis.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import moe.ramon.cryostasis.backend.ApiClient;
import moe.ramon.cryostasis.backend.SessionService;

import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches each visible player's active cosmetics from the backend. Mirrors the
 * original client's behavior: a lookup returns immediately from cache and, when the entry
 * is missing or stale, kicks off a non-blocking refresh so the render thread never waits
 * on the network.
 *
 * Reads are open, but every write carries the bearer token from the session handshake, which the
 * shared {@link ApiClient} attaches. The hosted backend enforces that a caller may only change
 * its own UUID, so without a token a toggle would simply come back 401 and silently do nothing.
 */
public final class CosmeticService {
	/** A player's currently active cosmetics as reported by the backend. */
	public record Active(Set<String> cosmetics, String cape) {
		public static final Active EMPTY = new Active(Collections.emptySet(), "");

		public boolean has(String cosmetic) {
			return cosmetics.contains(cosmetic.toLowerCase());
		}
	}

	private static final long TTL_MS = 30_000;

	private final ApiClient api;
	private final SessionService session;
	private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

	private static final class Entry {
		volatile Active data = Active.EMPTY;
		volatile long fetchedAt;
		volatile boolean loading;
	}

	public CosmeticService(ApiClient api, SessionService session) {
		this.api = api;
		this.session = session;
	}

	public String baseUrl() {
		return api.baseUrl();
	}

	/**
	 * Active cosmetics for a player, served from cache. Triggers an async refresh when the
	 * entry is missing or older than the TTL. Never blocks.
	 */
	public Active get(UUID player) {
		Entry entry = cache.computeIfAbsent(player, k -> new Entry());
		long now = System.currentTimeMillis();
		if (!entry.loading && (entry.fetchedAt == 0 || now - entry.fetchedAt > TTL_MS)) {
			refresh(player, entry);
		}
		return entry.data;
	}

	/** Force the next {@link #get} for this player to refetch. */
	public void invalidate(UUID player) {
		Entry entry = cache.get(player);
		if (entry != null) {
			entry.fetchedAt = 0;
		}
	}

	/**
	 * Activate a cosmetic for a player. Updates the cache optimistically so the change shows in
	 * the menu preview at once, then posts it to the backend without blocking. The entry is
	 * marked stale on completion so the next read re-syncs with the server, which quietly reverts
	 * the optimistic change if the write did not take.
	 */
	public void activate(UUID player, String cosmetic) {
		setLocalActive(player, cosmetic, true);
		JsonObject body = new JsonObject();
		body.addProperty("cosmetic", cosmetic.toLowerCase());
		track(player, api.post("/players/" + player + "/cosmetics", body));
	}

	/** Deactivate a cosmetic for a player. Optimistic and non-blocking, mirroring {@link #activate}. */
	public void deactivate(UUID player, String cosmetic) {
		setLocalActive(player, cosmetic, false);
		track(player, api.delete("/players/" + player + "/cosmetics/" + cosmetic.toLowerCase()));
	}

	private void track(UUID player, CompletableFuture<HttpResponse<String>> pending) {
		pending.whenComplete((response, error) -> {
			// A lapsed token is the one failure worth acting on: mark the session stale so the
			// next tick re-runs the handshake, rather than letting every later write 401 too.
			if (response != null && response.statusCode() == 401) {
				session.invalidate();
			}
			invalidate(player);
		});
	}

	/**
	 * Optimistically flip a cosmetic in the cached active set so the render reflects the choice
	 * before the network round-trip returns. Rebuilds the set into a new immutable {@link Active}
	 * so readers on the render thread never see a half-mutated set.
	 */
	private void setLocalActive(UUID player, String cosmetic, boolean active) {
		Entry entry = cache.computeIfAbsent(player, k -> new Entry());
		String key = cosmetic.toLowerCase();
		Set<String> next = new LinkedHashSet<>(entry.data.cosmetics());
		if (active) {
			next.add(key);
		} else {
			next.remove(key);
		}
		entry.data = new Active(next, entry.data.cape());
	}

	private void refresh(UUID player, Entry entry) {
		entry.loading = true;
		api.get("/players/" + player + "/cosmetics")
				.thenAccept(response -> {
					if (response.statusCode() == 200) {
						entry.data = parse(response.body());
					}
				})
				.whenComplete((ok, error) -> {
					entry.fetchedAt = System.currentTimeMillis();
					entry.loading = false;
				});
	}

	static Active parse(String body) {
		JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
		Set<String> cosmetics = new LinkedHashSet<>();
		if (obj.has("cosmetics") && obj.get("cosmetics").isJsonArray()) {
			obj.getAsJsonArray("cosmetics").forEach(e -> cosmetics.add(e.getAsString().toLowerCase()));
		}
		String cape = obj.has("cape") && !obj.get("cape").isJsonNull() ? obj.get("cape").getAsString() : "";
		return new Active(cosmetics, cape);
	}
}
