package com.openrsc.server.content.cleric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Shared, server-authoritative area selection for launch Cleric support.
 *
 * <p>The supplied candidates must be a snapshot of the caster's current party;
 * this class deliberately has no global-player lookup. Runtime-specific online,
 * party-continuity, and PvP checks belong in {@link CandidateView} while the
 * geometry and per-recipient line-of-effect rules remain common.</p>
 */
public final class ClericSupportTargeting {
	private ClericSupportTargeting() {
	}

	public static <T> List<T> resolve(final T caster,
			final Iterable<T> partyCandidates, final int radius,
			final CandidateView<T> view) {
		if (caster == null || partyCandidates == null || view == null) {
			throw new IllegalArgumentException(
				"Cleric targeting requires a caster, party snapshot, and candidate view");
		}
		if (radius < 1) {
			throw new IllegalArgumentException("Cleric support radius must be positive");
		}

		final Object casterWorldSpace = view.getWorldSpace(caster);
		if (casterWorldSpace == null) {
			throw new IllegalArgumentException("Cleric caster world space is required");
		}
		final int casterLevel = view.getSignedLevel(caster);
		final int casterX = view.getX(caster);
		final int casterY = view.getY(caster);
		final Set<T> observed = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
		final List<T> recipients = new ArrayList<T>();

		for (T candidate : partyCandidates) {
			if (candidate == null || candidate == caster || !observed.add(candidate)
				|| !view.isEligibleRecipient(candidate)) {
				continue;
			}
			final Object candidateWorldSpace = view.getWorldSpace(candidate);
			if (!casterWorldSpace.equals(candidateWorldSpace)
				|| casterLevel != view.getSignedLevel(candidate)) {
				continue;
			}
			final long deltaX = Math.abs((long) casterX - view.getX(candidate));
			final long deltaY = Math.abs((long) casterY - view.getY(candidate));
			if (Math.max(deltaX, deltaY) > radius
				|| !view.hasLineOfEffect(caster, candidate)) {
				continue;
			}
			recipients.add(candidate);
		}
		return Collections.unmodifiableList(recipients);
	}

	/** Runtime adapter for candidate state and exact logical coordinates. */
	public interface CandidateView<T> {
		boolean isEligibleRecipient(T candidate);

		Object getWorldSpace(T candidate);

		int getSignedLevel(T candidate);

		int getX(T candidate);

		int getY(T candidate);

		boolean hasLineOfEffect(T caster, T candidate);
	}
}
