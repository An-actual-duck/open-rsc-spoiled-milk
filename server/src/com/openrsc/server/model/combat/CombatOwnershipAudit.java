package com.openrsc.server.model.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result from an explicitly requested ownership audit. */
public final class CombatOwnershipAudit {
	private final List<String> discrepancies;
	private final int repairedCount;

	CombatOwnershipAudit(final List<String> discrepancies,
			final int repairedCount) {
		this.discrepancies = Collections.unmodifiableList(
			new ArrayList<String>(discrepancies));
		this.repairedCount = repairedCount;
	}

	public boolean isConsistent() {
		return discrepancies.isEmpty();
	}

	public List<String> getDiscrepancies() {
		return discrepancies;
	}

	public int getRepairedCount() {
		return repairedCount;
	}
}
