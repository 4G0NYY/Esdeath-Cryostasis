package moe.ramon.cryostasis.gui;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.backend.PresenceService;
import moe.ramon.cryostasis.backend.SessionService;
import moe.ramon.cryostasis.cosmetics.CosmeticCatalogue;
import moe.ramon.cryostasis.cosmetics.CosmeticService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/**
 * In-game cosmetics and presence menu: previews the local player with their active cosmetics,
 * lets them toggle each one, lets them write the status line everyone else sees, and lists who
 * else is on the client right now.
 *
 * The cosmetic rows come from the backend catalogue rather than a compiled-in list, so a cosmetic
 * added there appears here without a new mod jar. One that this client has no model for yet is
 * listed greyed out and cannot be toggled, since wearing it would show nothing.
 *
 * The preview reuses the same {@code CosmeticLayer} the world renderer uses, so what shows here is
 * exactly what other players see. The menu therefore needs an in-world player to preview and is
 * opened from within a world.
 *
 * Presence sits beside the cosmetics rather than in a menu of its own because the two are the same
 * question asked twice: this is the screen for how this player appears to everyone else, and a
 * status line is as much a part of that as a hat is.
 */
public final class CosmeticsScreen extends Screen {
	/**
	 * The panel's preferred width. It shrinks to fit rather than overflowing: the game's UI is
	 * never narrower than 320 units, but it is often exactly that, since auto GUI scale picks the
	 * largest scale that still leaves 320 by 240. A fixed 400 would hang off the side of the
	 * screen for anyone at a high scale.
	 */
	private static final int PANEL_WIDTH_MAX = 400;
	/** Below this there is no room for the preview, and the two lists get the space instead. */
	private static final int PREVIEW_THRESHOLD = 360;
	private static final int PANEL_HEIGHT = 200;
	private static final int ROW_HEIGHT = 16;
	private static final int PREVIEW_WIDTH = 110;
	private static final int GUTTER = 12;
	private static final int MARGIN = 6;
	private static final int HEADER_HEIGHT = 15;
	private static final int STATUS_HEIGHT = 14;
	/** The backend caps a status at this, so the field refuses the rest rather than losing it. */
	private static final int STATUS_MAX = 64;

	private static final int COLOR_ONLINE = 0xFF4CC77A;
	private static final int COLOR_AWAY = 0xFFE8B14C;

	private EditBox status;
	// What the field held when it was last sent, so closing the screen does not repost an
	// unchanged line on every visit.
	private String sentStatus = "";

