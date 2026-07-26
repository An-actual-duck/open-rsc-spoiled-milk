package com.openrsc.server.model.entity.player;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.net.rsc.ActionSender;

public class Prayers {
	public static final int THICK_SKIN = 0, BURST_OF_STRENGTH = 1, CLARITY_OF_THOUGHT = 2, ROCK_SKIN = 3,
		SUPERHUMAN_STRENGTH = 4, IMPROVED_REFLEXES = 5, RAPID_RESTORE = 6, RAPID_HEAL = 7, PROTECT_ITEMS = 8,
		STEEL_SKIN = 9, ULTIMATE_STRENGTH = 10, INCREDIBLE_REFLEXES = 11, PARALYZE_MONSTER = 12,
		PROTECT_FROM_MISSILES = 13, DIVINE_RETRIBUTION = 15, DIVINE_GRACE = 15, CORROSIVE_AURA = 15;
	private static final int SPECIAL_PRAYER_NO_CAPE_COST_NUMERATOR = 3;
	private static final int SPECIAL_PRAYER_NO_CAPE_COST_DENOMINATOR = 2;

	private final boolean[] activatedPrayers = new boolean[PrayerCatalog.PRAYERS_PER_BOOK];
	private final Player player;

	public Prayers(final Player player) {
		this.player = player;
	}

	public boolean isPrayerActivated(final int prayerID) {
		return activatedPrayers[prayerID];
	}

	public boolean[] getActivePrayers() {
		return activatedPrayers;
	}

	public void setPrayer(final int prayerID, final boolean activated) {
		setPrayer(prayerID, activated, true);
	}

	public void setPrayer(final int prayerID, final boolean activated, final boolean updatePlayer) {
		activatedPrayers[prayerID] = activated;
		if (updatePlayer) ActionSender.sendPrayers(player, activatedPrayers);
	}

