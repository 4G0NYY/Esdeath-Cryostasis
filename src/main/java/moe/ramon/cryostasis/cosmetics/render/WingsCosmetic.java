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
 * A pair of wings on the upper back: a leading bone, a membrane hanging off it, and a spar along
 * the trailing edge, mirrored either side and swept back so they read as folded rather than as
 * two boards.
 *
 * The wing is scaled up on its pose rather than built larger. Box dimensions and uv extents are
 * the same numbers in a model builder, so a bigger box would stop sampling the one 10 by 20 wing
 * shape the file holds and start reading whatever sits beside it.
 *
 * The flap is a one second sine, as the original was. On top of it the wings inherit the cape
 * values the player renderer has already worked out for this frame, so they lag and settle with
 * the player's own horizontal motion instead of beating on regardless.
 */
public final class WingsCosmetic extends BodyCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/wings.png");

	/** The purple the original washed over the near-black texture. */
	private static final int TINT = 0xFFB070FF;

	private static final float SPAN = 10.0f;
	private static final float WEB_HEIGHT = 20.0f;

	private static final float SCALE = 1.1f;
	/** How far the wings sit open at rest, and how far back they are swept. */
	private static final float REST_SPREAD = 0.35f;
	private static final float SWEEP = 0.55f;

	private static final float FLAP_AMPLITUDE = 0.30f;
	private static final float FLAP_PERIOD_TICKS = 20.0f;

	private final ModelPart left;
	private final ModelPart right;

	public WingsCosmetic(ModelPart part) {
		super("wings", part, TEXTURE);
		this.left = part.getChild("left");
		this.right = part.getChild("right");
	}

	@Override
	protected int tint() {
		return TINT;
	}

	@Override
	protected void animate(PlayerRenderState state) {
		float flap = (float) Math.sin(state.ageInTicks / FLAP_PERIOD_TICKS * (2.0 * Math.PI)) * FLAP_AMPLITUDE;
		float lift = (state.capeFlap + state.capeLean * 0.5f) * DEG_TO_RAD;
		float lag = state.capeLean2 * 0.5f * DEG_TO_RAD;

		left.zRot = -REST_SPREAD + flap;
		right.zRot = REST_SPREAD - flap;
		left.yRot = -SWEEP + lag;
		right.yRot = SWEEP + lag;
		left.xRot = lift;
		right.xRot = lift;
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// The shoulder blades, just off the spine on the back face of the torso.
		root.addOrReplaceChild("left", half(false), PartPose.offset(2.0f, 2.0f, 2.0f).scaled(SCALE));
		root.addOrReplaceChild("right", half(true), PartPose.offset(-2.0f, 2.0f, 2.0f).scaled(SCALE));
		return LayerDefinition.create(mesh, 30, 30);
	}

	private static CubeListBuilder half(boolean mirrored) {
		float x = mirrored ? -SPAN : 0.0f;
		return CubeListBuilder.create()
				.mirror(mirrored)
				.texOffs(0, 0).addBox(x, -1.0f, -1.0f, SPAN, 2.0f, 2.0f)
				.texOffs(0, 7).addBox(x, 1.0f, -0.5f, SPAN, WEB_HEIGHT, 1.0f)
				.texOffs(0, 5).addBox(x, WEB_HEIGHT + 1.0f, -0.5f, SPAN, 1.0f, 1.0f);
	}
}
