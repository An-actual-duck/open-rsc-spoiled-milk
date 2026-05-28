package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.CombatStyleStruct;
import com.openrsc.server.util.rsc.Formulae;

public class CombatStyleHandler implements PayloadProcessor<CombatStyleStruct, OpcodeIn> {

	public void process(final CombatStyleStruct payload, final Player player) throws Exception {

		int style = payload.style;
		if (style < Skills.CONTROLLED_MODE || style > Skills.DEFENSIVE_MODE) {
			player.setSuspiciousPlayer(true, "style handler style < 0 or style > 3");
			return;
		}
		player.setCombatStyle(style);
		if (player.getConfig().WANT_MYWORLD) {
			player.message(getGatheringFocusMessage(player, style));
		}
	}

	private String getGatheringFocusMessage(Player player, int style) {
		if (hasEquippedFishingRod(player)) {
			return "Fishing focus set to " + fishingFocusLabel(style);
		}
		if (hasEquippedWoodcuttingTool(player)) {
			return "Woodcutting focus set to " + seedFocusLabel(style);
		}
		if (hasEquippedHarvestingShears(player)) {
			return "Harvesting focus set to " + seedFocusLabel(style);
		}
		return "Mining focus set to " + miningFocusLabel(style);
	}

	private String miningFocusLabel(int style) {
		switch (style) {
			case Skills.CONTROLLED_MODE:
				return "Just the ore";
			case Skills.AGGRESSIVE_MODE:
				return "A few gems";
			case Skills.ACCURATE_MODE:
				return "Plenty of gems";
			case Skills.DEFENSIVE_MODE:
				return "Lots of gems";
			default:
				return "A few gems";
		}
	}

	private String fishingFocusLabel(int style) {
		switch (style) {
			case Skills.CONTROLLED_MODE:
				return "Just the fish";
			case Skills.AGGRESSIVE_MODE:
				return "A little loot";
			case Skills.ACCURATE_MODE:
				return "Plenty of loot";
			case Skills.DEFENSIVE_MODE:
				return "Lots of loot";
			default:
				return "A little loot";
		}
	}

	private String seedFocusLabel(int style) {
		switch (style) {
			case Skills.CONTROLLED_MODE:
				return "No seeds for me";
			case Skills.AGGRESSIVE_MODE:
				return "A few seeds";
			case Skills.ACCURATE_MODE:
				return "More seeds";
			case Skills.DEFENSIVE_MODE:
				return "Even more seeds!";
			default:
				return "A few seeds";
		}
	}

	private boolean hasEquippedFishingRod(Player player) {
		return player.getCarriedItems().getEquipment().hasCatalogID(ItemId.FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.PINE_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.OAK_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.WILLOW_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.PALM_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.MAPLE_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.YEW_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.EBONY_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.MAGIC_FISHING_ROD.id())
			|| player.getCarriedItems().getEquipment().hasCatalogID(ItemId.BLOOD_FISHING_ROD.id());
	}

	private boolean hasEquippedWoodcuttingTool(Player player) {
		for (int axeId : Formulae.woodcuttingAxeIDs) {
			if (player.getCarriedItems().getEquipment().hasCatalogID(axeId)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasEquippedHarvestingShears(Player player) {
		for (int shearsId : Formulae.harvestingShearsIDs) {
			if (player.getCarriedItems().getEquipment().hasCatalogID(shearsId)) {
				return true;
			}
		}
		return false;
	}

}
