package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.gui.Theme;
import moe.ramon.cryostasis.hud.HudLine;
import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/**
 * While sneaking, scans straight down for the first solid block and reports the fall height, so
 * the player knows when to clutch an MLG. The height turns amber once the fall would hurt, which
 * is the moment the water bucket matters. The scan runs on the tick thread into a reused mutable
 * position, keeping the render path free of allocation and world lookups.
 */
public final class MlgHelperModule extends HudModule {
	private static final int MAX_SCAN = 256;
	private static final int MIN_SHOW = 3;
	/** Falls of up to this many blocks deal no damage. */
	private static final int SAFE_FALL = 3;
	private static final int NOTHING = -1;
	private static final int VOID = -2;

	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private int fall = NOTHING;

	public MlgHelperModule() {
		super("MLGHelper", "Shows fall height and an MLG cue while sneaking.", 0.01, 0.9);
	}

	@Override
	public boolean isAutoStacked() {
		// The MLG cue sits near the bottom-left on its own, out of the top-left column.
		return false;
	}

	@Override
	public void onTick() {
		fall = NOTHING;
		if (mc.player == null || mc.level == null || !mc.player.isShiftKeyDown()) {
			return;
		}
		int startY = (int) Math.floor(mc.player.getY());
		int x = mc.player.blockPosition().getX();
		int z = mc.player.blockPosition().getZ();
		int minY = mc.level.getMinY();

		for (int y = startY - 1; y > startY - MAX_SCAN && y >= minY; y--) {
			cursor.set(x, y, z);
			if (!mc.level.getBlockState(cursor).isAir()) {
				int height = startY - (y + 1);
				fall = height >= MIN_SHOW ? height : NOTHING;
				return;
			}
		}
		fall = VOID;
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (fall == NOTHING) {
			setBounds(0, 0, 0, 0);
			return;
		}
		if (fall == VOID) {
			HudText.draw(this, context, new HudLine("Fall", "void", Theme.BAD));
			return;
		}
		HudText.draw(this, context, new HudLine("Fall", fall + " blocks", fall > SAFE_FALL ? Theme.WARN : Theme.TEXT));
	}
}
