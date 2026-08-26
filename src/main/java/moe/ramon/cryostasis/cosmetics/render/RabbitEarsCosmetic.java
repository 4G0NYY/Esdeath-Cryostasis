package moe.ramon.cryostasis.cosmetics.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Rabbit ears: two tall thin boxes standing on the crown, each splayed slightly outward and
 * leaning back. Worn on the head, so they follow pitch and yaw the way the original did.
 */
public final class RabbitEarsCosmetic extends HeadCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/rabbit.png");

	/** Outward splay per ear, from the original model. */
	private static final float SPLAY = 0.06f;
	/** Backward lean, which keeps the ears off the brow when the player looks up. */
	private static final float LEAN = -0.15f;

	public RabbitEarsCosmetic(ModelPart part) {
		super("rabbitears", part, TEXTURE);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		CubeListBuilder ear = CubeListBuilder.create().texOffs(0, 0)
				.addBox(-1.0f, -8.0f, -0.5f, 2.0f, 8.0f, 1.0f);
		// Pivoted at the crown so the splay swings the tip out rather than shearing the whole ear.
		root.addOrReplaceChild("left", ear,
				PartPose.offsetAndRotation(2.0f, -8.0f, 0.0f, LEAN, 0.0f, SPLAY));
		root.addOrReplaceChild("right", ear,
				PartPose.offsetAndRotation(-2.0f, -8.0f, 0.0f, LEAN, 0.0f, -SPLAY));
		return LayerDefinition.create(mesh, 32, 32);
	}
}
