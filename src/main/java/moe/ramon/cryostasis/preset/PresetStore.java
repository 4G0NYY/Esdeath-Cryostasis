package moe.ramon.cryostasis.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The presets on disk, beside the main config rather than inside it, so a broken preset file can
 * never cost a player their module setup.
 *
 * It also records which presets the backend has not confirmed yet, so an edit made before the
 * session handshake finished is still pushed after a restart instead of being overwritten by the
 * next pull.
 */
public final class PresetStore {
	private static final int VERSION = 1;

	/** What the file holds: the presets, then the names still owed to the backend. */
	public record Contents(List<Preset> presets, List<String> unsynced, List<String> deleted) {
		static final Contents EMPTY = new Contents(List.of(), List.of(), List.of());
	}

	private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final Logger logger;
	private final Path file;

	public PresetStore(Logger logger) {
		this.logger = logger;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("esdeath-cryostasis-presets.json");
	}

	public Contents load() {
		if (!Files.exists(file)) {
			return Contents.EMPTY;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			List<Preset> presets = new ArrayList<>();
			JsonObject stored = root.getAsJsonObject("presets");
			for (String name : stored.keySet()) {
				// One hand-edited entry gone wrong costs that entry, not every preset in the file,
				// which the next save would otherwise overwrite with nothing.
				if (stored.get(name).isJsonObject()) {
					presets.add(new Preset(name, stored.getAsJsonObject(name)));
				} else {
					logger.warn("Skipping malformed preset {} in {}", name, file);
				}
			}
			return new Contents(presets, names(root, "unsynced"), names(root, "deleted"));
		} catch (IOException | RuntimeException e) {
			logger.error("Failed to load presets from {}, starting with none", file, e);
			return Contents.EMPTY;
		}
	}

	public void save(Collection<Preset> presets, Collection<String> unsynced, Collection<String> deleted) {
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		JsonObject stored = new JsonObject();
		for (Preset preset : presets) {
			stored.add(preset.name(), preset.modules());
		}
		root.add("presets", stored);
		root.add("unsynced", array(unsynced));
		root.add("deleted", array(deleted));
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(root));
		} catch (IOException e) {
			logger.error("Failed to save presets to {}", file, e);
		}
	}

	private static List<String> names(JsonObject root, String key) {
		List<String> names = new ArrayList<>();
		if (root.has(key) && root.get(key).isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray(key)) {
				names.add(element.getAsString());
			}
		}
		return names;
	}

	private static JsonArray array(Collection<String> names) {
		JsonArray array = new JsonArray();
		names.forEach(array::add);
		return array;
	}
}
