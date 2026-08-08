package com.openrsc.server.model.combat.dot;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Versioned, live-reference-free cache representation of generic poison. */
public final class PoisonDurableRecord {
	public static final String CACHE_KEY = "poison_state_v1";
	private final PoisonTargetState state;

	public PoisonDurableRecord(final PoisonTargetState state) {
		this.state = state;
	}

	public PoisonTargetState getState() { return state; }

	public String encode() {
		final PeriodicEffectProvenance p = state.getProvenance();
		final String kind = p == null ? "NONE" : p.getSourceKind().name();
		final String id = p == null || p.getSourceId() == null ? ""
			: p.getSourceId().toString();
		final String lifecycle = p == null ? "0"
			: Long.toString(p.getSourceLifecycle());
		final String key = p == null || p.getSourceKey() == null ? ""
			: Base64.getUrlEncoder().withoutPadding().encodeToString(
				p.getSourceKey().getBytes(StandardCharsets.UTF_8));
		return "1|" + state.getCurrentPower() + "|" + state.getMaximumPower()
			+ "|" + kind + "|" + id + "|" + lifecycle + "|" + key;
	}

	public static PoisonDurableRecord decode(final String encoded) {
		try {
			final String[] parts = encoded.split("\\|", -1);
			if (parts.length != 7 || !"1".equals(parts[0])) return null;
			final int current = Integer.parseInt(parts[1]);
			final int maximum = Integer.parseInt(parts[2]);
			if (current <= 0 || maximum < current) return null;
			PeriodicEffectProvenance provenance = null;
			if (!"NONE".equals(parts[3])) {
				final PeriodicEffectSourceKind kind =
					PeriodicEffectSourceKind.valueOf(parts[3]);
				if (kind == PeriodicEffectSourceKind.PLAYER) {
					provenance = PeriodicEffectProvenance.player(UUID.fromString(parts[4]));
				} else if (kind == PeriodicEffectSourceKind.NPC) {
					provenance = PeriodicEffectProvenance.npc(UUID.fromString(parts[4]),
						Long.parseLong(parts[5]));
				} else {
					final String key = new String(Base64.getUrlDecoder().decode(parts[6]),
						StandardCharsets.UTF_8);
					provenance = kind == PeriodicEffectSourceKind.ENVIRONMENT
						? PeriodicEffectProvenance.environment(key)
						: PeriodicEffectProvenance.script(key);
				}
			}
			return new PoisonDurableRecord(PoisonTargetState.of(current, maximum,
				provenance));
		} catch (final RuntimeException invalid) {
			return null;
		}
	}
}
