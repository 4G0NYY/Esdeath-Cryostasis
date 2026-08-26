package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * Base for cosmetics worn on the head. Aligns to the player's head part so the cosmetic
 * inherits head pitch and yaw automatically, which is what nearly all of the original
 * head cosmetics did.
 *
 * Nothing here has to handle sneaking: the head part has already been posed for the frame, so a
 * cosmetic anchored to it drops with the head the way the original did by hand.
 */
public abstract class HeadCosmetic extends ModelCosmetic {
	protected HeadCosmetic(String key, ModelPart part, ResourceLocation texture) {
		super(key, part, texture);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state) {
		pose.pushPose();
		model.head.translateAndRotate(pose);
		draw(pose, buffers, packedLight);
		pose.popPose();
	}
}
