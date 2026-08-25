package moe.ramon.cryostasis;

import moe.ramon.cryostasis.backend.ApiClient;
import moe.ramon.cryostasis.backend.ChatService;
import moe.ramon.cryostasis.backend.SessionService;
import moe.ramon.cryostasis.config.ConfigManager;
import moe.ramon.cryostasis.cosmetics.CosmeticService;
import moe.ramon.cryostasis.event.EventBus;
import moe.ramon.cryostasis.hud.HudManager;
import moe.ramon.cryostasis.input.InputHandler;
import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.service.ClickTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Process wide service holder. Everything the client needs hangs off one instance so
 * modules, HUD, and Mixins reach shared services without threading references through
 * constructors. Built once during client init; {@link #get()} is valid only after
 * {@link EsdeathCryostasisClient#onInitializeClient()} has run.
 */
public final class Cryostasis {
	public static final String MOD_ID = "esdeath-cryostasis";
	public static final String MOD_NAME = "Esdeath: Cryostasis";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	private static Cryostasis instance;

	private final EventBus eventBus = new EventBus();
	private final ClickTracker clickTracker = new ClickTracker();
	private final ModuleManager moduleManager = new ModuleManager();
	private final HudManager hudManager = new HudManager(moduleManager);
	private final ConfigManager configManager = new ConfigManager(moduleManager, LOGGER);
	private final InputHandler inputHandler = new InputHandler(moduleManager);

	// One connection to the backend, shared by everything that talks to it, so the bearer token
	// the session handshake buys is attached in exactly one place.
	private final ApiClient apiClient = new ApiClient();
	private final SessionService sessionService = new SessionService(apiClient);
	private final CosmeticService cosmeticService = new CosmeticService(apiClient, sessionService);
	private final ChatService chatService = new ChatService(apiClient, sessionService);

	Cryostasis() {
		instance = this;
	}

	public static Cryostasis get() {
		return instance;
	}

	public EventBus getEventBus() {
		return eventBus;
	}

	public ClickTracker getClickTracker() {
		return clickTracker;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	public HudManager getHudManager() {
		return hudManager;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}

	public InputHandler getInputHandler() {
		return inputHandler;
	}

	public ApiClient getApiClient() {
		return apiClient;
	}

	public SessionService getSessionService() {
		return sessionService;
	}

	public CosmeticService getCosmeticService() {
		return cosmeticService;
	}

	public ChatService getChatService() {
		return chatService;
	}
}
