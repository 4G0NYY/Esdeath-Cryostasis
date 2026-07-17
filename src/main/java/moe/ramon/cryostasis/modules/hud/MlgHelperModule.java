package moe.ramon.cryostasis.modules.hud;

import moe.ramon.cryostasis.hud.HudModule;
import moe.ramon.cryostasis.hud.HudText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

/**
 * While sneaking, scans straight down for the first solid block and reports the fall
 * height plus a water-bucket cue, so the player knows when to clutch an MLG. The scan
 * runs on the tick thread into a reused mutable position, keeping the render path free of
 * allocation and world lookups.
 */
public final class MlgHelperModule extends HudModule {
	private static final int MAX_SCAN = 256;
	private static final int MIN_SHOW = 3;

	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
	private String advice = "";

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
		advice = "";
		if (mc.player == null || mc.level == null || !mc.player.isShiftKeyDown()) {
			return;
		}
		int startY = (int) Math.floor(mc.player.getY());
		int x = mc.player.blockPosition().getX();
		int z = mc.player.blockPosition().getZ();
		int minY = mc.level.getMinY();

		int groundY = Integer.MIN_VALUE;
		for (int y = startY - 1; y > startY - MAX_SCAN && y >= minY; y--) {
			cursor.set(x, y, z);
			if (!mc.level.getBlockState(cursor).isAir()) {
				groundY = y;
				break;
			}
		}
		if (groundY == Integer.MIN_VALUE) {
			advice = "Void below";
			return;
		}
		int fall = startY - (groundY + 1);
		if (fall < MIN_SHOW) {
			return;
		}
		advice = "Fall " + fall + (fall >= 3 ? " - bucket ready" : "");
	}

	@Override
	public void render(GuiGraphics context, float tickDelta) {
		if (advice.isEmpty()) {
			setBounds(0, 0, 0, 0);
			return;
		}
		HudText.drawLine(this, context, advice, HudText.WHITE);
	}
}
