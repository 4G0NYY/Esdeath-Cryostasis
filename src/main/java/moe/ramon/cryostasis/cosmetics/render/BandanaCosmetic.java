package moe.ramon.cryostasis.cosmetics.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A bandana: a wide flat band across the head. Geometry transcribed from the original 1.8
 * model (9x3x9).
 */
public final class BandanaCosmetic extends HeadCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/shi.png");

	public BandanaCosmetic(ModelPart part) {
		super("bandana", part, TEXTURE);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("band",
				CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -7.3f, -4.25f, 9.0f, 3.0f, 9.0f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}
}
