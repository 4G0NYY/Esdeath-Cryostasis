package moe.ramon.cryostasis.cosmetics;

import java.util.List;

/**
 * The list of cosmetics the client can render, used by the in-game cosmetics menu to build
 * its rows. This is the catalogue side of the free-for-all model: every linked account may
 * wear any of these, so the menu offers all of them and the per-player state is only which
 * ones are active (see {@link CosmeticService.Active}).
 *
 * Keys are the lowercase backend identifiers the ownership check matches, so they must stay
 * in step with the {@code key()} each {@code Cosmetic} returns. Adding a cosmetic here makes
 * it appear in the menu; giving it a renderer makes it show on players.
 */
public final class CosmeticCatalogue {
	/** One selectable cosmetic: its backend key and the label shown in the menu. */
	public record Entry(String key, String displayName) {
	}

	private static final List<Entry> ENTRIES = List.of(
			new Entry("halo", "Halo"),
			new Entry("bandana", "Bandana"),
			new Entry("tophat", "Top Hat"));

	private CosmeticCatalogue() {
	}

	public static List<Entry> entries() {
		return ENTRIES;
	}
}
