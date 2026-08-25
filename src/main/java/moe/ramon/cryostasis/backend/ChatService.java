package moe.ramon.cryostasis.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * The client half of Cryostasis global chat: one channel shared by everyone running the client,
 * regardless of which game server they happen to be on.
 *
 * Delivery is a long poll, not a socket, because the backend runs as several stateless replicas
 * with nothing to broadcast through. A read asks for everything after the highest message id this
 * client holds and is held open until something arrives, so an idle channel costs roughly one
 * request per wait window rather than one per second.
 *
 * Threading: the poll and the send are async HTTP calls that complete on the HTTP client's
 * executor, so nothing they do may touch the game. They only enqueue; {@link #tick()} runs on the
 * client thread and is the only place messages reach the chat overlay.
 */
public final class ChatService {
	/** Matches the backend's poll ceiling; the HTTP timeout has to outlast it. */
	private static final int WAIT_SECONDS = 25;
	private static final Duration POLL_TIMEOUT = Duration.ofSeconds(WAIT_SECONDS + 10);

	private static final long RETRY_DELAY_MS = 5000L;
	private static final int TAG_COLOR = 0xFF5A8FC7;

	private final ApiClient api;
	private final SessionService session;

	// Handed from the HTTP executor to the client thread. ArrayDeque behind a lock rather than a
	// concurrent queue: a poll delivers a whole batch at once, and draining is one tick's work.
	private final Queue<Component> inbox = new ArrayDeque<>();

	private volatile boolean running;
	private volatile boolean polling;
	private volatile long cursor = -1;  // -1 until the first read establishes the live end
	private volatile long nextPollAt;
	private int backlog = 5;

	public ChatService(ApiClient api, SessionService session) {
		this.api = api;
		this.session = session;
	}

	/** Begin reading the channel, showing at most {@code backlog} messages of recent history. */
	public void start(int backlog) {
		this.backlog = Math.max(0, backlog);
		this.cursor = -1;
		this.nextPollAt = 0;
		this.running = true;
	}

	public void stop() {
		running = false;
		synchronized (inbox) {
			inbox.clear();
		}
	}

	/** Client thread: deliver whatever arrived, then start the next read if one is due. */
	public void tick() {
		drain();
		if (!running || polling || System.currentTimeMillis() < nextPollAt) {
			return;
		}
		poll();
	}

	private void drain() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui == null) {
			return;
		}
		synchronized (inbox) {
			while (!inbox.isEmpty()) {
				minecraft.gui.getChat().addMessage(inbox.poll());
			}
		}
	}

	private void poll() {
		polling = true;
		// The first read has no cursor, so it asks for a slice of history instead and takes the
		// live end from the reply. Later reads wait, since there is a position to wait from.
		String path = cursor < 0
				? "/chat?limit=" + Math.max(backlog, 1)
				: "/chat?after=" + cursor + "&wait=" + WAIT_SECONDS;

		api.get(path, POLL_TIMEOUT)
				.thenAccept(response -> {
					if (response.statusCode() != 200) {
						return;
					}
					JsonObject body = ApiClient.asObject(response.body());
					if (body == null) {
						return;
					}
					boolean first = cursor < 0;
					if (body.has("cursor")) {
						cursor = body.get("cursor").getAsLong();
					}
					queue(body, first);
				})
				.whenComplete((ok, error) -> {
					polling = false;
					// A failed read backs off; a successful one polls again at once, since the
					// wait already happened on the server side.
					nextPollAt = error != null ? System.currentTimeMillis() + RETRY_DELAY_MS : 0;
				});
	}

	private void queue(JsonObject body, boolean first) {
		if (!body.has("messages") || !body.get("messages").isJsonArray()) {
			return;
		}
		JsonArray messages = body.getAsJsonArray("messages");
		// On the very first read the whole slice is history, and the Backlog setting decides how
		// much of it a player wanted to see. Later reads are all live.
		int skip = first ? Math.max(0, messages.size() - backlog) : 0;
		synchronized (inbox) {
			for (int i = skip; i < messages.size(); i++) {
				JsonElement element = messages.get(i);
				if (element.isJsonObject()) {
					inbox.add(format(element.getAsJsonObject()));
				}
			}
		}
	}

	/** Render one message as {@code [EC] [Rank] Name: text}, coloured by the sender's rank. */
	private static Component format(JsonObject message) {
		String username = string(message, "username", "Player");
		String rank = string(message, "rank", "Default");
		int color = SessionService.parseColor(string(message, "color", "#9AA7B8"), 0xFF9AA7B8);
		Style rankStyle = Style.EMPTY.withColor(TextColor.fromRgb(color & 0xFFFFFF));

		MutableComponent line = Component.literal("[EC] ")
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(TAG_COLOR & 0xFFFFFF)));
		// The Default tag is left off: it is every player's starting rank, so printing it on
		// every line would be noise rather than information.
		if (!"Default".equalsIgnoreCase(rank)) {
			line.append(Component.literal("[" + rank + "] ").withStyle(rankStyle));
		}
		return line.append(Component.literal(username).withStyle(rankStyle))
				.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(string(message, "message", "")).withStyle(ChatFormatting.WHITE));
	}

	/**
	 * Post a line to the channel, or run a moderation command when it starts with a slash.
	 *
	 * Called from the chat screen the moment a player presses enter, so it must not block; the
	 * result comes back as a local notice.
	 */
	public void send(String text) {
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return;
		}
		if (!session.isAuthenticated()) {
			notice("Not signed in to the Cryostasis backend yet, try again in a moment.");
			return;
		}
		if (trimmed.startsWith("/")) {
			command(trimmed.substring(1));
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("message", trimmed);
		api.post("/chat", body).thenAccept(response -> {
			if (response.statusCode() == 401) {
				// The token lapsed mid-session; the next tick re-runs the handshake.
				session.invalidate();
				notice("Session expired, reconnecting.");
			} else if (response.statusCode() != 200) {
				notice(ApiClient.detail(response));
			}
		});
	}

	/**
	 * Moderation, for the staff ranks the backend grants it to. Deliberately small: mute, unmute,
	 * and list. Deleting a message is left to the API, since a player never sees the message id
	 * a delete would need.
	 */
	private void command(String raw) {
		String[] parts = raw.split("\\s+", 4);
		String verb = parts[0].toLowerCase();
		switch (verb) {
			case "mute" -> {
				if (parts.length < 2) {
					notice("Usage: /mute <player> [minutes] [reason]");
					return;
				}
				JsonObject body = new JsonObject();
				body.addProperty("player", parts[1]);
				body.addProperty("minutes", parts.length > 2 ? parseMinutes(parts[2]) : 10);
				body.addProperty("reason", parts.length > 3 ? parts[3] : "");
				api.post("/chat/mutes", body).thenAccept(response -> report(response, "Muted " + parts[1] + "."));
			}
			case "unmute" -> {
				if (parts.length < 2) {
					notice("Usage: /unmute <player>");
					return;
				}
				api.delete("/chat/mutes/" + parts[1])
						.thenAccept(response -> report(response, "Unmuted " + parts[1] + "."));
			}
			case "mutes" -> api.get("/chat/mutes").thenAccept(ChatService::reportMutes);
			default -> notice("Unknown command. Try mute, unmute, or mutes.");
		}
	}

	private static int parseMinutes(String raw) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException notANumber) {
			return 10;
		}
	}

	private static void report(HttpResponse<String> response, String success) {
		notice(response.statusCode() < 300 ? success : ApiClient.detail(response));
	}

	private static void reportMutes(HttpResponse<String> response) {
		JsonObject body = ApiClient.asObject(response.body());
		if (response.statusCode() != 200 || body == null || !body.has("mutes")) {
			notice(ApiClient.detail(response));
			return;
		}
		JsonArray mutes = body.getAsJsonArray("mutes");
		if (mutes.isEmpty()) {
			notice("Nobody is muted.");
			return;
		}
		StringBuilder names = new StringBuilder();
		for (JsonElement element : mutes) {
			JsonObject mute = element.getAsJsonObject();
			if (!names.isEmpty()) {
				names.append(", ");
			}
			names.append(string(mute, "username", string(mute, "uuid", "?")));
		}
		notice("Muted: " + names);
	}

	/**
	 * A client-side line, shown only to this player. Queued rather than drawn, because these are
	 * produced by HTTP callbacks that are not on the client thread.
	 */
	private static void notice(String text) {
		Component line = Component.literal("[EC] ")
				.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(TAG_COLOR & 0xFFFFFF)))
				.append(Component.literal(text).withStyle(ChatFormatting.GRAY));
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.gui != null) {
				minecraft.gui.getChat().addMessage(line);
			}
		});
	}

	private static String string(JsonObject object, String key, String fallback) {
		return object.has(key) && object.get(key).isJsonPrimitive()
				? object.get(key).getAsString()
				: fallback;
	}
}
