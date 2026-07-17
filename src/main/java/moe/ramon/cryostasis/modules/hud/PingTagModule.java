package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Shows the local player's connection latency to the current server. The original
 * displayed other players' ping over nametags; a self-ping HUD is the modern, no-Mixin
 * take that fits Group A. A nametag overlay can follow later as a render layer.
 */
public final class PingTagModule extends HudModule {
	public PingTagModule() {
		super("PingTag", "Shows your ping to the current server.", 0.01, 0.13);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (mc.player == null) {
			return;
		}
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return;
		}
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		int ping = info != null ? info.getLatency() : 0;
		HudText.drawLine(this, context, "Ping " + ping + "ms", HudText.WHITE);
	}
}
