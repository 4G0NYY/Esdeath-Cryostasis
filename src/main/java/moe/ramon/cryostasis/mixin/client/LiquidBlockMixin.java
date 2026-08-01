package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.movement.JesusModule;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements Jesus by handing the local player a solid top on a liquid block. Vanilla already
 * has this exact case: a liquid normally collides with nothing, except that it returns a
 * stable top for an entity that can stand on that fluid and is above it, which is how a strider
 * walks on lava. This widens that answer to the local player, so walking on water is real
 * collision and the player keeps ordinary ground movement rather than being repositioned.
 *
 * {@code getCollisionShape} runs for every entity's collision query, so the hook guards itself
 * to the client's own player first. The guard is by UUID rather than instance identity so that
 * in singleplayer it catches both the client LocalPlayer (the movement the player feels) and
 * the integrated server's ServerPlayer (the authority that would otherwise drop them into the
 * water and rubber-band them), matching the EntityMixin approach.
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
	@Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
	private void cryostasis$jesus(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		JesusModule jesus = cryostasis.getModuleManager().get(JesusModule.class);
		if (jesus == null || !jesus.isEnabled()) {
			return;
		}
		// Lava is opt in: falling in costs a life, so it is not something to switch on by
		// accident along with water.
		if (!state.is(Blocks.WATER) && !(state.is(Blocks.LAVA) && jesus.walksOnLava())) {
			return;
		}
		// A context with no entity behind it (block placement previews, pathfinding) is not a
		// player standing on anything, so leave those answering as vanilla does.
		if (!(context instanceof EntityCollisionContext entityContext)) {
			return;
		}
		Entity entity = entityContext.getEntity();
		Player local = Minecraft.getInstance().player;
		if (entity == null || local == null || !entity.getUUID().equals(local.getUUID())) {
			return;
		}
		// Sneak to sink, and stay out of the way of creative flight and spectator, where the
		// player is not walking on anything to begin with.
		if (entity.isShiftKeyDown() || entity.isSpectator()) {
			return;
		}
		if (entity instanceof Player player && player.getAbilities().flying) {
			return;
		}
		VoxelShape surface = JesusModule.surfaceShape(state.getFluidState());
		if (surface == null) {
			return;
		}
		// Solid from above only, the same test vanilla applies to its own stable top: inside the
		// liquid there is still no collision, so swimming, diving, and climbing back out work.
		if (context.isAbove(surface, pos, true)) {
			cir.setReturnValue(surface);
		}
	}
}
