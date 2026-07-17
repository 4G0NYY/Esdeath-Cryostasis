package moe.ramon.cryostasis.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves all module state to a single versioned JSON file. Versioning lets
 * old configs migrate forward: {@link #migrate} runs before values are applied, so a
 * bump to {@link #CURRENT_VERSION} can rewrite the tree in place.
 *
 * The on disk shape is deliberately flat and human editable:
 * <pre>
 * { "version": 1, "modules": { "cps": { "enabled": true, "key": 67, "settings": { ... } } } }
 * </pre>
 */
public final class ConfigManager {
	// Bump when the persisted shape changes, and add a case to migrate().
	public static final int CURRENT_VERSION = 1;

	private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private final ModuleManager modules;
	private final Logger logger;
	private final Path file;

	public ConfigManager(ModuleManager modules, Logger logger) {
		this.modules = modules;
		this.logger = logger;
		this.file = FabricLoader.getInstance().getConfigDir().resolve("esdeath-cryostasis.json");
	}

	public void load() {
		if (!Files.exists(file)) {
			logger.info("No config found at {}, starting from defaults", file);
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file));
			if (!root.isJsonObject()) {
				logger.warn("Config root is not an object, ignoring");
				return;
			}
			JsonObject obj = root.getAsJsonObject();
			int version = obj.has("version") ? obj.get("version").getAsInt() : 0;
			if (version != CURRENT_VERSION) {
				logger.info("Migrating config from version {} to {}", version, CURRENT_VERSION);
				obj = migrate(obj, version);
			}
			apply(obj);
			logger.info("Loaded config from {}", file);
		} catch (Exception e) {
			logger.error("Failed to load config, keeping defaults", e);
		}
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CURRENT_VERSION);

		JsonObject modulesObj = new JsonObject();
		for (Module module : modules.getModules()) {
			modulesObj.add(module.getName().toLowerCase(), writeModule(module));
		}
		root.add("modules", modulesObj);

		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, gson.toJson(root));
		} catch (IOException e) {
			logger.error("Failed to save config to {}", file, e);
		}
	}

	private JsonObject writeModule(Module module) {
		JsonObject obj = new JsonObject();
		obj.addProperty("enabled", module.isEnabled());
		obj.addProperty("key", module.getKeyCode());
		if (module instanceof HudModule hud) {
			obj.addProperty("anchorX", hud.getAnchorX());
			obj.addProperty("anchorY", hud.getAnchorY());
		}
		JsonObject settings = new JsonObject();
		for (Setting<?> setting : module.getSettings()) {
			settings.add(setting.getName(), setting.write());
		}
		obj.add("settings", settings);
		return obj;
	}

	private void apply(JsonObject root) {
		if (!root.has("modules") || !root.get("modules").isJsonObject()) {
			return;
		}
		JsonObject modulesObj = root.getAsJsonObject("modules");
		for (Module module : modules.getModules()) {
			JsonElement entry = modulesObj.get(module.getName().toLowerCase());
			if (entry == null || !entry.isJsonObject()) {
				continue;
			}
			JsonObject obj = entry.getAsJsonObject();
			if (obj.has("key")) {
				module.setKeyCode(obj.get("key").getAsInt());
			}
			if (module instanceof HudModule hud && obj.has("anchorX") && obj.has("anchorY")) {
				hud.setAnchor(obj.get("anchorX").getAsDouble(), obj.get("anchorY").getAsDouble());
			}
			if (obj.has("settings") && obj.get("settings").isJsonObject()) {
				JsonObject settings = obj.getAsJsonObject("settings");
				for (Setting<?> setting : module.getSettings()) {
					setting.read(settings.get(setting.getName()));
				}
			}
			// Apply enabled last so onEnable runs after settings are restored. Apply the stored
			// value both ways so a module that ships enabled by default can still be turned off
			// persistently (setEnabled is a no-op when the state already matches).
			if (obj.has("enabled")) {
				module.setEnabled(obj.get("enabled").getAsBoolean());
			}
		}
	}

	/**
	 * Rewrite an older config tree into the current shape. Version 0 is any config
	 * written before versioning existed; it is already shape compatible, so it just
	 * gets stamped. Add further cases as the schema evolves.
	 */
	private JsonObject migrate(JsonObject root, int fromVersion) {
		// No structural changes yet. Placeholder so the pathway is exercised and future
		// migrations have an obvious home.
		root.addProperty("version", CURRENT_VERSION);
		return root;
	}
}
