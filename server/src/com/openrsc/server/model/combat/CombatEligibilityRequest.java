package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;

/** Immutable input to the shared eligibility evaluator. */
public final class CombatEligibilityRequest {
	private final Mob source;
	private final Mob target;
	private final CombatEligibilityPhase phase;
	private final CombatStyle style;
	private final CombatParticipantSnapshot sourceSnapshot;
	private final CombatParticipantSnapshot targetSnapshot;
	private final boolean requireCurrentParticipants;
	private final boolean requireRegistration;
	private final boolean requireSameSpatialDomain;
	private final boolean enforceSummonRules;
	private final boolean enforcePlayerAttackRules;
	private final boolean allowUnattackableTarget;

	private CombatEligibilityRequest(final Builder builder) {
		this.source = builder.source;
		this.target = builder.target;
		this.phase = builder.phase;
		this.style = builder.style;
		this.sourceSnapshot = builder.sourceSnapshot;
		this.targetSnapshot = builder.targetSnapshot;
		this.requireCurrentParticipants = builder.requireCurrentParticipants;
		this.requireRegistration = builder.requireRegistration;
		this.requireSameSpatialDomain = builder.requireSameSpatialDomain;
		this.enforceSummonRules = builder.enforceSummonRules;
		this.enforcePlayerAttackRules = builder.enforcePlayerAttackRules;
		this.allowUnattackableTarget = builder.allowUnattackableTarget;
	}

	public static Builder builder(final Mob source, final Mob target,
			final CombatEligibilityPhase phase, final CombatStyle style) {
		return new Builder(source, target, phase, style);
	}

	public Mob getSource() { return source; }
	public Mob getTarget() { return target; }
	public CombatEligibilityPhase getPhase() { return phase; }
	public CombatStyle getStyle() { return style; }
	public CombatParticipantSnapshot getSourceSnapshot() { return sourceSnapshot; }
	public CombatParticipantSnapshot getTargetSnapshot() { return targetSnapshot; }
	public boolean requiresCurrentParticipants() { return requireCurrentParticipants; }
	public boolean requiresRegistration() { return requireRegistration; }
	public boolean requiresSameSpatialDomain() { return requireSameSpatialDomain; }
	public boolean enforcesSummonRules() { return enforceSummonRules; }
	public boolean enforcesPlayerAttackRules() { return enforcePlayerAttackRules; }
	public boolean allowsUnattackableTarget() { return allowUnattackableTarget; }

	public static final class Builder {
		private final Mob source;
		private final Mob target;
		private final CombatEligibilityPhase phase;
		private final CombatStyle style;
		private CombatParticipantSnapshot sourceSnapshot;
		private CombatParticipantSnapshot targetSnapshot;
		private boolean requireCurrentParticipants;
		private boolean requireRegistration;
		private boolean requireSameSpatialDomain;
		private boolean enforceSummonRules;
		private boolean enforcePlayerAttackRules;
		private boolean allowUnattackableTarget;

		private Builder(final Mob source, final Mob target,
				final CombatEligibilityPhase phase, final CombatStyle style) {
			if (phase == null || style == null) {
				throw new IllegalArgumentException("phase and style are required");
			}
			this.source = source;
			this.target = target;
			this.phase = phase;
			this.style = style;
		}

		public Builder snapshots(final CombatParticipantSnapshot sourceIdentity,
				final CombatParticipantSnapshot targetIdentity) {
			this.sourceSnapshot = sourceIdentity;
			this.targetSnapshot = targetIdentity;
			return this;
		}

		public Builder currentParticipants(final boolean required) {
			this.requireCurrentParticipants = required;
			return this;
		}

		public Builder registration(final boolean required) {
			this.requireRegistration = required;
			return this;
		}

		public Builder sameSpatialDomain(final boolean required) {
			this.requireSameSpatialDomain = required;
			return this;
		}

		public Builder summonRules(final boolean enforced) {
			this.enforceSummonRules = enforced;
			return this;
		}

		public Builder playerAttackRules(final boolean enforced) {
			this.enforcePlayerAttackRules = enforced;
			return this;
		}

		public Builder allowUnattackableTarget(final boolean allowed) {
			this.allowUnattackableTarget = allowed;
			return this;
		}

		public CombatEligibilityRequest build() {
			return new CombatEligibilityRequest(this);
		}
	}
}
