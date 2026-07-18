package moe.ramon.cryostasis.mixin.client;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.modules.movement.SpiderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements Spider by reporting the local player as on a climbable surface while it is pressed
 * against a wall. Vanilla drives all ladder physics off {@code onClimbable}, so returning true
 * there lets the player climb any wall with the same controls as a ladder, with no separate
 * movement code. {@code onClimbable} is declared on LivingEntity and runs for every living
 * entity, so this is guarded to the client's own player only.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void cryostasis$spider(CallbackInfoReturnable<Boolean> cir) {
		Cryostasis cryostasis = Cryostasis.get();
		if (cryostasis == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if ((Object) this != mc.player || mc.player == null) {
			return;
		}
		SpiderModule spider = cryostasis.getModuleManager().get(SpiderModule.class);
		// Only grab the wall while actually touching one, so the player still falls in open air.
		if (spider != null && spider.isEnabled() && mc.player.horizontalCollision) {
			cir.setReturnValue(true);
		}
	}
}
