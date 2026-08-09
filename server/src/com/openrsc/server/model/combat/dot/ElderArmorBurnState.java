package com.openrsc.server.model.combat.dot;

import java.util.Objects;

/**
 * Target-owned state for the Elder Green Dragon armor's finite burn.
 *
 * <p>The state deliberately contains only durable source identity and the
 * remaining pulse count. It does not retain a Player or event instance, so a
 * disconnected or replaced player object cannot keep ownership alive.</p>
 */
public final class ElderArmorBurnState {
	private final PeriodicEffectProvenance provenance;
	private final int pulsesRemaining;

	private ElderArmorBurnState(final PeriodicEffectProvenance provenance,
			final int pulsesRemaining) {
		this.provenance = Objects.requireNonNull(provenance, "provenance");
		if (!provenance.isPlayer() || pulsesRemaining <= 0) {
			throw new IllegalArgumentException("invalid Elder armor burn state");
		}
		this.pulsesRemaining = pulsesRemaining;
	}

	public static ElderArmorBurnState of(final PeriodicEffectProvenance provenance,
			final int pulsesRemaining) {
		return new ElderArmorBurnState(provenance, pulsesRemaining);
	}

	public PeriodicEffectProvenance getProvenance() { return provenance; }
	public int getPulsesRemaining() { return pulsesRemaining; }

	public ElderArmorBurnState refresh(final PeriodicEffectProvenance source,
			final int refreshedPulses) {
		return of(source, refreshedPulses);
	}

	public ElderArmorBurnState afterPulse() {
		return pulsesRemaining <= 1 ? null : of(provenance, pulsesRemaining - 1);
	}
}
