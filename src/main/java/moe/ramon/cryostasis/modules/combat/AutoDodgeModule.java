package moe.ramon.cryostasis.modules.combat;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.NumberSetting;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sidesteps incoming arrows. Each tick it looks for an arrow that is both still in flight and on
 * a course that would pass through the player, then adds a sideways impulse to the player's own
 * velocity so they slide out of the line of fire. The impulse rides on the normal movement the
 * client already sends, so nothing here talks to the server directly: it is the same as the
 * player having strafed.
 *
 * Detection is velocity based rather than reading the arrow's stuck flag (which is protected):
 * an arrow lodged in a block has near-zero velocity and is skipped, so only live shots trigger a
 * dodge. Only the single nearest threat is handled per tick to avoid stacking impulses.
 */
public final class AutoDodgeModule extends Module {
	private final NumberSetting range = register(new NumberSetting("Range", 5.0, 2.0, 10.0, 0.5));
	private final NumberSetting strength = register(new NumberSetting("Strength", 0.5, 0.1, 1.5, 0.05));
	private final BooleanSetting onlyGrounded = register(new BooleanSetting("Only Grounded", false));

	// How close the arrow's path must come to the player's center to count as a real threat. A
	// touch over the half-width of the hitbox so grazing shots still provoke a dodge.
	private static final double THREAT_RADIUS = 0.6;

	public AutoDodgeModule() {
		super("AutoDodge", "Strafes you out of the path of incoming arrows.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (onlyGrounded.get() && !mc.player.onGround()) {
			return;
		}

		double reach = range.get();
		Vec3 self = mc.player.position();
		AABB box = mc.player.getBoundingBox().inflate(reach);
		List<AbstractArrow> arrows = mc.level.getEntitiesOfClass(AbstractArrow.class, box);

		Vec3 threatDir = null;
		Vec3 threatMiss = null;
		double bestDistance = Double.MAX_VALUE;
		for (AbstractArrow arrow : arrows) {
			Vec3 velocity = arrow.getDeltaMovement();
			if (velocity.lengthSqr() < 0.1) {
				// Not moving: lodged in the ground or spent, no dodge needed.
				continue;
			}
			Vec3 toPlayer = self.subtract(arrow.position());
			if (velocity.dot(toPlayer) <= 0.0) {
				// Heading away from the player.
				continue;
			}
			Vec3 dir = velocity.normalize();
			// Closest point of the arrow's forward ray to the player, and how far it misses by.
			Vec3 closest = arrow.position().add(dir.scale(toPlayer.dot(dir)));
			double miss = closest.distanceTo(self);
			if (miss > THREAT_RADIUS) {
				continue;
			}
			double distance = arrow.position().distanceTo(self);
			if (distance < bestDistance) {
				bestDistance = distance;
				threatDir = dir;
				// Direction from the arrow's line to the player, the way we want to keep moving.
				threatMiss = self.subtract(closest);
			}
		}

		if (threatDir == null) {
			return;
		}
		applyDodge(threatDir, threatMiss);
	}

	/**
	 * Push the player sideways relative to the arrow's horizontal heading, choosing the side that
	 * moves away from the incoming line so a near-center shot is cleared rather than crossed. When
	 * the shot is dead center (no measurable offset) the sign is arbitrary, so it defaults to one
	 * side rather than stalling.
	 */
	private void applyDodge(Vec3 arrowDir, Vec3 missOffset) {
		Vec3 perpendicular = new Vec3(-arrowDir.z, 0.0, arrowDir.x).normalize();
		double projection = missOffset.x * perpendicular.x + missOffset.z * perpendicular.z;
		double side = projection >= 0.0 ? 1.0 : -1.0;

		double power = strength.get();
		Vec3 velocity = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(
				velocity.x + perpendicular.x * side * power,
				velocity.y,
				velocity.z + perpendicular.z * side * power);
	}
}
