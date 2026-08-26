package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;

/**
 * Shows a Cryostasis player's rank, away state, and status line under their name tag.
 *
 * The module is only the gate and the settings; the drawing lives in
 * {@code render.StatusTagLayer}, which is attached to the player renderer once at startup the way
 * the cosmetic layer is. A render layer cannot be added and removed at runtime, so the layer asks
 * this module whether it should draw rather than the other way round.
 *
 * It is a Render module rather than a HUD one because it draws in the world, over other players,
 * and has no anchor of its own to drag: it sits wherever the player it describes is.
 */
public final class StatusTagModule extends Module {
	private final BooleanSetting rank = register(new BooleanSetting("Rank", true));
	private final BooleanSetting away = register(new BooleanSetting("Away", true));
	private final BooleanSetting status = register(new BooleanSetting("Status", true));

	public StatusTagModule() {
		super("StatusTag", "Shows rank and status under Cryostasis players.", Category.RENDER);
	}

	public boolean showRank() {
		return rank.get();
	}

	public boolean showAway() {
		return away.get();
	}

	public boolean showStatus() {
		return status.get();
	}
}
