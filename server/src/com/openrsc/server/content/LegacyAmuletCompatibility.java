package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.custom.MyWorldItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemStatus;

/**
 * Canonical ownership conversion for the retired standard craftable Amulet
 * family.
 *
 * <p>The legacy constants and definitions remain addressable so old database
 * rows and preset blobs can be decoded. Runtime ownership is converted to the
 * equivalent completed Bangle without replacing the item instance or its
 * status. Classic enchanted IDs are deliberately not mapped here: those IDs
 * now directly define wrist-slot Bangle products.</p>
 */
public final class LegacyAmuletCompatibility {
	private LegacyAmuletCompatibility() {
	}

	public static int canonicalCatalogId(final int catalogId) {
		if (catalogId == ItemId.AMULET_MOULD.id()) {
			return MyWorldItemId.BANGLE_MOULD;
		}
		switch (ItemId.getById(catalogId)) {
			case UNSTRUNG_GOLD_AMULET:
			case GOLD_AMULET:
				return MyWorldItemId.GOLD_BANGLE;
			case UNSTRUNG_SAPPHIRE_AMULET:
			case SAPPHIRE_AMULET:
				return MyWorldItemId.SAPPHIRE_BANGLE;
			case UNSTRUNG_EMERALD_AMULET:
			case EMERALD_AMULET:
				return MyWorldItemId.EMERALD_BANGLE;
			case UNSTRUNG_RUBY_AMULET:
			case RUBY_AMULET:
				return MyWorldItemId.RUBY_BANGLE;
			case UNSTRUNG_DIAMOND_AMULET:
			case DIAMOND_AMULET:
				return MyWorldItemId.DIAMOND_BANGLE;
			case DRAGONSTONE_AMULET:
			case UNSTRUNG_DRAGONSTONE_AMULET:
			case UNENCHANTED_DRAGONSTONE_AMULET:
				return MyWorldItemId.DRAGONSTONE_BANGLE;
			default:
				return catalogId;
		}
	}

	public static boolean isRetiredCatalogId(final int catalogId) {
		return canonicalCatalogId(catalogId) != catalogId;
	}

	public static boolean canonicalize(final Item item) {
		if (item == null) {
			return false;
		}
		return canonicalize(item.getItemStatus());
	}

	public static boolean canonicalize(final ItemStatus status) {
		if (status == null) {
			return false;
		}
		final int canonicalId = canonicalCatalogId(status.getCatalogId());
		if (canonicalId == status.getCatalogId()) {
			return false;
		}
		status.setCatalogId(canonicalId);
		return true;
	}
}
