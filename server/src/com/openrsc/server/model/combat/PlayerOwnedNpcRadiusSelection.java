package com.openrsc.server.model.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Selects the ordinary NPC recipients of a player-owned radius effect.
 *
 * <p>The candidate collection and its view order are captured once, while
 * candidate state is checked immediately before each result is returned. This
 * preserves the live revalidation used by robe splashes and terminal bursts:
 * an earlier child effect may make a later candidate ineligible. Callers whose
 * established contract selects every recipient before applying any effect can
 * use {@link #snapshotViewOrder()}.</p>
 *
 * <p>View-area membership remains the world/layer boundary. Radius is the
 * existing legacy point comparison; this policy deliberately performs no
 * independent path or line-of-effect check.</p>
 */
public final class PlayerOwnedNpcRadiusSelection implements Iterable<Npc> {
	private final Collection<Npc> candidates;
	private final Mob movingCenter;
	private final Point fixedCenter;
	private final Mob excluded;
	private final int radius;

	private PlayerOwnedNpcRadiusSelection(final Player owner,
			final Mob movingCenter, final Point fixedCenter,
			final Mob excluded, final int radius) {
		this.movingCenter = movingCenter;
		this.fixedCenter = fixedCenter;
		this.excluded = excluded;
		this.radius = radius;
		if (owner == null || (movingCenter == null && fixedCenter == null)
				|| radius < 0
				|| Summoning.isPlayerAreaEffectSuppressed(owner)) {
			this.candidates = Collections.emptyList();
		} else {
			this.candidates = owner.getViewArea().getNpcsInView();
		}
	}

	/**
	 * Uses the target's current point for each candidate check, matching the
	 * ordinary primary-centered splash loops.
	 */
	public static PlayerOwnedNpcRadiusSelection aroundPrimary(
			final Player owner, final Npc primaryTarget, final int radius) {
		return new PlayerOwnedNpcRadiusSelection(owner, primaryTarget, null,
			primaryTarget, radius);
	}

	/**
	 * Uses a point captured by the caller, matching player-centered terminal
	 * bursts whose center must not move while child effects settle.
	 */
	public static PlayerOwnedNpcRadiusSelection aroundFixedPoint(
			final Player owner, final Point center, final Mob excluded,
			final int radius) {
		return new PlayerOwnedNpcRadiusSelection(owner, null, center, excluded,
			radius);
	}

	/**
	 * Resolves all currently eligible targets before the caller applies effects.
	 */
	public List<Npc> snapshotViewOrder() {
		final List<Npc> snapshot = new ArrayList<>();
		for (Npc npc : this) {
			snapshot.add(npc);
		}
		return snapshot;
	}

	@Override
	public Iterator<Npc> iterator() {
		return new EligibleNpcIterator(candidates.iterator());
	}

	private boolean isEligible(final Npc npc) {
		if (npc == null || npc == excluded || npc.isRemoved()
				|| npc.isRespawning() || Summoning.isSummon(npc)
				|| !npc.getDef().isAttackable()
				|| npc.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}
		final Point center = movingCenter == null
			? fixedCenter : movingCenter.getLocation();
		return npc.withinRange(center, radius);
	}

	private final class EligibleNpcIterator implements Iterator<Npc> {
		private final Iterator<Npc> candidatesIterator;
		private Npc next;
		private boolean prepared;

		private EligibleNpcIterator(final Iterator<Npc> candidatesIterator) {
			this.candidatesIterator = candidatesIterator;
		}

		@Override
		public boolean hasNext() {
			prepareNext();
			return next != null;
		}

		@Override
		public Npc next() {
			prepareNext();
			if (next == null) {
				throw new NoSuchElementException();
			}
			final Npc result = next;
			next = null;
			prepared = false;
			return result;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException(
				"Player-owned radius selections are read-only");
		}

		private void prepareNext() {
			if (prepared) {
				return;
			}
			prepared = true;
			while (candidatesIterator.hasNext()) {
				final Npc candidate = candidatesIterator.next();
				if (isEligible(candidate)) {
					next = candidate;
					return;
				}
			}
			next = null;
		}
	}
}
