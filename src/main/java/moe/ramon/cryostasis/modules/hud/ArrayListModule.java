package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.hud.HudColors;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The classic HUD list of active modules, drawn width-sorted and right-aligned so the
 * longest entry sits at the top and the block forms a clean left-facing staircase.
 *
 * The sorted view is rebuilt each frame into a reused list to avoid resorting the shared
 * module registry, but the per frame allocation is bounded by the module count and only
 * happens while this HUD is enabled.
 */
public final class ArrayListModule extends HudModule {
	private static final int PAD = 2;

	private final BooleanSetting background = register(new BooleanSetting("Background", true));

	private final List<Module> sorted = new ArrayList<>();
	private final Comparator<Module> byWidthDesc =
			Comparator.comparingInt((Module m) -> mc.font.width(m.getHudLabel())).reversed();

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

		sorted.clear();
		for (Module module : Cryostasis.get().getModuleManager().getModules()) {
			// Do not list this HUD itself, matching the original's "visible" gate.
			if (module.isEnabled() && module != this) {
				sorted.add(module);
			}
		}
		if (sorted.isEmpty()) {
			setBounds(context.guiWidth(), 0, 0, 0);
			return;
		}
		sorted.sort(byWidthDesc);

		int lineHeight = font.lineHeight + 1;
		int widest = 0;
		for (int i = 0; i < sorted.size(); i++) {
			widest = Math.max(widest, font.width(sorted.get(i).getHudLabel()));
		}
		int width = widest + PAD * 2;
		int height = lineHeight * sorted.size() + PAD;
		int x = resolveX(context.guiWidth(), width);
		int y = resolveY(context.guiHeight(), height);

		int rowY = y + PAD;
		for (int i = 0; i < sorted.size(); i++) {
			String label = sorted.get(i).getHudLabel();
			// Right aligned inside the block, which is what gives the list its staircase edge
			// however the block itself has been dragged.
			int labelX = x + width - PAD - font.width(label);
			if (background.get()) {
				context.fill(labelX - PAD, rowY - 1, x + width, rowY + font.lineHeight, 0x90000000);
			}
			int color = HudColors.isRainbow() ? HudColors.rainbow(i * 0.08f) : 0xFFFFFFFF;
			context.drawString(font, label, labelX, rowY, color);
			rowY += lineHeight;
		}
		setBounds(x, y, width, height);
	}
}
