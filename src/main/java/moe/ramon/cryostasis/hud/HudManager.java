package moe.ramon.cryostasis.hud;

import moe.ramon.cryostasis.gui.HudEditorScreen;
import moe.ramon.cryostasis.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Renders every enabled HUD module each frame. Skips work entirely when the HUD is
 * hidden or the debug screen (F3) is open, matching vanilla overlay behavior and
 * avoiding overdraw on screens where the HUD is not wanted.
 *
 * The editor draws through {@link #renderElements} instead, since an element has to stay visible
 * there whatever the overlay would normally do with it.
 */
public final class HudManager {
	private final ModuleManager modules;

	public HudManager(ModuleManager modules) {
		this.modules = modules;
	}

	public void render(GuiGraphics context, float tickDelta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		// The overlay draws behind an open screen, and the editor draws the same elements over
		// its own backdrop. Letting both through would stack every translucent plate twice.
		if (mc.screen instanceof HudEditorScreen) {
			return;
		}
		renderElements(context, tickDelta);
	}

	public void renderElements(GuiGraphics context, float tickDelta) {
		List<HudModule> huds = modules.getHudModules();

		// Auto-stacked elements form a tidy top-left column: each is assigned the slot below
		// the previous one using its real drawn height, so they never overlap and every
		// element gets the same left margin no matter the GUI scale. An element the player has
		// dragged has left the column and places itself.
		int stackY = 2;
		for (int i = 0; i < huds.size(); i++) {
			HudModule hud = huds.get(i);
			if (!hud.isEnabled() || !hud.usesStack()) {
				continue;
			}
			hud.beginStack(2, stackY);
			hud.render(context, tickDelta);
			hud.endStack();
			stackY += hud.getLastHeight() + 2;
		}

		// Elements that own their own corner render at their anchor.
		for (int i = 0; i < huds.size(); i++) {
			HudModule hud = huds.get(i);
			if (hud.isEnabled() && !hud.usesStack()) {
				hud.render(context, tickDelta);
			}
		}
	}
}
