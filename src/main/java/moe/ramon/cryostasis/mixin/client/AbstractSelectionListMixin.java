package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Themes the scrolling lists: world select, server select, the options rows, resource packs,
 * and everything else built on AbstractSelectionList. The list body and its header and footer
 * separators are textures rather than widget sprites, so they are swapped here instead of in
 * WidgetSkin, but the entries themselves are left to draw their own contents untouched.
 *
 * The mixin extends AbstractWidget so it can read the target's inherited geometry (getX, width,
 * getRight) directly. A @Shadow would not do: it only resolves members declared on the target
 * class itself, and these are all inherited.
 */
@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin extends AbstractWidget {
	// Never invoked: mixins are merged into the target, not instantiated. Present only so this
	// class satisfies the AbstractWidget superclass at compile time.
	private AbstractSelectionListMixin() {
		super(0, 0, 0, 0, null);
	}

	/** Height of the separator bars, matching the vanilla textures they replace. */
	private static final int SEPARATOR = 2;

	@Inject(
			method = "renderListBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void esdeath$listBackground(GuiGraphics graphics, CallbackInfo ci) {
		graphics.fill(getX(), getY(), getRight(), getBottom(), Theme.LIST);
		ci.cancel();
	}

	@Inject(
			method = "renderListSeparators(Lnet/minecraft/client/gui/GuiGraphics;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void esdeath$listSeparators(GuiGraphics graphics, CallbackInfo ci) {
		// Same placement as the vanilla separators: the header sits on the list's top edge and
		// the footer just below its bottom edge.
		graphics.fill(getX(), getY(), getRight(), getY() + SEPARATOR, Theme.ACCENT_DIM);
		graphics.fill(getX(), getBottom(), getRight(), getBottom() + SEPARATOR, Theme.ACCENT_DIM);
		ci.cancel();
	}

	/**
	 * Draws the selected entry's outline in the accent color instead of vanilla's white. The
	 * caller signals focus through the outer color it asks for (white when the list has focus,
	 * grey when it does not), so that distinction is carried over rather than dropped.
	 */
	@Inject(
			method = "renderSelection(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V",
			at = @At("HEAD"),
			cancellable = true)
	private void esdeath$selection(GuiGraphics graphics, int top, int entryWidth, int entryHeight,
			int outerColor, int innerColor, CallbackInfo ci) {
		int left = getX() + (this.width - entryWidth) / 2;
		int right = getX() + (this.width + entryWidth) / 2;
		boolean focused = outerColor == Theme.TEXT;
		graphics.fill(left, top - 2, right, top + entryHeight + 2, focused ? Theme.ACCENT : Theme.ACCENT_DIM);
		graphics.fill(left + 1, top - 1, right - 1, top + entryHeight + 1, Theme.CELL);
		ci.cancel();
	}
}
