package moe.ramon.cryostasis.render;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.backend.PresenceService;
import moe.ramon.cryostasis.modules.render.StatusTagModule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Writes what Cryostasis knows about a player into their name tag: the client emblem and their
 * rank in front of the name, their away state and status line underneath it.
 *
 * Both lines are handed to the game as text on the render state rather than drawn here. A name tag
 * lives in world space but is billboarded to face the camera, and the pose a render layer is given
 * has already been turned to face the way the player is standing and flipped upside down for the
 * model; text placed in that space leans away from the camera and reads bottom up. Letting the
 * game place the lines also inherits every rule it already applies: when a name shows at all, how
 * far away it stops, the background opacity, and drawing through walls.
 *
 * The lower line rides on {@code scoreText}, the field the game reserves for a line under the name
 * and only uses when a server puts an objective in the below-name slot. It is shared rather than
 * taken, so a server already using that slot keeps its line.
 *
 * Presence is read from {@link PresenceService}'s cache and never fetched here, so extraction never
 * waits on the network. A player nobody has looked up yet is plain until the next batch lands,
 * which is the same bargain the cosmetic layer makes.
 */
public final class NameTagDecorator {
	private static final ResourceLocation EMBLEM_FONT =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "emblem");

	/** A private use codepoint, so it cannot collide with anything a player is able to type. */
	private static final String EMBLEM = "\uE000";

	/** White, because a bitmap glyph is tinted by the text colour and the emblem brings its own. */
	private static final Style EMBLEM_STYLE =
			Style.EMPTY.withFont(EMBLEM_FONT).withColor(TextColor.fromRgb(0xFFFFFF));

	/** The same amber the roster, the chat tag, and the online list use for an away player. */
	private static final int COLOR_AWAY = 0xE8B14C;

	private NameTagDecorator() {
	}

	/**
	 * Decorate one player's name tag in place. Does nothing to a player the backend has never
	 * heard of, or to one whose name the game has already decided not to show.
	 */
	public static void decorate(UUID player, PlayerRenderState state) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null || state.nameTag == null) {
			return;
		}
		StatusTagModule module = cryostasis.getModuleManager().get(StatusTagModule.class);
		if (module == null || !module.isEnabled()) {
			return;
		}
		PresenceService.Entry entry = cryostasis.getPresenceService().get(player);
		// No uuid is the cache saying the backend does not know this player, so they are not one
		// of ours and their name tag stays vanilla.
		if (entry.uuid() == null) {
			return;
		}
		state.nameTag = prefixed(entry, module, state.nameTag);
		state.scoreText = underName(entry, module, state.scoreText);
	}

	private static Component prefixed(PresenceService.Entry entry, StatusTagModule module,
			Component name) {
		MutableComponent prefix = Component.empty();
		boolean any = false;

		if (module.showEmblem()) {
			prefix.append(Component.literal(EMBLEM).withStyle(EMBLEM_STYLE)).append(" ");
			any = true;
		}
		// The Default tag is left off for the reason chat lines leave it off: it is every player's
		// starting rank, so showing it would put a label over everyone that says nothing.
		if (module.showRank() && !"Default".equalsIgnoreCase(entry.rank())) {
			prefix.append(Component.literal("[" + entry.rank() + "] ")
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(entry.color() & 0xFFFFFF))));
			any = true;
		}
		return any ? prefix.append(name) : name;
	}

	/**
	 * The line under the name, or the server's own line unchanged when there is nothing to add. An
	 * online player who has set no status has nothing to say beyond their name.
	 */
	private static Component underName(PresenceService.Entry entry, StatusTagModule module,
			Component score) {
		MutableComponent line = Component.empty();
		boolean any = false;

		if (module.showAway() && entry.isAfk()) {
			line.append(Component.literal("AFK")
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR_AWAY))));
			any = true;
		}
		if (module.showStatus() && !entry.status().isBlank()) {
			if (any) {
				line.append(" ");
			}
			line.append(Component.literal(entry.status()).withStyle(ChatFormatting.GRAY));
			any = true;
		}
		if (!any) {
			return score;
		}
		return score == null ? line : Component.empty().append(score).append(" ").append(line);
	}
}
