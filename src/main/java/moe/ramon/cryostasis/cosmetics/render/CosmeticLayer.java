package moe.ramon.cryostasis.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.cosmetics.CosmeticService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

import java.util.List;
import java.util.UUID;

/**
 * Player render layer that draws each visible player's owned cosmetics. The 1.21 render
 * state does not carry a UUID, only the display name, so the player is resolved back to a
 * UUID through the connection's tab list. Ownership comes from the cached
 * {@link CosmeticService}, so this layer never touches the network on the render thread.
 */
public final class CosmeticLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
	private final List<Cosmetic> cosmetics;

	public CosmeticLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
		super(parent);
		// Bake each cosmetic's model once for this renderer.
		this.cosmetics = List.of(
				new TopHatCosmetic(context.bakeLayer(CosmeticModels.TOP_HAT)),
				new HaloCosmetic(context.bakeLayer(CosmeticModels.HALO)),
				new BandanaCosmetic(context.bakeLayer(CosmeticModels.BANDANA)));
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, PlayerRenderState state, float yaw, float pitch) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		UUID uuid = resolveUuid(state.name);
		if (uuid == null) {
			return;
		}
		CosmeticService.Active active = cryostasis.getCosmeticService().get(uuid);
		if (active.cosmetics().isEmpty()) {
			return;
		}
		PlayerModel model = getParentModel();
		for (int i = 0; i < cosmetics.size(); i++) {
			Cosmetic cosmetic = cosmetics.get(i);
			if (active.has(cosmetic.key())) {
				cosmetic.render(pose, buffers, packedLight, model, state);
			}
		}
	}

	private static UUID resolveUuid(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (name == null || mc.getConnection() == null) {
			return null;
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(name);
		return info != null ? info.getProfile().getId() : null;
	}
}
