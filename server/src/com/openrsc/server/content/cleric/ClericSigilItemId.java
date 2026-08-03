package com.openrsc.server.content.cleric;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Stable item identities for the inert Cleric sigil foundation.
 *
 * <p>These identities intentionally live outside the legacy {@code ItemId}
 * enum, whose generated static initializer is already at the JVM method-size
 * limit. Protocol, storage, and later gameplay code must use the explicit item
 * ID rather than the enum ordinal.</p>
 */
public enum ClericSigilItemId {
	UNBLESSED_STONE_SARADOMIN_SIGIL(3293, ClericSigilMaterial.STONE, ClericAlignment.SARADOMIN, false),
	BLESSED_STONE_SARADOMIN_SIGIL(3294, ClericSigilMaterial.STONE, ClericAlignment.SARADOMIN, true),
	UNBLESSED_STONE_GUTHIX_SIGIL(3295, ClericSigilMaterial.STONE, ClericAlignment.GUTHIX, false),
	BLESSED_STONE_GUTHIX_SIGIL(3296, ClericSigilMaterial.STONE, ClericAlignment.GUTHIX, true),
	UNBLESSED_STONE_ZAMORAK_SIGIL(3297, ClericSigilMaterial.STONE, ClericAlignment.ZAMORAK, false),
	BLESSED_STONE_ZAMORAK_SIGIL(3298, ClericSigilMaterial.STONE, ClericAlignment.ZAMORAK, true),
	UNBLESSED_STONE_NEUTRAL_SIGIL(3299, ClericSigilMaterial.STONE, ClericAlignment.NEUTRAL, false),
	BLESSED_STONE_NEUTRAL_SIGIL(3300, ClericSigilMaterial.STONE, ClericAlignment.NEUTRAL, true),
	UNBLESSED_SILVER_SARADOMIN_SIGIL(3301, ClericSigilMaterial.SILVER, ClericAlignment.SARADOMIN, false),
	BLESSED_SILVER_SARADOMIN_SIGIL(3302, ClericSigilMaterial.SILVER, ClericAlignment.SARADOMIN, true),
	UNBLESSED_SILVER_GUTHIX_SIGIL(3303, ClericSigilMaterial.SILVER, ClericAlignment.GUTHIX, false),
	BLESSED_SILVER_GUTHIX_SIGIL(3304, ClericSigilMaterial.SILVER, ClericAlignment.GUTHIX, true),
	UNBLESSED_SILVER_ZAMORAK_SIGIL(3305, ClericSigilMaterial.SILVER, ClericAlignment.ZAMORAK, false),
	BLESSED_SILVER_ZAMORAK_SIGIL(3306, ClericSigilMaterial.SILVER, ClericAlignment.ZAMORAK, true),
	UNBLESSED_SILVER_NEUTRAL_SIGIL(3307, ClericSigilMaterial.SILVER, ClericAlignment.NEUTRAL, false),
	BLESSED_SILVER_NEUTRAL_SIGIL(3308, ClericSigilMaterial.SILVER, ClericAlignment.NEUTRAL, true);

	private static final Map<Integer, ClericSigilItemId> BY_ITEM_ID;

	static {
		Map<Integer, ClericSigilItemId> identities = new HashMap<Integer, ClericSigilItemId>();
		for (ClericSigilItemId identity : ClericSigilItemId.values()) {
			if (identities.put(identity.itemId, identity) != null) {
				throw new IllegalStateException("Duplicate Cleric sigil item ID: " + identity.itemId);
			}
		}
		BY_ITEM_ID = Collections.unmodifiableMap(identities);
	}

	private final int itemId;
	private final ClericSigilMaterial material;
	private final ClericAlignment alignment;
	private final boolean blessed;

	ClericSigilItemId(int itemId, ClericSigilMaterial material,
			ClericAlignment alignment, boolean blessed) {
		this.itemId = itemId;
		this.material = material;
		this.alignment = alignment;
		this.blessed = blessed;
	}

	public int getItemId() {
		return itemId;
	}

	public ClericSigilMaterial getMaterial() {
		return material;
	}

	public ClericAlignment getAlignment() {
		return alignment;
	}

	public boolean isBlessed() {
		return blessed;
	}

	public static ClericSigilItemId fromItemId(int itemId) {
		ClericSigilItemId identity = BY_ITEM_ID.get(itemId);
		if (identity == null) {
			throw new IllegalArgumentException("Unknown Cleric sigil item ID: " + itemId);
		}
		return identity;
	}
}
