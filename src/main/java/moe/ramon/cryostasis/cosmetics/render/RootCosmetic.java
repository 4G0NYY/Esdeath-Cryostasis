package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * Base for cosmetics that surround the whole player rather than hanging off one limb. They draw
 * in the model's root space, which stands upright at the player's feet and turns with the body
 * but never with the head, so a ring stays level however the player is looking.
 */
public abstract class RootCosmetic extends ModelCosmetic {
	protected RootCosmetic(String key, ModelPart part, ResourceLocation texture) {
		super(key, part, texture);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state) {
		if (hidden(state)) {
			return;
		}
		pose.pushPose();
		animate(state);
		draw(pose, buffers, packedLight, tint());
		pose.popPose();
	}

	protected void animate(PlayerRenderState state) {
	}

	protected int tint() {
		return 0xFFFFFFFF;
	}

	protected boolean hidden(PlayerRenderState state) {
		return false;
	}
}
