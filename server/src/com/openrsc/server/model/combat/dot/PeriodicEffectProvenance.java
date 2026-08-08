package com.openrsc.server.model.combat.dot;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, serializable-in-principle provenance for a target-owned periodic
 * effect.
 *
 * <p>No live {@code Mob}, event, or session object is retained. Player IDs are
 * stable across sessions; NPC provenance additionally carries the source
 * lifetime generation. Environment and script sources instead use a bounded
 * stable key. Persistence wiring is deliberately deferred to A08.4.</p>
 */
public final class PeriodicEffectProvenance {
	private final PeriodicEffectSourceKind sourceKind;
	private final UUID sourceId;
	private final long sourceLifecycle;
	private final String sourceKey;

	private PeriodicEffectProvenance(final PeriodicEffectSourceKind sourceKind,
			final UUID sourceId, final long sourceLifecycle,
			final String sourceKey) {
		this.sourceKind = Objects.requireNonNull(sourceKind, "sourceKind");
		this.sourceId = sourceId;
		this.sourceLifecycle = sourceLifecycle;
		this.sourceKey = sourceKey;
		validate();
	}

	public static PeriodicEffectProvenance player(final UUID playerId) {
		return new PeriodicEffectProvenance(PeriodicEffectSourceKind.PLAYER,
			Objects.requireNonNull(playerId, "playerId"), 0L, null);
	}

	public static PeriodicEffectProvenance npc(final UUID npcId,
			final long lifecycle) {
		return new PeriodicEffectProvenance(PeriodicEffectSourceKind.NPC,
			Objects.requireNonNull(npcId, "npcId"), lifecycle, null);
	}

	public static PeriodicEffectProvenance environment(final String key) {
		return new PeriodicEffectProvenance(
			PeriodicEffectSourceKind.ENVIRONMENT, null, 0L, key);
	}

	public static PeriodicEffectProvenance script(final String key) {
		return new PeriodicEffectProvenance(PeriodicEffectSourceKind.SCRIPT,
			null, 0L, key);
	}

	private void validate() {
		switch (sourceKind) {
			case PLAYER:
				if (sourceId == null || sourceLifecycle != 0L || sourceKey != null) {
					throw new IllegalArgumentException(
						"player provenance requires only a stable player ID");
				}
				return;
			case NPC:
				if (sourceId == null || sourceLifecycle <= 0L || sourceKey != null) {
					throw new IllegalArgumentException(
						"NPC provenance requires ID and positive lifetime");
				}
				return;
			case ENVIRONMENT:
			case SCRIPT:
				if (sourceId != null || sourceLifecycle != 0L
					|| sourceKey == null || sourceKey.trim().isEmpty()) {
					throw new IllegalArgumentException(
						"non-actor provenance requires only a stable key");
				}
				return;
			default:
				throw new IllegalStateException("unknown periodic source kind");
		}
	}

	public PeriodicEffectSourceKind getSourceKind() { return sourceKind; }
	public UUID getSourceId() { return sourceId; }
	public long getSourceLifecycle() { return sourceLifecycle; }
	public String getSourceKey() { return sourceKey; }

	public boolean isPlayer() {
		return sourceKind == PeriodicEffectSourceKind.PLAYER;
	}

	@Override
	public boolean equals(final Object value) {
		if (this == value) {
			return true;
		}
		if (!(value instanceof PeriodicEffectProvenance)) {
			return false;
		}
		final PeriodicEffectProvenance other =
			(PeriodicEffectProvenance) value;
		return sourceKind == other.sourceKind
			&& Objects.equals(sourceId, other.sourceId)
			&& sourceLifecycle == other.sourceLifecycle
			&& Objects.equals(sourceKey, other.sourceKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sourceKind, sourceId, sourceLifecycle, sourceKey);
	}
}
