package moe.ramon.cryostasis.hud;

import moe.ramon.cryostasis.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Renders every enabled HUD module each frame. Skips work entirely when the HUD is
 * hidden or the debug screen (F3) is open, matching vanilla overlay behavior and
 * avoiding overdraw on screens where the HUD is not wanted.
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
		List<HudModule> huds = modules.getHudModules();

		// Auto-stacked elements form a tidy top-left column: each is assigned the slot below
		// the previous one using its real drawn height, so they never overlap and every
		// element gets the same left margin no matter the GUI scale.
		int stackY = 2;
		for (int i = 0; i < huds.size(); i++) {
			HudModule hud = huds.get(i);
			if (!hud.isEnabled() || !hud.isAutoStacked()) {
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
			if (hud.isEnabled() && !hud.isAutoStacked()) {
				hud.render(context, tickDelta);
			}
		}
	}
}
