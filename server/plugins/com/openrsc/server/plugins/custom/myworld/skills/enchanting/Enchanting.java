package com.openrsc.server.plugins.custom.myworld.skills.enchanting;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.UseLocTrigger;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.give;

public final class Enchanting implements UseLocTrigger {
	@Override
	public boolean blockUseLoc(final Player player, final GameObject obj, final Item item) {
		return item != null
			&& !item.getNoted()
			&& (EnchantingItemEffects.isAmuletBase(item.getCatalogId())
				|| EnchantingItemEffects.isNecklaceBase(item.getCatalogId())
				|| EnchantingItemEffects.isRingBase(item.getCatalogId())
				|| EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId())
				|| EnchantingItemEffects.isEnchantedWoolRobePiece(item.getCatalogId())
				|| EnchantingItemEffects.isBaseStaff(item.getCatalogId()))
			&& normalizeAltarId(obj.getID()) != -1;
	}

	@Override
	public void onUseLoc(final Player player, final GameObject obj, final Item item) {
		final int altarId = normalizeAltarId(obj.getID());
		if (altarId == -1 || item == null || item.getNoted()) {
			return;
		}

		final boolean amulet = EnchantingItemEffects.isAmuletBase(item.getCatalogId());
		final boolean necklace = EnchantingItemEffects.isNecklaceBase(item.getCatalogId());
		final boolean ring = EnchantingItemEffects.isRingBase(item.getCatalogId());
		final boolean robe = EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId());
		final boolean enchantedRobe = EnchantingItemEffects.isEnchantedWoolRobePiece(item.getCatalogId());
		final boolean staff = EnchantingItemEffects.isBaseStaff(item.getCatalogId());
		if (robe || enchantedRobe) {
			enchantOrUpgradeRobe(player, altarId, item);
			return;
		}

		final int productId = amulet
			? EnchantingItemEffects.getAmuletProduct(altarId, item.getCatalogId())
			: necklace ? EnchantingItemEffects.getNecklaceProduct(altarId, item.getCatalogId())
			: ring ? EnchantingItemEffects.getRingProduct(altarId, item.getCatalogId())
			: staff ? EnchantingItemEffects.getStaffProduct(altarId, item.getCatalogId()) : -1;
		final int tier = amulet
			? EnchantingItemEffects.getTierForBaseAmulet(item.getCatalogId())
			: necklace ? EnchantingItemEffects.getTierForBaseNecklace(item.getCatalogId())
			: ring ? EnchantingItemEffects.getTierForBaseRing(item.getCatalogId())
			: staff ? EnchantingItemEffects.getTierForBaseStaff(item.getCatalogId()) : -1;
		final int altarLevelRequirement = EnchantingItemEffects.getAltarLevelRequirement(altarId);
		final int runeId = EnchantingItemEffects.getRuneForAltar(altarId);
		if (runeId == -1 || altarLevelRequirement == -1 || (!robe && (productId == -1 || tier == -1))) {
			player.message("Nothing interesting happens.");
			return;
		}

		if (productId == -1) {
			player.message("Nothing interesting happens.");
			return;
		}

		if (player.getSkills().getLevel(Skill.RUNECRAFT.id()) < altarLevelRequirement) {
			player.message("You require more Enchanting skill to use this altar.");
			return;
		}

		final int itemLevelRequirement = staff
			? EnchantingItemEffects.getStaffEnchantingRequirementForTier(tier)
			: EnchantingItemEffects.getJewelryEnchantingRequirementForTier(tier);
		if (itemLevelRequirement == -1) {
			player.message("Nothing interesting happens.");
			return;
		}
		if (player.getSkills().getLevel(Skill.RUNECRAFT.id()) < itemLevelRequirement) {
			player.message(staff
				? "You require more Enchanting skill to attune this staff."
				: "You require more Enchanting skill to empower this jewelry.");
			return;
		}

		final int runeCost = staff
			? EnchantingItemEffects.getStaffRuneCost(tier)
			: EnchantingItemEffects.getRuneCostForTier(tier);
		if (player.getCarriedItems().getInventory().countId(runeId, Optional.of(false)) < runeCost) {
			player.message(staff
				? "You need more altar runes to attune this staff."
				: "You need more runes to empower this jewelry.");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}
		if (player.getCarriedItems().remove(new Item(runeId, runeCost)) == -1) {
			player.getCarriedItems().getInventory().add(item);
			return;
		}

		give(player, productId, 1);
		player.message(staff
			? "You attune the staff through Enchanting."
			: "You bind the altar's energy through Enchanting.");
	}

	private void enchantOrUpgradeRobe(final Player player, final int altarId, final Item item) {
		final int runeId = EnchantingItemEffects.getRuneForAltar(altarId);
		final int altarLevelRequirement = EnchantingItemEffects.getAltarLevelRequirement(altarId);
		if (runeId == -1 || altarLevelRequirement == -1) {
			player.message("Nothing interesting happens.");
			return;
		}

		if (player.getSkills().getLevel(Skill.RUNECRAFT.id()) < altarLevelRequirement) {
			player.message("You require more Enchanting skill to use this altar.");
			return;
		}

		final boolean baseRobe = EnchantingItemEffects.isBaseWoolRobePiece(item.getCatalogId());
		if (!baseRobe) {
			final int itemAltar = EnchantingItemEffects.getAltarIdForWoolRobeItem(item.getCatalogId());
			if (itemAltar != altarId) {
				player.message("This robe is already bound to another altar.");
				return;
			}
		}

		final int currentTier = baseRobe ? 0 : EnchantingItemEffects.getWoolRobeTier(item);
		final int nextTier = currentTier + 1;
		if (nextTier > EnchantingItemEffects.MAX_ENCHANTED_WOOL_ROBE_TIER) {
			player.message("This robe cannot be strengthened further.");
			return;
		}

		final int requiredLevel = EnchantingItemEffects.getTemporaryEnchantingRequirementForTier(nextTier);
		if (player.getSkills().getLevel(Skill.RUNECRAFT.id()) < requiredLevel) {
			player.message("You require more Enchanting skill to bind this robe.");
			return;
		}

		final int productId = EnchantingItemEffects.getWoolRobeProductForPiece(altarId, item.getCatalogId(), nextTier);
		final int runeCost = getWoolRobeUpgradeRuneCost(nextTier);
		if (productId == -1 || runeCost <= 0) {
			player.message("Nothing interesting happens.");
			return;
		}

		if (player.getCarriedItems().getInventory().countId(runeId, Optional.of(false)) < runeCost) {
			player.message("You need " + runeCost + " " + getItemName(player, runeId) + " to bind this robe.");
			return;
		}

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}
		if (player.getCarriedItems().remove(new Item(runeId, runeCost)) == -1) {
			player.getCarriedItems().getInventory().add(item);
			return;
		}

		player.getCarriedItems().getInventory().add(new Item(productId, 1));
		player.message(nextTier == 1
			? "You bind the altar's energy into the cloth."
			: "You strengthen the robe with the altar's energy.");
	}

	private int getWoolRobeUpgradeRuneCost(final int tier) {
		return EnchantingItemEffects.getWoolRobeRuneCost(tier);
	}

	private String getItemName(final Player player, final int itemId) {
		return player.getWorld().getServer().getEntityHandler().getItemDef(itemId).getName().toLowerCase();
	}

	private int normalizeAltarId(final int objectId) {
		if (EnchantingItemEffects.isSupportedAltar(objectId)) {
			return objectId;
		}
		if (objectId >= EnchantingItemEffects.AIR_ALTAR + 1 && objectId <= EnchantingItemEffects.BLOOD_ALTAR + 1 && objectId % 2 == 1) {
			final int entryId = objectId - 1;
			if (EnchantingItemEffects.isSupportedAltar(entryId)) {
				return entryId;
			}
		}
		return -1;
	}
}
