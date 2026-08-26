package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * Base for cosmetics worn on the torso. Aligns to the player's body part, so the cosmetic leans
 * with the body when the player sneaks or swims instead of hanging in the air where the upright
 * torso used to be.
 *
 * Body space has its origin at the neck with y running down the chest, which is the frame every
 * back-worn and waist-worn cosmetic here is written in.
 */
public abstract class BodyCosmetic extends ModelCosmetic {
	protected BodyCosmetic(String key, ModelPart part, ResourceLocation texture) {
		super(key, part, texture);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state) {
		if (hidden(state)) {
			return;
		}
		pose.pushPose();
		model.body.translateAndRotate(pose);
		animate(state);
		draw(pose, buffers, packedLight, tint());
		pose.popPose();
	}

	/** Pose the model for this frame. Called with the part already in body space. */
	protected void animate(PlayerRenderState state) {
	}

	/** Packed ARGB the part is drawn with, white unless a subclass wants a colour of its own. */
	protected int tint() {
		return 0xFFFFFFFF;
	}

	protected boolean hidden(PlayerRenderState state) {
		return false;
	}
}
