package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.misc.GlobalChatModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes a prefixed chat line to Cryostasis global chat instead of to the game server.
 *
 * This is the hook the whole feature hangs on: cancelling here, before vanilla hands the string
 * to the connection, is what guarantees a message meant for the Cryostasis channel is never seen
 * by whatever server the player happens to be on. Anything less (reacting after the send, or
 * filtering the outgoing packet) would leak the first message.
 *
 * The line is still added to the sent-message history by hand, because cancelling skips the
 * vanilla call that would have done it, and a player pressing up-arrow expects their own message
 * back regardless of where it went.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void cryostasis$globalChat(String message, boolean addToHistory, CallbackInfo ci) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		GlobalChatModule module = cryostasis.getModuleManager().get(GlobalChatModule.class);
		if (module == null || !module.isEnabled()) {
			return;
		}
		String prefix = module.prefix();
		if (!message.startsWith(prefix)) {
			return;
		}

		String body = message.substring(prefix.length());
		if (addToHistory) {
			Minecraft.getInstance().gui.getChat().addRecentChat(message);
		}
		cryostasis.getChatService().send(body);
		ci.cancel();
	}
}
