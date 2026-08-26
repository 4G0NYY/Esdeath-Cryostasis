package moe.ramon.cryostasis;

import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.modules.hud.ArrayListModule;
import moe.ramon.cryostasis.modules.hud.CpsModule;
import moe.ramon.cryostasis.modules.hud.FpsModule;
import moe.ramon.cryostasis.modules.hud.MlgHelperModule;
import moe.ramon.cryostasis.modules.hud.OnlineListModule;
import moe.ramon.cryostasis.modules.hud.PingTagModule;
import moe.ramon.cryostasis.modules.hud.PlainsModule;
import moe.ramon.cryostasis.modules.hud.RainbowModule;
import moe.ramon.cryostasis.modules.hud.ReachDisplayModule;
import moe.ramon.cryostasis.modules.hud.XyzModule;
import moe.ramon.cryostasis.modules.combat.AutoDodgeModule;
import moe.ramon.cryostasis.modules.combat.KillauraModule;
import moe.ramon.cryostasis.modules.combat.MoreParticlesModule;
import moe.ramon.cryostasis.modules.combat.ReachModule;
import moe.ramon.cryostasis.modules.combat.SharpnessModule;
import moe.ramon.cryostasis.modules.misc.AutoTextModule;
import moe.ramon.cryostasis.modules.misc.DiscordPresenceModule;
import moe.ramon.cryostasis.modules.misc.GlobalChatModule;
import moe.ramon.cryostasis.modules.misc.TabGuiModule;
import moe.ramon.cryostasis.modules.misc.TakeAllModule;
import moe.ramon.cryostasis.modules.movement.AutoPathModule;
import moe.ramon.cryostasis.modules.movement.FlyModule;
import moe.ramon.cryostasis.modules.movement.JesusModule;
import moe.ramon.cryostasis.modules.movement.NoCobwebModule;
import moe.ramon.cryostasis.modules.movement.NoSoulsandModule;
import moe.ramon.cryostasis.modules.movement.SafeWalkModule;
import moe.ramon.cryostasis.modules.movement.SpiderModule;
import moe.ramon.cryostasis.modules.movement.ZootModule;
import moe.ramon.cryostasis.modules.player.AutoEquipModule;
import moe.ramon.cryostasis.modules.player.AutoToolModule;
import moe.ramon.cryostasis.modules.player.AutoTotemModule;
import moe.ramon.cryostasis.modules.player.FastBreakModule;
import moe.ramon.cryostasis.modules.player.NoHungerModule;
import moe.ramon.cryostasis.modules.player.ToggleSprintModule;
import moe.ramon.cryostasis.modules.render.BlockOutlineModule;
import moe.ramon.cryostasis.modules.render.CleanChatModule;
import moe.ramon.cryostasis.modules.render.FreecamModule;
import moe.ramon.cryostasis.modules.render.FreelookModule;
import moe.ramon.cryostasis.modules.render.HitboxModule;
import moe.ramon.cryostasis.modules.render.NightvisionModule;
import moe.ramon.cryostasis.modules.render.NoBlindModule;
import moe.ramon.cryostasis.modules.render.StatusTagModule;
import moe.ramon.cryostasis.modules.render.XrayModule;
import moe.ramon.cryostasis.modules.render.ZoomModule;
import moe.ramon.cryostasis.cosmetics.CosmeticCatalogue;
import moe.ramon.cryostasis.cosmetics.render.CosmeticLayer;
import moe.ramon.cryostasis.cosmetics.render.CosmeticModels;
import moe.ramon.cryostasis.render.StatusTagLayer;
import moe.ramon.cryostasis.render.WorldRenderHooks;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.EntityType;

/**
 * Client entry point. Builds the service holder, registers modules, restores config, and
 * binds the module system to Fabric's tick and HUD render events. All shared services
 * live on the single {@link Cryostasis} instance so nothing here needs static wiring
 * beyond constructing it.
 */
