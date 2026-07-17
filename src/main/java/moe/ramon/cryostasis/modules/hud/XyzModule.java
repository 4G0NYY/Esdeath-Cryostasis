package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Player coordinates readout. */
public final class XyzModule extends HudModule {
	public XyzModule() {
		super("XYZ", "Shows your current coordinates.", 0.01, 0.09);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (mc.player == null) {
			return;
		}
		String x = "X " + (int) Math.floor(mc.player.getX());
		String y = "Y " + (int) Math.floor(mc.player.getY());
		String z = "Z " + (int) Math.floor(mc.player.getZ());
		HudText.drawLines(this, context, List.of(x, y, z), HudText.WHITE);
	}
}
