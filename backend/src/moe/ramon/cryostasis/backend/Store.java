package moe.ramon.cryostasis.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory data store with JSON file persistence, backing the dev instance of the
 * cosmetics backend. Every mutation flushes the whole state to disk, which is fine at dev
 * scale and keeps restarts lossless.
 *
 * This is deliberately the one place that knows how data is stored. A production build
 * swaps this class for a Postgres-backed repository plus object storage for textures,
 * without the HTTP layer changing.
 */
public final class Store {
	/** All mutable per-player state the protocol exposes. */
	public static final class Player {
		public String rank = "Default";
		public String status = "";
		public boolean online = false;
		public String server = "";
		public String cape = "";
		public Set<String> cosmetics = new LinkedHashSet<>();
	}

	/** A single global chat line. */
	public static final class ChatLine {
		public String from;
		public String message;

		public ChatLine(String from, String message) {
			this.from = from;
			this.message = message;
		}
	}

	private static final int CHAT_HISTORY = 100;

	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;

	private final Map<String, Player> players = new ConcurrentHashMap<>();
	private final List<String> capes = new ArrayList<>();
	private final Deque<ChatLine> chat = new ArrayDeque<>();

	public Store(Path file) {
		this.file = file;
		load();
		if (capes.isEmpty()) {
			// Seed the cape catalogue so a fresh dev instance has something to serve.
			capes.add("classic-Default");
			capes.add("aqua-Premium");
			capes.add("ember-Epic");
			capes.add("mythic-Chef");
		}
	}

	public synchronized Player player(String uuid) {
		return players.computeIfAbsent(uuid, k -> new Player());
	}

	public synchronized List<String> capes() {
		return new ArrayList<>(capes);
	}

	public synchronized void setOnline(String uuid, boolean online) {
		player(uuid).online = online;
		save();
	}

	public synchronized void setServer(String uuid, String server) {
		player(uuid).server = server;
		player(uuid).online = true;
		save();
	}

	public synchronized void setStatus(String uuid, String status) {
		player(uuid).status = status;
		save();
	}

	public synchronized void setCape(String uuid, String cape) {
		player(uuid).cape = cape;
		save();
	}

	public synchronized boolean addCosmetic(String uuid, String cosmetic) {
		boolean added = player(uuid).cosmetics.add(cosmetic.toLowerCase());
		save();
		return added;
	}

	public synchronized boolean removeCosmetic(String uuid, String cosmetic) {
		boolean removed = player(uuid).cosmetics.remove(cosmetic.toLowerCase());
		save();
		return removed;
	}

	public synchronized boolean hasCosmetic(String uuid, String cosmetic) {
		Player p = players.get(uuid);
		return p != null && p.cosmetics.contains(cosmetic.toLowerCase());
	}

	public synchronized List<String> onlinePlayers() {
		List<String> result = new ArrayList<>();
		for (Map.Entry<String, Player> e : players.entrySet()) {
			if (e.getValue().online) {
				result.add(e.getKey());
			}
		}
		return result;
	}

	public synchronized List<String> playersOnServer(String server) {
		List<String> result = new ArrayList<>();
		for (Map.Entry<String, Player> e : players.entrySet()) {
			if (e.getValue().online && server.equalsIgnoreCase(e.getValue().server)) {
				result.add(e.getKey());
			}
		}
		return result;
	}

	public synchronized void addChat(String from, String message) {
		chat.addLast(new ChatLine(from, message));
		while (chat.size() > CHAT_HISTORY) {
			chat.removeFirst();
		}
		save();
	}

	public synchronized ChatLine latestChat() {
		return chat.peekLast();
	}

	// Persistence. The whole store is one JSON document.

	private static final class Snapshot {
		Map<String, Player> players;
		List<String> capes;
		Deque<ChatLine> chat;
	}

	private void save() {
		Snapshot snap = new Snapshot();
		snap.players = players;
		snap.capes = capes;
		snap.chat = chat;
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			Files.writeString(file, gson.toJson(snap));
		} catch (IOException e) {
			System.err.println("Failed to persist store: " + e.getMessage());
		}
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try {
			Type type = new TypeToken<Snapshot>() {
			}.getType();
			Snapshot snap = gson.fromJson(Files.readString(file), type);
			if (snap == null) {
				return;
			}
			if (snap.players != null) {
				players.putAll(snap.players);
			}
			if (snap.capes != null) {
				capes.addAll(snap.capes);
			}
			if (snap.chat != null) {
				chat.addAll(snap.chat);
			}
		} catch (Exception e) {
			System.err.println("Failed to load store, starting empty: " + e.getMessage());
		}
	}
}
