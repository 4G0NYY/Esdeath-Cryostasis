package moe.ramon.cryostasis.module;

/**
 * Top level grouping for modules, used by the click GUI and the ArrayList HUD.
 * Mirrors the original Esdeath client categories, with HUD split out so pure
 * overlay modules are easy to find.
 */
public enum Category {
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	HUD("HUD"),
	PLAYER("Player"),
	MISC("Misc");

	private final String displayName;

	Category(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
