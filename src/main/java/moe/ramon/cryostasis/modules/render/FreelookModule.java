package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.Cryostasis;
import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.KeybindSetting;
import moe.ramon.cryostasis.setting.ModeSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Look around without turning. Hold the key (Left Alt by default) and the mouse aims the camera
 * only: the body keeps facing, and keeps running, exactly where it was. Let go and the view
 * snaps back to wherever the body is pointing, because the body was never turned in the first
 * place, so there is nothing to undo.
 *
 * The view switches to third person while the key is held, which is what makes it a glance over
 * your own shoulder rather than a disembodied swivel, and the previous view comes back on
 * release. View is a setting for the players who would rather keep the first-person camera and
 * just free the direction.
 *
 * The key is read on its press and release edges rather than polled, so the moment it goes down
 * is the moment the mouse stops reaching the body; a poll would let a fast flick turn the player
 * a few degrees before the next tick caught up. A press while a screen is open is ignored, but a
 * release is always honored, so a menu opening mid-glance can never strand the view outside the
 * body.
 *
 * {@link FreecamModule} is the heavier sibling: it takes the camera off the body entirely and
 * flies it. That one wins if both are active, so this stays out of its way.
 */
public final class FreelookModule extends Module {
	private final KeybindSetting key = keybindSetting("Key", GLFW.GLFW_KEY_LEFT_ALT);
	private final ModeSetting view = register(new ModeSetting("View", "Third", List.of("Third", "Front", "First")));

	private boolean looking;
	private CameraType restoreView;
	private float yaw;
	private float pitch;

	public FreelookModule() {
		super("Freelook", "Hold a key to look around without turning your body.", Category.RENDER);
	}

	@Override
	public void onDisable() {
		release();
	}

	@Override
	public void onTick() {
		// Only a safety net: the key edges do the real work. A screen that opened without a key
		// release behind it, or a world that went away, both end the glance here.
		if (looking && (mc.player == null || mc.screen != null)) {
			release();
		}
	}

	/** Called for every key press that reaches the client, whether or not it is this one. */
	public void onKeyDown(int pressed) {
		if (looking || !key.matches(pressed) || mc.player == null) {
			return;
		}
		// Freecam owns the camera outright while it is out, and it saves and restores the view
		// itself, so a glance started underneath it would restore the wrong one on release.
		FreecamModule freecam = Cryostasis.get().getModuleManager().get(FreecamModule.class);
		if (freecam != null && freecam.isDetached()) {
			return;
		}
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		restoreView = mc.options.getCameraType();
		mc.options.setCameraType(cameraType());
		looking = true;
	}

	/** Called for every key release that reaches the client, whether or not it is this one. */
	public void onKeyUp(int released) {
		if (key.matches(released)) {
			release();
		}
	}

	/** Drop the glance and put the view back, if one is in progress. */
	public void release() {
		if (!looking) {
			return;
		}
		looking = false;
		if (restoreView != null) {
			mc.options.setCameraType(restoreView);
			restoreView = null;
		}
	}

	/** True while the key is held and the camera hooks should aim the camera from here. */
	public boolean isLooking() {
		return looking;
	}

	public float cameraYaw() {
		return yaw;
	}

	public float cameraPitch() {
		return pitch;
	}

	/**
	 * Apply a mouse movement to the glance. The deltas arrive exactly as vanilla hands them to
	 * {@code Entity.turn}, already scaled by sensitivity, so they are applied with the same
	 * factor and the same pitch limit and the camera turns at the speed the player is used to.
	 */
	public void turn(double yRot, double xRot) {
		yaw += (float) yRot * 0.15f;
		pitch = Mth.clamp(pitch + (float) xRot * 0.15f, -90.0f, 90.0f);
	}

	private CameraType cameraType() {
		if (view.is("First")) {
			return CameraType.FIRST_PERSON;
		}
		return view.is("Front") ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;
	}
}
