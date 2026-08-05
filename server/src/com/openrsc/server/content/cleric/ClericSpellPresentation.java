package com.openrsc.server.content.cleric;

/**
 * Optional, asset-independent presentation identifiers for a Cleric spell.
 *
 * <p>The spellbook icon always has a safe existing item definition. Caster
 * bubble and animation identifiers remain optional. The animation field keeps
 * its original wire position and accessor names for compatibility, while the
 * approved launch behavior attaches it to each successfully affected entity
 * rather than the caster. Gameplay never depends on a client visual.</p>
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
		return hasOnEntityAnimation();
	}

	public int getCasterAnimationId() {
		return getOnEntityAnimationId();
	}

	public boolean hasOnEntityAnimation() {
		return casterAnimationId != NONE;
	}

	public int getOnEntityAnimationId() {
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
