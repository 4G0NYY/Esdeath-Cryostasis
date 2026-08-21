package moe.ramon.cryostasis.modules.player;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.util.InventoryUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Keeps a Totem of Undying in the offhand whenever one is carried anywhere in the inventory.
 * The totem is moved through the player inventory menu with the same clicks a manual swap
 * sends, so the server sees an ordinary slot change and the totem really is held when the
 * killing blow lands.
 *
 * Like AutoEquip it only acts with no screen open and never in creative, so it never fights an
 * open container or the creative item grid, and it waits a few ticks between actions rather
 * than clicking every tick. Whatever was in the offhand is not lost: it goes back into the slot
 * the totem came from, so a shield ends up where the totem was.
 */
public final class AutoTotemModule extends Module {
	private static final int ACTION_INTERVAL_TICKS = 4;

	private int cooldown;

	public AutoTotemModule() {
		super("AutoTotem", "Keeps a Totem of Undying in your offhand.", Category.PLAYER);
	}

	@Override
	public void onDisable() {
		cooldown = 0;
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

		ItemStack offhand = mc.player.getOffhandItem();
		if (offhand.is(Items.TOTEM_OF_UNDYING)) {
			return;
		}
		int source = findTotem();
		if (source < 0) {
			return;
		}
		equipOffhand(source, offhand.isEmpty());
		// Then wait, so this reads as a deliberate swap rather than a burst of clicks.
		cooldown = ACTION_INTERVAL_TICKS;
	}

	/** Inventory index of the first totem carried, or -1 when there is none. */
	private int findTotem() {
		Inventory inventory = mc.player.getInventory();
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Move the totem at the given inventory index into the offhand. A quick move is no help
	 * here, because vanilla never routes a shift-click to the offhand, so the swap is done by
	 * hand: pick the totem up, drop it in the offhand slot, and put whatever came back out into
	 * the slot the totem vacated.
	 */
	private void equipOffhand(int inventoryIndex, boolean offhandEmpty) {
		int containerId = mc.player.inventoryMenu.containerId;
		int totemMenuSlot = InventoryUtil.inventoryToMenuSlot(inventoryIndex);

		mc.gameMode.handleInventoryMouseClick(containerId, totemMenuSlot, 0, ClickType.PICKUP, mc.player);
		mc.gameMode.handleInventoryMouseClick(containerId, InventoryMenu.SHIELD_SLOT, 0, ClickType.PICKUP, mc.player);
		if (!offhandEmpty) {
			// That second click left the displaced offhand item on the cursor.
			mc.gameMode.handleInventoryMouseClick(containerId, totemMenuSlot, 0, ClickType.PICKUP, mc.player);
		}
	}
}
