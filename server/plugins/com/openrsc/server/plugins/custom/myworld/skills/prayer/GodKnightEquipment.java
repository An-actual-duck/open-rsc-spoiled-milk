package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
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

		final int devotionRequirement = getDevotionRequirement(item.getCatalogId());
		final int currentDevotion = Devotion.getDevotionLevel(player, godLine);
		if (devotionRequirement > 0 && currentDevotion < devotionRequirement) {
			player.message("You need " + devotionRequirement + " devotion to " + formatGodLine(godLine) + " to bless that equipment.");
			player.message("Your current devotion to " + formatGodLine(godLine) + " is " + currentDevotion + ".");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		give(player, productId, 1);
		player.message("The altar blesses the steel equipment.");
		if (devotionRequirement > 0) {
			final int prayerXp = Devotion.getBlessingPrayerXp(player, godLine, getSteelSmithingXp(item.getCatalogId()));
			if (prayerXp > 0) {
				player.incExp(Skill.PRAYER.id(), prayerXp, true);
			}
		}
	}

	private int getDevotionRequirement(final int itemId) {
		return Devotion.getDevotionRequirementForResourceCost(getSteelResourceCost(itemId));
	}

	private int getSteelSmithingXp(final int itemId) {
		final int barCost = getSteelResourceCost(itemId);
		return barCost > 0 ? barCost * 150 : 0;
	}

	private int getSteelResourceCost(final int itemId) {
		switch (itemId) {
			case 63: // STEEL_DAGGER
			case 67: // STEEL_SHORT_SWORD
			case 95: // STEEL_MACE
				return 1;
			case 72: // STEEL_LONG_SWORD
			case 84: // STEEL_SCIMITAR
			case 698: // STEEL_GAUNTLETS
			case 1988: // STEEL_GREAVES
			case 109: // LARGE_STEEL_HELMET
				return 2;
			case 78: // STEEL_2_HANDED_SWORD
			case 90: // STEEL_BATTLE_AXE
			case 129: // STEEL_KITE_SHIELD
			case 121: // STEEL_PLATE_MAIL_LEGS
				return 3;
			case 118: // STEEL_PLATE_MAIL_BODY
				return 4;
			default:
				return 0;
		}
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
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
			case 109: // LARGE_STEEL_HELMET
				return ItemId.LARGE_BLACK_HELMET.id();
			case 129: // STEEL_KITE_SHIELD
				return ItemId.BLACK_KITE_SHIELD.id();
			case 118: // STEEL_PLATE_MAIL_BODY
				return ItemId.BLACK_PLATE_MAIL_BODY.id();
			case 121: // STEEL_PLATE_MAIL_LEGS
				return ItemId.BLACK_PLATE_MAIL_LEGS.id();
			case 698: // STEEL_GAUNTLETS
				return ItemId.BLACK_GAUNTLETS.id();
			case 1988: // STEEL_GREAVES
				return ItemId.BLACK_GREAVES.id();
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
			case 109: // LARGE_STEEL_HELMET
				return ItemId.LARGE_WHITE_HELMET.id();
			case 129: // STEEL_KITE_SHIELD
				return ItemId.WHITE_KITE_SHIELD.id();
			case 118: // STEEL_PLATE_MAIL_BODY
				return ItemId.WHITE_PLATE_MAIL_BODY.id();
			case 121: // STEEL_PLATE_MAIL_LEGS
				return ItemId.WHITE_PLATE_MAIL_LEGS.id();
			case 698: // STEEL_GAUNTLETS
				return ItemId.WHITE_GAUNTLETS.id();
			case 1988: // STEEL_GREAVES
				return ItemId.WHITE_GREAVES.id();
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
			case 109: // LARGE_STEEL_HELMET
				return ItemId.LARGE_GREY_HELMET.id();
			case 129: // STEEL_KITE_SHIELD
				return ItemId.GREY_KITE_SHIELD.id();
			case 118: // STEEL_PLATE_MAIL_BODY
				return ItemId.GREY_PLATE_MAIL_BODY.id();
			case 121: // STEEL_PLATE_MAIL_LEGS
				return ItemId.GREY_PLATE_MAIL_LEGS.id();
			case 698: // STEEL_GAUNTLETS
				return ItemId.GREY_GAUNTLETS.id();
			case 1988: // STEEL_GREAVES
				return ItemId.GREY_GREAVES.id();
			default:
				return -1;
		}
	}
}
