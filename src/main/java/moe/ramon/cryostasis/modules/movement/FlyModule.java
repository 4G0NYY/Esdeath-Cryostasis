package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.ModeSetting;
import moe.ramon.cryostasis.setting.NumberSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Abilities;

import java.util.List;

/**
 * Flies the player. The movement keys steer, jump and sneak go up and down, and holding sprint
 * doubles the speed, the same controls Freecam uses so the two feel alike.
 *
 * Two modes, because there are two different things a server will believe.
 *
 * Abilities hands the player the flight the game already knows how to do: it sets the creative
 * flight flags and tells the server about them, so the client flies through vanilla's own code
 * and the server is looking at a player it thinks is allowed to. That is the smooth one, and it
 * is the one that works where flight is permitted at all (creative, or a server with
 * {@code allow-flight} on). Where it is not permitted, the server answers by clearing the flags
 * again and the flight simply stops.
 *
 * Motion asks no one: it overwrites the player's velocity every tick, so gravity never gets to
 * accumulate and the player goes exactly where the keys point. Nothing is requested and nothing
 * can be refused, which is why it works in singleplayer survival where Abilities does not. What
 * it cannot do is hide: a fair-play server watching the position packets sees a player hanging in
 * the air and will pull them back down or kick them. Fall damage is handled either way, since the
 * fall distance is cleared every tick, but the server banks its own from the movement packets, so
 * pair it with {@link ZootModule} before landing on a server.
 */
public final class FlyModule extends Module {
	// Vanilla creative flight covers roughly this much ground per tick, so Speed 1.0 means the
	// same thing in both modes rather than one number for the flag and another for the velocity.
	private static final double CREATIVE_BLOCKS_PER_TICK = 0.45;
	private static final float DEFAULT_FLYING_SPEED = 0.05f;

	private final ModeSetting mode = register(new ModeSetting("Mode", "Motion", List.of("Motion", "Abilities")));
	private final NumberSetting speed = register(new NumberSetting("Speed", 1.0, 0.1, 5.0, 0.1));

	private boolean grantedFlight;

	public FlyModule() {
		super("Fly", "Fly wherever you point.", Category.MOVEMENT);
	}

	@Override
	public void onDisable() {
		revokeFlight();
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc.player;
		if (player == null) {
			// Left the world mid-flight. The abilities went with the player, so drop the claim
			// rather than trying to hand anything back.
			grantedFlight = false;
			return;
		}
		if (mode.is("Abilities")) {
			grantFlight(player);
		} else {
			revokeFlight();
			motionFly(player);
		}
	}

	/**
	 * The flags are only set once per flight, not once per tick: a server that refuses flight
	 * answers by clearing them, and re-setting them on the next tick would turn that refusal into
	 * an endless exchange of ability packets.
	 */
	private void grantFlight(LocalPlayer player) {
		Abilities abilities = player.getAbilities();
		abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED * speed.getFloat());
		if (grantedFlight) {
			return;
		}
		abilities.mayfly = true;
		abilities.flying = true;
		grantedFlight = true;
		player.onUpdateAbilities();
	}

	private void revokeFlight() {
		if (!grantedFlight) {
			return;
		}
		grantedFlight = false;
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		Abilities abilities = player.getAbilities();
		abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
		// Creative and spectator fly in their own right, so only take back what was borrowed.
		boolean ownsFlight = player.isCreative() || player.isSpectator();
		abilities.mayfly = ownsFlight;
		abilities.flying = ownsFlight && abilities.flying;
		player.onUpdateAbilities();
	}

	private void motionFly(LocalPlayer player) {
		double step = CREATIVE_BLOCKS_PER_TICK * speed.get();
		if (mc.options.keySprint.isDown()) {
			step *= 2.0;
		}
		double forward = axis(mc.options.keyUp, mc.options.keyDown);
		double left = axis(mc.options.keyLeft, mc.options.keyRight);
		double vertical = axis(mc.options.keyJump, mc.options.keyShift);

		double x = 0.0;
		double z = 0.0;
		if (forward != 0.0 || left != 0.0) {
			// Normalize so a diagonal is not faster than a straight line.
			double length = Math.sqrt(forward * forward + left * left);
			forward /= length;
			left /= length;
			// Vanilla's own input rotation, sign for sign, so strafing matches walking.
			double radians = Math.toRadians(player.getYRot());
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			x = (left * cos - forward * sin) * step;
			z = (left * sin + forward * cos) * step;
		}
		player.setDeltaMovement(x, vertical * step, z);
		player.resetFallDistance();
	}

	private static double axis(KeyMapping positive, KeyMapping negative) {
		double value = 0.0;
		if (positive.isDown()) {
			value += 1.0;
		}
		if (negative.isDown()) {
			value -= 1.0;
		}
		return value;
	}
}
