package com.openrsc.server.content.cleric;

/**
 * Optional, asset-independent presentation identifiers for a Cleric spell.
 *
 * <p>The spellbook icon always has a safe existing item definition. Caster
 * bubble and animation identifiers remain absent until final artwork is
 * approved. Later cast code may dispatch the optional values through
 * {@link Hooks} without making gameplay depend on a client visual.</p>
 */
public final class ClericSpellPresentation {
	public static final int NONE = -1;

	private final int spellbookIconItemId;
	private final int casterIconItemId;
	private final int casterAnimationId;

	public ClericSpellPresentation(int spellbookIconItemId, int casterIconItemId,
			int casterAnimationId) {
		if (spellbookIconItemId < 0 || casterIconItemId < NONE || casterAnimationId < NONE) {
			throw new IllegalArgumentException("Invalid Cleric presentation identifier");
		}
		this.spellbookIconItemId = spellbookIconItemId;
		this.casterIconItemId = casterIconItemId;
		this.casterAnimationId = casterAnimationId;
	}

	public int getSpellbookIconItemId() {
		return spellbookIconItemId;
	}

	public boolean hasCasterIcon() {
		return casterIconItemId != NONE;
	}

	public int getCasterIconItemId() {
		return casterIconItemId;
	}

	public boolean hasCasterAnimation() {
		return casterAnimationId != NONE;
	}

	public int getCasterAnimationId() {
		return casterAnimationId;
	}

	/** Dispatches only configured visuals; an asset-free definition is a no-op. */
	public void dispatch(Hooks hooks) {
		if (hooks == null) {
			throw new IllegalArgumentException("Cleric presentation hooks are required");
		}
		if (hasCasterIcon()) {
			hooks.showCasterIcon(casterIconItemId);
		}
		if (hasCasterAnimation()) {
			hooks.showCasterAnimation(casterAnimationId);
		}
	}

	public interface Hooks {
		void showCasterIcon(int itemId);

		void showCasterAnimation(int animationId);
	}
}
