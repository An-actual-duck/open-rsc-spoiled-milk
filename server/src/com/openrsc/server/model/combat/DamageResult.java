package com.openrsc.server.model.combat;

import java.util.Objects;

/** Immutable factual outcome from a resolved legacy Hits mutation. */
public final class DamageResult {
	public enum Status {
		OBSERVED_CURRENT_PATH,
		APPLIED_CURRENT_PATH
	}

	private final Status status;
	private final DamageRequest request;
	private final int hitsBefore;
	private final int actualDamage;
	private final int legacyDamageDealt;
	private final int hitsAfter;
	private final int overkillDamage;
	private final boolean targetTerminal;

	private DamageResult(final Status status, final DamageRequest request,
			final int hitsBefore, final int hitsAfter) {
		this.status = Objects.requireNonNull(status, "status");
		this.request = Objects.requireNonNull(request, "request");
		if (hitsBefore < 0 || hitsAfter < 0 || hitsAfter > hitsBefore) {
			throw new IllegalArgumentException(
				"observed Hits must satisfy 0 <= after <= before");
		}
		final int expectedHitsAfter = Math.max(0,
			hitsBefore - request.getResolvedDamage());
		if (status == Status.OBSERVED_CURRENT_PATH
				&& hitsAfter != expectedHitsAfter) {
			throw new IllegalArgumentException(
				"observed Hits do not match resolved legacy damage");
		}
		if (status == Status.APPLIED_CURRENT_PATH
				&& hitsAfter < expectedHitsAfter) {
			throw new IllegalArgumentException(
				"applied Hits exceed resolved legacy damage");
		}
		this.hitsBefore = hitsBefore;
		this.hitsAfter = hitsAfter;
		actualDamage = hitsBefore - hitsAfter;
		legacyDamageDealt = Math.min(request.getResolvedDamage(), hitsBefore);
		overkillDamage = Math.max(0,
			request.getResolvedDamage() - hitsBefore);
		targetTerminal = hitsAfter == 0;
	}

	public static DamageResult observedCurrentPath(final DamageRequest request,
			final int hitsBefore, final int hitsAfter) {
		return new DamageResult(Status.OBSERVED_CURRENT_PATH, request,
			hitsBefore, hitsAfter);
	}

	public static DamageResult appliedCurrentPath(final DamageRequest request,
			final int hitsBefore, final int hitsAfter) {
		return new DamageResult(Status.APPLIED_CURRENT_PATH, request,
			hitsBefore, hitsAfter);
	}

	public Status getStatus() { return status; }
	public DamageRequest getRequest() { return request; }
	public int getHitsBefore() { return hitsBefore; }
	public int getActualDamage() { return actualDamage; }
	/**
	 * Damage value historically passed to post-hit hooks by the direct melee
	 * events. It can exceed the factual HP delta when the shared Hits setter
	 * applies a compatibility survival effect such as Goblin's Tenacity.
	 */
	public int getLegacyDamageDealt() { return legacyDamageDealt; }
	public int getHitsAfter() { return hitsAfter; }
	public int getOverkillDamage() { return overkillDamage; }
	public boolean isTargetTerminal() { return targetTerminal; }
}