public final class EsdeathCryostasisClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Cryostasis cryostasis = new Cryostasis();
		registerModules(cryostasis.getModuleManager());

		// Restore saved state after modules exist so their settings and toggles apply.
		cryostasis.getConfigManager().load();

		// World-render modules draw through Fabric's render events, not their own Mixins.
		WorldRenderHooks.register(cryostasis.getModuleManager());

		// Cosmetics: register model layers, then attach the player render layers so owned
		// cosmetics and the presence status tag draw on every visible player. Both go on here
		// because a render layer is attached once at startup and cannot be added later.
		CosmeticModels.registerLayers();
		registerPlayerLayers();
		// The catalogue and its CDN textures come from the backend, so it needs the connection.
		CosmeticCatalogue.bind(cryostasis.getApiClient());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Ahead of the modules, because the backend enforces auth: a module that writes on
			// this tick wants the token already in hand.
			cryostasis.getSessionService().tick();
			// Presence is not gated on a module: whether this player shows as online is a
			// property of the client running, not of any one feature being switched on.
			cryostasis.getPresenceService().tick();
			cryostasis.getModuleManager().onTick();
		});

		HudRenderCallback.EVENT.register((drawContext, tickCounter) ->
				cryostasis.getHudManager().render(drawContext, tickCounter.getGameTimeDeltaPartialTick(false)));

		// The arrow-key TabGui draws its own overlay (it is not a stacked HUD element) and only
		// while no screen is open and the HUD is visible, matching the rest of the overlay.
		TabGuiModule tabGui = cryostasis.getModuleManager().get(TabGuiModule.class);
		Minecraft minecraft = Minecraft.getInstance();
		HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
			if (tabGui.isEnabled() && minecraft.screen == null && !minecraft.options.hideGui) {
				tabGui.render(drawContext);
			}
		});

		// Persist on shutdown as a backstop; the GUI also saves when it closes.
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> cryostasis.getConfigManager().save());

		Cryostasis.LOGGER.info("{} initialized with {} modules", Cryostasis.MOD_NAME,
				cryostasis.getModuleManager().getModules().size());
	}

	@SuppressWarnings("unchecked")
	private void registerPlayerLayers() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityType == EntityType.PLAYER) {
				// The player renderer is a RenderLayerParent for the player state and model.
				RenderLayerParent<PlayerRenderState, PlayerModel> parent =
						(RenderLayerParent<PlayerRenderState, PlayerModel>) (Object) entityRenderer;
				helper.register(new CosmeticLayer(parent, context));
				// After the cosmetics, so the tag is not hidden behind anything worn on the head.
				helper.register(new StatusTagLayer(parent));
			}
		});
	}

	private void registerModules(ModuleManager modules) {
		// HUD
		modules.register(new FpsModule());
		modules.register(new CpsModule());
		modules.register(new XyzModule());
		modules.register(new ReachDisplayModule());
		modules.register(new PingTagModule());
		modules.register(new PlainsModule());
		modules.register(new MlgHelperModule());
		modules.register(new ArrayListModule());
		modules.register(new RainbowModule());
		modules.register(new OnlineListModule());
		// Movement
		modules.register(new ToggleSprintModule());
		modules.register(new SafeWalkModule());
		modules.register(new AutoPathModule());
		modules.register(new ZootModule());
		modules.register(new SpiderModule());
		modules.register(new JesusModule());
		modules.register(new NoCobwebModule());
		modules.register(new NoSoulsandModule());
		modules.register(new FlyModule());
		// Render
		modules.register(new HitboxModule());
		modules.register(new BlockOutlineModule());
		modules.register(new ZoomModule());
		modules.register(new CleanChatModule());
		modules.register(new XrayModule());
		modules.register(new NoBlindModule());
		modules.register(new NightvisionModule());
		modules.register(new FreecamModule());
		modules.register(new FreelookModule());
		modules.register(new StatusTagModule());
		// Combat
		modules.register(new MoreParticlesModule());
		modules.register(new SharpnessModule());
		modules.register(new KillauraModule());
		modules.register(new AutoDodgeModule());
		modules.register(new ReachModule());
		// Player
		modules.register(new AutoToolModule());
		modules.register(new AutoEquipModule());
		modules.register(new AutoTotemModule());
		modules.register(new FastBreakModule());
		modules.register(new NoHungerModule());
		// Misc
		modules.register(new AutoTextModule());
		modules.register(new TakeAllModule());
		modules.register(new TabGuiModule());
		modules.register(new DiscordPresenceModule());
		modules.register(new GlobalChatModule());
	}
}
