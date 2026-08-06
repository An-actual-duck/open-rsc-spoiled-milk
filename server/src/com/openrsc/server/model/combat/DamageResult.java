package com.openrsc.server.model.combat;

import java.util.Objects;

/** Immutable factual outcome observed after the current Hits mutation. */
public final class DamageResult {
	public enum Status {
		OBSERVED_CURRENT_PATH
	}

	private final Status status;
	private final DamageRequest request;
	private final int hitsBefore;
	private final int actualDamage;
	private final int hitsAfter;
	private final int overkillDamage;
	private final boolean targetTerminal;

	private DamageResult(final DamageRequest request, final int hitsBefore,
			final int hitsAfter) {
		this.request = Objects.requireNonNull(request, "request");
		if (hitsBefore < 0 || hitsAfter < 0 || hitsAfter > hitsBefore) {
			throw new IllegalArgumentException(
				"observed Hits must satisfy 0 <= after <= before");
		}
		final int expectedHitsAfter = Math.max(0,
			hitsBefore - request.getResolvedDamage());
		if (hitsAfter != expectedHitsAfter) {
			throw new IllegalArgumentException(
				"observed Hits do not match resolved legacy damage");
		}
		status = Status.OBSERVED_CURRENT_PATH;
		this.hitsBefore = hitsBefore;
		this.hitsAfter = hitsAfter;
		actualDamage = hitsBefore - hitsAfter;
		overkillDamage = Math.max(0,
			request.getResolvedDamage() - hitsBefore);
		targetTerminal = hitsAfter == 0;
	}

	public static DamageResult observedCurrentPath(final DamageRequest request,
			final int hitsBefore, final int hitsAfter) {
		return new DamageResult(request, hitsBefore, hitsAfter);
	}

	public Status getStatus() { return status; }
	public DamageRequest getRequest() { return request; }
	public int getHitsBefore() { return hitsBefore; }
	public int getActualDamage() { return actualDamage; }
	public int getHitsAfter() { return hitsAfter; }
	public int getOverkillDamage() { return overkillDamage; }
	public boolean isTargetTerminal() { return targetTerminal; }
}
