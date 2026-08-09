package com.openrsc.server.model.combat.dot;

import java.util.Objects;

/**
 * Target-owned wall-clock state for the Elder Green Dragon boss burn.
 *
 * <p>NPC provenance includes the source's combat lifecycle so a despawned,
 * dead, or replaced dragon cannot continue an old burn through an object
 * reference retained on the target.</p>
 */
public final class ElderGreenDragonBurnState {
	private final PeriodicEffectProvenance provenance;
	private final long endAtMillis;

	private ElderGreenDragonBurnState(final PeriodicEffectProvenance provenance,
			final long endAtMillis) {
		this.provenance = Objects.requireNonNull(provenance, "provenance");
		if (provenance.getSourceKind() != PeriodicEffectSourceKind.NPC
				|| endAtMillis <= 0L) {
			throw new IllegalArgumentException("invalid Elder Green Dragon burn state");
		}
		this.endAtMillis = endAtMillis;
	}

	public static ElderGreenDragonBurnState of(
			final PeriodicEffectProvenance provenance, final long endAtMillis) {
		return new ElderGreenDragonBurnState(provenance, endAtMillis);
	}

	public PeriodicEffectProvenance getProvenance() { return provenance; }
	public long getEndAtMillis() { return endAtMillis; }
}
