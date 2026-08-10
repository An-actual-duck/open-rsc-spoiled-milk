package com.openrsc.server.model.combat;

import com.openrsc.server.model.entity.Mob;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable description of damage already resolved by the legacy combat path.
 *
 * <p>This type is deliberately not an HP-mutation or policy boundary. During
 * A05.1 it records only facts available immediately before the existing Hits
 * subtraction. Later source-family branches may move authority behind a shared
 * transaction only after their complete ordering has executable parity
 * coverage.</p>
 */
public final class DamageRequest {
	/** Broad provenance only; detailed effect policy remains with current callers. */
	public enum SourceCategory {
		ACTOR,
		OWNED_EFFECT,
		DOT,
		ENVIRONMENT,
		SCRIPT
	}

	/** A05.1 supports observation of already-resolved legacy values only. */
	public enum InputStage {
		RESOLVED_LEGACY
	}

	/** Presentation written by the resolved-damage transaction. */
	public enum Presentation {
		DAMAGE_AND_HITSPLAT,
		DAMAGE_ONLY
	}

	private final UUID requestId;
	private final UUID parentRequestId;
	private final UUID eventId;
	private final UUID encounterId;
	private final long tick;
	private final Mob source;
	private final Mob target;
	private final CombatParticipantSnapshot sourceSnapshot;
	private final CombatParticipantSnapshot targetSnapshot;
	private final SourceCategory sourceCategory;
	private final CombatStyle style;
	private final String effectKey;
	private final InputStage inputStage;
	private final int resolvedDamage;
	private final int hitSplatType;
	private final Presentation presentation;
	private final boolean applyGoblinTenacity;

	private DamageRequest(final Builder builder) {
		requestId = builder.requestId;
		parentRequestId = builder.parentRequestId;
		eventId = builder.eventId;
		encounterId = builder.encounterId;
		tick = builder.tick;
		source = builder.source;
		target = builder.target;
		sourceSnapshot = source == null
			? null : CombatParticipantSnapshot.capture(source);
		targetSnapshot = CombatParticipantSnapshot.capture(target);
		sourceCategory = builder.sourceCategory;
		style = builder.style;
		effectKey = builder.effectKey;
		inputStage = InputStage.RESOLVED_LEGACY;
		resolvedDamage = builder.resolvedDamage;
		hitSplatType = builder.hitSplatType;
		presentation = builder.presentation;
		applyGoblinTenacity = builder.applyGoblinTenacity;
	}

	public static Builder resolvedLegacy(final Mob source, final Mob target,
			final SourceCategory sourceCategory, final String effectKey,
			final int resolvedDamage) {
		return new Builder(source, target, sourceCategory, effectKey,
			resolvedDamage);
	}

	public UUID getRequestId() { return requestId; }
	public UUID getParentRequestId() { return parentRequestId; }
	public UUID getEventId() { return eventId; }
	public UUID getEncounterId() { return encounterId; }
	public long getTick() { return tick; }
	public Mob getSource() { return source; }
	public Mob getTarget() { return target; }
	public CombatParticipantSnapshot getSourceSnapshot() { return sourceSnapshot; }
	public CombatParticipantSnapshot getTargetSnapshot() { return targetSnapshot; }
	public SourceCategory getSourceCategory() { return sourceCategory; }
	public CombatStyle getStyle() { return style; }
	public String getEffectKey() { return effectKey; }
	public InputStage getInputStage() { return inputStage; }
	public int getResolvedDamage() { return resolvedDamage; }
	public int getHitSplatType() { return hitSplatType; }
	public Presentation getPresentation() { return presentation; }
	/** Whether the shared Hits settlement owns the Goblin Tenacity roll. */
	public boolean shouldApplyGoblinTenacity() { return applyGoblinTenacity; }

	@Override
	public boolean equals(final Object value) {
		return this == value || value instanceof DamageRequest
			&& requestId.equals(((DamageRequest) value).requestId);
	}

	@Override
	public int hashCode() {
		return requestId.hashCode();
	}

	public static final class Builder {
		private UUID requestId = UUID.randomUUID();
		private UUID parentRequestId;
		private UUID eventId;
		private UUID encounterId;
		private final long tick;
		private final Mob source;
		private final Mob target;
		private final SourceCategory sourceCategory;
		private CombatStyle style;
		private final String effectKey;
		private final int resolvedDamage;
		private int hitSplatType;
		private Presentation presentation = Presentation.DAMAGE_AND_HITSPLAT;
		private boolean applyGoblinTenacity = true;

		private Builder(final Mob source, final Mob target,
				final SourceCategory sourceCategory, final String effectKey,
				final int resolvedDamage) {
			this.source = source;
			this.target = Objects.requireNonNull(target, "target");
			this.sourceCategory = Objects.requireNonNull(
				sourceCategory, "sourceCategory");
			if (effectKey == null || effectKey.trim().isEmpty()) {
				throw new IllegalArgumentException("effectKey is required");
			}
			if (resolvedDamage < 0) {
				throw new IllegalArgumentException(
					"resolvedDamage must be non-negative");
			}
			this.effectKey = effectKey;
			this.resolvedDamage = resolvedDamage;
			tick = target.getWorld().getServer().getCurrentTick();
		}

		public Builder requestId(final UUID value) {
			requestId = Objects.requireNonNull(value, "requestId");
			return this;
		}

		public Builder parentRequestId(final UUID value) {
			parentRequestId = value;
			return this;
		}

		public Builder eventId(final UUID value) {
			eventId = value;
			return this;
		}

		public Builder encounterId(final UUID value) {
			encounterId = value;
			return this;
		}

		public Builder style(final CombatStyle value) {
			style = value;
			return this;
		}

		public Builder hitSplatType(final int value) {
			if (value < 0) {
				throw new IllegalArgumentException(
					"hitSplatType must be non-negative");
			}
			hitSplatType = value;
			return this;
		}

		public Builder presentation(final Presentation value) {
			presentation = Objects.requireNonNull(value, "presentation");
			return this;
		}

		/**
		 * Marks the request's value as already settled through Goblin Tenacity.
		 * Only compatibility paths that preserve a post-mitigation presentation
		 * may opt out of the shared settlement roll.
		 */
		public Builder goblinTenacityAlreadyApplied() {
			applyGoblinTenacity = false;
			return this;
		}

		public DamageRequest build() {
			return new DamageRequest(this);
		}
	}
}
