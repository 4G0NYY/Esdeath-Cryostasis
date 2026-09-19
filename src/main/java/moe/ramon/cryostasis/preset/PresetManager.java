package moe.ramon.cryostasis.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.setting.KeybindSetting;
import moe.ramon.cryostasis.setting.Setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The player's presets and the built-in QoL one, and what applying each does to the modules.
 *
 * A user preset is a snapshot of which modules are on and how each is set. Keybinds (a module's
 * toggle key and any key setting) and HUD positions stay out of it and belong to the config alone,
 * so switching presets never moves the HUD or rebinds a key. Applying one switches off every
 * module it does not mention, which is what keeps a preset saved before a module existed from
 * leaving that module on.
 *
 * QoL is not a snapshot but a rule: switch off everything not marked {@link Module#isQol()} and
 * leave the rest alone, so it makes a setup server-safe without replacing the player's HUD.
 *
 * Names compare ignoring case, so "PvP" and "pvp" are one preset, and it keeps the spelling it was
 * first saved under. Every method runs on the render thread; the sync hops back onto it before
 * calling in.
 */
public final class PresetManager {
	public static final String QOL = "QoL";
	/** Both limits match the backend's, so a save refused here would have been refused there. */
	public static final int MAX_PRESETS = 32;
	public static final int MAX_NAME_LENGTH = 24;

	private final ModuleManager modules;
	private final PresetStore store;

	private TreeMap<String, Preset> presets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private final Set<String> unsynced = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
	private final Set<String> deleted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

	public PresetManager(ModuleManager modules, PresetStore store) {
		this.modules = modules;
		this.store = store;
	}

	public void load() {
		PresetStore.Contents contents = store.load();
		for (Preset preset : contents.presets()) {
			presets.put(preset.name(), preset);
		}
		unsynced.addAll(contents.unsynced());
		deleted.addAll(contents.deleted());
	}

	public Collection<Preset> presets() {
		return presets.values();
	}

	// Applying.

	/** Switch off every module that is not QoL. Returns how many were switched off. */
	public int applyQol() {
		int switchedOff = 0;
		for (Module module : modules.getModules()) {
			if (module.isEnabled() && !module.isQol()) {
				module.setEnabled(false);
				switchedOff++;
			}
		}
		return switchedOff;
	}

	/** Whether nothing unsafe is on right now, which is what the menu marks the QoL row by. */
	public boolean isQolActive() {
		for (Module module : modules.getModules()) {
			if (module.isEnabled() && !module.isQol()) {
				return false;
			}
		}
		return true;
	}

	public void apply(Preset preset) {
		for (Module module : modules.getModules()) {
			JsonElement state = preset.modules().get(key(module));
			if (state == null || !state.isJsonObject()) {
				module.setEnabled(false);
				continue;
			}
			JsonObject object = state.getAsJsonObject();
			if (object.has("settings") && object.get("settings").isJsonObject()) {
				JsonObject settings = object.getAsJsonObject("settings");
				for (Setting<?> setting : module.getSettings()) {
					if (!(setting instanceof KeybindSetting) && settings.has(setting.getName())) {
						setting.read(settings.get(setting.getName()));
					}
				}
			}
			// Last, so onEnable runs with the preset's settings already in place.
			module.setEnabled(preset.enables(key(module)));
		}
	}

	/**
	 * Whether the modules that are on right now are exactly the ones the preset turns on. Settings
	 * are not compared: a player who nudges a slider after applying a preset is still using it.
	 */
	public boolean matches(Preset preset) {
		for (Module module : modules.getModules()) {
			if (module.isEnabled() != preset.enables(key(module))) {
				return false;
			}
		}
		return true;
	}

	// Editing.

	/**
	 * Save the current setup under this name, replacing any preset already called that.
	 *
	 * @return the name it was stored under, which keeps an existing preset's spelling
	 * @throws IllegalArgumentException with a message fit to show the player
	 */
	public String save(String rawName) {
		String name = validateName(rawName);
		Preset existing = presets.get(name);
		if (existing != null) {
			name = existing.name();
		} else if (presets.size() >= MAX_PRESETS) {
			throw new IllegalArgumentException("At most " + MAX_PRESETS + " presets");
		}
		presets.put(name, new Preset(name, capture()));
		unsynced.add(name);
		deleted.remove(name);
		persist();
		return name;
	}

	public void delete(String name) {
		if (presets.remove(name) == null) {
			return;
		}
		unsynced.remove(name);
		deleted.add(name);
		persist();
	}

	/** The same rules the backend applies, checked here so the player hears why at once. */
	static String validateName(String raw) {
		String name = raw.strip();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("Type a name first");
		}
		if (name.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Name is too long");
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != ' ' && c != '-' && c != '_') {
				throw new IllegalArgumentException("Letters, digits, space, - and _ only");
			}
		}
		if (name.equalsIgnoreCase(QOL)) {
			throw new IllegalArgumentException(QOL + " is the built-in preset");
		}
		return name;
	}

	private JsonObject capture() {
		JsonObject snapshot = new JsonObject();
		for (Module module : modules.getModules()) {
			JsonObject settings = new JsonObject();
			for (Setting<?> setting : module.getSettings()) {
				if (!(setting instanceof KeybindSetting)) {
					settings.add(setting.getName(), setting.write());
				}
			}
			JsonObject state = new JsonObject();
			state.addProperty("enabled", module.isEnabled());
			state.add("settings", settings);
			snapshot.add(key(module), state);
		}
		return snapshot;
	}

	private static String key(Module module) {
		return module.getName().toLowerCase(Locale.ROOT);
	}

	private void persist() {
		store.save(presets.values(), unsynced, deleted);
	}

	// Sync. Called by PresetService, always on the render thread.

	public boolean hasUnsynced() {
		return !unsynced.isEmpty() || !deleted.isEmpty();
	}

	public boolean isUnsynced(Preset preset) {
		return unsynced.contains(preset.name());
	}

	public List<Preset> unsyncedPresets() {
		List<Preset> pending = new ArrayList<>();
		for (String name : unsynced) {
			Preset preset = presets.get(name);
			if (preset != null) {
				pending.add(preset);
			}
		}
		return pending;
	}

	public List<String> deletedNames() {
		return new ArrayList<>(deleted);
	}

	/** The backend has this version. Still unsynced if the player changed it again meanwhile. */
	public void confirmSaved(Preset sent) {
		if (sent.equals(presets.get(sent.name())) && unsynced.remove(sent.name())) {
			persist();
		}
	}

	/** The backend has forgotten this one. Kept owed if the player saved it again meanwhile. */
	public void confirmDeleted(String name) {
		if (!presets.containsKey(name) && deleted.remove(name)) {
			persist();
		}
	}

	/**
	 * Take the backend's list as the truth, apart from whatever it has not heard about yet. That is
	 * what lets a preset deleted on another machine disappear here too, rather than being uploaded
	 * again because this machine still had a copy.
	 */
	public void replaceWith(List<Preset> remote) {
		TreeMap<String, Preset> next = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Preset preset : remote) {
			next.put(preset.name(), preset);
		}
		for (String name : deleted) {
			next.remove(name);
		}
		for (String name : unsynced) {
			Preset local = presets.get(name);
			if (local != null) {
				next.put(local.name(), local);
			}
		}
		presets = next;
		persist();
	}
}
