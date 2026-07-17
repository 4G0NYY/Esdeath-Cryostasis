package moe.ramon.cryostasis.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fetches and caches each visible player's active cosmetics from the backend. Mirrors the
 * original client's behavior: a lookup returns immediately from cache and, when the entry
 * is missing or stale, kicks off a non-blocking refresh so the render thread never waits
 * on the network.
 *
 * The base URL defaults to the local dev instance and can be overridden with the
 * {@code cryostasis.api} system property, so pointing the client at a live server or a
 * different dev port needs no recompile.
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

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final String baseUrl;
	private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

	private static final class Entry {
		volatile Active data = Active.EMPTY;
		volatile long fetchedAt;
		volatile boolean loading;
	}

	public CosmeticService() {
		this(System.getProperty("cryostasis.api", "http://localhost:8080/api"));
	}

	public CosmeticService(String baseUrl) {
		// Trim a trailing slash so path concatenation stays clean.
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public String baseUrl() {
		return baseUrl;
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

	private void refresh(UUID player, Entry entry) {
		entry.loading = true;
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/players/" + player + "/cosmetics"))
				.timeout(Duration.ofSeconds(5))
				.GET()
				.build();
		http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
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
