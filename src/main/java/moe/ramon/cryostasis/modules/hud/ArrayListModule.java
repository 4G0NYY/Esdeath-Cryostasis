package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.gui.Skin;
import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.Easing;
import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The classic HUD list of active modules, sorted by width so the longest entry sits at the top and
 * the block forms a staircase. Each row is a strip of panel with an accent edge, and the staircase
 * faces whichever screen edge the list is nearer, so a list dragged to the left reads left to right
 * instead of hanging off an edge it is nowhere near.
 *
 * Rows slide in when a module is switched on and back out when it is switched off, and the rows
 * below close up as one leaves rather than jumping. A module stays in {@link #shown} while it is on
 * its way out, which is the only reason that map holds anything that is not enabled.
 */
public final class ArrayListModule extends HudModule {
	private static final int PAD = 3;
	private static final int STRIPE = 2;
	private static final float EASE_RATE = 14.0f;
	private static final float VISIBLE = 0.02f;

	private final BooleanSetting background = register(new BooleanSetting("Background", true));

	private final Map<Module, Float> shown = new HashMap<>();
	private final List<Module> sorted = new ArrayList<>();
	private final Comparator<Module> byWidthDesc =
			Comparator.comparingInt((Module m) -> rowWidth(mc.font, m)).reversed();
	private final Easing easing = new Easing();

	public ArrayListModule() {
		super("ArrayList", "Lists all active modules.", 1.0, 0.0);
	}

	@Override
	public boolean isAutoStacked() {
		// The ArrayList owns the top-right corner and lays itself out, so it is not part of
		// the left auto-stack column.
		return false;
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		Font font = mc.font;
		advance(easing.step(EASE_RATE));

		sorted.clear();
		sorted.addAll(shown.keySet());
		if (sorted.isEmpty()) {
			setBounds(context.guiWidth(), 0, 0, 0);
			return;
		}
		sorted.sort(byWidthDesc);

		int rowHeight = font.lineHeight + PAD;
		int width = rowWidth(font, sorted.get(0));
		float totalHeight = 0.0f;
		for (Module module : sorted) {
			totalHeight += shown.get(module) * rowHeight;
		}
		int height = Math.round(totalHeight);
		int x = resolveX(context.guiWidth(), width);
		int y = resolveY(context.guiHeight(), height);
		boolean fromRight = getAnchorX() >= 0.5;

		float rowY = y;
		for (int i = 0; i < sorted.size(); i++) {
			Module module = sorted.get(i);
			float progress = shown.get(module);
			int rowWidth = rowWidth(font, module);
			// Slides out towards the edge the staircase faces, which is the edge it came in from.
			int slide = Math.round((1.0f - progress) * rowWidth);
			int rowX = fromRight ? x + width - rowWidth + slide : x - slide;
			drawRow(context, font, module, rowX, Math.round(rowY), rowWidth, rowHeight, fromRight, progress, i);
			rowY += progress * rowHeight;
		}
		setBounds(x, y, width, height);
	}

	/** Move every module's slide towards whether it is on, and forget the ones fully gone. */
	private void advance(float step) {
		for (Module module : Cryostasis.get().getModuleManager().getModules()) {
			// Not listing this HUD itself matches the original's "visible" gate.
			if (module == this) {
				continue;
			}
			float target = module.isEnabled() ? 1.0f : 0.0f;
			float current = shown.getOrDefault(module, 0.0f);
			float next = current + (target - current) * step;
			if (!module.isEnabled() && next < VISIBLE) {
				shown.remove(module);
			} else {
				shown.put(module, next);
			}
		}
	}

	private void drawRow(GuiGraphics context, Font font, Module module, int rowX, int rowY,
			int rowWidth, int rowHeight, boolean fromRight, float alpha, int index) {
		boolean plate = background.get();
		if (plate) {
			context.fill(rowX, rowY, rowX + rowWidth, rowY + rowHeight, Skin.fade(Theme.PANEL, alpha));
			int stripeX = fromRight ? rowX + rowWidth - STRIPE : rowX;
			context.fill(stripeX, rowY, stripeX + STRIPE, rowY + rowHeight,
					Skin.fade(HudColors.accent(index * 0.08f), alpha));
		}
		int textX = fromRight ? rowX + PAD : rowX + STRIPE + PAD;
		int textY = rowY + (rowHeight - font.lineHeight) / 2 + 1;
		int nameColor = HudColors.isRainbow() ? HudColors.rainbow(index * 0.08f) : Theme.TEXT;
		// With no plate behind it the text sits straight on the world, and only a shadow keeps it
		// legible over snow or sky.
		context.drawString(font, module.getName(), textX, textY, Skin.fade(nameColor, alpha), !plate);
		String suffix = module.getHudSuffix();
		if (!suffix.isEmpty()) {
			int suffixX = textX + font.width(module.getName()) + font.width(" ");
			context.drawString(font, suffix, suffixX, textY, Skin.fade(Theme.SUBTEXT, alpha), !plate);
		}
	}

	private static int rowWidth(Font font, Module module) {
		int text = font.width(module.getName());
		String suffix = module.getHudSuffix();
		if (!suffix.isEmpty()) {
			text += font.width(" ") + font.width(suffix);
		}
		return STRIPE + PAD + text + PAD;
	}
}
