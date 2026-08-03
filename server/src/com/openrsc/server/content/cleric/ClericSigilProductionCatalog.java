package com.openrsc.server.content.cleric;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Authoritative recipes and pure production math for launch sigils. */
public final class ClericSigilProductionCatalog {
	public static final int INTERNAL_EXPERIENCE_UNITS_PER_DISPLAYED_POINT = 4;

	private static final List<ClericSigilItemId> UNBLESSED_IDENTITIES;
	private static final Map<Integer, ClericSigilItemId> BY_UNBLESSED_ITEM_ID;

	static {
		List<ClericSigilItemId> identities = new ArrayList<ClericSigilItemId>();
		Map<Integer, ClericSigilItemId> itemIds = new HashMap<Integer, ClericSigilItemId>();
		for (ClericSigilMaterial material : ClericSigilMaterial.values()) {
			for (ClericAlignment alignment : new ClericAlignment[]{
				ClericAlignment.SARADOMIN,
				ClericAlignment.GUTHIX,
				ClericAlignment.ZAMORAK,
				ClericAlignment.NEUTRAL
			}) {
				ClericSigilItemId identity = ClericSigilItemId.get(material, alignment, false);
				identities.add(identity);
				if (itemIds.put(identity.getItemId(), identity) != null) {
					throw new IllegalStateException("Duplicate unblessed Cleric sigil item ID");
				}
			}
		}
		UNBLESSED_IDENTITIES = Collections.unmodifiableList(identities);
		BY_UNBLESSED_ITEM_ID = Collections.unmodifiableMap(itemIds);
	}

	private ClericSigilProductionCatalog() {
	}

	public static List<ClericSigilItemId> getUnblessedIdentities() {
		return UNBLESSED_IDENTITIES;
	}

	public static List<ClericSigilItemId> getUnblessedIdentities(ClericSigilMaterial material) {
		if (material == null) {
			throw new IllegalArgumentException("Cleric sigil material is required");
		}
		List<ClericSigilItemId> matches = new ArrayList<ClericSigilItemId>();
		for (ClericSigilItemId identity : UNBLESSED_IDENTITIES) {
			if (identity.getMaterial() == material) {
				matches.add(identity);
			}
		}
		return Collections.unmodifiableList(matches);
	}

	public static ClericSigilItemId fromUnblessedItemId(int itemId) {
		ClericSigilItemId identity = BY_UNBLESSED_ITEM_ID.get(itemId);
		if (identity == null) {
			throw new IllegalArgumentException("Unknown unblessed Cleric sigil item ID: " + itemId);
		}
		return identity;
	}

	public static ClericSigilMaterial fromSourceItemId(int itemId) {
		for (ClericSigilMaterial material : ClericSigilMaterial.values()) {
			if (material.getSourceItemId() == itemId) {
				return material;
			}
		}
		throw new IllegalArgumentException("Unknown Cleric sigil source item ID: " + itemId);
	}

	public static int getOutputMultiplier(int blessingLevel, ClericSigilMaterial material) {
		if (material == null) {
			throw new IllegalArgumentException("Cleric sigil material is required");
		}
		if (blessingLevel < material.getBlessingLevel()) {
			return 0;
		}
		return 1 + (blessingLevel - material.getBlessingLevel()) / 10;
	}

	public static int getBlessedOutputCount(int inputCount, int outputMultiplier) {
		if (inputCount <= 0 || outputMultiplier <= 0) {
			throw new IllegalArgumentException("Sigil input count and multiplier must be positive");
		}
		return Math.multiplyExact(inputCount, outputMultiplier);
	}

	public static int toInternalExperience(int displayedExperience) {
		if (displayedExperience < 0) {
			throw new IllegalArgumentException("Displayed experience cannot be negative");
		}
		return Math.multiplyExact(displayedExperience, INTERNAL_EXPERIENCE_UNITS_PER_DISPLAYED_POINT);
	}

	/** Mirrors rune production's exact diminishing 1x, 1.5x, 1.75x... series. */
	public static int getDiminishingInternalExperience(int displayedBaseExperience,
			int processedInputCount, int outputMultiplier) {
		if (displayedBaseExperience <= 0 || processedInputCount <= 0 || outputMultiplier <= 0) {
			return 0;
		}
		final BigInteger internalBaseExperience = BigInteger.valueOf(
			(long) toInternalExperience(displayedBaseExperience) * processedInputCount);
		final BigInteger doubledBaseExperience = internalBaseExperience.shiftLeft(1);
		final int denominatorExponent = outputMultiplier - 1;
		final BigInteger roundedExperience;
		if (denominatorExponent >= doubledBaseExperience.bitLength()) {
			roundedExperience = doubledBaseExperience;
		} else {
			final BigInteger denominator = BigInteger.ONE.shiftLeft(denominatorExponent);
			final BigInteger numerator = doubledBaseExperience.multiply(denominator)
				.subtract(internalBaseExperience);
			roundedExperience = numerator.add(denominator.shiftRight(1)).divide(denominator);
		}
		return roundedExperience.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
	}
}
