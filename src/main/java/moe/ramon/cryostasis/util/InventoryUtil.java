package moe.ramon.cryostasis.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared hotbar and item-ranking helpers for the automation modules (AutoTool, AutoEquip,
 * AutoTotem, AutoPath). Kept in one place so the scoring stays identical across features and
 * so the server-sync detail of a slot change lives in exactly one spot.
 *
 * Item behavior classes (SwordItem, ArmorItem, DiggerItem) were folded into data
 * components in 1.21.2, so ranking reads the item's attribute modifiers and destroy speed
 * rather than testing instanceof on classes that no longer exist.
 */
public final class InventoryUtil {
	private InventoryUtil() {
	}

	/**
	 * Select a hotbar slot and tell the server, the way pressing a number key would. Setting
	 * the slot without the packet would leave the server holding the old item, so a
	 * programmatic swap has to send it explicitly.
	 */
	public static void selectHotbarSlot(Minecraft mc, int slot) {
		if (mc.player == null || slot < 0 || slot > 8) {
			return;
		}
		Inventory inv = mc.player.getInventory();
		if (inv.getSelectedSlot() == slot) {
			return;
		}
		inv.setSelectedSlot(slot);
		mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
	}

	/**
	 * Hotbar slot of the fastest tool that mines the given block, or -1 when nothing in the
	 * hotbar beats a bare fist. A tool that actually drops the block always outranks a faster
	 * but incorrect one, so stone is broken with a pickaxe rather than a faster shovel.
	 */
	public static int bestToolSlot(Minecraft mc, BlockState state) {
		Inventory inv = mc.player.getInventory();
		int best = -1;
		double bestScore = 0.0;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			float speed = s.getDestroySpeed(state);
			boolean correct = s.isCorrectToolForDrops(state);
			if (speed <= 1.0f && !correct) {
				// No better than punching it, so not worth a swap.
				continue;
			}
			double score = (correct ? 1_000_000.0 : 0.0) + speed;
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/**
	 * Hotbar slot of the best melee weapon, or -1 when nothing in the hotbar beats a bare fist.
	 *
	 * Weapons are ranked by what they land over time rather than by the damage on the tooltip. An
	 * axe hits harder per swing but recovers at roughly half a sword's rate, so a sword beats the
	 * axe of its own material, gold aside. An axe far enough ahead in material still wins outright:
	 * a netherite axe beats a stone sword, and an iron sword beats a diamond axe.
	 *
	 * The player's own base damage and swing rate are read off the player rather than written down
	 * here, so a server that has changed either still gets a ranking that matches what its players
	 * feel. Base rather than current, because the current value already carries the modifiers of
	 * whatever is held right now and would count that item twice.
	 */
	public static int bestWeaponSlot(Minecraft mc) {
		Inventory inv = mc.player.getInventory();
		double baseDamage = mc.player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
		double baseSpeed = mc.player.getAttributeBaseValue(Attributes.ATTACK_SPEED);

		int best = -1;
		double bestScore = baseDamage * baseSpeed;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) {
				continue;
			}
			double damage = baseDamage + attackDamage(s);
			double speed = Math.max(0.0, baseSpeed + attackSpeed(s));
			double score = damage * speed;
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		return best;
	}

	/** Hotbar slot of the first placeable block item, or -1 when the hotbar has none. */
	public static int firstBlockSlot(Minecraft mc) {
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.getItem() instanceof BlockItem) {
				return i;
			}
		}
		return -1;
	}

	/** Sum of the item's attack-damage attribute modifiers; 0 for anything without them. */
	public static double attackDamage(ItemStack s) {
		return modifierSum(s, Attributes.ATTACK_DAMAGE);
	}

	/**
	 * Sum of the item's attack-speed attribute modifiers, which are negative for every weapon: a
	 * weapon takes swings away from the player's base rate rather than granting its own.
	 */
	public static double attackSpeed(ItemStack s) {
		return modifierSum(s, Attributes.ATTACK_SPEED);
	}

	private static double modifierSum(ItemStack s, Holder<Attribute> attribute) {
		ItemAttributeModifiers mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (mods == null) {
			return 0.0;
		}
		double total = 0.0;
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().value() == attribute.value()) {
				total += e.modifier().amount();
			}
		}
		return total;
	}

	/**
	 * Defensive rating used to compare armor pieces. Armor points dominate; toughness breaks
	 * ties. Returns -1 for an empty stack so any real piece outranks an empty slot.
	 */
	public static double armorRating(ItemStack s) {
		if (s.isEmpty()) {
			return -1.0;
		}
		ItemAttributeModifiers mods = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
		if (mods == null) {
			return 0.0;
		}
		double armor = 0.0;
		double toughness = 0.0;
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().value() == Attributes.ARMOR.value()) {
				armor += e.modifier().amount();
			} else if (e.attribute().value() == Attributes.ARMOR_TOUGHNESS.value()) {
				toughness += e.modifier().amount();
			}
		}
		return armor * 10.0 + toughness;
	}

	/**
	 * Map a raw {@link Inventory} index to its slot index in the player inventory menu, the
	 * numbering {@code handleInventoryMouseClick} expects. The main inventory (9..35) lines up
	 * one to one; the hotbar (0..8) sits at the end of the menu instead, after the armor and
	 * crafting slots.
	 */
	public static int inventoryToMenuSlot(int inventoryIndex) {
		if (inventoryIndex >= Inventory.SELECTION_SIZE) {
			return inventoryIndex;
		}
		return InventoryMenu.USE_ROW_SLOT_START + inventoryIndex;
	}
}
