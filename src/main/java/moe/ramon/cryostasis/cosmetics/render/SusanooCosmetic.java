package moe.ramon.cryostasis.cosmetics.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Susanoo: a rib cage standing around the player, eight ribs rising from a ring at the feet and
 * curving in to close above the head, with a short splay of inner ribs at the waist.
 *
 * It does not animate. The original did not either, and a cage this size sweeping about would
 * make the player inside it impossible to read.
 *
 * Drawn translucent rather than cutout, because the texture is a flat dark red with no alpha of
 * its own and an opaque cage would simply hide whoever is wearing it.
 */
public final class SusanooCosmetic extends RootCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/susanoo.png");

	/** Alpha carries in the tint, since the texture has none to give. */
	private static final int TINT = 0xA0FFFFFF;

	private static final int RIBS = 8;
	private static final int INNER_RIBS = 4;
	private static final float RADIUS = 14.0f;
	/** The model's feet, where the base ring sits. */
	private static final float FLOOR_Y = 22.0f;

	private static final float[] SEGMENT_LENGTH = {14.0f, 13.0f, 12.0f};
	/** Lean per joint, chosen so the three segments meet over the head rather than past it. */
	private static final float[] SEGMENT_LEAN = {0.15f, 0.26f, 0.36f};
	private static final float[] SEGMENT_WIDTH = {3.0f, 2.5f, 2.0f};

	public SusanooCosmetic(ModelPart part) {
		super("susanoo", part, TEXTURE);
	}

	@Override
	protected int tint() {
		return TINT;
	}

	@Override
	protected RenderType renderType(ResourceLocation resolved) {
		return RenderType.entityTranslucent(resolved, false);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		baseRing(root);
		for (int i = 0; i < RIBS; i++) {
			rib(root, i, (float) (i * 2.0 * Math.PI / RIBS));
		}
		for (int i = 0; i < INNER_RIBS; i++) {
			innerRib(root, i, (float) (i * 2.0 * Math.PI / INNER_RIBS + Math.PI / INNER_RIBS));
		}
		return LayerDefinition.create(mesh, 32, 32);
	}

	private static void baseRing(PartDefinition root) {
		float span = (float) (2.0 * Math.PI * RADIUS / RIBS) + 0.6f;
		CubeListBuilder segment = CubeListBuilder.create().texOffs(0, 0)
				.addBox(-span / 2.0f, -1.5f, -1.0f, span, 3.0f, 2.0f);
		for (int i = 0; i < RIBS; i++) {
			float angle = (float) ((i + 0.5) * 2.0 * Math.PI / RIBS);
			root.addOrReplaceChild("base" + i, segment, PartPose.offsetAndRotation(
					(float) Math.sin(angle) * RADIUS, FLOOR_Y, (float) Math.cos(angle) * RADIUS,
					0.0f, angle, 0.0f));
		}
	}

	private static void rib(PartDefinition root, int index, float angle) {
		PartDefinition parent = root;
		// Standing on the ring, turned so the joint lean tips the rib inward rather than sideways.
		PartPose pose = PartPose.offsetAndRotation(
				(float) Math.sin(angle) * RADIUS, FLOOR_Y, (float) Math.cos(angle) * RADIUS,
				SEGMENT_LEAN[0], angle, 0.0f);

		for (int i = 0; i < SEGMENT_LENGTH.length; i++) {
			float half = SEGMENT_WIDTH[i] / 2.0f;
			parent = parent.addOrReplaceChild("rib" + index + "_" + i, CubeListBuilder.create()
					.texOffs(0, 0)
					.addBox(-half, -SEGMENT_LENGTH[i], -half, SEGMENT_WIDTH[i], SEGMENT_LENGTH[i], SEGMENT_WIDTH[i]),
					pose);
			if (i + 1 < SEGMENT_LENGTH.length) {
				pose = PartPose.offsetAndRotation(0.0f, -SEGMENT_LENGTH[i], 0.0f, SEGMENT_LEAN[i + 1], 0.0f, 0.0f);
			}
		}
	}

	/** A short rib leaning out from the waist, the static splay the original cage carried. */
	private static void innerRib(PartDefinition root, int index, float angle) {
		root.addOrReplaceChild("inner" + index, CubeListBuilder.create()
						.texOffs(0, 0).addBox(-1.0f, -9.0f, -1.0f, 2.0f, 9.0f, 2.0f),
				PartPose.offsetAndRotation(
						(float) Math.sin(angle) * 7.0f, 12.0f, (float) Math.cos(angle) * 7.0f,
						-0.45f, angle, 0.0f));
	}
}
