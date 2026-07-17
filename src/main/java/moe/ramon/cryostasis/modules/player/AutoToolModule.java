package moe.ramon.cryostasis.modules.player;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.util.InventoryUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Swaps the hotbar to the best tool for whatever the player is breaking, and (through the
 * attack Mixin) to the best weapon when the player hits an entity. Mining detection reads the
 * crosshair block while the attack key is held; the weapon swap lives in
 * {@code MultiPlayerGameModeMixin} so it runs before the attack resolves.
 *
 * Creative is skipped for mining because blocks break instantly there and the swap would just
 * flicker the hotbar for no benefit.
 */
public final class AutoToolModule extends Module {
	private final BooleanSetting weaponOnAttack = register(new BooleanSetting("Weapon On Attack", true));

	public AutoToolModule() {
		super("AutoTool", "Swaps to the best tool while mining and the best weapon on hit.", Category.PLAYER);
	}

	public boolean swapsWeaponOnAttack() {
		return weaponOnAttack.get();
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE) {
			return;
		}
		if (!mc.options.keyAttack.isDown()) {
			return;
		}
		if (!(mc.hitResult instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockState state = mc.level.getBlockState(block.getBlockPos());
		if (state.isAir()) {
			return;
		}
		int best = InventoryUtil.bestToolSlot(mc, state);
		if (best >= 0) {
			InventoryUtil.selectHotbarSlot(mc, best);
		}
	}
}
