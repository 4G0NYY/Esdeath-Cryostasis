package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.HudLine;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Shows the local player's connection latency to the current server. The original
 * displayed other players' ping over nametags; a self-ping HUD is the modern, no-Mixin
 * take that fits Group A. A nametag overlay can follow later as a render layer.
 *
 * The value is graded by colour, because the question a player glancing at it is asking is
 * whether the connection is fine, not what the exact number is.
 */
public final class PingTagModule extends HudModule {
	private static final int WARN_AT_MS = 100;
	private static final int BAD_AT_MS = 200;

	public PingTagModule() {
		super("PingTag", "Shows your ping to the current server.", 0.01, 0.13);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		ClientPacketListener connection = mc.getConnection();
		if (mc.player == null || connection == null) {
			setBounds(0, 0, 0, 0);
			return;
		}
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		int ping = info != null ? info.getLatency() : 0;
		HudText.draw(this, context, new HudLine("Ping", ping + " ms", grade(ping)));
	}

	private static int grade(int ping) {
		if (ping >= BAD_AT_MS) {
			return Theme.BAD;
		}
		return ping >= WARN_AT_MS ? Theme.WARN : Theme.GOOD;
	}
}
