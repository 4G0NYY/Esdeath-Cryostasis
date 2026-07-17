package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.modules.misc.TakeAllModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Draws a "Take All" button on chest-like containers and empties the container into the player
 * inventory when it is clicked. Kept out of the theming Mixin on the same class so the two
 * concerns stay separate: this one owns a widget and a click, that one owns the panel skin.
 *
 * The button is drawn by hand (a filled rect plus centered label) and hit-tested in
 * mouseClicked, matching the click-GUI's own custom-widget style, which avoids the generics
 * dance of registering a real Screen widget from a Mixin.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class TakeAllMixin {
	@Shadow
	protected int leftPos;
	@Shadow
	protected int topPos;
	@Shadow
	protected int imageWidth;
	@Shadow
	@SuppressWarnings("rawtypes")
	protected AbstractContainerMenu menu;

	private static final int BUTTON_HEIGHT = 12;
	private static final int PADDING = 4;
	private static final String LABEL = "Take All";

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
	private void esdeath$drawTakeAll(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!shouldShow()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int width = font.width(LABEL) + PADDING * 2;
		int x = leftPos + imageWidth - width;
		int y = topPos - BUTTON_HEIGHT - 2;
		boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + BUTTON_HEIGHT;

		graphics.fill(x, y, x + width, y + BUTTON_HEIGHT, hovered ? Theme.ROW_HOVER : Theme.HEADER);
		graphics.fill(x, y + BUTTON_HEIGHT - 1, x + width, y + BUTTON_HEIGHT, Theme.ACCENT);
		graphics.drawString(font, LABEL, x + PADDING, y + 2, Theme.TEXT, false);
	}

	@Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
	private void esdeath$clickTakeAll(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (button != 0 || !shouldShow()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int width = font.width(LABEL) + PADDING * 2;
		int x = leftPos + imageWidth - width;
		int y = topPos - BUTTON_HEIGHT - 2;
		if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + BUTTON_HEIGHT) {
			return;
		}
		takeAll();
		cir.setReturnValue(true);
	}

	private void takeAll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gameMode == null) {
			return;
		}
		// Shift-click every non-player slot that holds something. Quick-move sends the item to
		// the player inventory; player slots are left alone so nothing gets pushed back out.
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory || !slot.hasItem()) {
				continue;
			}
			mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
		}
	}

	private boolean shouldShow() {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return false;
		}
		TakeAllModule module = cryostasis.getModuleManager().get(TakeAllModule.class);
		return module != null && module.isEnabled() && isChestLike();
	}

	private boolean isChestLike() {
		return menu instanceof ChestMenu
				|| menu instanceof ShulkerBoxMenu
				|| menu instanceof HopperMenu
				|| menu instanceof DispenserMenu;
	}
}
