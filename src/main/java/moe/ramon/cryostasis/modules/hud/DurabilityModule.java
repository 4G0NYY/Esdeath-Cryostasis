package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.HudLine;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import moe.ramon.cryostasis.setting.BooleanSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * How much use is left in the gear a player actually has in play: whatever is in each hand, and
 * each piece of armor being worn.
 *
 * Only gear that can break puts a line on screen. Holding a sword in a chestplate draws those two
 * lines and nothing else, and a player carrying nothing breakable gets no panel at all, so the
 * element stays out of the way until it has something to say. That also takes care of creative
 * mode and unbreakable gear for free, since neither can be damaged and so neither is counted.
 *
 * The number is uses left rather than damage taken. In the middle of a fight what matters is how
 * many more swings a sword has in it, not how far it has already come, and it is the number a
 * player can compare against the fight in front of them without doing arithmetic first.
 */
public final class DurabilityModule extends HudModule {
	/** Both hands and the four armor pieces, the most lines this element can ever draw. */
	private static final int SLOTS = 6;

	/** Fractions of durability remaining at which the readout starts warning. */
	private static final float WARN_AT = 0.25f;
	private static final float CRITICAL_AT = 0.10f;

	private final BooleanSetting hand = register(new BooleanSetting("Hand", true));
	private final BooleanSetting offhand = register(new BooleanSetting("Offhand", true));
	private final BooleanSetting armor = register(new BooleanSetting("Armor", true));
	private final BooleanSetting percent = register(new BooleanSetting("Percent", true));

	public DurabilityModule() {
		super("Durability", "Shows the durability left in your held gear and armor.", 0.01, 0.25);
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (mc.player == null) {
			setBounds(0, 0, 0, 0);
			return;
		}
		List<HudLine> lines = new ArrayList<>(SLOTS);

		// Hands first: they are what a player swaps mid-fight, so they are the lines worth having
		// closest to a fixed position. Armor then reads head down, the way it does on the
		// inventory screen.
		if (hand.get()) {
			append(lines, "Hand", mc.player.getMainHandItem());
		}
		if (offhand.get()) {
			append(lines, "Offhand", mc.player.getOffhandItem());
		}
		if (armor.get()) {
			append(lines, "Head", mc.player.getItemBySlot(EquipmentSlot.HEAD));
			append(lines, "Chest", mc.player.getItemBySlot(EquipmentSlot.CHEST));
			append(lines, "Legs", mc.player.getItemBySlot(EquipmentSlot.LEGS));
			append(lines, "Feet", mc.player.getItemBySlot(EquipmentSlot.FEET));
		}
		if (lines.isEmpty()) {
			setBounds(0, 0, 0, 0);
			return;
		}
		HudText.draw(this, context, lines);
	}

	/** Add one slot's line, or nothing at all when the slot holds nothing that can break. */
	private void append(List<HudLine> lines, String label, ItemStack stack) {
		if (!stack.isDamageableItem()) {
			return;
		}
		int left = stack.getMaxDamage() - stack.getDamageValue();
		float remaining = (float) left / stack.getMaxDamage();

		String value = Integer.toString(left);
		if (percent.get()) {
			value = value + " \u00b7 " + Math.round(remaining * 100.0f) + "%";
		}
		lines.add(new HudLine(label, value, color(remaining)));
	}

	private static int color(float remaining) {
		if (remaining <= CRITICAL_AT) {
			return Theme.BAD;
		}
		return remaining <= WARN_AT ? Theme.WARN : Theme.TEXT;
	}
}
