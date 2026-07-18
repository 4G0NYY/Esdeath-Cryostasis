package moe.ramon.cryostasis.modules.render;

import moe.ramon.cryostasis.module.Category;
import moe.ramon.cryostasis.module.Module;
import moe.ramon.cryostasis.setting.BooleanSetting;
import moe.ramon.cryostasis.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Reveals selected ores and blocks through terrain by hiding everything else. The render
 * side lives in {@code BlockMixin}, which asks {@link #isVisible(Block)} for every block
 * face: selected blocks always draw (even against solid neighbours, so they show through
 * walls), and everything else stops drawing.
 *
 * The selection is a set of clickable per-material toggles plus an {@code Extra} field for
 * any block id the toggles do not cover. The visible set is rebuilt into a plain HashSet so
 * the render hook does an O(1) identity lookup with no per-face allocation, and chunks are
 * only rebuilt when the selection actually changes.
 */
public final class XrayModule extends Module {
	// Ore groups. Deepslate variants ride with their parent so one toggle covers both depths.
	private final BooleanSetting coal = register(new BooleanSetting("Coal", false));
	private final BooleanSetting iron = register(new BooleanSetting("Iron", true));
	private final BooleanSetting copper = register(new BooleanSetting("Copper", true));
	private final BooleanSetting gold = register(new BooleanSetting("Gold", true));
	private final BooleanSetting redstone = register(new BooleanSetting("Redstone", true));
	private final BooleanSetting lapis = register(new BooleanSetting("Lapis", true));
	private final BooleanSetting diamond = register(new BooleanSetting("Diamond", true));
	private final BooleanSetting emerald = register(new BooleanSetting("Emerald", true));
	private final BooleanSetting quartz = register(new BooleanSetting("Quartz", true));
	private final BooleanSetting netherite = register(new BooleanSetting("Netherite", true));
	private final BooleanSetting amethyst = register(new BooleanSetting("Amethyst", true));
	// Loot and utility groups.
	private final BooleanSetting containers = register(new BooleanSetting("Containers", true));
	private final BooleanSetting spawners = register(new BooleanSetting("Spawners", true));
	private final BooleanSetting vaults = register(new BooleanSetting("Vaults", true));
	private final BooleanSetting suspicious = register(new BooleanSetting("Suspicious", false));
	// Free-form escape hatch: comma-separated block ids, for example "minecraft:beacon".
	private final StringSetting extra = register(new StringSetting("Extra", ""));

	// The set the render hook queries. Identity lookups, no allocation on the hot path.
	private final Set<Block> visible = new HashSet<>();
	// Signature of the selection the visible set was last built from, so a tick that changed
	// nothing costs one string compare instead of a chunk rebuild.
	private String applied = "";

	public XrayModule() {
		super("Xray", "See through terrain to reveal selected ores and blocks.", Category.RENDER);
	}

	@Override
	public void onEnable() {
		rebuild();
		refreshChunks();
	}

	@Override
	public void onDisable() {
		// Restore normal rendering by rebuilding the world without the face filter.
		refreshChunks();
	}

	@Override
	public void onTick() {
		// Pick up click-GUI edits to the selection while enabled, and only pay for a chunk
		// rebuild when the selection actually changed.
		if (!signature().equals(applied)) {
			rebuild();
			refreshChunks();
		}
	}

	/** Hot path: queried by {@code BlockMixin} for every rendered face. Keep it allocation free. */
	public boolean isVisible(Block block) {
		return visible.contains(block);
	}

	private void rebuild() {
		visible.clear();
		if (coal.get()) add(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
		if (iron.get()) add(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
		if (copper.get()) add(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
		if (gold.get()) add(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
		if (redstone.get()) add(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
		if (lapis.get()) add(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
		if (diamond.get()) add(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
		if (emerald.get()) add(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
		if (quartz.get()) add(Blocks.NETHER_QUARTZ_ORE);
		if (netherite.get()) add(Blocks.ANCIENT_DEBRIS);
		if (amethyst.get()) add(Blocks.BUDDING_AMETHYST);
		if (containers.get()) {
			add(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL);
			add(Blocks.SHULKER_BOX, Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX,
					Blocks.MAGENTA_SHULKER_BOX, Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX,
					Blocks.LIME_SHULKER_BOX, Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX,
					Blocks.LIGHT_GRAY_SHULKER_BOX, Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX,
					Blocks.BLUE_SHULKER_BOX, Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX,
					Blocks.RED_SHULKER_BOX, Blocks.BLACK_SHULKER_BOX);
		}
		if (spawners.get()) add(Blocks.SPAWNER, Blocks.TRIAL_SPAWNER);
		if (vaults.get()) add(Blocks.VAULT);
		if (suspicious.get()) add(Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL);
		addExtra();
		applied = signature();
	}

	private void add(Block... blocks) {
		for (Block block : blocks) {
			visible.add(block);
		}
	}

	private void addExtra() {
		String raw = extra.get();
		if (raw == null || raw.isBlank()) {
			return;
		}
		for (String token : raw.split(",")) {
			String id = token.trim();
			if (id.isEmpty()) {
				continue;
			}
			ResourceLocation location = ResourceLocation.tryParse(id);
			if (location != null) {
				BuiltInRegistries.BLOCK.getOptional(location).ifPresent(visible::add);
			}
		}
	}

	/** Compact fingerprint of the current selection, used to detect edits between ticks. */
	private String signature() {
		StringBuilder sb = new StringBuilder(32);
		sb.append(coal.get() ? '1' : '0').append(iron.get() ? '1' : '0').append(copper.get() ? '1' : '0')
				.append(gold.get() ? '1' : '0').append(redstone.get() ? '1' : '0').append(lapis.get() ? '1' : '0')
				.append(diamond.get() ? '1' : '0').append(emerald.get() ? '1' : '0').append(quartz.get() ? '1' : '0')
				.append(netherite.get() ? '1' : '0').append(amethyst.get() ? '1' : '0').append(containers.get() ? '1' : '0')
				.append(spawners.get() ? '1' : '0').append(vaults.get() ? '1' : '0').append(suspicious.get() ? '1' : '0')
				.append('|').append(extra.get());
		return sb.toString();
	}

	private void refreshChunks() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) {
			// Rebuild every section so the face filter is applied or removed everywhere at once.
			mc.levelRenderer.allChanged();
		}
	}
}
