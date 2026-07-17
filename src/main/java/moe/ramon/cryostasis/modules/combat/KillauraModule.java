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
 * Attacks living entities that come within range. Targets are filtered by the Targets mode,
 * gated on line of sight when asked, and struck no faster than the held item's attack
 * cooldown allows so hits land with full damage rather than as weak spam.
 *
 * The attack itself goes through {@code mc.gameMode.attack}, the same path a real click
 * uses, so AutoTool's weapon swap and the crit-particle hooks all fire exactly as they would
 * for a manual hit.
 */
public final class KillauraModule extends Module {
	private final NumberSetting range = register(new NumberSetting("Range", 4.0, 3.0, 6.0, 0.1));
	private final ModeSetting targets = register(new ModeSetting("Targets", "All", List.of("Players", "Mobs", "All")));
	private final BooleanSetting requireLineOfSight = register(new BooleanSetting("Line of Sight", true));
	private final BooleanSetting rotate = register(new BooleanSetting("Rotate", false));

	public KillauraModule() {
		super("Killaura", "Automatically hits nearby entities.", Category.COMBAT);
	}

	@Override
	public void onTick() {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
			return;
		}
		// Wait for the swing to recharge so each hit does full, cooldown-scaled damage.
		if (mc.player.getAttackStrengthScale(0.0f) < 1.0f) {
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
	}

	private LivingEntity findTarget() {
		double reach = range.get();
		AABB box = mc.player.getBoundingBox().inflate(reach);
		List<Entity> candidates = mc.level.getEntities(mc.player, box, this::isValidTarget);

		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity e : candidates) {
			double distance = mc.player.distanceTo(e);
			if (distance > reach || distance >= bestDistance) {
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
