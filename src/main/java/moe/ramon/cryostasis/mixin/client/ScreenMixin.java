package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the backdrop of every vanilla screen with the client's navy gradient.
 *
 * Screen.renderBackground calls renderMenuBackground for the backdrop, and every screen that
 * does not draw its own inherits it, so this one hook covers the whole menu tree: options,
 * controls, video settings, world and server select, world creation, the pause menu, and any
 * mod screen that does not override the method. The four-int overload is the one that draws,
 * and the no-argument overload delegates to it, so hooking it here catches both.
 *
 * In world the backdrop stays translucent rather than becoming the opaque gradient. Vanilla
 * makes the same distinction (a separate in-world background texture), and it matters: an
 * opaque fill would black out the world behind the pause menu.
 *
 * The blur pass is deliberately left alone. It runs before the backdrop and gives in-world
 * menus their depth, and the client already has one screen that must avoid re-requesting it
 * (see ClickGuiScreen).
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Shadow
	protected Minecraft minecraft;
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(
			method = "renderMenuBackground(Lnet/minecraft/client/gui/GuiGraphics;IIII)V",
			at = @At("HEAD"),
			cancellable = true)
	private void esdeath$menuBackground(GuiGraphics graphics, int x0, int y0, int x1, int y1, CallbackInfo ci) {
		if (minecraft.level == null) {
			graphics.fillGradient(x0, y0, x1, y1, Theme.BG_TOP, Theme.BG_BOTTOM);
		} else {
			graphics.fill(x0, y0, x1, y1, Theme.DIM);
		}
		ci.cancel();
	}

	@Inject(
			method = "renderTransparentBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void esdeath$transparentBackground(GuiGraphics graphics, CallbackInfo ci) {
		graphics.fill(0, 0, width, height, Theme.DIM);
		ci.cancel();
	}
}
