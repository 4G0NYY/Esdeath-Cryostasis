package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import moe.ramon.cryostasis.cosmetics.CosmeticTextures;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Base for a cosmetic drawn from one baked model. Holds the part and the bundled texture and
 * owns the single draw call, leaving subclasses to decide where on the player the part hangs
 * and how it animates.
 *
 * The texture passed in is the bundled one; the one actually drawn is resolved every frame, so a
 * cosmetic whose texture lives on the CDN picks it up as soon as it has downloaded and falls back
 * to the bundled bytes until then.
 */
public abstract class ModelCosmetic implements Cosmetic {
	protected static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

	/**
	 * One model unit expressed in blocks. A render layer's pose stack is in blocks, while
	 * everything written in a model builder is in the texture pixel a {@link ModelPart} divides
	 * down itself, so a translate applied outside a part has to be scaled by this or it lands
	 * sixteen times too far.
	 */
	protected static final float PIXEL = 1.0f / 16.0f;

	private final String key;
	private final ResourceLocation texture;
	protected final ModelPart part;

	protected ModelCosmetic(String key, ModelPart part, ResourceLocation texture) {
		this.key = key;
		this.part = part;
		this.texture = texture;
	}

	@Override
	public final String key() {
		return key;
	}

	protected final void draw(PoseStack pose, MultiBufferSource buffers, int packedLight) {
		draw(pose, buffers, packedLight, 0xFFFFFFFF);
	}

	/** Draw the part wherever the pose stack currently is, tinted by the given packed ARGB. */
	protected final void draw(PoseStack pose, MultiBufferSource buffers, int packedLight, int tint) {
		VertexConsumer consumer = buffers.getBuffer(renderType(CosmeticTextures.resolve(key, texture)));
		part.render(pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, tint);
	}

	/** Cutout by default; a cosmetic that wants to be see-through overrides this. */
	protected RenderType renderType(ResourceLocation resolved) {
		return RenderType.entityCutoutNoCull(resolved, false);
	}
}
