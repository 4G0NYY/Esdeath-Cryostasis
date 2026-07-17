package moe.ramon.cryostasis.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import moe.ramon.cryostasis.gui.ContainerSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps a container screen's background texture for the themed panel.
 *
 * This overload is the one container screens use to draw their background, and only their
 * background: everything dynamic a screen paints in renderBg (sprites, text, items, entity
 * models) reaches GuiGraphics through other methods, so intercepting here leaves all of it
 * intact. The guard is armed only for the duration of AbstractContainerScreen.renderBg, so
 * raw texture blits anywhere else in the game are untouched.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
	@Inject(
			method = "blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;"
					+ "IIFFIIII)V",
			at = @At("HEAD"),
			cancellable = true)
	private void esdeath$replaceContainerBackground(RenderPipeline pipeline, ResourceLocation texture,
			int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight,
			CallbackInfo ci) {
		if (!ContainerSkin.isActive()) {
			return;
		}
		ContainerSkin.paintOnce((GuiGraphics) (Object) this);
		ci.cancel();
	}
}
