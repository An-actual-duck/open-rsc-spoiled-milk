package com.openrsc.server.model.entity.npc;

/**
 * A factual source category for player-owned damage recorded against one NPC
 * lifetime. The category deliberately does not decide who receives a kill
 * reward: settlement has separate final-hit, loot, XP, and summon policies.
 */
public enum NpcContributionRole {
	MELEE(true),
	RANGED(true),
	MAGIC(true),
	SUMMON(false);

	private final boolean combatStyleExperienceEligible;

	NpcContributionRole(final boolean combatStyleExperienceEligible) {
		this.combatStyleExperienceEligible = combatStyleExperienceEligible;
	}

	/** Whether this role participates in the legacy melee/ranged/magic XP split. */
	public boolean isCombatStyleExperienceEligible() {
		return combatStyleExperienceEligible;
	}
}
