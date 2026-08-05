package com.openrsc.server.model.combat;

import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-mob authority for one outgoing direction, many incoming directions, and
 * the compatibility event slots owned by that mob. Legacy accessors delegate
 * here; no second opponent or event field is maintained.
 */
public final class CombatEngagementAuthority {
	private static final Logger LOGGER = LogManager.getLogger();

	private final Mob owner;
	private CombatEngagement outgoing;
	private final LinkedHashMap<UUID, CombatEngagement> incoming =
		new LinkedHashMap<UUID, CombatEngagement>();
	private final EnumMap<CombatEventSlot, EventBinding> events =
		new EnumMap<CombatEventSlot, EventBinding>(CombatEventSlot.class);

	public CombatEngagementAuthority(final Mob owner) {
		if (owner == null) {
			throw new IllegalArgumentException("engagement owner cannot be null");
		}
		this.owner = owner;
	}

	public CombatEngagement beginOutgoing(final Mob target,
			final CombatStyle style, final boolean legacyOpponentProjection) {
		if (target == null || target == owner) {
			throw new IllegalArgumentException(
				"outgoing combat requires a distinct target");
		}
		final CombatEngagement existing = getOutgoing();
		if (existing != null && existing.peerOf(owner) != target) {
			closeOutgoing(CombatEngagementTerminalReason.RETARGETED);
		}

		final CombatEngagementAuthority targetAuthority =
			target.getCombatEngagementAuthority();
		final CombatEngagementAuthority first = firstLock(this, targetAuthority);
		final CombatEngagementAuthority second = first == this
			? targetAuthority : this;
		synchronized (first) {
			synchronized (second) {
				pruneLocked();
				targetAuthority.pruneLocked();
				CombatEngagement engagement = validOutgoingLocked();
				if (engagement == null) {
					engagement = findIncomingLocked(target);
				}
				if (engagement == null
					|| !engagement.hasCurrentParticipantIdentity()) {
					engagement = new CombatEngagement(
						owner, target, currentTick());
				}
				engagement.activate(owner, style,
					legacyOpponentProjection, currentTick());
				outgoing = engagement;
				targetAuthority.incoming.put(owner.getUUID(), engagement);
				return engagement;
			}
		}
	}

	public void closeOutgoing(final CombatEngagementTerminalReason reason) {
		final CombatEngagement engagement = getOutgoing();
		if (engagement != null) {
			closeDirection(engagement, owner, normalize(reason));
		}
	}

	public void closeOutgoingIfEncounter(final UUID encounterId,
			final CombatEngagementTerminalReason reason) {
		final CombatEngagement engagement = getOutgoing();
		if (engagement != null && encounterId != null
			&& encounterId.equals(engagement.getEncounterId())) {
			closeDirection(engagement, owner, normalize(reason));
		}
	}

	public void closeAll(final CombatEngagementTerminalReason reason) {
		final List<DirectionClose> directions = new ArrayList<DirectionClose>();
		synchronized (this) {
			pruneLocked();
			if (outgoing != null && outgoing.isDirectionActive(owner)) {
				directions.add(new DirectionClose(outgoing, owner));
			}
			for (final CombatEngagement engagement : incoming.values()) {
				final Mob source = engagement.peerOf(owner);
				if (engagement.isDirectionActive(source)) {
					directions.add(new DirectionClose(engagement, source));
				}
			}
		}
		for (final DirectionClose direction : directions) {
			closeDirection(direction.engagement, direction.source,
				normalize(reason));
		}
	}

	public synchronized CombatEngagement getOutgoing() {
		pruneLocked();
		return validOutgoingLocked();
	}

	public synchronized Mob getOutgoingTarget() {
		final CombatEngagement engagement = getOutgoing();
		return engagement == null ? null : engagement.peerOf(owner);
	}

	public synchronized Mob getLegacyOpponentProjection() {
		final CombatEngagement engagement = getOutgoing();
		return engagement != null
			&& engagement.projectsLegacyOpponentFrom(owner)
			? engagement.peerOf(owner) : null;
	}

	public synchronized boolean hasOutgoing() {
		return getOutgoing() != null;
	}

	public synchronized boolean hasOutgoingAgainst(final Mob target) {
		return target != null && getOutgoingTarget() == target;
	}

	public synchronized boolean hasIncoming() {
		pruneLocked();
		return !incoming.isEmpty();
	}

	public synchronized boolean hasIncomingFrom(final Mob attacker) {
		return attacker != null && findIncomingLocked(attacker) != null;
	}

	public synchronized int incomingCount() {
		pruneLocked();
		return incoming.size();
	}

