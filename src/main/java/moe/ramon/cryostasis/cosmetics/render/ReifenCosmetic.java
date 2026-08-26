package moe.ramon.cryostasis.cosmetics.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;

/**
 * The Reifen: a red and white swim ring worn around the waist. Eight straight segments laid on a
 * circle, which reads as a hoop at any distance the player is actually seen from and costs eight
 * boxes rather than a mesh.
 *
 * It hides while sneaking, as the original did. A ring pinned to the waist cuts straight through
 * the thighs once the body folds forward, and no amount of offsetting saves it.
 */
public final class ReifenCosmetic extends BodyCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/reifen.png");

	private static final int SEGMENTS = 8;
	private static final float RADIUS = 6.5f;
	/**
	 * Down the torso from the neck. The seam where the torso ends and the legs begin is the only
	 * height that works: any higher and the ring runs through the arms, any lower and a walking
	 * player's thighs swing out through it.
	 */
	private static final float WAIST_Y = 12.0f;

	public ReifenCosmetic(ModelPart part) {
		super("reifen", part, TEXTURE);
	}

	@Override
	protected boolean hidden(PlayerRenderState state) {
		return state.isCrouching;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition hoop = mesh.getRoot().addOrReplaceChild("hoop",
				CubeListBuilder.create(), PartPose.offset(0.0f, WAIST_Y, 0.0f));

		// One segment slightly longer than the arc it spans, so the corners meet instead of
		// leaving a gap the player can see through.
		float span = (float) (2.0 * Math.PI * RADIUS / SEGMENTS) + 0.6f;
		CubeListBuilder segment = CubeListBuilder.create().texOffs(0, 0)
				.addBox(-span / 2.0f, -1.5f, -1.5f, span, 3.0f, 3.0f);

		for (int i = 0; i < SEGMENTS; i++) {
			float angle = (float) (i * 2.0 * Math.PI / SEGMENTS);
			hoop.addOrReplaceChild("segment" + i, segment, PartPose.offsetAndRotation(
					(float) Math.sin(angle) * RADIUS, 0.0f, (float) Math.cos(angle) * RADIUS,
					0.0f, angle, 0.0f));
		}
		return LayerDefinition.create(mesh, 32, 32);
	}
}
