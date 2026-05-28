package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import static com.openrsc.server.plugins.Functions.give;

public final class GodKnightEquipment implements UseLocTrigger {

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return false;
		}
		final PrayerCatalog.GodLine godLine = PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY());
		return godLine != null && isSteelKnightSource(item.getCatalogId());
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		if (item == null || item.getNoted()) {
			return;
		}
		final PrayerCatalog.GodLine godLine = PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY());
		if (godLine == null) {
			return;
		}
		if (!isSteelKnightSource(item.getCatalogId())) {
			player.message("Only steel equipment can be blessed this way.");
			return;
		}

		final int productId = getGodKnightProduct(godLine, item.getCatalogId());
		if (productId == -1) {
			player.message("This god's knight equipment is not ready for that steel piece yet.");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		give(player, productId, 1);
		player.message("The altar blesses the steel equipment.");
	}

	private boolean isSteelKnightSource(final int itemId) {
		return getZamorakProduct(itemId) != -1
			|| getSaradominProduct(itemId) != -1
			|| getGuthixProduct(itemId) != -1;
	}

	private int getGodKnightProduct(final PrayerCatalog.GodLine godLine, final int itemId) {
		if (godLine == PrayerCatalog.GodLine.ZAMORAK) {
			return getZamorakProduct(itemId);
		}
		if (godLine == PrayerCatalog.GodLine.SARADOMIN) {
			return getSaradominProduct(itemId);
		}
		if (godLine == PrayerCatalog.GodLine.GUTHIX) {
			return getGuthixProduct(itemId);
		}
		return -1;
	}

	private int getZamorakProduct(final int itemId) {
		switch (itemId) {
			case 63: // STEEL_DAGGER
				return ItemId.BLACK_DAGGER.id();
			case 67: // STEEL_SHORT_SWORD
				return ItemId.BLACK_SHORT_SWORD.id();
			case 72: // STEEL_LONG_SWORD
				return ItemId.BLACK_LONG_SWORD.id();
			case 78: // STEEL_2_HANDED_SWORD
				return ItemId.BLACK_2_HANDED_SWORD.id();
			case 84: // STEEL_SCIMITAR
				return ItemId.BLACK_SCIMITAR.id();
			case 90: // STEEL_BATTLE_AXE
				return ItemId.BLACK_BATTLE_AXE.id();
			case 95: // STEEL_MACE
				return ItemId.BLACK_MACE.id();
			case 105: // MEDIUM_STEEL_HELMET
				return ItemId.MEDIUM_BLACK_HELMET.id();
			case 109: // LARGE_STEEL_HELMET
				return ItemId.LARGE_BLACK_HELMET.id();
			case 114: // STEEL_CHAIN_MAIL_BODY
				return ItemId.BLACK_CHAIN_MAIL_BODY.id();
			case 1532: // STEEL_CHAIN_MAIL_TOP
				return ItemId.BLACK_CHAIN_MAIL_TOP.id();
			case 1420: // STEEL_CHAIN_MAIL_LEGS
				return ItemId.BLACK_CHAIN_MAIL_LEGS.id();
			case 125: // STEEL_SQUARE_SHIELD
				return ItemId.BLACK_SQUARE_SHIELD.id();
			case 129: // STEEL_KITE_SHIELD
				return ItemId.BLACK_KITE_SHIELD.id();
			case 118: // STEEL_PLATE_MAIL_BODY
				return ItemId.BLACK_PLATE_MAIL_BODY.id();
			case 309: // STEEL_PLATE_MAIL_TOP
				return ItemId.BLACK_PLATE_MAIL_TOP.id();
			case 121: // STEEL_PLATE_MAIL_LEGS
				return ItemId.BLACK_PLATE_MAIL_LEGS.id();
			case 225: // STEEL_PLATED_SKIRT
				return ItemId.BLACK_PLATED_SKIRT.id();
			default:
				return -1;
		}
	}

	private int getSaradominProduct(final int itemId) {
		switch (itemId) {
			case 63: // STEEL_DAGGER
				return ItemId.WHITE_DAGGER.id();
			case 67: // STEEL_SHORT_SWORD
				return ItemId.WHITE_SHORT_SWORD.id();
			case 72: // STEEL_LONG_SWORD
				return ItemId.WHITE_LONG_SWORD.id();
			case 78: // STEEL_2_HANDED_SWORD
				return ItemId.WHITE_2_HANDED_SWORD.id();
			case 84: // STEEL_SCIMITAR
				return ItemId.WHITE_SCIMITAR.id();
			case 90: // STEEL_BATTLE_AXE
				return ItemId.WHITE_BATTLE_AXE.id();
			case 95: // STEEL_MACE
				return ItemId.WHITE_MACE.id();
			case 105: // MEDIUM_STEEL_HELMET
				return ItemId.MEDIUM_WHITE_HELMET.id();
			case 109: // LARGE_STEEL_HELMET
				return ItemId.LARGE_WHITE_HELMET.id();
			case 114: // STEEL_CHAIN_MAIL_BODY
				return ItemId.WHITE_CHAIN_MAIL_BODY.id();
			case 1532: // STEEL_CHAIN_MAIL_TOP
				return ItemId.WHITE_CHAIN_MAIL_TOP.id();
			case 1420: // STEEL_CHAIN_MAIL_LEGS
				return ItemId.WHITE_CHAIN_MAIL_LEGS.id();
			case 125: // STEEL_SQUARE_SHIELD
				return ItemId.WHITE_SQUARE_SHIELD.id();
			case 129: // STEEL_KITE_SHIELD
				return ItemId.WHITE_KITE_SHIELD.id();
			case 118: // STEEL_PLATE_MAIL_BODY
				return ItemId.WHITE_PLATE_MAIL_BODY.id();
			case 309: // STEEL_PLATE_MAIL_TOP
				return ItemId.WHITE_PLATE_MAIL_TOP.id();
			case 121: // STEEL_PLATE_MAIL_LEGS
				return ItemId.WHITE_PLATE_MAIL_LEGS.id();
			case 225: // STEEL_PLATED_SKIRT
				return ItemId.WHITE_PLATED_SKIRT.id();
			default:
				return -1;
		}
	}

	private int getGuthixProduct(final int itemId) {
		switch (itemId) {
			case 63: // STEEL_DAGGER
				return ItemId.GREY_DAGGER.id();
			case 67: // STEEL_SHORT_SWORD
				return ItemId.GREY_SHORT_SWORD.id();
			case 72: // STEEL_LONG_SWORD
				return ItemId.GREY_LONG_SWORD.id();
			case 78: // STEEL_2_HANDED_SWORD
				return ItemId.GREY_2_HANDED_SWORD.id();
			case 84: // STEEL_SCIMITAR
				return ItemId.GREY_SCIMITAR.id();
			case 90: // STEEL_BATTLE_AXE
				return ItemId.GREY_BATTLE_AXE.id();
			case 95: // STEEL_MACE
				return ItemId.GREY_MACE.id();
			case 105: // MEDIUM_STEEL_HELMET
				return ItemId.MEDIUM_GREY_HELMET.id();
			case 109: // LARGE_STEEL_HELMET
				return ItemId.LARGE_GREY_HELMET.id();
			case 114: // STEEL_CHAIN_MAIL_BODY
				return ItemId.GREY_CHAIN_MAIL_BODY.id();
			case 1532: // STEEL_CHAIN_MAIL_TOP
				return ItemId.GREY_CHAIN_MAIL_TOP.id();
			case 1420: // STEEL_CHAIN_MAIL_LEGS
				return ItemId.GREY_CHAIN_MAIL_LEGS.id();
			case 125: // STEEL_SQUARE_SHIELD
				return ItemId.GREY_SQUARE_SHIELD.id();
			case 129: // STEEL_KITE_SHIELD
				return ItemId.GREY_KITE_SHIELD.id();
			case 118: // STEEL_PLATE_MAIL_BODY
				return ItemId.GREY_PLATE_MAIL_BODY.id();
			case 309: // STEEL_PLATE_MAIL_TOP
				return ItemId.GREY_PLATE_MAIL_TOP.id();
			case 121: // STEEL_PLATE_MAIL_LEGS
				return ItemId.GREY_PLATE_MAIL_LEGS.id();
			case 225: // STEEL_PLATED_SKIRT
				return ItemId.GREY_PLATED_SKIRT.id();
			default:
				return -1;
		}
	}
}
