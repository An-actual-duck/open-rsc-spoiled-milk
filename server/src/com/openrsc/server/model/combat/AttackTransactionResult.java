package com.openrsc.server.model.combat;

/** Typed result from one attack-intent operation. */
public final class AttackTransactionResult {
	public enum Status {
		WAITING_FOR_APPROACH,
		READY_TO_COMMIT,
		COMMITTED,
		REJECTED,
		CONFLICT
	}

	public enum Reason {
		ACCEPTED,
		MANUAL_INTENT_HAS_PRIORITY,
		INTENT_NOT_CURRENT,
		EXPIRED,
		PARTICIPANT_CHANGED,
		ELIGIBILITY_REJECTED,
		LOADOUT_CHANGED,
		APPROACH_REPLACED,
		SUPERSEDED,
		PLUGIN_BLOCKED,
		COMMIT_FAILED
	}

	private final Status status;
	private final Reason reason;
	private final AttackIntent intent;
	private final CombatEligibilityReason eligibilityReason;

	AttackTransactionResult(final Status status, final Reason reason,
			final AttackIntent intent,
			final CombatEligibilityReason eligibilityReason) {
		this.status = status;
		this.reason = reason;
		this.intent = intent;
		this.eligibilityReason = eligibilityReason;
	}

	public Status getStatus() { return status; }
	public Reason getReason() { return reason; }
	public AttackIntent getIntent() { return intent; }
	public CombatEligibilityReason getEligibilityReason() { return eligibilityReason; }
	public boolean isCommitted() { return status == Status.COMMITTED; }
	public boolean isReadyToCommit() { return status == Status.READY_TO_COMMIT; }
}
