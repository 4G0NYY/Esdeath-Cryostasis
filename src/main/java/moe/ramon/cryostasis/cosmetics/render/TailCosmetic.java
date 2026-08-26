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
 * A tail hanging off the lower back: four tapering segments, each parented to the one before it,
 * so a small rotation per joint compounds into a curve.
 *
 * The swing rides on the cape values the player renderer already works out for this frame
 * ({@code capeFlap} for lift and {@code capeLean2} for sideways lag), which is the same horizontal
 * motion the original reimplemented by hand. Reusing them means the tail lags and settles exactly
 * as a cape does, and costs nothing to track.
 */
public final class TailCosmetic extends BodyCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/stripes.png");

	private static final int SEGMENTS = 4;
	/** Droop per joint at rest, negative because model y runs downward. */
	private static final float REST_DROOP = -0.30f;
	/** Idle sway, so a standing tail is not a rigid stick. */
	private static final float IDLE_AMPLITUDE = 0.05f;
	private static final float IDLE_PERIOD_TICKS = 50.0f;

	private final ModelPart[] joints = new ModelPart[SEGMENTS];

	public TailCosmetic(ModelPart part) {
		super("tail", part, TEXTURE);
		ModelPart joint = part;
		for (int i = 0; i < SEGMENTS; i++) {
			joint = joint.getChild("tail" + i);
			joints[i] = joint;
		}
	}

	@Override
	protected void animate(PlayerRenderState state) {
		float lift = (state.capeFlap + state.capeLean * 0.5f) * DEG_TO_RAD;
		float sway = state.capeLean2 * 0.5f * DEG_TO_RAD;
		float idle = (float) Math.sin(state.ageInTicks / IDLE_PERIOD_TICKS * (2.0 * Math.PI)) * IDLE_AMPLITUDE;

		for (int i = 0; i < SEGMENTS; i++) {
			// Later joints take a larger share, which is what turns a uniform lag into a whip.
			float weight = (i + 1) / (float) SEGMENTS;
			joints[i].xRot = REST_DROOP + lift * weight;
			joints[i].yRot = (sway + idle) * weight;
		}
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition parent = mesh.getRoot();
		// The lower back, at the seam where the torso meets the legs.
		PartPose pose = PartPose.offset(0.0f, 10.0f, 2.0f);
		float[] thickness = {4.0f, 3.0f, 2.0f, 1.0f};
		int[] texV = {0, 10, 19, 27};

		for (int i = 0; i < SEGMENTS; i++) {
			float half = thickness[i] / 2.0f;
			parent = parent.addOrReplaceChild("tail" + i, CubeListBuilder.create()
					.texOffs(0, texV[i])
					.addBox(-half, -half, 0.0f, thickness[i], thickness[i], 5.0f), pose);
			pose = PartPose.offset(0.0f, 0.0f, 5.0f);
		}
		return LayerDefinition.create(mesh, 64, 64);
	}
}
