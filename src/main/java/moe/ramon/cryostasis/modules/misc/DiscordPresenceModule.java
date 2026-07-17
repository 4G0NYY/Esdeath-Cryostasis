package moe.ramon.cryostasis.modules.misc;

import com.google.gson.JsonObject;
import moe.ramon.cryostasis.discord.DiscordIpc;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.GameType;

/**
 * Shows the client on Discord as a Rich Presence. The activity text reflects the live game
 * state: the game mode as the top line, and where you are playing as the second line.
 *
 * Presence text is only rebuilt when the mode or location actually changes, so the tick path
 * allocates nothing on the common case where neither moved. The IPC client dedupes again and
 * throttles the send, so this can be called every tick without hammering Discord.
 */
public final class DiscordPresenceModule extends Module {
	// Provided by the client owner. The asset keys must match the art uploaded to the app.
	private static final String APPLICATION_ID = "1527711412225577011";
	private static final String LARGE_IMAGE = "esdeath-cryostasis";
	private static final String LARGE_TEXT = "Esdeath: Cryostasis";
	private static final String SMALL_IMAGE = "logo";

	private final DiscordIpc ipc = new DiscordIpc(APPLICATION_ID);

	private long startEpochSeconds;
	private String lastDetails;
	private String lastState;

	public DiscordPresenceModule() {
		super("DiscordRPC", "Shows what you are playing on Discord.", Category.MISC);
	}

	@Override
	public void onEnable() {
		// Elapsed time runs from when presence turns on, matching how other clients read.
		startEpochSeconds = System.currentTimeMillis() / 1000L;
		lastDetails = null;
		lastState = null;
		ipc.start();
	}

	@Override
	public void onDisable() {
		ipc.stop();
	}

	@Override
	public void onTick() {
		String details = describeMode();
		String state = describeLocation();
		if (details.equals(lastDetails) && java.util.Objects.equals(state, lastState)) {
			return;
		}
		lastDetails = details;
		lastState = state;
		ipc.setActivity(buildActivity(details, state));
	}

	private String describeMode() {
		if (mc.level == null) {
			return "In the Main Menu";
		}
		if (mc.level.getLevelData().isHardcore()) {
			return "Experiencing Hardcore Mode";
		}
		GameType mode = mc.gameMode != null ? mc.gameMode.getPlayerMode() : GameType.SURVIVAL;
		if (mode == null) {
			return "Survival Mode";
		}
		return switch (mode) {
			case CREATIVE -> "Roaming around in Creative";
			case ADVENTURE -> "Adventure Mode";
			case SPECTATOR -> "Spectating";
			default -> "Survival Mode";
		};
	}

	private String describeLocation() {
		if (mc.level == null) {
			return null;
		}
		ServerData server = mc.getCurrentServer();
		if (server != null && !mc.hasSingleplayerServer()) {
			return "Playing on " + server.ip;
		}
		return "Playing Singleplayer";
	}

	private String buildActivity(String details, String state) {
		JsonObject activity = new JsonObject();
		activity.addProperty("details", details);
		if (state != null) {
			activity.addProperty("state", state);
		}

		JsonObject assets = new JsonObject();
		assets.addProperty("large_image", LARGE_IMAGE);
		assets.addProperty("large_text", LARGE_TEXT);
		assets.addProperty("small_image", SMALL_IMAGE);
		String user = mc.getUser() != null ? mc.getUser().getName() : null;
		if (user != null) {
			assets.addProperty("small_text", user);
		}
		activity.add("assets", assets);

		JsonObject timestamps = new JsonObject();
		timestamps.addProperty("start", startEpochSeconds);
		activity.add("timestamps", timestamps);

		return activity.toString();
	}
}
