package moe.ramon.cryostasis.backend;

import com.google.gson.JsonObject;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import moe.ramon.cryostasis.Cryostasis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Proves to the backend that this client owns its Minecraft account, and holds the bearer token
 * that proof buys.
 *
 * The hosted instance enforces auth, so without this every write the client makes (a cosmetic
 * toggle, a chat line) comes back 401. The handshake is the one every Minecraft server already
 * performs, so it needs no Microsoft app registration and no second login:
 *
 *   1. ask the backend for a nonce,
 *   2. call Mojang's joinServer with the session this client already holds and that nonce,
 *   3. post {uuid, username, server_id}, and the backend confirms it through Mojang's hasJoined
 *      before issuing its own short-lived token.
 *
 * Nothing here blocks the render thread: {@link #tick()} only decides whether a handshake is due
 * and hands the work to the HTTP client's executor. Step 2 is a blocking authlib call, so it runs
 * on that executor too, never inline.
 */
public final class SessionService {
	/** Re-authenticate this long before expiry, so a token never lapses mid-session. */
	private static final long REFRESH_MARGIN_MS = 5 * 60 * 1000L;

	/** Backoff after a failed handshake, doubling to this ceiling. */
	private static final long FIRST_RETRY_MS = 15 * 1000L;
	private static final long MAX_RETRY_MS = 10 * 60 * 1000L;

	private final ApiClient api;

	private volatile long expiresAt;
	private volatile boolean inFlight;
	private volatile long nextAttemptAt;
	private volatile long retryDelay = FIRST_RETRY_MS;

	// The local player's own rank, fetched once per handshake so the cosmetics menu can show it
	// without a lookup of its own. Chat lines carry their sender's rank inline, so nothing else
	// needs this.
	private volatile String rank = "Default";
	private volatile int rankColor = 0xFF9AA7B8;

	private boolean warned;

	public SessionService(ApiClient api) {
		this.api = api;
	}

	public boolean isAuthenticated() {
		return api.hasToken() && System.currentTimeMillis() < expiresAt;
	}

	public String rank() {
		return rank;
	}

	public int rankColor() {
		return rankColor;
	}

	/**
	 * Called every client tick. Starts a handshake when one is due and no other is running,
	 * which covers the first authentication, a renewal before expiry, and a retry after a
	 * failure, without any of them needing their own timer.
	 */
	public void tick() {
		if (inFlight || System.currentTimeMillis() < nextAttemptAt) {
			return;
		}
		if (System.currentTimeMillis() < expiresAt - REFRESH_MARGIN_MS) {
			return;
		}
		authenticate();
	}

	/** Force the next tick to re-run the handshake, after a write came back 401. */
	public void invalidate() {
		api.setToken(null);
		expiresAt = 0;
		nextAttemptAt = 0;
	}

	private void authenticate() {
		Minecraft minecraft = Minecraft.getInstance();
		User user = minecraft.getUser();
		String accessToken = user.getAccessToken();
		if (accessToken == null || accessToken.isBlank()) {
			// An offline-mode or demo session has nothing Mojang would confirm, so there is no
			// point retrying quickly. Back off to the ceiling and stay quiet.
			backOff();
			return;
		}

		inFlight = true;
		UUID uuid = user.getProfileId();
		String username = user.getName();
		MinecraftSessionService sessions = minecraft.getMinecraftSessionService();

		api.post("/auth/nonce", new JsonObject())
				.thenCompose(response -> {
					JsonObject body = ApiClient.asObject(response.body());
					if (response.statusCode() != 200 || body == null || !body.has("server_id")) {
						throw new IllegalStateException("nonce refused: " + ApiClient.detail(response));
					}
					String serverId = body.get("server_id").getAsString();
					// joinServer blocks on Mojang. It runs here, inside the HTTP client's
					// executor, so the render thread never waits on it.
					try {
						sessions.joinServer(uuid, accessToken, serverId);
					} catch (Exception failed) {
						throw new IllegalStateException("mojang joinServer failed", failed);
					}
					return proveSession(uuid, username, serverId);
				})
				.whenComplete((ok, error) -> {
					inFlight = false;
					if (error != null) {
						if (!warned) {
							// Once only: a client that cannot authenticate would otherwise log
							// this on every retry for the whole session.
							Cryostasis.LOGGER.warn("Backend session handshake failed, cosmetics and "
									+ "global chat will stay read-only", error);
							warned = true;
						}
						backOff();
					}
				});
	}

	private CompletableFuture<Void> proveSession(UUID uuid, String username, String serverId) {
		JsonObject body = new JsonObject();
		body.addProperty("uuid", uuid.toString());
		body.addProperty("username", username);
		body.addProperty("server_id", serverId);

		return api.post("/auth/session", body).thenAccept(response -> {
			JsonObject parsed = ApiClient.asObject(response.body());
			if (response.statusCode() != 200 || parsed == null || !parsed.has("token")) {
				throw new IllegalStateException("session refused: " + ApiClient.detail(response));
			}
			api.setToken(parsed.get("token").getAsString());
			long ttl = parsed.has("expires_in") ? parsed.get("expires_in").getAsLong() : 3600L;
			expiresAt = System.currentTimeMillis() + ttl * 1000L;
			retryDelay = FIRST_RETRY_MS;
			warned = false;
			fetchRank(uuid);
		});
	}

	private void fetchRank(UUID uuid) {
		api.get("/players/" + uuid + "/rank").thenAccept(response -> {
			JsonObject body = ApiClient.asObject(response.body());
			if (response.statusCode() != 200 || body == null || !body.has("rank")) {
				return;
			}
			rank = body.get("rank").getAsString();
			if (body.has("color")) {
				rankColor = parseColor(body.get("color").getAsString(), rankColor);
			}
		});
	}

	/** Parse the backend's "#RRGGBB" into the opaque ARGB int GuiGraphics draws with. */
	public static int parseColor(String hex, int fallback) {
		try {
			return 0xFF000000 | Integer.parseInt(hex.replace("#", ""), 16);
		} catch (NumberFormatException malformed) {
			return fallback;
		}
	}

	private void backOff() {
		nextAttemptAt = System.currentTimeMillis() + retryDelay;
		retryDelay = Math.min(retryDelay * 2, MAX_RETRY_MS);
	}
}
