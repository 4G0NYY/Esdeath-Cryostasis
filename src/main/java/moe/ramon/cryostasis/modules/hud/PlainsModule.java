package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/** Shows the biome the player is standing in. Named after the original Plains module. */
public final class PlainsModule extends HudModule {
	public PlainsModule() {
		super("Plains", "Shows the biome you are standing in.", 0.01, 0.21);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		BlockPos pos = mc.player.blockPosition();
		String biome = mc.level.getBiome(pos)
				.unwrapKey()
				.map(key -> prettify(key.location().getPath()))
				.orElse("Unknown");
		HudText.drawLine(this, context, "Biome: " + biome, HudText.WHITE);
	}

	/** Turn a registry path such as "snowy_taiga" into "Snowy Taiga". */
	private static String prettify(String path) {
		String[] parts = path.split("_");
		StringBuilder sb = new StringBuilder(path.length());
		for (int i = 0; i < parts.length; i++) {
			if (parts[i].isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
		}
		return sb.toString();
	}
}
