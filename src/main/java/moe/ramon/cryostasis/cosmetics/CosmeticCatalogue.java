package moe.ramon.cryostasis.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import moe.ramon.cryostasis.backend.ApiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The list of cosmetics offered in the menu, served by the backend rather than compiled in.
 *
 * This is the catalogue side of the free-for-all model: every linked account may wear any of
 * these, so the menu offers all of them and the per-player state is only which ones are active
 * (see {@link CosmeticService.Active}). Reading it from the backend is what lets a cosmetic be
 * added there, with its texture on the CDN, and appear in the menu with no new mod jar.
 *
 * Two things stay local. {@link #RENDERED} is the set of slugs this client actually has a model
 * for, so the menu can show the rest as not-yet-available instead of letting a player toggle
 * something invisible; it must stay in step with the cosmetics {@code CosmeticLayer} bakes.
 * {@link #DISPLAY_NAMES} spells the ones whose slug does not title-case into a decent label.
 *
 * Until the first fetch lands, and if it never does, the built-in entries below stand in, so the
 * menu is never empty and works against a backend that is down.
 */
public final class CosmeticCatalogue {
	/** One selectable cosmetic. {@code renderable} is whether this client can actually draw it. */
	public record Entry(
			String key,
			String displayName,
			String rarity,
			String textureUrl,
			boolean renderable) {
	}

	/** Slugs with a model in this client. Keep in step with the layer's baked cosmetics. */
	public static final Set<String> RENDERED = Set.of(
			"halo", "bandana", "tophat", "rabbitears", "reifen", "stripes", "tail", "wings", "susanoo");

	private static final Map<String, String> DISPLAY_NAMES = Map.of(
			"tophat", "Top Hat",
			"rabbitears", "Rabbit Ears");

	private static final List<Entry> BUILT_IN = List.of(
			new Entry("halo", "Halo", "Epic", null, true),
			new Entry("bandana", "Bandana", "Default", null, true),
			new Entry("tophat", "Top Hat", "Premium", null, true),
			new Entry("wings", "Wings", "Epic", null, true),
			new Entry("tail", "Tail", "Default", null, true),
			new Entry("rabbitears", "Rabbit Ears", "Default", null, true),
			new Entry("reifen", "Reifen", "Epic", null, true),
			new Entry("susanoo", "Susanoo", "Chef", null, true),
			new Entry("stripes", "Stripes", "Default", null, true));

	/** Long enough that the menu is not a polling loop, short enough to pick up a new cosmetic. */
	private static final long TTL_MS = 5 * 60 * 1000L;

	private static volatile List<Entry> entries = BUILT_IN;
	private static volatile long fetchedAt;
	private static volatile boolean loading;
	private static ApiClient api;

	private CosmeticCatalogue() {
	}

	/** Give the catalogue the connection it refreshes over. Called once at client init. */
	public static void bind(ApiClient client) {
		api = client;
	}

	/**
	 * The catalogue, served from cache. Triggers a refresh when the cache is missing or stale and
	 * returns immediately either way, so the menu draws on the frame it is opened.
	 */
	public static List<Entry> entries() {
		long now = System.currentTimeMillis();
		if (api != null && !loading && (fetchedAt == 0 || now - fetchedAt > TTL_MS)) {
			refresh();
		}
		return entries;
	}

	/** The CDN URL for a slug's texture, or null when it has none and the bundled one is used. */
	public static String textureUrl(String slug) {
		for (Entry entry : entries) {
			if (entry.key().equals(slug)) {
				return entry.textureUrl();
			}
		}
		return null;
	}

	private static void refresh() {
		loading = true;
		api.get("/cosmetics")
				.thenAccept(response -> {
					if (response.statusCode() != 200) {
						return;
					}
					JsonObject body = ApiClient.asObject(response.body());
					if (body == null || !body.has("cosmetics") || !body.get("cosmetics").isJsonArray()) {
						return;
					}
					List<Entry> parsed = parse(body.getAsJsonArray("cosmetics"));
					// An empty catalogue is treated as a bad answer rather than as "no cosmetics
					// exist", so a misconfigured backend cannot empty the menu.
					if (!parsed.isEmpty()) {
						entries = parsed;
					}
				})
				.whenComplete((ok, error) -> {
					fetchedAt = System.currentTimeMillis();
					loading = false;
				});
	}

	private static List<Entry> parse(JsonArray array) {
		List<Entry> parsed = new ArrayList<>(array.size());
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject object = element.getAsJsonObject();
			if (!object.has("slug")) {
				continue;
			}
			String slug = object.get("slug").getAsString().toLowerCase();
			String textureUrl = object.has("texture_url") && !object.get("texture_url").isJsonNull()
					? object.get("texture_url").getAsString()
					: null;
			String rarity = object.has("rarity") ? object.get("rarity").getAsString() : "Default";
			parsed.add(new Entry(slug, displayName(slug), rarity, textureUrl, RENDERED.contains(slug)));
		}
		return parsed;
	}

	private static String displayName(String slug) {
		String known = DISPLAY_NAMES.get(slug);
		if (known != null) {
			return known;
		}
		return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
	}
}
