package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * An out-of-body camera. While it is on, the view detaches from the player and flies on the
 * movement keys, passing through blocks; the body stays where it was standing, facing where it
 * was facing. Turn it off and the view drops straight back into the body.
 *
 * The camera is the only thing that moves. Nothing is sent to the server and nothing is
 * teleported: {@code KeyboardInputMixin} empties the movement input while the camera is out, so
 * the body simply stops receiving any, and {@code EntityMixin} routes the mouse into this module
 * instead of the player, so the body does not even turn. To another player, someone in Freecam
 * is standing still. That also means the crosshair keeps pointing wherever the body was left, so
 * mining and attacking still reach only what the player could actually reach.
 *
 * The camera view is switched to third person for the duration, which is what makes the body
 * visible from the outside and stops the held item drawing over the view. The previous view is
 * restored on the way out.
 *
 * Range is a tether rather than a preference: the client only has chunks loaded around the
 * player, so a camera that outruns them looks out into an empty void. Holding the sprint key
 * doubles the speed.
 *
 * {@link FreelookModule} is the lighter sibling: it leaves the camera on the body and only frees
 * the direction it faces, for a glance over the shoulder while running.
 */
public final class FreecamModule extends Module {
	private final NumberSetting speed = register(new NumberSetting("Speed", 0.6, 0.1, 3.0, 0.1));
	private final NumberSetting range = register(new NumberSetting("Range", 64.0, 8.0, 256.0, 8.0));

	private boolean detached;
	private CameraType restoreView;

	private double x;
	private double y;
	private double z;
	private double prevX;
	private double prevY;
	private double prevZ;
	private float yaw;
	private float pitch;

	public FreecamModule() {
		super("Freecam", "Detach the camera and fly it through anything.", Category.RENDER);
	}

	@Override
	public void onEnable() {
		// Enabling from the config before a world exists is normal, so this is not an error: the
		// first tick with a player detaches instead.
		if (mc.player != null) {
			detach(mc.player);
		}
	}

	@Override
	public void onDisable() {
		reattach();
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			// Left the world with the camera out. Drop it so a rejoin does not resume from a
			// position in a world that is gone.
			reattach();
			return;
		}
		if (!detached) {
			detach(player);
			return;
		}
		prevX = x;
		prevY = y;
		prevZ = z;
		fly();
		tether(player);
	}

	/** True while the camera is out of the body and the view hooks should take over. */
	public boolean isDetached() {
		return detached;
	}

	public double cameraX(float partialTick) {
		return Mth.lerp(partialTick, prevX, x);
	}

	public double cameraY(float partialTick) {
		return Mth.lerp(partialTick, prevY, y);
	}

	public double cameraZ(float partialTick) {
		return Mth.lerp(partialTick, prevZ, z);
	}

	public float cameraYaw() {
		return yaw;
	}

	public float cameraPitch() {
		return pitch;
	}

	/**
	 * Apply a mouse movement to the camera. The deltas arrive exactly as vanilla hands them to
	 * {@code Entity.turn}, already scaled by sensitivity, so they are applied with the same
	 * factor and the same pitch limit and the camera turns at the speed the player is used to.
	 */
	public void turn(double yRot, double xRot) {
		yaw += (float) yRot * 0.15f;
		pitch = Mth.clamp(pitch + (float) xRot * 0.15f, -90.0f, 90.0f);
	}

	private void detach(LocalPlayer player) {
		// Freelook may already have the view out of the body. Take it back first, so the view it
		// restores is the one the player started from rather than the third person set below.
		FreelookModule freelook = Cryostasis.get().getModuleManager().get(FreelookModule.class);
		if (freelook != null) {
			freelook.release();
		}
		x = prevX = player.getX();
		y = prevY = player.getEyeY();
		z = prevZ = player.getZ();
		yaw = player.getYRot();
		pitch = player.getXRot();
		restoreView = mc.options.getCameraType();
		mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
		detached = true;
	}

	private void reattach() {
		detached = false;
		if (restoreView != null) {
			mc.options.setCameraType(restoreView);
			restoreView = null;
		}
	}

	private void fly() {
		double step = speed.get();
		if (mc.options.keySprint.isDown()) {
			step *= 2.0;
		}
		double forward = axis(mc.options.keyUp, mc.options.keyDown);
		double strafe = axis(mc.options.keyRight, mc.options.keyLeft);
		double vertical = axis(mc.options.keyJump, mc.options.keyShift);
		if (forward != 0.0 || strafe != 0.0) {
			// Normalize so a diagonal is not faster than a straight line.
			double length = Math.sqrt(forward * forward + strafe * strafe);
			forward /= length;
			strafe /= length;
			double radians = Math.toRadians(yaw);
			double sin = Math.sin(radians);
			double cos = Math.cos(radians);
			x += (strafe * cos - forward * sin) * step;
			z += (strafe * sin + forward * cos) * step;
		}
		y += vertical * step;
	}

	/**
	 * Pull the camera back to within Range of the body. Run every tick rather than only on a
	 * move, because the body is still subject to physics: if it falls or is pushed, the tether
	 * has to follow it.
	 */
	private void tether(LocalPlayer player) {
		double eyeY = player.getEyeY();
		double dx = x - player.getX();
		double dy = y - eyeY;
		double dz = z - player.getZ();
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double max = range.get();
		if (distance <= max || distance == 0.0) {
			return;
		}
		double scale = max / distance;
		x = player.getX() + dx * scale;
		y = eyeY + dy * scale;
		z = player.getZ() + dz * scale;
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
