package moe.ramon.cryostasis.render;

import com.mojang.blaze3d.vertex.PoseStack;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.backend.PresenceService;
import moe.ramon.cryostasis.modules.render.StatusTagModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Draws a Cryostasis player's rank and status as a second line under their name tag.
 *
 * It is deliberately tied to the vanilla name tag rather than drawn whenever a player is visible:
 * the render state only carries a name tag when the game decided one should show, so gating on
 * that inherits every rule vanilla already applies (sneaking, the distance limit, F1, spectators)
 * instead of reimplementing them and getting one wrong.
 *
 * The placement mirrors what {@code EntityRenderer.renderNameTag} does, offset by one line so the
 * tag sits under the name: translate to the name tag attachment, face the camera, and scale down
 * to the size the game draws world text at.
 *
 * Presence is read from {@link PresenceService}'s cache and never fetched here. A player nobody
 * has looked up yet simply has no tag on the first frame and one on the next poll, which is the
 * same bargain the cosmetic layer makes.
 */
public final class StatusTagLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
	/** The size the game draws world-space text at, negated in y because the pose is flipped. */
	private static final float SCALE = 0.025f;
	/** Matches the lift renderNameTag applies before drawing. */
	private static final double LIFT = 0.5;

	private static final int COLOR_AWAY = 0xE8B14C;

	public StatusTagLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight,
			PlayerRenderState state, float yaw, float pitch) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		StatusTagModule module = cryostasis.getModuleManager().get(StatusTagModule.class);
		if (module == null || !module.isEnabled()) {
			return;
		}
		// No name tag means the game decided this player's name should not show, so neither
		// should anything hanging off it.
		Vec3 attachment = state.nameTagAttachment;
		if (state.nameTag == null || attachment == null) {
			return;
		}
		UUID uuid = resolveUuid(state.name);
		if (uuid == null) {
			return;
		}
		PresenceService.Entry entry = cryostasis.getPresenceService().get(uuid);
		Component tag = describe(entry, module);
		if (tag == null) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		boolean seeThrough = !state.isDiscrete;

		pose.pushPose();
		pose.translate(attachment.x, attachment.y + LIFT, attachment.z);
		pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
		pose.scale(SCALE, -SCALE, SCALE);
		Matrix4f matrix = pose.last().pose();

		float x = -font.width(tag) / 2.0f;
		// One line below the name. The pose's y is already flipped, so down is a positive step.
		float y = font.lineHeight + 1.0f;
		int background = (int) (minecraft.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;

		font.drawInBatch(tag, x, y, -1, false, matrix, buffers,
				seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
				background, packedLight);
		if (seeThrough) {
			// The second pass is what makes a name legible against a lit wall: the first draws
			// through geometry at low contrast, this one draws the solid glyphs on top.
			font.drawInBatch(tag, x, y, -1, false, matrix, buffers, Font.DisplayMode.NORMAL, 0,
					packedLight);
		}
		pose.popPose();
	}

	/**
	 * The tag line, or null when there is nothing worth drawing. A default-rank player who is
	 * online and has set no status has nothing to say beyond their name, and a tag repeating that
	 * would put a line over every player on the server for no information.
	 */
	private static Component describe(PresenceService.Entry entry, StatusTagModule module) {
		if (entry.uuid() == null) {
			return null;
		}
		MutableComponent line = Component.empty();
		boolean any = false;

		if (module.showRank() && !"Default".equalsIgnoreCase(entry.rank())) {
			line.append(Component.literal(entry.rank())
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(entry.color() & 0xFFFFFF))));
			any = true;
		}
		if (module.showAway() && entry.isAfk()) {
			if (any) {
				line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(Component.literal("AFK")
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_AWAY))));
			any = true;
		}
		if (module.showStatus() && !entry.status().isBlank()) {
			if (any) {
				line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
			}
			line.append(Component.literal(entry.status()).withStyle(ChatFormatting.GRAY));
			any = true;
		}
		return any ? line : null;
	}

	/**
	 * The render state carries a display name rather than a UUID, so the player is resolved back
	 * through the connection's tab list, the same way the cosmetic layer does it.
	 */
	private static UUID resolveUuid(String name) {
		Minecraft minecraft = Minecraft.getInstance();
		if (name == null || minecraft.getConnection() == null) {
			return null;
		}
		PlayerInfo info = minecraft.getConnection().getPlayerInfo(name);
		return info != null ? info.getProfile().getId() : null;
	}
}
