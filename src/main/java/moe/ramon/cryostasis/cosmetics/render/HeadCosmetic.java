package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Base for cosmetics worn on the head. Aligns to the player's head part so the cosmetic
 * inherits head pitch and yaw automatically, which is what nearly all of the original
 * head cosmetics did.
 */
public abstract class HeadCosmetic implements Cosmetic {
	private final String key;
	private final ModelPart part;
	private final ResourceLocation texture;

	protected HeadCosmetic(String key, ModelPart part, ResourceLocation texture) {
		this.key = key;
		this.part = part;
		this.texture = texture;
	}

	@Override
	public final String key() {
		return key;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state) {
		pose.pushPose();
		// Inherit the head transform, then draw the cosmetic in head-local space.
		model.head.translateAndRotate(pose);
		VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture, false));
		part.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
