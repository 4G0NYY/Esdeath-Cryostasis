package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skins every container screen (inventory, crafting, furnace, chests, and the rest) with a
 * themed frame. A dark plate and steel accent border are drawn in the margin around the
 * vanilla container texture, just before the container contents render, so the vanilla
 * texture still covers the slot area and nothing shifts out of alignment.
 *
 * This is the shared first pass: it themes the frame of all containers at once. Replacing
 * the per-container slot textures is a larger follow-up.
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

	private static final int MARGIN = 4;

	@Inject(
			method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;"
							+ "renderContents(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
	private void esdeath$frame(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		int x0 = leftPos - MARGIN;
		int y0 = topPos - MARGIN;
		int x1 = leftPos + imageWidth + MARGIN;
		int y1 = topPos + imageHeight + MARGIN;

		// Backing plate. The vanilla texture draws over the inner area in renderContents, so
		// only the themed margin and border remain visible around it.
		graphics.fill(x0, y0, x1, y1, Theme.PANEL);
		graphics.fill(x0, y0, x1, y0 + 1, Theme.ACCENT);
		graphics.fill(x0, y1 - 1, x1, y1, Theme.ACCENT);
		graphics.fill(x0, y0, x0 + 1, y1, Theme.ACCENT);
		graphics.fill(x1 - 1, y0, x1, y1, Theme.ACCENT);
	}
}
