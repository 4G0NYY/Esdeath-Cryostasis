package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;

/**
 * Suppresses consecutive duplicate chat lines, so a spammed message lands once instead of
 * scrolling the rest of the chat off screen. The original client's exact behavior did not
 * survive decompilation (the spec records only "cleans or deduplicates chat"), so this takes
 * the deduplicate reading, which is the common and useful one. The filtering lives in
 * {@code ChatComponentMixin}; this module is the gate it reads.
 */
public final class CleanChatModule extends Module {
	public CleanChatModule() {
		super("CleanChat", "Hides consecutive duplicate chat messages.", Category.RENDER);
	}
}
