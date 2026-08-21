package moe.ramon.cryostasis.modules.player;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Stops the hunger bar from draining. The behavior lives in {@code PlayerMixin}, which drops the
 * exhaustion the game would otherwise bank against the local player; food and saturation only
 * ever fall once exhaustion fills up, so a player that never accrues any keeps what they have.
 * Nothing is added back, so this is not a food source: a bar already emptied stays empty until
 * something is eaten.
 *
 * Hunger is owned by whichever side runs the food data, and that is never the client. In
 * singleplayer the hook covers the integrated server too (it is guarded by UUID, the way
 * FastBreak is), so the bar really does hold. On a fair-play multiplayer server the server keeps
 * its own count and this changes nothing.
 */
public final class NoHungerModule extends Module {
	public NoHungerModule() {
		super("NoHunger", "Keep the hunger bar from draining.", Category.PLAYER);
	}
}
