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
	public static final ModelLayerLocation RABBIT_EARS = location("rabbitears");
	public static final ModelLayerLocation REIFEN = location("reifen");
	public static final ModelLayerLocation STRIPES = location("stripes");
	public static final ModelLayerLocation TAIL = location("tail");
	public static final ModelLayerLocation WINGS = location("wings");
	public static final ModelLayerLocation SUSANOO = location("susanoo");

	private CosmeticModels() {
	}

	public static void registerLayers() {
		EntityModelLayerRegistry.registerModelLayer(TOP_HAT, TopHatCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(HALO, HaloCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(BANDANA, BandanaCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(RABBIT_EARS, RabbitEarsCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(REIFEN, ReifenCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(STRIPES, StripesCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(TAIL, TailCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(WINGS, WingsCosmetic::createLayer);
		EntityModelLayerRegistry.registerModelLayer(SUSANOO, SusanooCosmetic::createLayer);
	}

	private static ModelLayerLocation location(String name) {
		return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", name), "main");
	}
}
