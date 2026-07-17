package moe.ramon.cryostasis.cosmetics.render;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/**
 * Central registry of cosmetic model layers. Each cosmetic has one baked layer, keyed by
 * a {@link ModelLayerLocation}. Registered once at client init; the feature layer bakes
 * each location per player renderer.
 */
public final class CosmeticModels {
	public static final ModelLayerLocation TOP_HAT = location("tophat");
	public static final ModelLayerLocation HALO = location("halo");
	public static final ModelLayerLocation BANDANA = location("bandana");

	private CosmeticModels() {
	}

	public static void registerLayers() {
		EntityModelLayerRegistry.registerModelLayer(TOP_HAT, TopHatCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(HALO, HaloCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(BANDANA, BandanaCosmetic::createLayer);
	}

	private static ModelLayerLocation location(String name) {
		return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", name), "main");
	}
}
