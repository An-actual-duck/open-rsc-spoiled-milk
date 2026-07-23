package com.openrsc.server.constants;

import com.openrsc.server.external.SkillDef;

/**
 * Shared eligibility rules for the in-game hiscores.
 * The overall board must match the "Skill total" figure shown in the client
 * stats tab, which skips Defense, Strength and Fletching (retired by the
 * melee merge / crafting rework) plus the hidden auto-maxed Firemaking slot.
 */
public final class HiscoreSkills {

	private HiscoreSkills() {
	}

	public static boolean countsTowardOverall(final SkillDef skill) {
		final String name = skill.getLongName().toLowerCase();
		return !name.equals("defense") && !name.equals("strength")
			&& !name.equals("fletching") && !name.equals("firemaking");
	}
}
