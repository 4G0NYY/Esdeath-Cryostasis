package moe.ramon.cryostasis.preset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.backend.ApiClient;
import moe.ramon.cryostasis.backend.SessionService;
import net.minecraft.client.Minecraft;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the player's presets in step with the backend, so they follow the account to every
 * machine it plays on.
 *
 * Local first, the way cosmetics are: a save or delete lands in {@link PresetManager} at once and
 * is marked owed to the backend, and {@link #tick()} pushes whatever is owed once the session is
 * signed in. With nothing owed, a pull replaces the local list with the backend's. A pull happens
 * on every sign-in and whenever the click GUI opens, which is where a preset saved on another
 * machine would be looked for.
 *
 * Responses arrive on the HTTP client's threads and are handed back to the render thread before
 * they touch anything, so the manager never needs a lock.
 */
public final class PresetService {
	/** How long to wait after a failed round before trying again. */
	private static final long RETRY_MS = 30_000L;

	private final ApiClient api;
	private final SessionService session;
	private final PresetManager presets;

	// Render thread only.
	private boolean inFlight;
	private boolean pullWanted = true;
	private boolean wasAuthenticated;
	private long nextAttemptAt;

	public PresetService(ApiClient api, SessionService session, PresetManager presets) {
		this.api = api;
		this.session = session;
		this.presets = presets;
	}

	/** Ask for a fresh copy of the backend's list on the next chance. */
	public void requestPull() {
		pullWanted = true;
	}

	public void tick() {
		boolean authenticated = session.isAuthenticated();
		if (authenticated && !wasAuthenticated) {
			pullWanted = true;
		}
		wasAuthenticated = authenticated;
		if (!authenticated || inFlight || System.currentTimeMillis() < nextAttemptAt) {
			return;
		}
		if (presets.hasUnsynced()) {
			push();
		} else if (pullWanted) {
			pull();
		}
	}

	private void push() {
		inFlight = true;
		AtomicBoolean failed = new AtomicBoolean();
		List<CompletableFuture<?>> pending = new ArrayList<>();

		for (Preset preset : presets.unsyncedPresets()) {
			JsonObject body = new JsonObject();
			body.add("modules", preset.modules());
			pending.add(api.put(path(preset.name()), body).handle((response, error) -> {
				if (settled(response, error, preset.name())) {
					onRenderThread(() -> presets.confirmSaved(preset));
				} else {
					failed.set(true);
				}
				return null;
			}));
		}
		for (String name : presets.deletedNames()) {
			pending.add(api.delete(path(name)).handle((response, error) -> {
				if (settled(response, error, name)) {
					onRenderThread(() -> presets.confirmDeleted(name));
				} else {
					failed.set(true);
				}
				return null;
			}));
		}

		CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
				.whenComplete((ok, error) -> onRenderThread(() -> finish(failed.get())));
	}

	/**
	 * Whether the backend has answered this change for good. Success settles it, and so does a
	 * refusal that would be the same next time (a bad name, the limit): retrying those forever would
	 * only hammer the backend. A lapsed token, the rate limit, and anything that never got an answer
	 * are worth another try.
	 */
	private boolean settled(HttpResponse<String> response, Throwable error, String name) {
		if (error != null || response == null) {
			return false;
		}
		int status = response.statusCode();
		if (status == 401) {
			session.invalidate();
			return false;
		}
		if (status == 429 || status >= 500) {
			return false;
		}
		if (status >= 400) {
			Cryostasis.LOGGER.warn("Backend refused preset {}: {}", name, ApiClient.detail(response));
		}
		return true;
	}

	private void pull() {
		inFlight = true;
		api.get("/players/" + localUuid() + "/presets").whenComplete((response, error) -> {
			List<Preset> remote = error == null && response != null && response.statusCode() == 200
					? parse(response.body())
					: null;
			onRenderThread(() -> {
				if (remote != null) {
					presets.replaceWith(remote);
					pullWanted = false;
				} else if (response != null && response.statusCode() == 401) {
					session.invalidate();
				}
				finish(remote == null);
			});
		});
	}

	private void finish(boolean failed) {
		inFlight = false;
		if (failed) {
			nextAttemptAt = System.currentTimeMillis() + RETRY_MS;
		}
	}

	/** The backend's list, or null when the body is not the shape it promises. */
	static List<Preset> parse(String body) {
		JsonObject root = ApiClient.asObject(body);
		if (root == null || !root.has("presets") || !root.get("presets").isJsonArray()) {
			return null;
		}
		JsonArray array = root.getAsJsonArray("presets");
		List<Preset> presets = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject entry = element.getAsJsonObject();
			if (entry.has("name") && entry.has("modules") && entry.get("modules").isJsonObject()) {
				presets.add(new Preset(entry.get("name").getAsString(), entry.getAsJsonObject("modules")));
			}
		}
		return presets;
	}

	private static String path(String name) {
		// URLEncoder writes a space as '+', which is literal in a path segment, hence the swap.
		String segment = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
		return "/players/" + localUuid() + "/presets/" + segment;
	}

	/** The account the session handshake signed in, which is the uuid the token is issued for. */
	private static String localUuid() {
		return Minecraft.getInstance().getUser().getProfileId().toString();
	}

	private static void onRenderThread(Runnable task) {
		Minecraft.getInstance().execute(task);
	}
}
