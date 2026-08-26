package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.render.NameTagDecorator;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hangs the Cryostasis emblem, rank, and status off other players' name tags.
 *
 * The hook sits on extraction rather than on the draw call because extraction is the one place
 * that still holds the player: it hands over the real entity, so the account can be looked up by
 * uuid instead of by matching the name back through the tab list. What it writes is text on the
 * render state, which leaves the placing and the billboarding to the game.
 *
 * Extraction runs afresh every frame and the game assigns the name tag before this returns, so the
 * decoration is applied to a clean name each time rather than piling up on the last frame's.
 *
 * @see NameTagDecorator
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;"
					+ "Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
			at = @At("TAIL"))
	private void cryostasis$decorateNameTag(AbstractClientPlayer player, PlayerRenderState state,
			float partialTick, CallbackInfo ci) {
		NameTagDecorator.decorate(player.getUUID(), state);
	}
}
