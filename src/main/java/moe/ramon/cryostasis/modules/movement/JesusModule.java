package moe.ramon.cryostasis.modules.movement;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lets the player walk on water instead of swimming in it. The behavior lives in
 * {@code LiquidBlockMixin}, which gives a liquid a solid top for the local player, reusing the
 * exact mechanism vanilla already has for standing on a fluid (the one that lets a strider walk
 * on lava). Because it is a real collision shape, the movement is ordinary walking: jumping,
 * sprinting, and friction all behave as they do on land, with no per-tick position rewriting.
 * This module owns the surface shapes and the Lava toggle; the Mixin is the hook that reads
 * them.
 *
 * Sneak to sink. The collision only exists while the player is above the surface, so holding
 * shift drops them in, and swimming and diving underneath are untouched.
 *
 * The client is the only side that moves the player here, so on a fair-play multiplayer server
 * the server still believes the player is in the water and may pull them back; it is honest in
 * singleplayer, where the integrated server shares the local player's UUID and so agrees.
 */
public final class JesusModule extends Module {
	/**
	 * One collision top per ninth of a block, indexed by the fluid's amount. Fluid heights are
	 * always a ninth (a source is 8/9), so a flowing edge is walked at the height it is actually
	 * drawn at instead of a fixed plane, and the shapes are built once rather than allocated on
	 * the collision path.
	 */
	private static final VoxelShape[] SURFACES = buildSurfaces();

	private final BooleanSetting lava = register(new BooleanSetting("Lava", false));

	public JesusModule() {
		super("Jesus", "Walk on the surface of water.", Category.MOVEMENT);
	}

	public boolean walksOnLava() {
		return lava.get();
	}

	/**
	 * The collision top for a fluid, or null when there is nothing to stand on. Null rather than
	 * an empty shape because the caller measures the shape's height, which an empty shape has no
	 * meaningful answer for.
	 */
	public static VoxelShape surfaceShape(FluidState fluid) {
		int ninths = Math.round(fluid.getOwnHeight() * 9.0f);
		if (ninths <= 0 || ninths >= SURFACES.length) {
			return null;
		}
		return SURFACES[ninths];
	}

	private static VoxelShape[] buildSurfaces() {
		VoxelShape[] shapes = new VoxelShape[10];
		for (int ninths = 1; ninths < shapes.length; ninths++) {
			shapes[ninths] = Shapes.box(0.0, 0.0, 0.0, 1.0, ninths / 9.0, 1.0);
		}
		return shapes;
	}
}
