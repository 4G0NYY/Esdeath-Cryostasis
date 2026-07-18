package moe.ramon.cryostasis.gui;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.cosmetics.CosmeticCatalogue;
import moe.ramon.cryostasis.cosmetics.CosmeticService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

/**
 * In-game cosmetics menu: previews the local player with their active cosmetics and lets them
 * toggle each one on or off. It reads and writes through {@link CosmeticService}, which updates
 * its cache optimistically, so a click shows on the live preview at once while the change is
 * posted to the backend in the background.
 *
 * The preview reuses the same {@code CosmeticLayer} the world renderer uses, so what shows here
 * is exactly what other players see. The menu therefore needs an in-world player to preview and
 * is opened from within a world.
 */
public final class CosmeticsScreen extends Screen {
	private static final int PANEL_WIDTH = 280;
	private static final int PANEL_HEIGHT = 190;
	private static final int ROW_HEIGHT = 16;
	private static final int PREVIEW_WIDTH = 120;

	public CosmeticsScreen() {
		super(Component.literal("Cosmetics"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		// Flat dim instead of renderBackground: this non-pausing screen sits over a frame that
		// already requested the one allowed blur, matching ClickGuiScreen.
		context.fill(0, 0, width, height, Theme.DIM);

		int panelX = (width - PANEL_WIDTH) / 2;
		int panelY = (height - PANEL_HEIGHT) / 2;
		Skin.plate(context, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, Theme.PANEL_SOLID, Theme.ACCENT_DIM);

		// Header with the accent seam the rest of the client uses.
		context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 15, Theme.HEADER);
		context.fill(panelX, panelY + 14, panelX + PANEL_WIDTH, panelY + 15, Theme.ACCENT);
		context.drawString(font, "Cosmetics", panelX + 6, panelY + 4, Theme.TEXT);

		int contentY = panelY + 15;
		renderPreview(context, panelX, contentY, mouseX, mouseY);
		renderRows(context, panelX, contentY, mouseX, mouseY);
	}

	private void renderPreview(GuiGraphics context, int panelX, int contentY, int mouseX, int mouseY) {
		int px0 = panelX + 6;
		int py0 = contentY + 6;
		int px1 = px0 + PREVIEW_WIDTH;
		int py1 = panelY() + PANEL_HEIGHT - 6;
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

	private void renderRows(GuiGraphics context, int panelX, int contentY, int mouseX, int mouseY) {
		int listX = panelX + PREVIEW_WIDTH + 12;
		int listRight = panelX + PANEL_WIDTH - 6;
		int rowY = contentY + 8;

		UUID uuid = localUuid();
		CosmeticService service = Cryostasis.get().getCosmeticService();
		CosmeticService.Active active = uuid != null ? service.get(uuid) : CosmeticService.Active.EMPTY;

		List<CosmeticCatalogue.Entry> entries = CosmeticCatalogue.entries();
		for (CosmeticCatalogue.Entry entry : entries) {
			boolean on = active.has(entry.key());
			boolean hovered = mouseX >= listX && mouseX <= listRight && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;
			context.fill(listX, rowY, listRight, rowY + ROW_HEIGHT, hovered ? Theme.ROW_HOVER : Theme.ROW);
			if (on) {
				context.fill(listX, rowY, listX + 2, rowY + ROW_HEIGHT, Theme.ACCENT);
			}
			context.drawString(font, entry.displayName(), listX + 6, rowY + 4, on ? Theme.ACCENT : Theme.TEXT);
			String state = on ? "on" : "off";
			context.drawString(font, state, listRight - font.width(state) - 4, rowY + 4,
					on ? Theme.ACCENT : Theme.SUBTEXT);
			rowY += ROW_HEIGHT + 2;
		}

		context.drawString(font, "Click a row to toggle", listX, panelY() + PANEL_HEIGHT - 16, Theme.SUBTEXT);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		int panelX = (width - PANEL_WIDTH) / 2;
		int contentY = panelY() + 15;
		int listX = panelX + PREVIEW_WIDTH + 12;
		int listRight = panelX + PANEL_WIDTH - 6;
		int rowY = contentY + 8;

		UUID uuid = localUuid();
		if (uuid == null) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		CosmeticService service = Cryostasis.get().getCosmeticService();
		CosmeticService.Active active = service.get(uuid);

		for (CosmeticCatalogue.Entry entry : CosmeticCatalogue.entries()) {
			if (mouseX >= listX && mouseX <= listRight && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT) {
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

	private int panelY() {
		return (height - PANEL_HEIGHT) / 2;
	}
}
