package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;

/**
 * Marks Cryostasis players on the name tags floating over their heads: the client emblem and their
 * rank in front of the name, their away state and status line underneath it.
 *
 * The module is only the gate and the settings; the text is written into the render state by
 * {@code render.NameTagDecorator}, reached from the player renderer Mixin. Nothing is drawn here
 * and nothing is drawn there either, which is what keeps the lines placed and turned the way every
 * other name tag is.
 *
 * It is a Render module rather than a HUD one because it draws in the world, over other players,
 * and has no anchor of its own to drag: it sits wherever the player it describes is.
 */
public final class StatusTagModule extends Module {
	private final BooleanSetting emblem = register(new BooleanSetting("Emblem", true));
	private final BooleanSetting rank = register(new BooleanSetting("Rank", true));
	private final BooleanSetting away = register(new BooleanSetting("Away", true));
	private final BooleanSetting status = register(new BooleanSetting("Status", true));

	public StatusTagModule() {
		super("StatusTag", "Shows the Cryostasis emblem, rank, and status on player name tags.",
				Category.RENDER);
		markQol();
	}

	public boolean showEmblem() {
		return emblem.get();
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
