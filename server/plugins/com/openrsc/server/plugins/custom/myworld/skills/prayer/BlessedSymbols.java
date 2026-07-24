package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

public final class BlessedSymbols implements UseLocTrigger {
	private static final int SYMBOL_DEVOTION_REQUIREMENT = 50;
	private static final int SYMBOL_CRAFTING_XP = 200;

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return false;
		}
		return PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY()) != null
			&& isUnblessedSymbol(item.getCatalogId());
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return;
		}

		final PrayerCatalog.GodLine godLine = PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY());
		if (godLine == null || !isUnblessedSymbol(item.getCatalogId())) {
			return;
		}

		final int productId = getBlessedSymbolProduct(godLine, item.getCatalogId());
		if (productId == -1) {
			player.message("This symbol is not aligned with " + formatGodLine(godLine) + ".");
			return;
		}

		PrayerBlessingTransaction.bless(
			player,
			godLine,
			item,
			productId,
			SYMBOL_DEVOTION_REQUIREMENT,
			0,
			SYMBOL_CRAFTING_XP,
			"The altar blesses the symbol."
		);
	}

	private boolean isUnblessedSymbol(final int itemId) {
		return itemId == ItemId.UNBLESSED_HOLY_SYMBOL.id()
			|| itemId == ItemId.UNBLESSED_UNHOLY_SYMBOL_OF_ZAMORAK.id()
			|| itemId == ItemId.UNBLESSED_GUTHIX_SYMBOL.id();
	}

	private int getBlessedSymbolProduct(final PrayerCatalog.GodLine godLine, final int itemId) {
		if (godLine == PrayerCatalog.GodLine.SARADOMIN && itemId == ItemId.UNBLESSED_HOLY_SYMBOL.id()) {
			return ItemId.HOLY_SYMBOL_OF_SARADOMIN.id();
		}
		if (godLine == PrayerCatalog.GodLine.ZAMORAK && itemId == ItemId.UNBLESSED_UNHOLY_SYMBOL_OF_ZAMORAK.id()) {
			return ItemId.UNHOLY_SYMBOL_OF_ZAMORAK.id();
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX && itemId == ItemId.UNBLESSED_GUTHIX_SYMBOL.id()) {
			return ItemId.GUTHIX_SYMBOL.id();
		}
		return -1;
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

}
