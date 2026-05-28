package com.openrsc.server.model.entity.player;

import com.openrsc.server.net.rsc.ActionSender;

public class Prayers {
	public static final int THICK_SKIN = 0, BURST_OF_STRENGTH = 1, CLARITY_OF_THOUGHT = 2, ROCK_SKIN = 3,
		SUPERHUMAN_STRENGTH = 4, IMPROVED_REFLEXES = 5, RAPID_RESTORE = 6, RAPID_HEAL = 7, PROTECT_ITEMS = 8,
		STEEL_SKIN = 9, ULTIMATE_STRENGTH = 10, INCREDIBLE_REFLEXES = 11, PARALYZE_MONSTER = 12,
		PROTECT_FROM_MISSILES = 13;

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
				allocatedPoints += definition.getPointCost();
			}
		}
		return allocatedPoints;
	}

	public boolean canActivate(final int prayerID) {
		final PrayerCatalog.PrayerDefinition definition = PrayerCatalog.getDefinition(player.getPrayerBook(), prayerID);
		if (definition == null) {
			return false;
		}
		return getAllocatedPoints() + definition.getPointCost() <= player.getPrayerAllocationPoints();
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
		return Math.min(totalPercent, PrayerCatalog.COMBAT_EFFECT_CAP_PERCENT);
	}

	public void resetPrayers() {
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (activatedPrayers[i]) {
				activatedPrayers[i] = false;
			}
		}
		ActionSender.sendPrayers(player, activatedPrayers);
	}
}
