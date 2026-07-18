package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.render.XrayModule;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives Xray from the one static face-culling decision every block goes through. Vanilla's
 * {@code shouldRenderFace} decides whether a block face is drawn given its neighbour; forcing
 * true for selected blocks draws them even against solid neighbours (so ores show through
 * walls), and forcing false for everything else stops it drawing at all. One hook covers the
 * whole world with no per-block special cases.
 */
@Mixin(Block.class)
public abstract class BlockMixin {
	@Inject(method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
			at = @At("HEAD"), cancellable = true)
	private static void cryostasis$xray(BlockState state, BlockState neighbor, Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		XrayModule xray = cryostasis.getModuleManager().get(XrayModule.class);
		if (xray != null && xray.isEnabled()) {
			cir.setReturnValue(xray.isVisible(state.getBlock()));
		}
	}
}
