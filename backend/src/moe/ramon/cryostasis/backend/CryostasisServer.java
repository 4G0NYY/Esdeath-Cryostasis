package moe.ramon.cryostasis.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;

/**
 * Local dev instance of the Cryostasis cosmetics backend. Implements the REST contract in
 * docs/backend-api.md on the JDK's built-in HTTP server, so it runs with only the JDK and
 * Gson, no framework. State lives in {@link Store}.
 *
 * This is the dev target the client is tested against. Production replaces the store with
 * a real database plus object storage and adds Microsoft OAuth token verification in
 * {@link #authorized}; the routing and payload shapes stay identical.
 */
public final class CryostasisServer {
	private static final Gson GSON = new Gson();
	private static final String VERSION = "cryostasis-1";

	private final Store store;
	private final String requiredToken; // null in open dev mode
	private final List<Route> routes = new ArrayList<>();

	public CryostasisServer(Store store, String requiredToken) {
		this.store = store;
		this.requiredToken = requiredToken;
		buildRoutes();
	}

	public static void main(String[] args) throws IOException {
		int port = Integer.parseInt(envOr("CRYOSTASIS_PORT", "8080"));
		Path dataFile = Paths.get(envOr("CRYOSTASIS_DATA", "backend-data.json"));
		String token = System.getenv("CRYOSTASIS_TOKEN"); // unset means open dev mode

		Store store = new Store(dataFile);
		CryostasisServer server = new CryostasisServer(store, token);
		HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
		http.createContext("/", server::dispatch);
		http.setExecutor(Executors.newFixedThreadPool(8));
		http.start();
		System.out.println("Cryostasis backend listening on http://localhost:" + port
				+ (token == null ? " (open dev mode, no auth)" : " (token required)"));
		System.out.println("Data file: " + dataFile.toAbsolutePath());
	}

	// Routing.

	private interface Handler {
		void handle(HttpExchange exchange, Matcher path) throws IOException;
	}

	private record Route(String method, Pattern pattern, boolean requiresAuth, Handler handler) {
	}

	private void get(String regex, Handler h) {
		routes.add(new Route("GET", Pattern.compile("^" + regex + "$"), false, h));
	}

	private void mutate(String method, String regex, Handler h) {
		routes.add(new Route(method, Pattern.compile("^" + regex + "$"), true, h));
	}

	private void buildRoutes() {
		String uuid = "([0-9a-fA-F-]{1,64})";
		String name = "([^/]{1,128})";

		get("/api/version", (ex, m) -> respond(ex, 200, Map.of("version", VERSION)));
		get("/api/capes", (ex, m) -> respond(ex, 200, Map.of("capes", store.capes())));

		mutate("POST", "/api/players/" + uuid + "/online", (ex, m) -> {
			store.setOnline(m.group(1), true);
			respond(ex, 204, null);
		});
		mutate("PUT", "/api/players/" + uuid + "/server", (ex, m) -> {
			store.setServer(m.group(1), body(ex).get("server").getAsString());
			respond(ex, 204, null);
		});
		mutate("PUT", "/api/players/" + uuid + "/status", (ex, m) -> {
			store.setStatus(m.group(1), body(ex).get("status").getAsString());
			respond(ex, 204, null);
		});
		get("/api/players/" + uuid + "/status", (ex, m) ->
				respond(ex, 200, Map.of("status", store.player(m.group(1)).status)));
		get("/api/players/" + uuid + "/rank", (ex, m) ->
				respond(ex, 200, Map.of("rank", store.player(m.group(1)).rank)));

		get("/api/servers/" + name + "/players", (ex, m) ->
				respond(ex, 200, Map.of("players", store.playersOnServer(m.group(1)))));
		get("/api/players/online", (ex, m) -> {
			List<String> online = store.onlinePlayers();
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("players", online);
			out.put("count", online.size());
			respond(ex, 200, out);
		});

		// Batch fetch, the addition over the original protocol so the renderer fetches once
		// per visible player instead of once per cosmetic.
		get("/api/players/" + uuid + "/cosmetics", (ex, m) -> {
			Store.Player p = store.player(m.group(1));
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("cosmetics", new ArrayList<>(p.cosmetics));
			out.put("cape", p.cape);
			respond(ex, 200, out);
		});
		mutate("POST", "/api/players/" + uuid + "/cosmetics", (ex, m) -> {
			boolean ok = store.addCosmetic(m.group(1), body(ex).get("cosmetic").getAsString());
			respond(ex, 200, Map.of("ok", ok));
		});
		mutate("DELETE", "/api/players/" + uuid + "/cosmetics/" + name, (ex, m) -> {
			boolean ok = store.removeCosmetic(m.group(1), m.group(2));
			respond(ex, 200, Map.of("ok", ok));
		});
		get("/api/players/" + uuid + "/cosmetics/" + name, (ex, m) ->
				respond(ex, 200, Map.of("has", store.hasCosmetic(m.group(1), m.group(2)))));

		mutate("PUT", "/api/players/" + uuid + "/cape", (ex, m) -> {
			store.setCape(m.group(1), body(ex).get("cape").getAsString());
			respond(ex, 204, null);
		});

		get("/api/chat", (ex, m) -> {
			Store.ChatLine line = store.latestChat();
			Map<String, Object> out = new LinkedHashMap<>();
			out.put("message", line == null ? "" : line.message);
			out.put("from", line == null ? "" : line.from);
			respond(ex, 200, out);
		});
		mutate("POST", "/api/chat", (ex, m) -> {
			JsonObject b = body(ex);
			String from = b.has("from") ? b.get("from").getAsString() : "anon";
			store.addChat(from, b.get("message").getAsString());
			respond(ex, 204, null);
		});
	}

	private void dispatch(HttpExchange exchange) {
		try {
			String method = exchange.getRequestMethod();
			String path = exchange.getRequestURI().getPath();
			for (Route route : routes) {
				if (!route.method().equals(method)) {
					continue;
				}
				Matcher m = route.pattern().matcher(path);
				if (!m.matches()) {
					continue;
				}
				if (route.requiresAuth() && !authorized(exchange)) {
					respond(exchange, 401, Map.of("error", "unauthorized"));
					return;
				}
				route.handler().handle(exchange, m);
				return;
			}
			respond(exchange, 404, Map.of("error", "not found"));
		} catch (Exception e) {
			try {
				respond(exchange, 400, Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
			} catch (IOException ignored) {
			}
		}
	}

	/**
	 * In dev mode (no token configured) every request is allowed. Otherwise a matching
	 * bearer token is required. Production replaces this with Microsoft OAuth token
	 * verification that also checks the token subject owns the UUID being mutated.
	 */
	private boolean authorized(HttpExchange exchange) {
		if (requiredToken == null) {
			return true;
		}
		String header = exchange.getRequestHeaders().getFirst("Authorization");
		return header != null && header.equals("Bearer " + requiredToken);
	}

	// IO helpers.

	private JsonObject body(HttpExchange exchange) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			if (raw.isBlank()) {
				return new JsonObject();
			}
			return JsonParser.parseString(raw).getAsJsonObject();
		}
	}

	private void respond(HttpExchange exchange, int status, Object payload) throws IOException {
		if (payload == null) {
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
			return;
		}
		byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}

	private static String envOr(String key, String fallback) {
		String value = System.getenv(key);
		return value == null || value.isBlank() ? fallback : value;
	}
}
