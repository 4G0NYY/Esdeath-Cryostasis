package moe.ramon.cryostasis.cosmetics.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * A top hat: a wide brim and a tall crown, worn on the head. Geometry transcribed from
 * the original 1.8 model (brim 11x2x11, crown 7x4x7).
 */
public final class TopHatCosmetic extends HeadCosmetic {
	private static final ResourceLocation TEXTURE =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/cosmetic/hat.png");

	public TopHatCosmetic(ModelPart part) {
		super("hat", part, TEXTURE);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("brim",
				CubeListBuilder.create().texOffs(0, 0).addBox(-5.5f, -8.0f, -5.5f, 11.0f, 2.0f, 11.0f),
				PartPose.ZERO);
		root.addOrReplaceChild("crown",
				CubeListBuilder.create().texOffs(0, 13).addBox(-3.5f, -12.0f, -3.5f, 7.0f, 4.0f, 7.0f),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}
}
