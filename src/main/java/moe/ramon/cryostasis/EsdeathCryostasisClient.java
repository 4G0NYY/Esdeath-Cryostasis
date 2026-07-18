package moe.ramon.cryostasis;

import moe.ramon.cryostasis.module.ModuleManager;
import moe.ramon.cryostasis.modules.hud.ArrayListModule;
import moe.ramon.cryostasis.modules.hud.CpsModule;
import moe.ramon.cryostasis.modules.hud.FpsModule;
import moe.ramon.cryostasis.modules.hud.MlgHelperModule;
import moe.ramon.cryostasis.modules.hud.PingTagModule;
import moe.ramon.cryostasis.modules.hud.PlainsModule;
import moe.ramon.cryostasis.modules.hud.RainbowModule;
import moe.ramon.cryostasis.modules.hud.ReachDisplayModule;
import moe.ramon.cryostasis.modules.hud.XyzModule;
import moe.ramon.cryostasis.modules.combat.AutoDodgeModule;
import moe.ramon.cryostasis.modules.combat.KillauraModule;
import moe.ramon.cryostasis.modules.combat.MoreParticlesModule;
import moe.ramon.cryostasis.modules.combat.SharpnessModule;
import moe.ramon.cryostasis.modules.misc.AutoTextModule;
import moe.ramon.cryostasis.modules.misc.DiscordPresenceModule;
import moe.ramon.cryostasis.modules.misc.TabGuiModule;
import moe.ramon.cryostasis.modules.misc.TakeAllModule;
import moe.ramon.cryostasis.modules.movement.AutoPathModule;
import moe.ramon.cryostasis.modules.movement.NoCobwebModule;
import moe.ramon.cryostasis.modules.movement.NoSoulsandModule;
import moe.ramon.cryostasis.modules.movement.SafeWalkModule;
import moe.ramon.cryostasis.modules.movement.SpiderModule;
import moe.ramon.cryostasis.modules.movement.ZootModule;
import moe.ramon.cryostasis.modules.player.AutoEquipModule;
import moe.ramon.cryostasis.modules.player.AutoToolModule;
import moe.ramon.cryostasis.modules.player.FastBreakModule;
import moe.ramon.cryostasis.modules.player.ToggleSprintModule;
import moe.ramon.cryostasis.modules.render.BlockOutlineModule;
import moe.ramon.cryostasis.modules.render.CleanChatModule;
import moe.ramon.cryostasis.modules.render.HitboxModule;
import moe.ramon.cryostasis.modules.render.NightvisionModule;
import moe.ramon.cryostasis.modules.render.NoBlindModule;
import moe.ramon.cryostasis.modules.render.XrayModule;
import moe.ramon.cryostasis.modules.render.ZoomModule;
import moe.ramon.cryostasis.cosmetics.render.CosmeticLayer;
import moe.ramon.cryostasis.cosmetics.render.CosmeticModels;
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

		// Cosmetics: register model layers, then attach the cosmetic layer to the player
		// renderer so owned cosmetics draw on every visible player.
		CosmeticModels.registerLayers();
		registerCosmeticLayer();

		ClientTickEvents.END_CLIENT_TICK.register(client -> cryostasis.getModuleManager().onTick());

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
	private void registerCosmeticLayer() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityType == EntityType.PLAYER) {
				// The player renderer is a RenderLayerParent for the player state and model.
				helper.register(new CosmeticLayer(
						(RenderLayerParent<PlayerRenderState, PlayerModel>) (Object) entityRenderer, context));
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
		// Movement
		modules.register(new ToggleSprintModule());
		modules.register(new SafeWalkModule());
		modules.register(new AutoPathModule());
		modules.register(new ZootModule());
		modules.register(new SpiderModule());
		modules.register(new NoCobwebModule());
		modules.register(new NoSoulsandModule());
		// Render
		modules.register(new HitboxModule());
		modules.register(new BlockOutlineModule());
		modules.register(new ZoomModule());
		modules.register(new CleanChatModule());
		modules.register(new XrayModule());
		modules.register(new NoBlindModule());
		modules.register(new NightvisionModule());
		// Combat
		modules.register(new MoreParticlesModule());
		modules.register(new SharpnessModule());
		modules.register(new KillauraModule());
		modules.register(new AutoDodgeModule());
		// Player
		modules.register(new AutoToolModule());
		modules.register(new AutoEquipModule());
		modules.register(new FastBreakModule());
		// Misc
		modules.register(new AutoTextModule());
		modules.register(new TakeAllModule());
		modules.register(new TabGuiModule());
		modules.register(new DiscordPresenceModule());
	}
}
