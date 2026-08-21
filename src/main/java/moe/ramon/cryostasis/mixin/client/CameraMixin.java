package moe.ramon.cryostasis.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.modules.render.FreecamModule;
import moe.ramon.cryostasis.modules.render.FreelookModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aims the camera for Freecam and Freelook, and moves it for Freecam.
 *
 * The rotation is supplied where vanilla asks the entity which way it is looking, rather than by
 * turning the camera afterwards, because everything setup does next is derived from that answer:
 * the view quaternion, the flip for the front view, and the third-person pull-back that orbits
 * the player. Answering early is what swings the third-person camera round behind the direction
 * being looked at instead of leaving it stuck behind the body. Both call sites are hooked because
 * setup reads the rotation once for a passenger and once for everyone else; only one of them runs.
 *
 * The position is replaced at the return instead, so the camera is still handed its level, its
 * entity, and its initialized flag the usual way, and everything downstream (culling, fog, the
 * fluid overlay) sees a consistent camera that simply happens to be somewhere else. It is
 * interpolated across the tick, because this runs once per frame while Freecam only moves the
 * camera once per tick; without it the view would step rather than glide.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @ModifyExpressionValue(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float cryostasis$viewYaw(float original) {
        FreecamModule freecam = cryostasis$module(FreecamModule.class);
        if (freecam != null && freecam.isDetached()) {
            return freecam.cameraYaw();
        }
        FreelookModule freelook = cryostasis$module(FreelookModule.class);
        if (freelook != null && freelook.isLooking()) {
            return freelook.cameraYaw();
        }
        return original;
    }

    @ModifyExpressionValue(method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float cryostasis$viewPitch(float original) {
        FreecamModule freecam = cryostasis$module(FreecamModule.class);
        if (freecam != null && freecam.isDetached()) {
            return freecam.cameraPitch();
        }
        FreelookModule freelook = cryostasis$module(FreelookModule.class);
        if (freelook != null && freelook.isLooking()) {
            return freelook.cameraPitch();
        }
        return original;
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void cryostasis$freecamPosition(BlockGetter level, Entity entity, boolean thirdPerson,
            boolean mirrored, float partialTick, CallbackInfo ci) {
        // Only the view the player is actually looking through, not any other camera a render
        // pass might set up.
        if (entity != Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        FreecamModule freecam = cryostasis$module(FreecamModule.class);
        if (freecam == null || !freecam.isDetached()) {
            return;
        }
        setPosition(freecam.cameraX(partialTick), freecam.cameraY(partialTick), freecam.cameraZ(partialTick));
    }

    private static <T extends Module> T cryostasis$module(Class<T> type) {
        Cryostasis cryostasis = Cryostasis.get();
        return cryostasis == null ? null : cryostasis.getModuleManager().get(type);
    }
}
