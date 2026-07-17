package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.gui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the vanilla title screen presentation with the Esdeath layout: a navy backdrop,
 * a steel side panel holding the menu buttons, and the client wordmark with a logo above
 * it. The vanilla buttons are reused as-is so Singleplayer, Multiplayer, Realms, Options,
 * Quit, and the language and accessibility icons keep their real behavior; only their
 * positions and the background change.
 *
 * The mixin extends Screen so it can read the inherited width, height, font, and children
 * of the target without reflection. init repositions the buttons after vanilla builds
 * them; render is fully replaced (head cancel) so the panorama, vanilla logo, splash, and
 * version strings never draw, then the widgets are re-rendered over the custom backdrop.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
	// Never invoked: mixins are merged into the target, not instantiated. Present only so
	// this class satisfies the Screen superclass at compile time.
	private TitleScreenMixin() {
		super(null);
	}

	// Full-screen wallpaper; the character logo is baked into the art, so no separate logo
	// is drawn. Supplied by the user under textures/gui/title/background.png.
	private static final ResourceLocation BACKGROUND =
			ResourceLocation.fromNamespaceAndPath("esdeath-cryostasis", "textures/gui/title/background.png");
	private static final Component WORDMARK = Component.literal("ESDEATH: CRYOSTASIS");

	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_GAP = 4;
	private static final int GROUP_GAP = 16;
	private static final int MIN_BUTTON_WIDTH = 74;
	private static final int RIGHT_MARGIN = 12;
	private static final int EDGE_PAD = 8;
	private static final float WORDMARK_SCALE = 2.0F;
	private static final int TOP_MARGIN = 12;

	// Left edge of the wallpaper's angled metal strip as a fraction of screen width, at the
	// top and bottom of the screen. Buttons hug just right of this diagonal so they sit on
	// the metal instead of spilling into the dark. These match the shipped background.png;
	// a different wallpaper needs different values.
	private static final float METAL_TOP_FRAC = 0.83F;
	private static final float METAL_BOTTOM_FRAC = 0.74F;

	// Menu buttons stacked top to bottom in this order; matched by their localized label.
	private static final String[] MAIN_ORDER = {
			"menu.singleplayer", "menu.multiplayer", "menu.online", "menu.options", "menu.quit"
	};

	@Inject(method = "init", at = @At("TAIL"))
	private void esdeath$layout(CallbackInfo ci) {
		List<Button> main = new ArrayList<>();
		List<SpriteIconButton> icons = new ArrayList<>();
		PlainTextButton copyright = null;
		for (GuiEventListener child : this.children()) {
			if (child instanceof SpriteIconButton icon) {
				icons.add(icon);
			} else if (child instanceof PlainTextButton text) {
				copyright = text;
			} else if (child instanceof Button button) {
				main.add(button);
			}
		}

		List<Button> ordered = esdeath$order(main);
		String optionsLabel = Component.translatable("menu.options").getString();

		int rows = ordered.size();
		int totalHeight = rows * BUTTON_HEIGHT + (rows - 1) * BUTTON_GAP + GROUP_GAP;
		int y = Math.max(TOP_MARGIN, (this.height - totalHeight) / 2);
		for (Button button : ordered) {
			// A larger gap before Options separates the play buttons from the utility pair,
			// matching the grouping in the original client.
			if (button.getMessage().getString().equals(optionsLabel)) {
				y += GROUP_GAP;
			}
			esdeath$placeOnMetal(button, y, BUTTON_HEIGHT);
			y += BUTTON_HEIGHT + BUTTON_GAP;
		}

		// Language and accessibility icons sit in a right-aligned row beneath the stack.
		int iconY = y + 4;
		int iconX = this.width - RIGHT_MARGIN;
		for (SpriteIconButton icon : icons) {
			iconX -= icon.getWidth() + 4;
			icon.setX(iconX);
			icon.setY(iconY);
		}

		if (copyright != null) {
			copyright.setX(3);
			copyright.setY(this.height - 12);
		}
	}

	private List<Button> esdeath$order(List<Button> buttons) {
		List<Button> ordered = new ArrayList<>();
		for (String key : MAIN_ORDER) {
			String label = Component.translatable(key).getString();
			for (Button button : buttons) {
				if (button.getMessage().getString().equals(label) && !ordered.contains(button)) {
					ordered.add(button);
				}
			}
		}
		// Anything unrecognized (for example a dev-only test world button) falls to the end.
		for (Button button : buttons) {
			if (!ordered.contains(button)) {
				ordered.add(button);
			}
		}
		return ordered;
	}

	/**
	 * Position and size a button so its left edge sits just right of the wallpaper's angled
	 * metal strip at that row's height, extending to the right margin. The strip is wider
	 * lower down, so lower buttons are wider. Width is floored so the labels always fit.
	 */
	private void esdeath$placeOnMetal(Button button, int rowY, int rowHeight) {
		float centerFrac = (rowY + rowHeight / 2.0F) / this.height;
		float edgeFrac = METAL_TOP_FRAC - (METAL_TOP_FRAC - METAL_BOTTOM_FRAC) * centerFrac;
		int edgeX = Math.round(this.width * edgeFrac) + EDGE_PAD;
		int buttonWidth = this.width - edgeX - RIGHT_MARGIN;
		if (buttonWidth < MIN_BUTTON_WIDTH) {
			buttonWidth = MIN_BUTTON_WIDTH;
		}
		int buttonX = this.width - buttonWidth - RIGHT_MARGIN;
		button.setX(buttonX);
		button.setY(rowY);
		button.setWidth(buttonWidth);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
	private void esdeath$render(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		esdeath$background(graphics);
		esdeath$brand(graphics);
		for (GuiEventListener child : this.children()) {
			if (child instanceof SpriteIconButton icon) {
				// Language and accessibility icons keep their vanilla sprite look.
				icon.render(graphics, mouseX, mouseY, delta);
			} else if (child instanceof PlainTextButton text) {
				text.render(graphics, mouseX, mouseY, delta);
			} else if (child instanceof Button button) {
				esdeath$drawButton(graphics, button, mouseX, mouseY);
			} else if (child instanceof Renderable renderable) {
				renderable.render(graphics, mouseX, mouseY, delta);
			}
		}
		ci.cancel();
	}

	private void esdeath$background(GuiGraphics graphics) {
		// Region equals texture size, so the whole wallpaper scales to fill the window
		// regardless of the source resolution.
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, 0.0F, 0.0F,
				this.width, this.height, 16, 16, 16, 16);
	}

	private void esdeath$brand(GuiGraphics graphics) {
		// Centered horizontally under the logo (which is baked into the wallpaper near the
		// screen center) and dropped below it so the two do not overlap.
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(this.width / 2.0F, this.height * 0.68F);
		pose.scale(WORDMARK_SCALE, WORDMARK_SCALE);
		graphics.drawCenteredString(this.font, WORDMARK, 0, 0, Theme.TEXT);
		pose.popMatrix();
	}

	/**
	 * Draws a menu button in the dark translucent style of the click GUI rows, larger to
	 * suit the title screen. The vanilla Button widget still handles the click and hover
	 * hit test; only its look is replaced here.
	 */
	private void esdeath$drawButton(GuiGraphics graphics, Button button, int mouseX, int mouseY) {
		int bx = button.getX();
		int by = button.getY();
		int bw = button.getWidth();
		int bh = button.getHeight();
		boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;

		graphics.fill(bx, by, bx + bw, by + bh, hovered ? Theme.ROW_HOVER : Theme.ROW);
		if (hovered) {
			graphics.fill(bx, by, bx + 2, by + bh, Theme.ACCENT);
		}
		// Thin steel border around the plate.
		graphics.fill(bx, by, bx + bw, by + 1, Theme.ACCENT_DIM);
		graphics.fill(bx, by + bh - 1, bx + bw, by + bh, Theme.ACCENT_DIM);
		graphics.fill(bx, by, bx + 1, by + bh, Theme.ACCENT_DIM);
		graphics.fill(bx + bw - 1, by, bx + bw, by + bh, Theme.ACCENT_DIM);

		int textColor = button.active ? (hovered ? Theme.TEXT : Theme.SUBTEXT) : 0xFF707070;
		graphics.drawCenteredString(this.font, button.getMessage(), bx + bw / 2, by + (bh - 8) / 2, textColor);
	}
}
