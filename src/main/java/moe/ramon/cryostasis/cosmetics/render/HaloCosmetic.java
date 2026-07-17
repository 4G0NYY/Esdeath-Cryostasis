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
 * A halo: a 6x6 open square ring of four thin bars floating above the head. Geometry
 * transcribed from the original 1.8 model. The ring bobs gently up and down over time,
 * matching the original animation.
 */
public final class HaloCosmetic extends HeadCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/halo.png");

	// Bob amplitude in model units (one is roughly one pixel) and the sweep period.
	private static final float BOB_AMPLITUDE = 1.5f;
	private static final double BOB_PERIOD_MS = 1600.0;

	public HaloCosmetic(ModelPart part) {
		super("halo", part, TEXTURE);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerModel model, PlayerRenderState state) {
		// Wall-clock sine so the bob advances smoothly without a per-tick hook. Model space
		// Y points down, so the sign just flips the phase, which does not matter here.
		float bob = (float) Math.sin(System.currentTimeMillis() / BOB_PERIOD_MS * (2.0 * Math.PI)) * BOB_AMPLITUDE;
		pose.pushPose();
		pose.translate(0.0f, bob, 0.0f);
		super.render(pose, buffers, packedLight, model, state);
		pose.popPose();
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("ring", CubeListBuilder.create()
						.texOffs(0, 0).addBox(-3.5f, -13.5f, -1.5f, 1.0f, 1.0f, 6.0f)
						.texOffs(0, 0).addBox(-3.5f, -13.5f, -1.5f, 6.0f, 1.0f, 1.0f)
						.texOffs(0, 0).addBox(2.5f, -13.5f, -1.5f, 1.0f, 1.0f, 6.0f)
						.texOffs(0, 0).addBox(-3.5f, -13.5f, 4.5f, 6.0f, 1.0f, 1.0f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}
}
