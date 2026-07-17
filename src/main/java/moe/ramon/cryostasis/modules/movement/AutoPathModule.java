package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.util.InventoryUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places a block into the space the player is about to step onto, so walking forward bridges a
 * gap automatically. It aims one step ahead of the feet using the current velocity, which is
 * what lets it cooperate with SafeWalk: the block appears before the player arrives, so the
 * edge is already filled and SafeWalk has nothing to stop. When there is no block to place or
 * no surface to place against, SafeWalk remains the fall-back.
 *
 * Placement targets a solid neighbor of the empty support position and clicks its face, the
 * same interaction a manual place performs, so the server accepts it normally.
 */
public final class AutoPathModule extends Module {
	private final BooleanSetting tower = register(new BooleanSetting("Tower", false));
	private final BooleanSetting swapBack = register(new BooleanSetting("Swap Back", true));

	public AutoPathModule() {
		super("AutoPath", "Places blocks under you as you walk so you never fall.", Category.MOVEMENT);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null || mc.gameMode == null) {
			return;
		}

		BlockPos target = supportTarget();
		BlockState existing = mc.level.getBlockState(target);
		if (!existing.canBeReplaced()) {
			// Already something solid to stand on here; nothing to bridge.
			return;
		}

		int blockSlot = InventoryUtil.firstBlockSlot(mc);
		if (blockSlot < 0) {
			// No blocks to place: SafeWalk (if on) keeps the player from falling.
			return;
		}

		BlockHitResult hit = placementAgainstNeighbor(target);
		if (hit == null) {
			return;
		}

		int previous = mc.player.getInventory().getSelectedSlot();
		InventoryUtil.selectHotbarSlot(mc, blockSlot);
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
		mc.player.swing(InteractionHand.MAIN_HAND);
		if (swapBack.get()) {
			InventoryUtil.selectHotbarSlot(mc, previous);
		}
	}

	/** The block position that should be solid for the next step (or straight down when towering). */
	private BlockPos supportTarget() {
		Vec3 pos = mc.player.position();
		if (tower.get() && mc.options.keyJump.isDown()) {
			// Building straight up: support the block directly beneath the feet.
			return BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
		}
		Vec3 velocity = mc.player.getDeltaMovement();
		return BlockPos.containing(pos.x + velocity.x, pos.y - 0.5, pos.z + velocity.z);
	}

	/**
	 * Build a hit result against a solid face adjacent to the empty support position, or null
	 * when the position is fully surrounded by air and cannot be placed against.
	 */
	private BlockHitResult placementAgainstNeighbor(BlockPos target) {
		for (Direction direction : Direction.values()) {
			BlockPos neighbor = target.relative(direction);
			BlockState neighborState = mc.level.getBlockState(neighbor);
			if (neighborState.isAir() || neighborState.canBeReplaced()) {
				continue;
			}
			// Click the face of the neighbor that points back toward the empty slot.
			Direction face = direction.getOpposite();
			Vec3 center = Vec3.atCenterOf(neighbor);
			Vec3 hitPoint = center.add(
					face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			return new BlockHitResult(hitPoint, face, neighbor, false);
		}
		return null;
	}
}
