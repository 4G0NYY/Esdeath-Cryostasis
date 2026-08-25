package moe.ramon.cryostasis.backend;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * The one HTTP connection to the Cryostasis backend. Every service that talks to it goes
 * through this: one {@link HttpClient} for the whole client, one place that knows the base URL,
 * and one place that attaches the bearer token, so a service can never accidentally send an
 * unauthenticated write.
 *
 * The base URL defaults to a local dev instance and is overridden with the {@code cryostasis.api}
 * system property, which is what the launcher writes into the launch profile. Nothing here
 * blocks: every call returns a future and callers hop back to the render thread themselves.
 */
public final class ApiClient {
	/** Enough for a request that is answered immediately; the long poll passes its own. */
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final String baseUrl;

	// Written by SessionService once the handshake completes, read by every request builder.
	private volatile String token;

	public ApiClient() {
		this(System.getProperty("cryostasis.api", "http://localhost:8080/api"));
	}

	public ApiClient(String baseUrl) {
		// Trim a trailing slash so path concatenation stays clean.
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	public String baseUrl() {
		return baseUrl;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public boolean hasToken() {
		return token != null;
	}

	/**
	 * A request builder for a path below the API root, already carrying the bearer token when
	 * one is held. Reads are open on the backend, so a missing token is not an error here; it
	 * simply means writes will come back 401 and the caller will ask for a fresh handshake.
	 */
	public HttpRequest.Builder request(String path, Duration timeout) {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + path))
				.timeout(timeout);
		String current = token;
		if (current != null) {
			builder.header("Authorization", "Bearer " + current);
		}
		return builder;
	}

	public CompletableFuture<HttpResponse<String>> get(String path) {
		return get(path, DEFAULT_TIMEOUT);
	}

	public CompletableFuture<HttpResponse<String>> get(String path, Duration timeout) {
		return send(request(path, timeout).GET().build());
	}

	public CompletableFuture<HttpResponse<String>> post(String path, JsonObject body) {
		// Gson serializes the body rather than string concatenation building it: a chat message
		// is arbitrary user text, and hand-built JSON would break on the first quote or
		// backslash a player types.
		HttpRequest request = request(path, DEFAULT_TIMEOUT)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
				.build();
		return send(request);
	}

	public CompletableFuture<HttpResponse<String>> delete(String path) {
		return send(request(path, DEFAULT_TIMEOUT).DELETE().build());
	}

	private CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
		return http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
	}

	/** Parse a response body as an object, or null when it is not one. Never throws. */
	public static JsonObject asObject(String body) {
		try {
			JsonElement parsed = JsonParser.parseString(body);
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException malformed) {
			return null;
		}
	}

	/**
	 * The backend's error text for a failed response, for showing a player why their message
	 * was refused. FastAPI puts it in "detail"; anything else falls back to the status code.
	 */
	public static String detail(HttpResponse<String> response) {
		JsonObject body = asObject(response.body());
		if (body != null && body.has("detail") && body.get("detail").isJsonPrimitive()) {
			return body.get("detail").getAsString();
		}
		return "HTTP " + response.statusCode();
	}
}
