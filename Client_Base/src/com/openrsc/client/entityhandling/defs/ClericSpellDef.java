package com.openrsc.client.entityhandling.defs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable Cleric spell metadata supplied by the authoritative server catalog. */
public final class ClericSpellDef {
	private final int stableCode;
	private final String stableKey;
	private final String name;
	private final String description;
	private final String alignment;
	private final int worshipLevel;
	private final int spellTier;
	private final int radius;
	private final boolean affectsCaster;
	private final int spellbookIconItemId;
	private final String iconAssetKey;
	private final int stoneSigilItemId;
	private final int stoneSigilCount;
	private final int silverSigilItemId;
	private final int silverSigilCount;
	private final int casterIconItemId;
	private final int casterAnimationId;
	private final List<ClericEffectRankDef> effectRanks;

	public ClericSpellDef(int stableCode, String stableKey, String name, String description,
			String alignment, int worshipLevel, int spellTier, int radius,
			boolean affectsCaster, int spellbookIconItemId, int stoneSigilItemId,
			int stoneSigilCount, int silverSigilItemId, int silverSigilCount,
			int casterIconItemId, int casterAnimationId) {
		this(stableCode, stableKey, name, description, alignment, worshipLevel,
			spellTier, radius, affectsCaster, spellbookIconItemId, stoneSigilItemId,
			stoneSigilCount, silverSigilItemId, silverSigilCount, casterIconItemId,
			casterAnimationId, "", Collections.<ClericEffectRankDef>emptyList());
	}

	public ClericSpellDef(int stableCode, String stableKey, String name, String description,
			String alignment, int worshipLevel, int spellTier, int radius,
			boolean affectsCaster, int spellbookIconItemId, int stoneSigilItemId,
			int stoneSigilCount, int silverSigilItemId, int silverSigilCount,
			int casterIconItemId, int casterAnimationId, String iconAssetKey,
			List<ClericEffectRankDef> effectRanks) {
		if (stableCode < 0 || isBlank(stableKey) || isBlank(name) || isBlank(description)
				|| isBlank(alignment)) {
			throw new IllegalArgumentException("Cleric spell identity and display metadata are required");
		}
		if (worshipLevel < 1 || spellTier < 1 || radius < 1 || spellbookIconItemId < 0
				|| stoneSigilItemId < 0 || stoneSigilCount < 1 || silverSigilCount < 0
				|| (silverSigilCount > 0 && silverSigilItemId < 0)
				|| casterIconItemId < -1 || casterAnimationId < -1) {
			throw new IllegalArgumentException("Invalid Cleric spell presentation metadata");
		}
		if (iconAssetKey == null || (!iconAssetKey.isEmpty()
				&& !iconAssetKey.matches("[a-z0-9]+(?:-[a-z0-9]+)*"))
				|| effectRanks == null || effectRanks.size() > 255) {
			throw new IllegalArgumentException("Invalid Cleric icon or effect-rank metadata");
		}
		ArrayList<ClericEffectRankDef> rankCopy =
			new ArrayList<ClericEffectRankDef>(effectRanks.size());
		for (int index = 0; index < effectRanks.size(); index++) {
			ClericEffectRankDef rank = effectRanks.get(index);
			if (rank == null || rank.getRank() != index + 1) {
				throw new IllegalArgumentException("Cleric effect ranks must be complete and contiguous");
			}
			rankCopy.add(rank);
		}
		this.stableCode = stableCode;
		this.stableKey = stableKey;
		this.name = name;
		this.description = description;
		this.alignment = alignment;
		this.worshipLevel = worshipLevel;
		this.spellTier = spellTier;
		this.radius = radius;
		this.affectsCaster = affectsCaster;
		this.spellbookIconItemId = spellbookIconItemId;
		this.iconAssetKey = iconAssetKey;
		this.stoneSigilItemId = stoneSigilItemId;
		this.stoneSigilCount = stoneSigilCount;
		this.silverSigilItemId = silverSigilItemId;
		this.silverSigilCount = silverSigilCount;
		this.casterIconItemId = casterIconItemId;
		this.casterAnimationId = casterAnimationId;
		this.effectRanks = Collections.unmodifiableList(rankCopy);
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	public int getStableCode() { return stableCode; }
	public String getStableKey() { return stableKey; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public String getAlignment() { return alignment; }
	public int getWorshipLevel() { return worshipLevel; }
	public int getSpellTier() { return spellTier; }
	public int getRadius() { return radius; }
	public boolean affectsCaster() { return affectsCaster; }
	public int getSpellbookIconItemId() { return spellbookIconItemId; }
	public String getIconAssetKey() { return iconAssetKey; }
	public int getStoneSigilItemId() { return stoneSigilItemId; }
	public int getStoneSigilCount() { return stoneSigilCount; }
	public int getSilverSigilItemId() { return silverSigilItemId; }
	public int getSilverSigilCount() { return silverSigilCount; }
	public int getCasterIconItemId() { return casterIconItemId; }
	public boolean hasCasterIcon() { return casterIconItemId >= 0; }
	/** Compatibility accessor for the original presentation wire field name. */
	public int getCasterAnimationId() { return getOnEntityAnimationId(); }
	/** Compatibility predicate for the original presentation wire field name. */
	public boolean hasCasterAnimation() { return hasOnEntityAnimation(); }
	public int getOnEntityAnimationId() { return casterAnimationId; }
	public boolean hasOnEntityAnimation() { return casterAnimationId >= 0; }
	public List<ClericEffectRankDef> getEffectRanks() { return effectRanks; }
	public ClericEffectRankDef getEffectRank(int rank) {
		return rank <= 0 || rank > effectRanks.size() ? null : effectRanks.get(rank - 1);
	}
}