	public CosmeticsScreen() {
		super(Component.literal("Cosmetics"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		// A resize rebuilds every widget, so the field's value is carried over rather than
		// re-read: a player who has typed a status and then resized the window should not have
		// to type it again.
		String current = status != null
				? status.getValue()
				: Cryostasis.get().getPresenceService().self().status();

		status = new EditBox(font, presenceX() + 1, contentY() + 18, presenceWidth() - 2,
				STATUS_HEIGHT, Component.literal("Status"));
		status.setMaxLength(STATUS_MAX);
		status.setHint(Component.literal("say something"));
		status.setValue(current);
		sentStatus = current;
		// Enter commits without closing the screen, which is what a player expects from a field
		// that sits beside a list they are still clicking through.
		addRenderableWidget(status);
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// Flat dim instead of renderBackground: this non-pausing screen sits over a frame that
		// already requested the one allowed blur, matching ClickGuiScreen.
		context.fill(0, 0, width, height, Theme.DIM);

		int panelX = panelX();
		int panelY = panelY();
		Skin.plate(context, panelX, panelY, panelWidth(), PANEL_HEIGHT, Theme.PANEL_SOLID, Theme.ACCENT_DIM);

		// Header with the accent seam the rest of the client uses.
		context.fill(panelX, panelY, panelX + panelWidth(), panelY + HEADER_HEIGHT, Theme.HEADER);
		context.fill(panelX, panelY + HEADER_HEIGHT - 1, panelX + panelWidth(), panelY + HEADER_HEIGHT, Theme.ACCENT);
		context.drawString(font, "Cosmetics", panelX + 6, panelY + 4, Theme.TEXT);
		renderRank(context, panelX, panelY);

		renderPreview(context, mouseX, mouseY);
		renderRows(context, mouseX, mouseY);
		renderPresence(context);

		super.render(context, mouseX, mouseY, delta);
	}

	/** The player's own rank, right-aligned in the header and coloured by the backend's palette. */
	private void renderRank(GuiGraphics context, int panelX, int panelY) {
		SessionService session = Cryostasis.get().getSessionService();
		String label = session.isAuthenticated() ? session.rank() : "Connecting";
		int color = session.isAuthenticated() ? session.rankColor() : Theme.SUBTEXT;
		context.drawString(font, label, panelX + panelWidth() - font.width(label) - 6, panelY + 4, color);
	}

	private void renderPreview(GuiGraphics context, int mouseX, int mouseY) {
		if (!hasPreview()) {
			return;
		}
		int px0 = panelX() + MARGIN;
		int py0 = contentY() + 6;
		int px1 = px0 + PREVIEW_WIDTH;
		int py1 = panelY() + PANEL_HEIGHT - MARGIN;
		Skin.plate(context, px0, py0, PREVIEW_WIDTH, py1 - py0, Theme.CELL, Theme.CELL_BORDER);

		LocalPlayer player = minecraft != null ? minecraft.player : null;
		if (player == null) {
			context.drawCenteredString(font, "No player", (px0 + px1) / 2, (py0 + py1) / 2, Theme.SUBTEXT);
			return;
		}
		// The model sits inside the cell and faces the cursor as it moves.
		int scale = 55;
		InventoryScreen.renderEntityInInventoryFollowsMouse(context, px0, py0, px1, py1, scale, 0.0625f,
				(float) mouseX, (float) mouseY, player);
	}

	private void renderRows(GuiGraphics context, int mouseX, int mouseY) {
		int listX = listX();
		int listRight = listX + listWidth();
		int rowY = contentY() + 8;

		UUID uuid = localUuid();
		CosmeticService service = Cryostasis.get().getCosmeticService();
		CosmeticService.Active active = uuid != null ? service.get(uuid) : CosmeticService.Active.EMPTY;

		List<CosmeticCatalogue.Entry> entries = CosmeticCatalogue.entries();
		for (CosmeticCatalogue.Entry entry : entries) {
			if (rowY + ROW_HEIGHT > panelY() + PANEL_HEIGHT - 18) {
				// The catalogue can outgrow the panel; the rest is reachable once the list
				// scrolls, which is not built yet, so stop rather than draw over the footer.
				break;
			}
			boolean on = active.has(entry.key());
			boolean hovered = mouseX >= listX && mouseX <= listRight && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
			context.fill(listX, rowY, listRight, rowY + ROW_HEIGHT,
					hovered && entry.renderable() ? Theme.ROW_HOVER : Theme.ROW);
			if (on) {
				context.fill(listX, rowY, listX + 2, rowY + ROW_HEIGHT, Theme.ACCENT);
			}
			int nameColor = !entry.renderable() ? Theme.SUBTEXT : on ? Theme.ACCENT : Theme.TEXT;
			context.drawString(font, entry.displayName(), listX + 6, rowY + 4, nameColor);
			// "soon" rather than a state, because the backend offers it but this client has no
			// model for it yet, so toggling it would change nothing visible.
			String state = !entry.renderable() ? "soon" : on ? "on" : "off";
			context.drawString(font, state, listRight - font.width(state) - 4, rowY + 4,
					on && entry.renderable() ? Theme.ACCENT : Theme.SUBTEXT);
			rowY += ROW_HEIGHT + 2;
		}

		context.drawString(font, "Click a row to toggle", listX, panelY() + PANEL_HEIGHT - 16, Theme.SUBTEXT);
	}

	/**
	 * The status field and the roster. Both read from {@link PresenceService}'s cache, which polls
	 * off-thread, so opening this screen starts no request of its own.
	 */
	private void renderPresence(GuiGraphics context) {
		PresenceService presence = Cryostasis.get().getPresenceService();
		int x = presenceX();
		int right = x + presenceWidth();
		int y = contentY() + 6;

		context.drawString(font, "Your status", x, y, Theme.TEXT);
		// The field itself is a widget and draws in super.render, so only its label is here.
		y += 18 + STATUS_HEIGHT + 8;

		String header = presence.onlineCount() + " online, " + presence.awayCount() + " away";
		context.drawString(font, header, x, y, Theme.SUBTEXT);
		context.fill(x, y + 11, right, y + 12, Theme.ACCENT_DIM);
		y += 16;

		int bottom = panelY() + PANEL_HEIGHT - 8;
		List<PresenceService.Entry> roster = presence.roster();
		boolean any = false;
		for (PresenceService.Entry entry : roster) {
			if (entry.username().isBlank() || y + font.lineHeight > bottom) {
				continue;
			}
			any = true;
			context.fill(x, y + 2, x + 4, y + font.lineHeight - 1,
					entry.isOnline() ? COLOR_ONLINE : COLOR_AWAY);
			String name = entry.username();
			context.drawString(font, name, x + 8, y, entry.color());
			// The status is trimmed to whatever room is left, since a player can write more than
			// this column is wide and a clipped line is better than one running into the border.
			String tail = entry.label();
			int room = right - (x + 8 + font.width(name) + 4);
			if (room > 12) {
				context.drawString(font, font.plainSubstrByWidth(tail, room),
						x + 8 + font.width(name) + 4, y, Theme.SUBTEXT);
			}
			y += font.lineHeight + 2;
		}
		if (!any) {
			context.drawString(font, "Nobody else is on.", x, y, Theme.SUBTEXT);
		}
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (status != null && status.isFocused()
				&& (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
			commitStatus();
			setFocused(null);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		// Closing commits too, so a player who typed a status and pressed Escape does not lose it.
		commitStatus();
		super.onClose();
	}

	private void commitStatus() {
		if (status == null) {
			return;
		}
		String value = status.getValue().trim();
		if (value.equals(sentStatus)) {
			return;
		}
		sentStatus = value;
		Cryostasis.get().getPresenceService().setStatus(value);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		int listX = listX();
		int listRight = listX + listWidth();
		int rowY = contentY() + 8;

		UUID uuid = localUuid();
		if (uuid == null) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		CosmeticService service = Cryostasis.get().getCosmeticService();
		CosmeticService.Active active = service.get(uuid);

		for (CosmeticCatalogue.Entry entry : CosmeticCatalogue.entries()) {
			if (mouseX >= listX && mouseX <= listRight && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
				if (!entry.renderable()) {
					return true;
				}
				if (active.has(entry.key())) {
					service.deactivate(uuid, entry.key());
				} else {
					service.activate(uuid, entry.key());
				}
				return true;
			}
			rowY += ROW_HEIGHT + 2;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/**
	 * The uuid to read and write cosmetics for. The player entity's uuid is preferred because it
	 * is exactly what the preview layer resolves the local player to, so a toggle and the preview
	 * always agree; the session profile id is the fallback when no player is in world.
	 */
	private UUID localUuid() {
		if (minecraft == null) {
			return null;
		}
		if (minecraft.player != null) {
			return minecraft.player.getUUID();
		}
		return minecraft.getUser().getProfileId();
	}

	private int panelWidth() {
		return Math.min(PANEL_WIDTH_MAX, width - GUTTER);
	}

	/** The preview is the first thing dropped when the panel has to shrink: the two lists carry
	 * the information, and the model is the decoration. */
	private boolean hasPreview() {
		return panelWidth() >= PREVIEW_THRESHOLD;
	}

	private int panelX() {
		return (width - panelWidth()) / 2;
	}

	private int panelY() {
		return (height - PANEL_HEIGHT) / 2;
	}

	private int contentY() {
		return panelY() + HEADER_HEIGHT;
	}

	private int listX() {
		return panelX() + MARGIN + (hasPreview() ? PREVIEW_WIDTH + MARGIN : 0);
	}

	/** The cosmetics list and the presence column split whatever the preview left, evenly. */
	private int listWidth() {
		return (panelX() + panelWidth() - MARGIN - listX() - GUTTER) / 2;
	}

	private int presenceX() {
		return listX() + listWidth() + GUTTER;
	}

	private int presenceWidth() {
		return panelX() + panelWidth() - MARGIN - presenceX();
	}
}
