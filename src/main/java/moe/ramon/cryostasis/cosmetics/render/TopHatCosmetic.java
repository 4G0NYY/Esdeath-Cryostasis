package moe.ramon.cryostasis.cosmetics.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A top hat: a wide brim and a tall crown, worn on the head. Geometry transcribed from the
 * original 1.8 model, whose boxes were twice this size and drawn at half scale.
 *
 * The texture is laid out for a 64 by 64 atlas once those boxes are halved. Declaring 64 by 32
 * instead, which an earlier pass did, stretches every v coordinate over twice the image and drops
 * the crown into the empty bottom half of the file, which is why the brim drew and the crown did
 * not.
 *
 * The key is "tophat", which is what the backend catalogue and the menu both use. The original
 * client called it "hat", but the rebuilt catalogue does not, and the key here is what the render
 * layer matches a player's active set against.
 */
public final class TopHatCosmetic extends HeadCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/hat.png");

	public TopHatCosmetic(ModelPart part) {
		super("tophat", part, TEXTURE);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("brim",
				CubeListBuilder.create().texOffs(0, 0).addBox(-5.5f, -8.0f, -5.5f, 11.0f, 2.0f, 11.0f),
				PartPose.ZERO);
		root.addOrReplaceChild("crown",
				CubeListBuilder.create().texOffs(0, 13).addBox(-3.5f, -16.0f, -3.5f, 7.0f, 8.0f, 7.0f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}
}
