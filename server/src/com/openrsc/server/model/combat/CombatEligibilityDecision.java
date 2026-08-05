package com.openrsc.server.model.combat;

/** Immutable reason-coded result from {@link CombatEligibility}. */
public final class CombatEligibilityDecision {
	private final CombatEligibilityPhase phase;
	private final CombatStyle style;
	private final CombatEligibilityReason reason;
	private final int detailValue;

	CombatEligibilityDecision(final CombatEligibilityRequest request,
			final CombatEligibilityReason reason, final int detailValue) {
		this.phase = request.getPhase();
		this.style = request.getStyle();
		this.reason = reason;
		this.detailValue = detailValue;
	}

	public boolean isAllowed() {
		return reason == CombatEligibilityReason.ALLOWED;
	}

	public CombatEligibilityPhase getPhase() {
		return phase;
	}

	public CombatStyle getStyle() {
		return style;
	}

	public CombatEligibilityReason getReason() {
		return reason;
	}

	public int getDetailValue() {
		return detailValue;
	}
}