	public synchronized List<Mob> incomingAttackers() {
		pruneLocked();
		final List<Mob> attackers = new ArrayList<Mob>(incoming.size());
		for (final CombatEngagement engagement : incoming.values()) {
			final Mob attacker = engagement.peerOf(owner);
			if (engagement.isDirectionActive(attacker)) {
				attackers.add(attacker);
			}
		}
		return attackers;
	}

	public synchronized boolean isMutuallyEngagedWith(final Mob peer) {
		final CombatEngagement engagement = getOutgoing();
		return peer != null && engagement != null
			&& engagement.peerOf(owner) == peer
			&& engagement.isDirectionActive(peer);
	}

	public GameTickEvent registerEvent(final CombatEventSlot slot,
			final GameTickEvent event, final Mob target,
			final CombatStyle style, final boolean legacyOpponentProjection) {
		if (slot == null || event == null || target == null) {
			throw new IllegalArgumentException(
				"event slot, event, and target are required");
		}
		final CombatEngagement engagement = beginOutgoing(
			target, style, legacyOpponentProjection);
		synchronized (this) {
			final EventBinding previous = events.put(slot,
				new EventBinding(event, target,
					CombatParticipantSnapshot.capture(target),
					engagement.getEncounterId()));
			return previous == null ? null : previous.event;
		}
	}

	public synchronized <T extends GameTickEvent> T getEvent(
			final CombatEventSlot slot, final Class<T> eventType) {
		final EventBinding binding = currentBindingLocked(slot);
		return binding != null && eventType.isInstance(binding.event)
			? eventType.cast(binding.event) : null;
	}

	public synchronized boolean isCurrentEvent(final CombatEventSlot slot,
			final GameTickEvent event) {
		final EventBinding binding = currentBindingLocked(slot);
		return event != null && binding != null && binding.event == event;
	}

	public boolean clearEventIfCurrent(final CombatEventSlot slot,
			final GameTickEvent event,
			final CombatEngagementTerminalReason reason) {
		final UUID encounterId;
		synchronized (this) {
			final EventBinding binding = currentBindingLocked(slot);
			if (event == null || binding == null || binding.event != event) {
				return false;
			}
			events.remove(slot);
			encounterId = binding.encounterId;
		}
		if (!hasAnotherEventForEncounter(encounterId)) {
			closeOutgoingIfEncounter(encounterId, reason);
		}
		return true;
	}

	public synchronized List<GameTickEvent> currentEvents() {
		pruneEventBindingsLocked();
		final List<GameTickEvent> result =
			new ArrayList<GameTickEvent>(events.size());
		for (final EventBinding binding : events.values()) {
			result.add(binding.event);
		}
		return result;
	}

	public synchronized List<GameTickEvent> currentEventsTargeting(
			final Mob target) {
		pruneEventBindingsLocked();
		final List<GameTickEvent> result = new ArrayList<GameTickEvent>();
		for (final EventBinding binding : events.values()) {
			if (binding.target == target) {
				result.add(binding.event);
			}
		}
		return result;
	}

	/**
	 * Explicit diagnostics only. Routine callbacks must maintain ownership and
	 * must never depend on repair to complete normal teardown.
	 */
	public CombatOwnershipAudit audit(final boolean repair) {
		final List<String> discrepancies = new ArrayList<String>();
		int repaired = 0;
		synchronized (this) {
			final CombatEngagement current = validOutgoingLocked();
			if (outgoing != null && current == null) {
				discrepancies.add("stale outgoing engagement");
				if (repair) {
					outgoing = null;
					repaired++;
				}
			}
			final Iterator<Map.Entry<CombatEventSlot, EventBinding>> iterator =
				events.entrySet().iterator();
			while (iterator.hasNext()) {
				final Map.Entry<CombatEventSlot, EventBinding> entry = iterator.next();
				final EventBinding binding = entry.getValue();
				if (!binding.isCurrent()) {
					discrepancies.add("stale event slot " + entry.getKey());
					if (repair) {
						binding.event.stop();
						iterator.remove();
						repaired++;
					}
				}
			}
		}
		if (!discrepancies.isEmpty()) {
			LOGGER.warn("Combat ownership audit for {} found {}: {}",
				owner, discrepancies.size(), discrepancies);
		}
		if (repair && repaired > 0) {
			closeOutgoing(CombatEngagementTerminalReason.AUDIT_REPAIR);
		}
		return new CombatOwnershipAudit(discrepancies, repaired);
	}

