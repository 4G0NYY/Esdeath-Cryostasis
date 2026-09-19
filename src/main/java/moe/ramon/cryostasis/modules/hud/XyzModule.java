package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudLine;
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
			setBounds(0, 0, 0, 0);
			return;
		}
		HudText.draw(this, context, List.of(
				HudLine.of("X", Integer.toString(mc.player.getBlockX())),
				HudLine.of("Y", Integer.toString(mc.player.getBlockY())),
				HudLine.of("Z", Integer.toString(mc.player.getBlockZ()))));
	}
}
