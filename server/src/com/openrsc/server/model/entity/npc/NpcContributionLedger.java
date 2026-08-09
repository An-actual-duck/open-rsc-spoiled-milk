package com.openrsc.server.model.entity.npc;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed, per-NPC-lifetime factual contribution ledger.
 *
 * <p>This is intentionally a recorder rather than a reward policy. In
 * particular, summon damage is factual contribution for threat, loot, and
 * kill-credit selection but remains excluded from the legacy style-XP split.
 * Keeping that distinction here prevents future callers from treating every
 * damaging path as interchangeable.</p>
 */
public final class NpcContributionLedger {
	private final Map<NpcContributionRole, Map<UUID, Contribution>> entries =
		new EnumMap<NpcContributionRole, Map<UUID, Contribution>>(
			NpcContributionRole.class);

	public NpcContributionLedger() {
		for (final NpcContributionRole role : NpcContributionRole.values()) {
			entries.put(role, new HashMap<UUID, Contribution>());
		}
	}

	/** Records a legacy factual damage amount, including a zero-damage touch. */
	public void record(final NpcContributionRole role, final UUID playerId,
			final long usernameHash, final int damage) {
		final Map<UUID, Contribution> byPlayer = entries.get(
			Objects.requireNonNull(role, "role"));
		final UUID checkedPlayerId = Objects.requireNonNull(playerId, "playerId");
		final Contribution previous = byPlayer.get(checkedPlayerId);
		final int accumulated = previous == null ? damage : previous.damage + damage;
		byPlayer.put(checkedPlayerId, new Contribution(accumulated, usernameHash));
	}

	public Contribution get(final NpcContributionRole role,
			final UUID playerId) {
		if (playerId == null) {
			return Contribution.NONE;
		}
		final Contribution contribution = entries.get(role).get(playerId);
		return contribution == null ? Contribution.NONE : contribution;
	}

	/**
	 * Returns contributor identities in the same per-role HashMap iteration
	 * order used by the legacy maps. Callers define cross-role ordering.
	 */
	public ArrayList<UUID> contributorIds(final NpcContributionRole role) {
		return new ArrayList<UUID>(entries.get(role).keySet());
	}

	public void clear() {
		for (final Map<UUID, Contribution> byPlayer : entries.values()) {
			byPlayer.clear();
		}
	}

	/** Immutable factual record; it intentionally contains no settlement decision. */
	public static final class Contribution {
		private static final Contribution NONE = new Contribution(0, 0L);

		private final int damage;
		private final long usernameHash;

		private Contribution(final int damage, final long usernameHash) {
			this.damage = damage;
			this.usernameHash = usernameHash;
		}

		public int getDamage() {
			return damage;
		}

		public long getUsernameHash() {
			return usernameHash;
		}
	}
}