	private synchronized boolean hasAnotherEventForEncounter(
			final UUID encounterId) {
		if (encounterId == null) {
			return false;
		}
		for (final EventBinding binding : events.values()) {
			if (encounterId.equals(binding.encounterId)
				&& binding.isCurrent()) {
				return true;
			}
		}
		return false;
	}

	private EventBinding currentBindingLocked(final CombatEventSlot slot) {
		final EventBinding binding = events.get(slot);
		if (binding != null && !binding.isCurrent()) {
			return null;
		}
		return binding;
	}

	private void pruneEventBindingsLocked() {
		final Iterator<Map.Entry<CombatEventSlot, EventBinding>> iterator =
			events.entrySet().iterator();
		while (iterator.hasNext()) {
			if (!iterator.next().getValue().isCurrent()) {
				iterator.remove();
			}
		}
	}

	private void pruneLocked() {
		if (outgoing != null && (!outgoing.hasCurrentParticipantIdentity()
			|| !outgoing.isDirectionActive(owner))) {
			outgoing = null;
		}
		final Iterator<Map.Entry<UUID, CombatEngagement>> iterator =
			incoming.entrySet().iterator();
		while (iterator.hasNext()) {
			final Map.Entry<UUID, CombatEngagement> entry = iterator.next();
			final CombatEngagement engagement = entry.getValue();
			final Mob source = engagement.peerOf(owner);
			if (!entry.getKey().equals(source.getUUID())
				|| !engagement.hasCurrentParticipantIdentity()
				|| !engagement.isDirectionActive(source)) {
				iterator.remove();
			}
		}
		pruneEventBindingsLocked();
	}

	private CombatEngagement validOutgoingLocked() {
		return outgoing != null && outgoing.hasCurrentParticipantIdentity()
			&& outgoing.isDirectionActive(owner) ? outgoing : null;
	}

	private CombatEngagement findIncomingLocked(final Mob attacker) {
		final CombatEngagement engagement = incoming.get(attacker.getUUID());
		return engagement != null && engagement.hasCurrentParticipantIdentity()
			&& engagement.isDirectionActive(attacker) ? engagement : null;
	}

	private static void closeDirection(final CombatEngagement engagement,
			final Mob source, final CombatEngagementTerminalReason reason) {
		if (engagement == null || source == null || !engagement.contains(source)) {
			return;
		}
		final Mob target = engagement.peerOf(source);
		final CombatEngagementAuthority sourceAuthority =
			source.getCombatEngagementAuthority();
		final CombatEngagementAuthority targetAuthority =
			target.getCombatEngagementAuthority();
		final CombatEngagementAuthority first =
			firstLock(sourceAuthority, targetAuthority);
		final CombatEngagementAuthority second = first == sourceAuthority
			? targetAuthority : sourceAuthority;
		synchronized (first) {
			synchronized (second) {
				if (!engagement.close(source, reason,
					sourceAuthority.currentTick())) {
					return;
				}
				if (sourceAuthority.outgoing == engagement) {
					sourceAuthority.outgoing = null;
				}
				final CombatEngagement incomingEngagement =
					targetAuthority.incoming.get(source.getUUID());
				if (incomingEngagement == engagement) {
					targetAuthority.incoming.remove(source.getUUID());
				}
			}
		}
	}

	private long currentTick() {
		return owner.getWorld().getServer().getCurrentTick();
	}

	private static CombatEngagementAuthority firstLock(
			final CombatEngagementAuthority left,
			final CombatEngagementAuthority right) {
		return left.owner.getUUID().compareTo(right.owner.getUUID()) <= 0
			? left : right;
	}

	private static CombatEngagementTerminalReason normalize(
			final CombatEngagementTerminalReason reason) {
		return reason == null
			? CombatEngagementTerminalReason.EVENT_ENDED : reason;
	}

	private static final class EventBinding {
		private final GameTickEvent event;
		private final Mob target;
		private final CombatParticipantSnapshot targetSnapshot;
		private final UUID encounterId;

		private EventBinding(final GameTickEvent event, final Mob target,
				final CombatParticipantSnapshot targetSnapshot,
				final UUID encounterId) {
			this.event = event;
			this.target = target;
			this.targetSnapshot = targetSnapshot;
			this.encounterId = encounterId;
		}

		private boolean isCurrent() {
			return event.isRunning() && targetSnapshot.matches(target);
		}
	}

	private static final class DirectionClose {
		private final CombatEngagement engagement;
		private final Mob source;

		private DirectionClose(final CombatEngagement engagement,
				final Mob source) {
			this.engagement = engagement;
			this.source = source;
		}
	}
}
