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
	private final String statusIconAssetKey;
	private final int casterIconItemId;
	private final int casterAnimationId;

	public ClericSpellPresentation(int spellbookIconItemId, int casterIconItemId,
			int casterAnimationId) {
		this(spellbookIconItemId, "", casterIconItemId, casterAnimationId);
	}

	public ClericSpellPresentation(int spellbookIconItemId, String statusIconAssetKey,
			int casterIconItemId, int casterAnimationId) {
		if (spellbookIconItemId < 0 || casterIconItemId < NONE || casterAnimationId < NONE) {
			throw new IllegalArgumentException("Invalid Cleric presentation identifier");
		}
		if (statusIconAssetKey == null
				|| (!statusIconAssetKey.isEmpty()
					&& !statusIconAssetKey.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
			throw new IllegalArgumentException("Invalid Cleric status icon asset key");
		}
		this.spellbookIconItemId = spellbookIconItemId;
		this.statusIconAssetKey = statusIconAssetKey;
		this.casterIconItemId = casterIconItemId;
		this.casterAnimationId = casterAnimationId;
	}

	public int getSpellbookIconItemId() {
		return spellbookIconItemId;
	}

	public String getStatusIconAssetKey() {
		return statusIconAssetKey;
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
