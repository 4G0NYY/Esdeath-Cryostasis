package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.render.CleanChatModule;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.components.ChatComponent;

/**
 * Implements CleanChat by dropping a message whose text matches the one added just before it.
 * Every displayed line funnels through this addMessage overload (the single-argument form
 * delegates to it), so one hook covers system and player chat alike. The last text is tracked
 * only when the line is actually kept, so a run of identical spam collapses to a single line
 * while a message that merely repeats an earlier, non-adjacent one still shows.
 */
@Mixin(ChatComponent.class)
public class ChatComponentMixin {
	@Unique
	private String cryostasis$lastMessage;

	@Inject(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void cryostasis$cleanChat(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		CleanChatModule cleanChat = cryostasis.getModuleManager().get(CleanChatModule.class);
		String text = message.getString();
		if (cleanChat != null && cleanChat.isEnabled() && text.equals(cryostasis$lastMessage)) {
			ci.cancel();
			return;
		}
		cryostasis$lastMessage = text;
	}
}
