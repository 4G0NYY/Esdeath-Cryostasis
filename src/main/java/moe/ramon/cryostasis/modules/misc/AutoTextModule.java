package moe.ramon.cryostasis.modules.misc;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.KeybindSetting;
import moe.ramon.cryostasis.setting.StringSetting;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.lwjgl.glfw.GLFW;

/**
 * Sends a preset chat message (or command) when its bound key is tapped. Polls the key on
 * the tick thread with edge detection so one tap sends exactly one message. The key is a
 * module setting rather than a module toggle key, so it does not pass through the normal
 * toggle routing.
 */
public final class AutoTextModule extends Module {
	private final KeybindSetting key = keybindSetting("Key", GLFW.GLFW_KEY_UNKNOWN);
	private final StringSetting message = register(new StringSetting("Message", "gg"));

	private boolean wasDown;

	public AutoTextModule() {
		super("AutoText", "Sends a preset message when its key is pressed.", Category.MISC);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.screen != null || !key.isBound()) {
			wasDown = false;
			return;
		}
		long window = mc.getWindow().getWindow();
		boolean down = GLFW.glfwGetKey(window, key.get()) == GLFW.GLFW_PRESS;
		if (down && !wasDown) {
			send(message.get());
		}
		wasDown = down;
	}

	private void send(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) {
			return;
		}
		if (text.startsWith("/")) {
			connection.sendCommand(text.substring(1));
		} else {
			connection.sendChat(text);
		}
	}
}
