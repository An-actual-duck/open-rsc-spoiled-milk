package com.openrsc.server.plugins.custom.myworld.skills.prayer;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Devotion;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PrayerCatalog;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import static com.openrsc.server.plugins.Functions.give;

public final class BlessedWoolArmor implements UseLocTrigger {

	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		return item != null
			&& !item.getNoted()
			&& PrayerCatalog.getGodLineForAltar(obj.getID(), obj.getX(), obj.getY()) != null
			&& EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId());
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
		if (!EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())) {
			player.message("Only wool armour can be blessed this way.");
			return;
		}

		final int productId = getGodWoolProduct(godLine, item.getCatalogId());
		if (productId == -1) {
			player.message("This wool armour cannot be blessed yet.");
			return;
		}

		final int resourceCost = getWoolResourceCost(item.getCatalogId());
		final int devotionRequirement = Devotion.getDevotionRequirementForResourceCost(resourceCost);
		final int currentDevotion = Devotion.getDevotionLevel(player, godLine);
		if (devotionRequirement > 0 && currentDevotion < devotionRequirement) {
			player.message("You need " + devotionRequirement + " devotion to " + formatGodLine(godLine) + " to bless that wool armour.");
			player.message("Your current devotion to " + formatGodLine(godLine) + " is " + currentDevotion + ".");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		give(player, productId, 1);
		final int prayerXp = Devotion.getBlessingPrayerXp(player, godLine, getWoolCraftingXp(item.getCatalogId()));
		if (prayerXp > 0) {
			player.incExp(Skill.PRAYER.id(), prayerXp, true);
		}
		player.message("The altar blesses the wool armour.");
	}

	private int getGodWoolProduct(final PrayerCatalog.GodLine godLine, final int itemId) {
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
			case 2050: // WOOL_WIZARD_HAT
				return ItemId.ZAMORAK_WOOL_HAT.id();
			case 2051: // WOOL_ROBE_TOP
				return ItemId.ZAMORAK_WOOL_ROBE_TOP.id();
			case 2052: // WOOL_ROBE_SKIRT
				return ItemId.ZAMORAK_WOOL_ROBE_BOTTOM.id();
			case 2794: // WOOL_GLOVES
				return ItemId.ZAMORAK_WOOL_GLOVES.id();
			case 2795: // WOOL_BOOTS
				return ItemId.ZAMORAK_WOOL_BOOTS.id();
			default:
				return -1;
		}
	}

	private int getSaradominProduct(final int itemId) {
		switch (itemId) {
			case 2050: // WOOL_WIZARD_HAT
				return ItemId.SARADOMIN_WOOL_HAT.id();
			case 2051: // WOOL_ROBE_TOP
				return ItemId.SARADOMIN_WOOL_ROBE_TOP.id();
			case 2052: // WOOL_ROBE_SKIRT
				return ItemId.SARADOMIN_WOOL_ROBE_BOTTOM.id();
			case 2794: // WOOL_GLOVES
				return ItemId.SARADOMIN_WOOL_GLOVES.id();
			case 2795: // WOOL_BOOTS
				return ItemId.SARADOMIN_WOOL_BOOTS.id();
			default:
				return -1;
		}
	}

	private int getGuthixProduct(final int itemId) {
		switch (itemId) {
			case 2050: // WOOL_WIZARD_HAT
				return ItemId.GUTHIX_WOOL_HAT.id();
			case 2051: // WOOL_ROBE_TOP
				return ItemId.GUTHIX_WOOL_ROBE_TOP.id();
			case 2052: // WOOL_ROBE_SKIRT
				return ItemId.GUTHIX_WOOL_ROBE_BOTTOM.id();
			case 2794: // WOOL_GLOVES
				return ItemId.GUTHIX_WOOL_GLOVES.id();
			case 2795: // WOOL_BOOTS
				return ItemId.GUTHIX_WOOL_BOOTS.id();
			default:
				return -1;
		}
	}

	private int getWoolResourceCost(final int itemId) {
		switch (itemId) {
			case 2050: // WOOL_WIZARD_HAT
				return 1;
			case 2051: // WOOL_ROBE_TOP
				return 4;
			case 2052: // WOOL_ROBE_SKIRT
				return 3;
			case 2794: // WOOL_GLOVES
			case 2795: // WOOL_BOOTS
				return 2;
			default:
				return 0;
		}
	}

	private int getWoolCraftingXp(final int itemId) {
		return getWoolResourceCost(itemId) * 6;
	}

	private String formatGodLine(final PrayerCatalog.GodLine godLine) {
		final String lower = godLine.name().toLowerCase();
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
