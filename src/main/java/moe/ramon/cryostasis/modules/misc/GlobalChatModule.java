package moe.ramon.cryostasis.modules.misc;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.NumberSetting;
import moe.ramon.cryostasis.setting.StringSetting;

/**
 * Cryostasis-wide chat: one channel every client shares, whichever game server each player is
 * actually on. The original client had a relay of its own on a second socket port; this is that
 * idea rebuilt on the REST backend, with the authentication and moderation the original had none
 * of.
 *
 * Messages arrive in the ordinary chat overlay, tagged and coloured by the sender's rank, so
 * there is nothing extra to keep open. To send, start a normal chat line with the prefix: the
 * line is intercepted before it reaches the game server and posted to the channel instead, so a
 * message meant for Cryostasis never leaks into the chat of whatever server you are on.
 *
 * The module is the gate: reading only happens while it is enabled, and {@code ChatScreenMixin}
 * checks it before claiming a prefixed line.
 */
public final class GlobalChatModule extends Module {
	private final StringSetting prefix = register(new StringSetting("Prefix", "@"));
	private final NumberSetting backlog = register(new NumberSetting("Backlog", 5, 0, 25, 1));

	public GlobalChatModule() {
		super("GlobalChat", "Cryostasis-wide chat, sent by prefixing a message.", Category.MISC);
	}

	@Override
	public void onEnable() {
		Cryostasis.get().getChatService().start((int) backlog.get().doubleValue());
	}

	@Override
	public void onDisable() {
		Cryostasis.get().getChatService().stop();
	}

	@Override
	public void onTick() {
		// The service does its own pacing; this is only the client-thread heartbeat it needs to
		// deliver what arrived and to start the next read.
		Cryostasis.get().getChatService().tick();
	}

	/** The prefix that claims a chat line for this channel, never empty. */
	public String prefix() {
		String value = prefix.get().trim();
		return value.isEmpty() ? "@" : value;
	}

	@Override
	public String getHudLabel() {
		return getName() + " " + prefix();
	}
}
