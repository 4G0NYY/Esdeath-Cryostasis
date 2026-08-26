package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * A halo: a square ring of four thin bars floating above the head, tilted a little off level and
 * drifting up and down.
 *
 * It follows the head's position but not the head's rotation. Anchoring it to the head part the
 * way a hat is would pitch the ring forward every time the player looks down and swing it through
 * the shoulders, which is the one thing a halo should never do.
 */
public final class HaloCosmetic extends ModelCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/halo.png");

	/**
	 * Drift amplitude in model units, and its period in ticks. Model units because the drift goes
	 * on the part rather than on the pose stack, where the same number would be read as blocks and
	 * swing the ring down through the player's knees and back over their head.
	 */
	private static final float BOB_AMPLITUDE = 1.0f;
	private static final float BOB_PERIOD_TICKS = 48.0f;

	/** The lean the original halo kept, which reads as a ring rather than as a flat disc. */
	private static final float TILT = 0.2f;

	public HaloCosmetic(ModelPart part) {
		super("halo", part, TEXTURE);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state) {
		pose.pushPose();
		// The head's pivot without its rotation, so the ring rides on the head yet stays level.
		pose.translate(model.head.x * PIXEL, model.head.y * PIXEL, model.head.z * PIXEL);
		part.y = (float) Math.sin(state.ageInTicks / BOB_PERIOD_TICKS * (2.0 * Math.PI)) * BOB_AMPLITUDE;
		draw(pose, buffers, packedLight);
		pose.popPose();
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// Four bars closing a 7x7 ring, written around its own centre and then hung four pixels
		// clear of the crown. Tilting the ring where it sits rather than at the model's origin is
		// the difference between a lean and a swing: the same angle applied a foot lower carries
		// the whole ring off to one side of the head.
		root.addOrReplaceChild("ring", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.5f, -0.5f, -3.5f, 7.0f, 1.0f, 1.0f)
						.texOffs(0, 0).addBox(-3.5f, -0.5f, 2.5f, 7.0f, 1.0f, 1.0f)
						.texOffs(0, 0).addBox(-3.5f, -0.5f, -2.5f, 1.0f, 1.0f, 5.0f)
						.texOffs(0, 0).addBox(2.5f, -0.5f, -2.5f, 1.0f, 1.0f, 5.0f),
				PartPose.offsetAndRotation(0.0f, -12.0f, 0.0f, 0.0f, 0.0f, TILT));
		return LayerDefinition.create(mesh, 64, 64);
	}
}
