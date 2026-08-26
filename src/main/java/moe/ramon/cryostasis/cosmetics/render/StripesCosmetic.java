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
 * Stripes: six bars standing in a ring around the player, drifting up and down and turning
 * slowly. The vertical drift is the original's; the ring rotation is not recoverable from the
 * decompilation and is put back here, since six bars frozen in place read as scenery rather than
 * as an aura.
 */
public final class StripesCosmetic extends RootCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/stripes.png");

	private static final int BARS = 6;
	private static final float RADIUS = 11.0f;
	/** Midway up the model, whose root sits at the neck and whose feet are at 24. */
	private static final float CENTRE_Y = 12.0f;

	private static final float BOB_AMPLITUDE = 2.0f;
	private static final float BOB_PERIOD_TICKS = 60.0f;
	private static final float SPIN_PERIOD_TICKS = 140.0f;

	public StripesCosmetic(ModelPart part) {
		super("stripes", part, TEXTURE);
	}

	@Override
	protected void animate(PlayerRenderState state) {
		part.y = (float) Math.sin(state.ageInTicks / BOB_PERIOD_TICKS * (2.0 * Math.PI)) * BOB_AMPLITUDE;
		part.yRot = (float) (state.ageInTicks / SPIN_PERIOD_TICKS * (2.0 * Math.PI));
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeListBuilder bar = CubeListBuilder.create().texOffs(0, 0)
				.addBox(-1.0f, -6.0f, -1.0f, 2.0f, 12.0f, 2.0f);

		for (int i = 0; i < BARS; i++) {
			float angle = (float) (i * 2.0 * Math.PI / BARS);
			root.addOrReplaceChild("bar" + i, bar, PartPose.offsetAndRotation(
					(float) Math.sin(angle) * RADIUS, CENTRE_Y, (float) Math.cos(angle) * RADIUS,
					0.0f, angle, 0.0f));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}
}
