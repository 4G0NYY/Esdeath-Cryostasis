package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.gui.ContainerSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
 *
 * The swap itself happens in ContainerSkin, driven from GuiGraphicsMixin. This class only
 * arms it around renderBg, because renderBg has to run: screens draw their live contents
 * there, not just their background.
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

	@Shadow
	protected abstract void renderBg(GuiGraphics graphics, float delta, int mouseX, int mouseY);

	private static final int TITLE_TEXT = 0xFFE6ECF5;
	private static final int LABEL_TEXT = 0xFF9AA7B8;

	/**
	 * Arms the themed panel for this screen's renderBg. renderBg is still called: replacing it
	 * would drop everything else screens draw inside it, from the survival inventory's player
	 * model to the creative tab strip.
	 */
	@Redirect(
			method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;"
							+ "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V"))
	private void esdeath$darkBackground(AbstractContainerScreen<?> instance, GuiGraphics graphics,
			float delta, int mouseX, int mouseY) {
		ContainerSkin.begin(leftPos, topPos, imageWidth, imageHeight, menu.slots);
		try {
			renderBg(graphics, delta, mouseX, mouseY);
		} finally {
			// A screen that throws mid-render must not leave the guard armed, or the next raw
			// texture blit anywhere in the game would be swallowed.
			ContainerSkin.end();
		}
	}

	@Inject(method = "renderContents(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
	private void esdeath$lightLabels(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		// The creative screen titles its own tabs and has no player-inventory label, so the
		// generic label pass would stamp a stray "Inventory" over the item grid.
		if ((Object) this instanceof CreativeModeInventoryScreen) {
			return;
		}

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
}
