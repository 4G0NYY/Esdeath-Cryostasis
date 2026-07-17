package moe.ramon.cryostasis.module;

import moe.ramon.cryostasis.hud.HudModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry and dispatcher for modules. Holds the single source of truth for
 * which modules exist. Tick and render dispatch iterate a plain array-backed list and
 * call the module hooks directly, so there is no reflection or map lookup on the hot
 * path.
 *
 * Not thread safe: all registration happens once at client init, and all dispatch
 * happens on the render thread.
 */
public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<HudModule> hudModules = new ArrayList<>();
	private final Map<String, Module> byName = new LinkedHashMap<>();

	public void register(Module module) {
		String key = module.getName().toLowerCase();
		if (byName.containsKey(key)) {
			throw new IllegalStateException("Duplicate module name: " + module.getName());
		}
		modules.add(module);
		byName.put(key, module);
		if (module instanceof HudModule hud) {
			hudModules.add(hud);
		}
	}

	/** Dispatch a client tick to every enabled module. */
	public void onTick() {
		// Indexed loop avoids allocating an iterator every tick.
		for (int i = 0; i < modules.size(); i++) {
			Module module = modules.get(i);
			if (module.isEnabled()) {
				module.onTick();
			}
		}
	}

	public Module get(String name) {
		return byName.get(name.toLowerCase());
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		for (int i = 0; i < modules.size(); i++) {
			if (type.isInstance(modules.get(i))) {
				return (T) modules.get(i);
			}
		}
		return null;
	}

	public List<Module> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public List<HudModule> getHudModules() {
		return Collections.unmodifiableList(hudModules);
	}

	public List<Module> getByCategory(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module module : modules) {
			if (module.getCategory() == category) {
				result.add(module);
			}
		}
		return result;
	}
}
