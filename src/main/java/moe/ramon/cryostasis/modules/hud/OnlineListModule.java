package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.backend.PresenceService;
import moe.ramon.cryostasis.gui.Skin;
import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.NumberSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Who else is running Cryostasis right now, whichever server they are on.
 *
 * The roster comes from the backend's presence endpoint, which derives each state from that
 * client's heartbeat rather than storing it, so a player who quits drops off on their own and one
 * who has stopped touching anything is shown away rather than gone. A dot carries the state and
 * the name carries the rank colour, which is the same palette the backend stamps onto chat lines.
 *
 * Nothing here fetches: {@link PresenceService} polls off-thread and this reads its last answer,
 * so a slow backend costs a stale list rather than a stutter.
 */
public final class OnlineListModule extends HudModule {
	private static final int DOT = 4;
	private static final int GAP = 4;
	private static final int PAD = 5;
	private static final int ROW_HEIGHT = 11;
	private static final int HEADER_HEIGHT = 13;

	private final NumberSetting maxRows = register(new NumberSetting("Max Rows", 8, 1, 20, 1));
	private final BooleanSetting showAway = register(new BooleanSetting("Show Away", true));
	private final BooleanSetting showStatus = register(new BooleanSetting("Show Status", true));
	private final BooleanSetting showHeader = register(new BooleanSetting("Header", true));

	private final List<PresenceService.Entry> shown = new ArrayList<>();

	public OnlineListModule() {
		// Top right by default, under where the ArrayList lays itself out.
		super("OnlineList", "Lists everyone currently on the client.", 1.0, 0.35);
	}

	@Override
	public boolean isAutoStacked() {
		// It sizes itself from its content and wants its own corner, like the ArrayList.
		return false;
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		Font font = mc.font;
		PresenceService presence = Cryostasis.get().getPresenceService();

		shown.clear();
		for (PresenceService.Entry entry : presence.roster()) {
			if (entry.username().isBlank()) {
				// A player who has never completed the session handshake has no name the backend
				// trusts, and a row reading "unknown" is worse than no row.
				continue;
			}
			if (!showAway.get() && entry.isAfk()) {
				continue;
			}
			if (shown.size() >= maxRows.getInt()) {
				break;
			}
			shown.add(entry);
		}
		boolean header = showHeader.get();
		if (shown.isEmpty() && !header) {
			setBounds(context.guiWidth(), 0, 0, 0);
			return;
		}

		String title = "Online";
		String tally = presence.onlineCount() + " · " + presence.awayCount() + " away";
		int widest = header ? font.width(title) + GAP * 2 + font.width(tally) : 0;
		for (PresenceService.Entry entry : shown) {
			widest = Math.max(widest, DOT + GAP + rowWidth(font, entry));
		}

		int width = widest + PAD * 2;
		int headerHeight = header ? HEADER_HEIGHT : 0;
		int height = headerHeight + shown.size() * ROW_HEIGHT + 2;
		int x = resolveX(context.guiWidth(), width);
		int y = resolveY(context.guiHeight(), height);

		Skin.shadow(context, x, y, width, height);
		Skin.panel(context, x, y, width, height, Theme.PANEL);
		if (header) {
			context.fill(x + 1, y, x + width - 1, y + HEADER_HEIGHT, Theme.HEADER);
			context.fill(x, y + HEADER_HEIGHT - 1, x + width, y + HEADER_HEIGHT, HudColors.accent(0.0f));
			context.drawString(font, title, x + PAD, y + 3, Theme.TEXT, false);
			context.drawString(font, tally, x + width - PAD - font.width(tally), y + 3, Theme.SUBTEXT, false);
		}

		int rowY = y + headerHeight + 1;
		for (PresenceService.Entry entry : shown) {
			int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
			int dotY = rowY + (ROW_HEIGHT - DOT) / 2;
			context.fill(x + PAD, dotY, x + PAD + DOT, dotY + DOT, entry.isOnline() ? Theme.GOOD : Theme.WARN);
			int nameX = x + PAD + DOT + GAP;
			context.drawString(font, entry.username(), nameX, textY, entry.color(), false);
			if (showStatus.get()) {
				int statusX = nameX + font.width(entry.username()) + GAP;
				context.drawString(font, entry.label(), statusX, textY, Theme.SUBTEXT, false);
			}
			rowY += ROW_HEIGHT;
		}

		setBounds(x, y, width, height);
	}

	private int rowWidth(Font font, PresenceService.Entry entry) {
		int name = font.width(entry.username());
		return showStatus.get() ? name + GAP + font.width(entry.label()) : name;
	}

	@Override
	public String getHudSuffix() {
		PresenceService presence = Cryostasis.get().getPresenceService();
		return Integer.toString(presence.onlineCount() + presence.awayCount());
	}
}
