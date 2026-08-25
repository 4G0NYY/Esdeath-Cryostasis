package moe.ramon.cryostasis.modules.combat;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.ModeSetting;
import moe.ramon.cryostasis.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Attacks living entities that come within range. Targets are filtered by the Targets mode and
 * gated on line of sight when asked, and the swing rate is the player's to set.
 *
 * The attack itself goes through {@code mc.gameMode.attack}, the same path a real click
 * uses, so AutoTool's weapon swap and the crit-particle hooks all fire exactly as they would
 * for a manual hit.
 *
 * Two settings decide how hard it swings. Hits Per Second is the ceiling on how often a swing
 * goes out, and Full Damage adds vanilla's own gate on top: the game scales a hit down when the
 * weapon's cooldown has not finished recharging, so leaving it on trades swing count for damage
 * per swing, and turning it off spends the extra swings on the weak hits vanilla would give a
 * player mashing the button.
 *
 * Reach is measured the way the game measures it, from the eye to the nearest point of the
 * target's box, so the number here means the same thing as the number the range check uses.
 * Past about 3 blocks that check is the only thing that matters on a fair-play multiplayer
 * server, and it belongs to the server: it will drop the hits it thinks were thrown from too far
 * away, whatever this is set to. In singleplayer the {@link ReachModule} raises the check itself,
 * so the two together do reach further.
 */
public final class KillauraModule extends Module {
	// Client ticks are 50ms apart and jitter either side of that, so the rate gate is measured a
	// little short of the exact interval. Without the slack, a tick that arrives a millisecond
	// early is thrown away and the top of the slider loses every other swing.
	private static final long TICK_JITTER_MS = 5;

	private final NumberSetting reach = register(new NumberSetting("Reach", 4.0, 3.0, 12.0, 0.1));
	// One swing per client tick is the hard ceiling, and there are twenty of those a second.
	private final NumberSetting hitsPerSecond = register(new NumberSetting("Hits Per Second", 8.0, 1.0, 20.0, 0.5));
	private final BooleanSetting fullDamage = register(new BooleanSetting("Full Damage", true));
	private final ModeSetting targets = register(new ModeSetting("Targets", "All", List.of("Players", "Mobs", "All")));
	private final BooleanSetting requireLineOfSight = register(new BooleanSetting("Line of Sight", true));
	private final BooleanSetting rotate = register(new BooleanSetting("Rotate", false));

	private long lastAttackMs;

	public KillauraModule() {
		super("Killaura", "Automatically hits nearby entities.", Category.COMBAT);
	}

	@Override
	public void onEnable() {
		// Do not carry a stale timestamp across a break in play, or the first swing after
		// switching back on waits out an interval that already elapsed.
		lastAttackMs = 0L;
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return;
		}
		if (fullDamage.get() && mc.player.getAttackStrengthScale(0.0f) < 1.0f) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastAttackMs < interval()) {
			return;
		}

		LivingEntity target = findTarget();
		if (target == null) {
			return;
		}
		if (rotate.get()) {
			faceEntity(target);
		}
		mc.gameMode.attack(mc.player, target);
		mc.player.swing(InteractionHand.MAIN_HAND);
		// Stamped on the swing rather than on the tick so the rate limits hits that landed, and
		// a stretch with nothing in range does not bank up a burst.
		lastAttackMs = now;
	}

	private long interval() {
		return Math.max(0L, (long) (1000.0 / hitsPerSecond.get()) - TICK_JITTER_MS);
	}

	private LivingEntity findTarget() {
		double range = reach.get();
		AABB box = mc.player.getBoundingBox().inflate(range);
		List<Entity> candidates = mc.level.getEntities(mc.player, box, this::isValidTarget);

		Vec3 eye = mc.player.getEyePosition();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity e : candidates) {
			double distance = Math.sqrt(e.getBoundingBox().distanceToSqr(eye));
			if (distance > range || distance >= bestDistance) {
				continue;
			}
			if (requireLineOfSight.get() && !mc.player.hasLineOfSight(e)) {
				continue;
			}
			best = (LivingEntity) e;
			bestDistance = distance;
		}
		return best;
	}

	private boolean isValidTarget(Entity e) {
		if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) {
			return false;
		}
		return switch (targets.get()) {
			case "Players" -> e instanceof Player;
			case "Mobs" -> e instanceof Mob;
			default -> true;
		};
	}

	private void faceEntity(Entity target) {
		Vec3 eye = mc.player.getEyePosition();
		Vec3 aim = target.getBoundingBox().getCenter();
		double dx = aim.x - eye.x;
		double dy = aim.y - eye.y;
		double dz = aim.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		mc.player.setYRot(yaw);
		mc.player.setXRot(pitch);
	}
}