	public int getAllocatedPoints() {
		int allocatedPoints = 0;
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (!activatedPrayers[i]) {
				continue;
			}
			final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), i);
			if (definition != null) {
				allocatedPoints += getActivationPointCost(definition);
			}
		}
		return allocatedPoints;
	}

	public boolean canActivate(final int prayerID) {
		final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), prayerID);
		if (definition == null) {
			return false;
		}
		if (!meetsEquipmentRequirement(definition)) {
			return false;
		}
		return getAllocatedPoints() + getActivationPointCost(definition) <= player.getPrayerAllocationPoints();
	}

	public String getActivationBlockMessage(final int prayerID) {
		final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), prayerID);
		if (definition == null) {
			return null;
		}
		final int requiredMace = getRequiredSpecialPrayerMace(definition);
		if (requiredMace > -1 && !player.getCarriedItems().getEquipment().hasEquipped(requiredMace)) {
			return "You lack the necessary holy artifact";
		}
		final int activationPointCost = getActivationPointCost(definition);
		if (getAllocatedPoints() + activationPointCost > player.getPrayerAllocationPoints()) {
			return "You need " + activationPointCost + " free prayer points to activate this prayer";
		}
		return null;
	}

	public void deactivateUnavailableEquipmentPrayers() {
		boolean changed = false;
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (!activatedPrayers[i]) {
				continue;
			}
			final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), i);
			if (definition != null && !meetsEquipmentRequirement(definition)) {
				activatedPrayers[i] = false;
				changed = true;
			}
		}
		if (changed) {
			ActionSender.sendPrayers(player, activatedPrayers);
		}
	}

	public void deactivateOverflowingPrayers() {
		boolean changed = false;
		for (int i = activatedPrayers.length - 1; i >= 0 && getAllocatedPoints() > player.getPrayerAllocationPoints(); i--) {
			if (activatedPrayers[i]) {
				activatedPrayers[i] = false;
				changed = true;
			}
		}
		if (changed) {
			ActionSender.sendPrayers(player, activatedPrayers);
		}
	}

	public int getOffenseBonusPercent(final PrayerCatalog.CombatStyle combatStyle) {
		return getCombatEffectPercent(PrayerCatalog.PrayerKind.OFFENSE, combatStyle);
	}

	public int getDefenseReductionPercent(final PrayerCatalog.CombatStyle combatStyle) {
		return getCombatEffectPercent(PrayerCatalog.PrayerKind.DEFENSE, combatStyle);
	}

	public int getSkillingBonusPercent(final String skillName) {
		int totalPercent = 0;
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (!activatedPrayers[i]) {
				continue;
			}
			final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), i);
			if (definition != null
				&& definition.getKind() == PrayerCatalog.PrayerKind.SKILLING
				&& skillName.equals(definition.getSkillName())) {
				totalPercent += definition.getEffectPercent();
			}
		}
		return totalPercent;
	}

	private int getCombatEffectPercent(final PrayerCatalog.PrayerKind kind, final PrayerCatalog.CombatStyle combatStyle) {
		int totalPercent = 0;
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (!activatedPrayers[i]) {
				continue;
			}
			final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), i);
			if (definition != null && definition.getKind() == kind && definition.getCombatStyle() == combatStyle) {
				totalPercent += definition.getEffectPercent();
			}
		}
		return totalPercent;
	}

	public void resetPrayers() {
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (activatedPrayers[i]) {
				activatedPrayers[i] = false;
			}
		}
		ActionSender.sendPrayers(player, activatedPrayers);
	}

	private boolean meetsEquipmentRequirement(final PrayerCatalog.PrayerDefinition definition) {
		final int requiredMace = getRequiredSpecialPrayerMace(definition);
		return requiredMace < 0 || player.getCarriedItems().getEquipment().hasEquipped(requiredMace);
	}

	private int getActivationPointCost(final PrayerCatalog.PrayerDefinition definition) {
		if (definition == null) {
			return 0;
		}
		final int pointCost = definition.getPointCost();
		if (definition.getKind() != PrayerCatalog.PrayerKind.SPECIAL || hasMatchingGodCape(definition)) {
			return pointCost;
		}
		return (pointCost * SPECIAL_PRAYER_NO_CAPE_COST_NUMERATOR) / SPECIAL_PRAYER_NO_CAPE_COST_DENOMINATOR;
	}

	private int getRequiredSpecialPrayerMace(final PrayerCatalog.PrayerDefinition definition) {
		if (definition == null || definition.getKind() != PrayerCatalog.PrayerKind.SPECIAL) {
			return -1;
		}
		if (definition.getGodLine() == PrayerCatalog.GodLine.SARADOMIN) {
			return ItemId.SARADOMIN_MACE.id();
		}
		if (definition.getGodLine() == PrayerCatalog.GodLine.ZAMORAK) {
			return ItemId.ZAMORAK_MACE.id();
		}
		if (definition.getGodLine() == PrayerCatalog.GodLine.GUTHIX) {
			return ItemId.GUTHIX_MACE.id();
		}
		return -1;
	}

	private boolean hasMatchingGodCape(final PrayerCatalog.PrayerDefinition definition) {
		final int requiredCape = getRequiredGodCape(definition);
		return requiredCape > -1 && player.getCarriedItems().getEquipment().hasEquipped(requiredCape);
	}

	private int getRequiredGodCape(final PrayerCatalog.PrayerDefinition definition) {
		if (definition == null || definition.getGodLine() == null) {
			return -1;
		}
		if (definition.getGodLine() == PrayerCatalog.GodLine.SARADOMIN) {
			return ItemId.SARADOMIN_CAPE.id();
		}
		if (definition.getGodLine() == PrayerCatalog.GodLine.ZAMORAK) {
			return ItemId.ZAMORAK_CAPE.id();
		}
		if (definition.getGodLine() == PrayerCatalog.GodLine.GUTHIX) {
			return ItemId.GUTHIX_CAPE.id();
		}
		return -1;
	}

}
