package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudLine;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shows the distance to the entity you last swung at, held on screen briefly after the
 * swing so the number is readable. Measured from the eye to the nearest point of the
 * target's bounding box, which is the distance that actually governs whether a hit lands.
 *
 * The readout fades over its last few ticks rather than vanishing, so it reads as the swing's
 * after-image instead of a panel that blinks off.
 */
public final class ReachDisplayModule extends HudModule {
	private static final int HOLD_TICKS = 40;
	private static final float FADE_TICKS = 10.0f;

	private double lastReach;
	private int holdTicks;

	public ReachDisplayModule() {
		super("ReachDisplay", "Shows your reach when attacking.", 0.01, 0.17);
	}

	@Override
	public void onTick() {
		if (mc.player == null) {
			if (holdTicks > 0) {
				holdTicks--;
			}
			return;
		}
		Entity target = mc.crosshairPickEntity;
		if (mc.options.keyAttack.isDown() && target != null) {
			Vec3 eye = mc.player.getEyePosition();
			// Nearest point of the target box to the eye, clamped per axis.
			double nx = clamp(eye.x, target.getBoundingBox().minX, target.getBoundingBox().maxX);
			double ny = clamp(eye.y, target.getBoundingBox().minY, target.getBoundingBox().maxY);
			double nz = clamp(eye.z, target.getBoundingBox().minZ, target.getBoundingBox().maxZ);
			lastReach = eye.distanceTo(new Vec3(nx, ny, nz));
			holdTicks = HOLD_TICKS;
		} else if (holdTicks > 0) {
			holdTicks--;
		}
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (holdTicks <= 0) {
			setBounds(0, 0, 0, 0);
			return;
		}
		float alpha = Math.min(1.0f, (holdTicks - tickDelta) / FADE_TICKS);
		HudText.draw(this, context, List.of(HudLine.of("Reach", String.format("%.2f", lastReach))), alpha);
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
