package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/**
 * One renderable cosmetic. Instances are created once per player renderer with their
 * baked model already supplied, so rendering allocates nothing. The backend key is the
 * lowercase identifier the ownership check uses.
 */
public interface Cosmetic {
	/** Lowercase backend key, matched against the player's active cosmetic set. */
	String key();

	void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state);
}
