package moe.ramon.cryostasis.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The client half of presence: it reports this player's state to the backend and keeps a picture
 * of everyone else's.
 *
 * The backend derives the state rather than being told it (see the presence section of
 * docs/backend-architecture.md): a heartbeat records that the client is alive, and a flag on that
 * heartbeat records whether the player did anything since the last one. Online, away, and offline
 * all fall out of those two timestamps, so a client that crashes goes offline on its own and one
 * that is merely parked stays visible instead of blinking out.
 *
 * Activity means in-world input: a key or mouse button the game has bound, or the player's
 * position or view moving. Menus deliberately do not count. Someone reading their inventory for
 * ten minutes is exactly the player the away state exists to describe, and counting a screen as
 * activity would make the state say only "the game is running", which the heartbeat already says.
 *
 * Threading follows the rest of the backend package: {@link #tick()} runs on the client thread
 * and never blocks, every HTTP call completes on the HTTP client's executor, and what those
 * callbacks write is read back through concurrent or volatile fields.
 */
public final class PresenceService {
	/** Comfortably inside the backend's 120 second presence window, with room for a lost beat. */
	private static final long HEARTBEAT_MS = 30_000;
	private static final long ROSTER_MS = 20_000;
	/** How long a looked-up player's state is trusted before the next batch refreshes it. */
	private static final long ENTRY_TTL_MS = 30_000;
	private static final long RETRY_MS = 15_000;

	/** Below this the view has not really moved, it is the jitter of a hand resting on a mouse. */
	private static final double LOOK_EPSILON = 0.05;
	private static final double MOVE_EPSILON = 0.001;

	public static final String ONLINE = "online";
	public static final String AFK = "afk";
	public static final String OFFLINE = "offline";

	/** One player as the backend reports them. Immutable, so the render thread reads it freely. */
	public record Entry(UUID uuid, String username, String rank, int color, String state,
			String status, String server) {
		public static final Entry UNKNOWN =
				new Entry(null, "", "Default", 0xFF9AA7B8, OFFLINE, "", "");

		public boolean isOnline() {
			return ONLINE.equals(state);
		}

		public boolean isAfk() {
			return AFK.equals(state);
		}

		/** What a roster line shows to the right of the name: the player's own words if they set
		 * any, otherwise the state the backend derived. */
		public String label() {
			if (!status.isBlank()) {
				return status;
			}
			return isOnline() ? "online" : isAfk() ? "away" : "offline";
		}
	}

	private final ApiClient api;
	private final SessionService session;

	private final ConcurrentHashMap<UUID, Entry> lookups = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, Long> lookedUpAt = new ConcurrentHashMap<>();
	// Requested by the render thread (a nametag wants a player it can see) and consumed by the
	// tick that sends the next batch, so rendering never starts a request of its own.
	private final Set<UUID> wanted = ConcurrentHashMap.newKeySet();

	private volatile List<Entry> roster = List.of();
	private volatile int online;
	private volatile int away;

	private volatile long nextHeartbeatAt;
	private volatile long nextRosterAt;
	private volatile boolean beating;
	private volatile boolean fetchingRoster;
	private volatile boolean batching;

	// Set by the client thread when it notices input, cleared by the heartbeat that reports it,
	// so a moment of activity between two beats is never lost to the gap.
	private volatile boolean activeSinceLastBeat;

	private double lastX;
	private double lastY;
	private double lastZ;
	private float lastYaw;
	private float lastPitch;
	private String server = "";

	public PresenceService(ApiClient api, SessionService session) {
		this.api = api;
		this.session = session;
	}

	/** Everyone the backend currently has a live client for, ordered as it sent them. */
	public List<Entry> roster() {
		return roster;
	}

	public int onlineCount() {
		return online;
	}

	public int awayCount() {
		return away;
	}

	/**
	 * A player's state, served from cache. Asks for a refresh when the entry is missing or stale
	 * and returns what is known meanwhile, so a caller on the render thread never waits. An
	 * unknown player reads as offline, which is the truthful answer until the backend says
	 * otherwise.
	 */
	public Entry get(UUID player) {
		Long fetched = lookedUpAt.get(player);
		if (fetched == null || System.currentTimeMillis() - fetched > ENTRY_TTL_MS) {
			wanted.add(player);
		}
		return lookups.getOrDefault(player, Entry.UNKNOWN);
	}

	/** Set this player's own free-text status line. Empty clears it. */
	public void setStatus(String status) {
		UUID uuid = localUuid();
		if (uuid == null || !session.isAuthenticated()) {
			return;
		}
		JsonObject body = new JsonObject();
		body.addProperty("status", status);
		api.put("/players/" + uuid + "/status", body).thenAccept(response -> {
			if (response.statusCode() == 401) {
				session.invalidate();
			}
		});
	}

	/** This player's own record, as the backend last reported it. */
	public Entry self() {
		UUID uuid = localUuid();
		return uuid == null ? Entry.UNKNOWN : get(uuid);
	}

	/** Client thread: notice input, then send whatever is due. Never blocks. */
	public void tick() {
		observeInput();
		if (!session.isAuthenticated()) {
			// Writes would come back 401 and reads would show a roster this client is not on, so
			// wait for the handshake rather than burning requests.
			return;
		}
		long now = System.currentTimeMillis();
		if (!beating && now >= nextHeartbeatAt) {
			heartbeat();
		}
		if (!fetchingRoster && now >= nextRosterAt) {
			fetchRoster();
		}
		if (!batching && !wanted.isEmpty()) {
			fetchWanted();
		}
	}

	/**
	 * Watch for in-world input. Any bound key or mouse button being down counts, as does the
	 * player having moved or looked somewhere since the last tick, which together cover playing
	 * without assuming which keys a player has bound to what.
	 */
	private void observeInput() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		if (moved(player) || anyKeyDown(minecraft)) {
			activeSinceLastBeat = true;
		}
		lastX = player.getX();
		lastY = player.getY();
		lastZ = player.getZ();
		lastYaw = player.getYRot();
		lastPitch = player.getXRot();
	}

	private boolean moved(LocalPlayer player) {
		return Math.abs(player.getX() - lastX) > MOVE_EPSILON
				|| Math.abs(player.getY() - lastY) > MOVE_EPSILON
				|| Math.abs(player.getZ() - lastZ) > MOVE_EPSILON
				|| Math.abs(player.getYRot() - lastYaw) > LOOK_EPSILON
				|| Math.abs(player.getXRot() - lastPitch) > LOOK_EPSILON;
	}

	private static boolean anyKeyDown(Minecraft minecraft) {
		// A screen swallows input before it reaches a key mapping, so a player standing in a menu
		// with a key held is not moving anything and should not read as active.
		if (minecraft.screen != null) {
			return false;
		}
		for (KeyMapping mapping : minecraft.options.keyMappings) {
			if (mapping.isDown()) {
				return true;
			}
		}
		return false;
	}

	private void heartbeat() {
		UUID uuid = localUuid();
		if (uuid == null) {
			return;
		}
		beating = true;
		// Read and clear together, so activity that happens while the request is in flight is
		// reported by the next beat rather than swallowed by this one.
		boolean active = activeSinceLastBeat;
		activeSinceLastBeat = false;

		JsonObject body = new JsonObject();
		body.addProperty("active", active);
		String current = currentServer();
		if (!current.equals(server)) {
			// Only when it changes: the server is a property of the session, not of the beat, and
			// resending it every thirty seconds writes a row for nothing.
			body.addProperty("server", current);
			server = current;
		}

		api.post("/players/" + uuid + "/online", body)
				.thenApply(response -> {
					if (response.statusCode() == 401) {
						session.invalidate();
					}
					return response.statusCode() < 300;
				})
				.exceptionally(error -> false)
				.thenAccept(stored -> {
					beating = false;
					if (!stored) {
						// A refused beat has to put the activity back, or a player who was moving
						// while the network hiccuped reads as away on the next attempt. Only ever
						// set, never cleared: the client thread is the other writer and it only
						// sets too, so neither can lose the other's activity.
						if (active) {
							activeSinceLastBeat = true;
						}
						// Likewise the server, which is only sent when it changes: forgetting it
						// here is what makes the next beat send it again.
						server = "";
					}
					nextHeartbeatAt = System.currentTimeMillis() + (stored ? HEARTBEAT_MS : RETRY_MS);
				});
	}

	private void fetchRoster() {
		fetchingRoster = true;
		api.get("/players/presence")
				.thenAccept(response -> {
					if (response.statusCode() != 200) {
						return;
					}
					JsonObject body = ApiClient.asObject(response.body());
					if (body == null || !body.has("players") || !body.get("players").isJsonArray()) {
						return;
					}
					List<Entry> parsed = new ArrayList<>();
					for (JsonElement element : body.getAsJsonArray("players")) {
						if (element.isJsonObject()) {
							Entry entry = parse(element.getAsJsonObject());
							parsed.add(entry);
							remember(entry);
						}
					}
					roster = Collections.unmodifiableList(parsed);
					online = number(body, "online");
					away = number(body, "afk");
				})
				.whenComplete((ok, error) -> {
					fetchingRoster = false;
					nextRosterAt = System.currentTimeMillis() + (error != null ? RETRY_MS : ROSTER_MS);
				});
	}

	/**
	 * Resolve the players the render surfaces asked about in one call. They are the players this
	 * client can see rather than the players who are around, so the roster would answer for most
	 * of them and none of the offline ones.
	 */
	private void fetchWanted() {
		List<UUID> batch = new ArrayList<>(new HashSet<>(wanted));
		wanted.clear();
		if (batch.isEmpty()) {
			return;
		}
		batching = true;
		JsonArray uuids = new JsonArray();
		for (UUID uuid : batch) {
			uuids.add(uuid.toString());
		}
		JsonObject body = new JsonObject();
		body.add("uuids", uuids);

		api.post("/players/presence/batch", body)
				.thenAccept(response -> {
					JsonObject parsed = ApiClient.asObject(response.body());
					if (response.statusCode() != 200 || parsed == null || !parsed.has("players")) {
						return;
					}
					JsonObject players = parsed.getAsJsonObject("players");
					for (UUID uuid : batch) {
						JsonElement element = players.get(uuid.toString());
						if (element != null && element.isJsonObject()) {
							remember(parse(element.getAsJsonObject()));
						}
						// Stamped whether or not the backend knew them, so an unknown player is
						// asked about once per TTL instead of on every frame.
						lookedUpAt.put(uuid, System.currentTimeMillis());
					}
				})
				.whenComplete((ok, error) -> batching = false);
	}

	private void remember(Entry entry) {
		if (entry.uuid() != null) {
			lookups.put(entry.uuid(), entry);
			lookedUpAt.put(entry.uuid(), System.currentTimeMillis());
		}
	}

	private static Entry parse(JsonObject object) {
		UUID uuid = parseUuid(string(object, "uuid", ""));
		return new Entry(
				uuid,
				string(object, "username", ""),
				string(object, "rank", "Default"),
				SessionService.parseColor(string(object, "color", "#9AA7B8"), 0xFF9AA7B8),
				string(object, "state", OFFLINE),
				string(object, "status", ""),
				string(object, "server", ""));
	}

	private static UUID parseUuid(String raw) {
		try {
			return raw.isEmpty() ? null : UUID.fromString(raw);
		} catch (IllegalArgumentException malformed) {
			return null;
		}
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive()
				? object.get(key).getAsString()
				: fallback;
	}

	private static int number(JsonObject object, String key) {
		return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : 0;
	}

	/**
	 * The name to report as the server this player is on. The address rather than the entry's
	 * label, because the label is whatever the player typed into their server list and two people
	 * on the same server would otherwise land in different buckets.
	 */
	private static String currentServer() {
		Minecraft minecraft = Minecraft.getInstance();
		ServerData data = minecraft.getCurrentServer();
		if (data != null) {
			return data.ip;
		}
		return minecraft.level != null ? "singleplayer" : "";
	}

	private static UUID localUuid() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			return minecraft.player.getUUID();
		}
		return minecraft.getUser().getProfileId();
	}
}
