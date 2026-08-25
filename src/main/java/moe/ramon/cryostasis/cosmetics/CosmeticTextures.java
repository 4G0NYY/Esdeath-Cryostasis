package moe.ramon.cryostasis.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import moe.ramon.cryostasis.Cryostasis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads cosmetic textures the backend serves from the CDN, so a cosmetic added there shows up
 * without shipping a new mod jar.
 *
 * The backend stores only an object key and returns a URL built from it. Keys are
 * content-addressed, which is what makes this cache safe to keep for the whole session: a changed
 * texture is a different key and so a different URL, and there is no invalidation to get wrong.
 *
 * A texture is only ever used once it is fully downloaded and registered. Until then, and forever
 * if the download fails, {@link #resolve} hands back the mod's own bundled texture, so a cosmetic
 * that ships with the client renders identically whether or not the CDN is reachable. That is
 * also why nothing here blocks: the render thread asks and takes whatever is ready.
 */
public final class CosmeticTextures {
	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	// slug -> registered texture, once the bytes have landed and been uploaded.
	private static final Map<String, ResourceLocation> LOADED = new ConcurrentHashMap<>();
	// Guards against a second request for a slug already in flight or already known to fail.
	private static final Map<String, Boolean> REQUESTED = new ConcurrentHashMap<>();

	private CosmeticTextures() {
	}

	/**
	 * The texture to draw for a cosmetic: the CDN one when it has arrived, the bundled one
	 * otherwise. Called on the render thread every frame, so it must stay a map lookup.
	 */
	public static ResourceLocation resolve(String slug, ResourceLocation bundled) {
		ResourceLocation remote = LOADED.get(slug);
		if (remote != null) {
			return remote;
		}
		String url = CosmeticCatalogue.textureUrl(slug);
		if (url != null && REQUESTED.putIfAbsent(slug, Boolean.TRUE) == null) {
			download(slug, url);
		}
		return bundled;
	}

	private static void download(String slug, String url) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
		HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
				.thenAccept(response -> {
					if (response.statusCode() != 200) {
						Cryostasis.LOGGER.warn("Cosmetic texture {} returned HTTP {}", slug, response.statusCode());
						return;
					}
					// Decoding and uploading both touch GL state, so they belong on the render
					// thread, not on the HTTP client's executor.
					Minecraft.getInstance().execute(() -> register(slug, response.body()));
				})
				.exceptionally(error -> {
					Cryostasis.LOGGER.warn("Cosmetic texture {} could not be fetched", slug, error);
					return null;
				});
	}

	private static void register(String slug, byte[] bytes) {
		try {
			NativeImage image = NativeImage.read(bytes);
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
					Cryostasis.MOD_ID, "textures/cosmetic/remote/" + slug + ".png");
			Minecraft.getInstance().getTextureManager()
					.register(id, new DynamicTexture(() -> "cryostasis/" + slug, image));
			LOADED.put(slug, id);
		} catch (Exception failed) {
			// A malformed image is not worth retrying: the bundled texture stays in use and the
			// slug is already marked as requested, so this logs once rather than every frame.
			Cryostasis.LOGGER.warn("Cosmetic texture {} could not be decoded", slug, failed);
		}
	}
}
