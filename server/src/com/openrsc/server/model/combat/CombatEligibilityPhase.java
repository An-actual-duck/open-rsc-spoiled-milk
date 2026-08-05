package com.openrsc.server.model.combat;

/** Point in the attack lifecycle at which eligibility is being checked. */
public enum CombatEligibilityPhase {
	COMMAND,
	APPROACH,
	COMMIT,
	COMPATIBILITY
}
