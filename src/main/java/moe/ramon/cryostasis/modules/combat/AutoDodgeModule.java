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
 * a course that would actually reach the player, then adds a sideways impulse to the player's own
 * velocity so they slide out of the line of fire. The impulse rides on the normal movement the
 * client already sends, so nothing here talks to the server directly: it is the same as the
 * player having strafed.
 *
 * The threat test walks the arrow's path forward and asks whether it enters the player's own
 * bounding box, inflated a little so a graze still counts. An earlier version measured the miss
 * distance against {@code player.position()}, which is the point between the feet, so an arrow
 * aimed at the chest missed by the better part of a block and the module never fired once.
 *
 * Detection is velocity based rather than reading the arrow's stuck flag, which is protected: an
 * arrow lodged in a block has near-zero velocity and is skipped. Only the most imminent threat is
 * handled, and only once per cooldown, so a volley cannot stack impulses into a launch.
 */
public final class AutoDodgeModule extends Module {
	private final NumberSetting range = register(new NumberSetting("Range", 5.0, 2.0, 10.0, 0.5));
	private final NumberSetting strength = register(new NumberSetting("Strength", 0.5, 0.1, 1.5, 0.05));
	private final BooleanSetting onlyGrounded = register(new BooleanSetting("Only Grounded", false));

	/** How far ahead the arrow's path is walked, in ticks. Beyond this there is time to react. */
	private static final int LOOKAHEAD_TICKS = 12;
	/** Slack around the hitbox, so a shot that would only clip the shoulder still counts. */
	private static final double MARGIN = 0.25;
	/** Below this an arrow is lodged in a block or spent rather than in flight. */
	private static final double MIN_SPEED_SQR = 0.05;
	/** Ticks between dodges, long enough for one shove to have carried the player clear. */
	private static final int COOLDOWN_TICKS = 6;

	private int cooldown;

	public AutoDodgeModule() {
		super("AutoDodge", "Strafes you out of the path of incoming arrows.", Category.COMBAT);
	}

	@Override
	public void onEnable() {
		cooldown = 0;
	}

	@Override
	public void onTick() {
		if (cooldown > 0) {
			cooldown--;
		}
		if (mc.player == null || mc.level == null || cooldown > 0) {
			return;
		}
		if (onlyGrounded.get() && !mc.player.onGround()) {
			return;
		}

		AABB hitbox = mc.player.getBoundingBox().inflate(MARGIN);
		List<AbstractArrow> arrows = mc.level.getEntitiesOfClass(
				AbstractArrow.class, mc.player.getBoundingBox().inflate(range.get()));

		AbstractArrow threat = null;
		double soonest = Double.MAX_VALUE;
		for (int i = 0; i < arrows.size(); i++) {
			AbstractArrow arrow = arrows.get(i);
			Vec3 velocity = arrow.getDeltaMovement();
			if (velocity.lengthSqr() < MIN_SPEED_SQR || arrow.getOwner() == mc.player) {
				continue;
			}
			Vec3 from = arrow.position();
			Vec3 to = from.add(velocity.scale(LOOKAHEAD_TICKS));
			if (hitbox.clip(from, to).isEmpty() && !hitbox.contains(from)) {
				continue;
			}
			// Time to impact rather than distance, so a fast arrow further out is dodged before a
			// slow one that is closer but will not arrive for another second.
			double eta = from.distanceTo(hitbox.getCenter()) / velocity.length();
			if (eta < soonest) {
				soonest = eta;
				threat = arrow;
			}
		}

		if (threat != null) {
			applyDodge(threat, hitbox.getCenter());
			cooldown = COOLDOWN_TICKS;
		}
	}

	/**
	 * Push the player sideways relative to the arrow's horizontal heading, picking the side the
	 * player is already offset towards so a near-centre shot is cleared rather than crossed. A
	 * shot dead down the middle has no side to prefer, so the sign falls to one of them rather
	 * than to zero.
	 */
	private void applyDodge(AbstractArrow arrow, Vec3 centre) {
		Vec3 heading = arrow.getDeltaMovement().normalize();
		Vec3 perpendicular = new Vec3(-heading.z, 0.0, heading.x).normalize();

		Vec3 toPlayer = centre.subtract(arrow.position());
		double projection = toPlayer.x * perpendicular.x + toPlayer.z * perpendicular.z;
		double side = projection >= 0.0 ? 1.0 : -1.0;

		double power = strength.get();
		Vec3 velocity = mc.player.getDeltaMovement();
		mc.player.setDeltaMovement(
				velocity.x + perpendicular.x * side * power,
				velocity.y,
				velocity.z + perpendicular.z * side * power);
	}
}
