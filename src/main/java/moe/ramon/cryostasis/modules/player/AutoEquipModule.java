package moe.ramon.cryostasis.modules.player;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.util.InventoryUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Keeps the player wearing the strongest armor it can find in the inventory. Each armor slot
 * is checked in turn; if a better piece is sitting in the inventory it is moved in through the
 * player inventory menu, exactly as a manual shift-click or slot swap would, so the server
 * stays in sync.
 *
 * It only acts with no screen open and never in creative, so it never fights an open container
 * or the creative item grid. Work is throttled and limited to one slot per pass to keep the
 * click traffic gentle and human-plausible.
 */
public final class AutoEquipModule extends Module {
	// Player inventory menu slot indices are fixed: 5..8 are head, chest, legs, feet.
	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};
	private static final int ARMOR_MENU_START = 5;
	private static final int ACTION_INTERVAL_TICKS = 4;

	private int cooldown;

	public AutoEquipModule() {
		super("AutoEquip", "Automatically wears the best armor you are carrying.", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.screen != null) {
			return;
		}
		if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		for (int armorIndex = 0; armorIndex < ARMOR_SLOTS.length; armorIndex++) {
			EquipmentSlot slot = ARMOR_SLOTS[armorIndex];
			ItemStack worn = mc.player.getItemBySlot(slot);
			double wornRating = InventoryUtil.armorRating(worn);

			int candidate = findBetterPiece(slot, wornRating);
			if (candidate >= 0) {
				equip(candidate, ARMOR_MENU_START + armorIndex, worn.isEmpty());
				// One action per pass, then wait, so this reads as deliberate rather than instant.
				cooldown = ACTION_INTERVAL_TICKS;
				return;
			}
		}
	}

	/** Inventory index of the strongest piece that fits this slot and beats what is worn. */
	private int findBetterPiece(EquipmentSlot slot, double wornRating) {
		int best = -1;
		double bestRating = wornRating;
		for (int i = 0; i < 36; i++) {
			ItemStack s = mc.player.getInventory().getItem(i);
			if (s.isEmpty() || mc.player.getEquipmentSlotForItem(s) != slot) {
				continue;
			}
			double rating = InventoryUtil.armorRating(s);
			if (rating > bestRating) {
				bestRating = rating;
				best = i;
			}
		}
		return best;
	}

	private void equip(int inventoryIndex, int armorMenuSlot, boolean armorSlotEmpty) {
		int containerId = mc.player.inventoryMenu.containerId;
		int candidateMenuSlot = inventoryToMenuSlot(inventoryIndex);

		if (armorSlotEmpty) {
			// Shift-click routes an armor piece straight to its empty slot.
			mc.gameMode.handleInventoryMouseClick(containerId, candidateMenuSlot, 0, ClickType.QUICK_MOVE, mc.player);
			return;
		}
		// Swap: grab the new piece, drop it into the armor slot (picking up the old one), then
		// put the old piece back where the new one came from.
		mc.gameMode.handleInventoryMouseClick(containerId, candidateMenuSlot, 0, ClickType.PICKUP, mc.player);
		mc.gameMode.handleInventoryMouseClick(containerId, armorMenuSlot, 0, ClickType.PICKUP, mc.player);
		mc.gameMode.handleInventoryMouseClick(containerId, candidateMenuSlot, 0, ClickType.PICKUP, mc.player);
	}

	/**
	 * Map a raw {@link net.minecraft.world.entity.player.Inventory} index to its slot index in
	 * the player inventory menu. Main inventory (9..35) lines up one to one; the hotbar (0..8)
	 * sits at menu slots 36..44.
	 */
	private int inventoryToMenuSlot(int inventoryIndex) {
		if (inventoryIndex >= 9) {
			return inventoryIndex;
		}
		return 36 + inventoryIndex;
	}
}
