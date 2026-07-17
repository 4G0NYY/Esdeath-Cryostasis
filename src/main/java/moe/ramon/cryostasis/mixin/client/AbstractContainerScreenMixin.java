package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dark-themes every container screen (inventory, crafting, furnace, chests, and the rest).
 * The vanilla container texture is replaced with a themed dark panel plus a per-slot cell
 * grid derived from the menu's own slot positions, so it works for any container without a
 * texture per screen. The title and inventory labels are redrawn in light text so they
 * stay readable on the dark panel.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	protected int imageWidth;
	@Shadow
	protected int imageHeight;
	@Shadow
	protected int titleLabelX;
	@Shadow
	protected int titleLabelY;
	@Shadow
	protected int inventoryLabelX;
	@Shadow
	protected int inventoryLabelY;
	@Shadow
	protected Component playerInventoryTitle;
	@Shadow
	@SuppressWarnings("rawtypes")
	protected AbstractContainerMenu menu;

	private static final int PANEL = 0xF01A2433;
	private static final int FIELD = 0xFF10161F;
	private static final int CELL = 0xFF0B0F16;
	private static final int CELL_BORDER = 0xFF2A3648;
	private static final int TITLE_TEXT = 0xFFE6ECF5;
	private static final int LABEL_TEXT = 0xFF9AA7B8;

	/**
	 * Replaces the vanilla container background texture with the themed dark panel. Called
	 * in place of renderBg, so the world dim before it is kept and the slots and items that
	 * render afterward land on top of the dark cells.
	 */
	@Redirect(
			method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;"
							+ "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
	private void esdeath$darkBackground(AbstractContainerScreen<?> instance, GuiGraphics graphics,
			float delta, int mouseX, int mouseY) {
		int x0 = leftPos;
		int y0 = topPos;
		int x1 = leftPos + imageWidth;
		int y1 = topPos + imageHeight;

		graphics.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, PANEL);
		esdeath$border(graphics, x0 - 3, y0 - 3, x1 + 3, y1 + 3, Theme.ACCENT);
		graphics.fill(x0, y0, x1, y1, FIELD);

		for (Slot slot : menu.slots) {
			int sx = leftPos + slot.x;
			int sy = topPos + slot.y;
			graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, CELL);
			esdeath$border(graphics, sx - 1, sy - 1, sx + 17, sy + 17, CELL_BORDER);
		}
	}

	@Inject(method = "renderContents(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
	private void esdeath$lightLabels(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		// Redraw the labels light, over the vanilla dark ones, in the container's local space.
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(leftPos, topPos);
		// getTitle lives on Screen, the target's superclass; reach it through a cast rather
		// than a @Shadow, which only resolves members declared on the target class itself.
		Component title = ((Screen) (Object) this).getTitle();
		graphics.drawString(Minecraft.getInstance().font, title, titleLabelX, titleLabelY, TITLE_TEXT, false);
		graphics.drawString(Minecraft.getInstance().font, playerInventoryTitle,
				inventoryLabelX, inventoryLabelY, LABEL_TEXT, false);
		pose.popMatrix();
	}

	private void esdeath$border(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
		graphics.fill(x0, y0, x1, y0 + 1, color);
		graphics.fill(x0, y1 - 1, x1, y1, color);
		graphics.fill(x0, y0, x0 + 1, y1, color);
		graphics.fill(x1 - 1, y0, x1, y1, color);
	}
}
