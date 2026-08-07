package com.openrsc.server.model.combat;

import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Per-projectile receipt for resource and progression work already settled by
 * the launch producer.
 *
 * <p>The ledger is deliberately observational. It never removes or grants an
 * item, awards experience, rolls recovery, refunds a cost, or changes impact
 * eligibility. Producers retain their established ordering and record the
 * result here. A ledger may be populated only before it is sealed; binding it
 * to an event does not grant it gameplay authority.</p>
 */
public final class ProjectileResourceLedger {
	public enum Coverage {
		TRACKED_LAUNCH,
		NO_PROJECTILE_RESOURCES,
		CALLER_OWNED,
		UNCLASSIFIED_COMPATIBILITY,
		UNRECORDED_TRACKED_PRODUCER
	}

	public enum State {
		OPEN,
		SEALED
	}

	public enum ItemSource {
		INVENTORY,
		EQUIPMENT,
		WIELDED_INVENTORY
	}

	public enum Preservation {
		NONE,
		MAGIC_CAPE,
		EQUIPMENT_EFFECT,
		REMOVAL_FAILED_COMPATIBILITY
	}

	public enum RecoveryDestination {
		NOT_RECOVERED,
		RING_OF_AVARICE,
		LOOT_GOBLIN,
		GROUND_NEW_STACK,
		GROUND_EXISTING_STACK,
		LEGACY_GROUND_PER_PLAYER
	}

	public enum ExperienceBasis {
		RANGED_HIT,
		MAGIC_BASE_CAST
	}

	public static final class ItemCost {
		private final int itemId;
		private final int requestedAmount;
		private final int removedAmount;
		private final ItemSource source;
		private final Preservation preservation;

		private ItemCost(final int itemId, final int requestedAmount,
				final int removedAmount, final ItemSource source,
				final Preservation preservation) {
			this.itemId = itemId;
			this.requestedAmount = requestedAmount;
			this.removedAmount = removedAmount;
			this.source = source;
			this.preservation = preservation;
		}

		public int getItemId() {
			return itemId;
		}

		public int getRequestedAmount() {
			return requestedAmount;
		}

		public int getRemovedAmount() {
			return removedAmount;
		}

		public ItemSource getSource() {
			return source;
		}

		public Preservation getPreservation() {
			return preservation;
		}
	}

	public static final class Recovery {
		private final int itemId;
		private final int recoveredAmount;
		private final RecoveryDestination destination;
		private final WorldLocation decisionLocation;

		private Recovery(final int itemId, final int recoveredAmount,
				final RecoveryDestination destination,
				final WorldLocation decisionLocation) {
			this.itemId = itemId;
			this.recoveredAmount = recoveredAmount;
			this.destination = destination;
			this.decisionLocation = decisionLocation;
		}

		public int getItemId() {
			return itemId;
		}

		public int getRecoveredAmount() {
			return recoveredAmount;
		}

		public RecoveryDestination getDestination() {
			return destination;
		}

		public WorldLocation getDecisionLocation() {
			return decisionLocation;
		}
	}

	public static final class ExperienceAward {
		private final int skillId;
		private final int baseAmount;
		private final int appliedAmount;
		private final ExperienceBasis basis;

		private ExperienceAward(final int skillId, final int baseAmount,
				final int appliedAmount, final ExperienceBasis basis) {
			this.skillId = skillId;
			this.baseAmount = baseAmount;
			this.appliedAmount = appliedAmount;
			this.basis = basis;
		}

		public int getSkillId() {
			return skillId;
		}

		public int getBaseAmount() {
			return baseAmount;
		}

		public int getAppliedAmount() {
			return appliedAmount;
		}

		public ExperienceBasis getBasis() {
			return basis;
		}
	}

	private final ProjectileLaunchSpecification.Producer producer;
	private final Coverage coverage;
	private final List<ItemCost> itemCosts = new ArrayList<ItemCost>();
	private final List<Recovery> recoveries = new ArrayList<Recovery>();
	private final List<ExperienceAward> experienceAwards =
		new ArrayList<ExperienceAward>();
	private State state;
	private UUID eventId;

	private ProjectileResourceLedger(
			final ProjectileLaunchSpecification.Producer producer,
			final Coverage coverage, final State state) {
		if (producer == null || coverage == null || state == null) {
			throw new IllegalArgumentException(
				"resource ledger identity cannot be null");
		}
		this.producer = producer;
		this.coverage = coverage;
		this.state = state;
	}

	public static ProjectileResourceLedger trackedLaunch(
			final ProjectileLaunchSpecification.Producer producer) {
		return new ProjectileResourceLedger(
			producer, Coverage.TRACKED_LAUNCH, State.OPEN);
	}

	public static ProjectileResourceLedger defaultFor(
			final ProjectileLaunchSpecification.Producer producer) {
		switch (producer) {
			case PLAYER_BOW:
			case PLAYER_THROWN:
			case PLAYER_SHURIKEN:
			case PLAYER_MAGIC:
			case PLAYER_IBAN_MAGIC:
			case CANNON:
			case LEGACY_NPC_RANGED:
			case MAGIC_SCRIPTED_EFFECT:
			case LEGENDS_HOLY_WATER:
				return sealed(producer, Coverage.UNRECORDED_TRACKED_PRODUCER);
			case GNOME_BALL:
				return sealed(producer, Coverage.CALLER_OWNED);
			case COMPATIBILITY:
				return sealed(producer, Coverage.UNCLASSIFIED_COMPATIBILITY);
			default:
				return sealed(producer, Coverage.NO_PROJECTILE_RESOURCES);
		}
	}

	private static ProjectileResourceLedger sealed(
			final ProjectileLaunchSpecification.Producer producer,
			final Coverage coverage) {
		return new ProjectileResourceLedger(producer, coverage, State.SEALED);
	}

	public synchronized void recordItemCost(final int itemId,
			final int requestedAmount, final int removedAmount,
			final ItemSource source, final Preservation preservation) {
		requireOpenTracked();
		if (itemId < 0 || requestedAmount <= 0 || removedAmount < 0
				|| removedAmount > requestedAmount || source == null
				|| preservation == null) {
			throw new IllegalArgumentException("invalid projectile item cost");
		}
		if (removedAmount < requestedAmount
				&& preservation == Preservation.NONE) {
			throw new IllegalArgumentException(
				"a partially preserved cost requires a reason");
		}
		itemCosts.add(new ItemCost(itemId, requestedAmount, removedAmount,
			source, preservation));
	}

	public synchronized void recordRecovery(final int itemId,
			final int recoveredAmount,
			final RecoveryDestination destination,
			final WorldLocation decisionLocation) {
		requireOpenTracked();
		if (itemId < 0 || recoveredAmount < 0 || destination == null
				|| decisionLocation == null) {
			throw new IllegalArgumentException("invalid projectile recovery");
		}
		if ((destination == RecoveryDestination.NOT_RECOVERED)
				!= (recoveredAmount == 0)) {
			throw new IllegalArgumentException(
				"recovery amount does not match its destination");
		}
		recoveries.add(new Recovery(itemId, recoveredAmount, destination,
			decisionLocation));
	}

	public synchronized void recordExperience(final int skillId,
			final int baseAmount, final int appliedAmount,
			final ExperienceBasis basis) {
		requireOpenTracked();
		if (skillId < 0 || baseAmount < 0 || appliedAmount < 0
				|| basis == null) {
			throw new IllegalArgumentException(
				"invalid projectile experience award");
		}
		experienceAwards.add(new ExperienceAward(
			skillId, baseAmount, appliedAmount, basis));
	}

	public synchronized void seal() {
		requireOpenTracked();
		state = State.SEALED;
	}

	public synchronized void bindEvent(final UUID newEventId,
			final ProjectileLaunchSpecification.Producer eventProducer) {
		if (newEventId == null || eventProducer == null) {
			throw new IllegalArgumentException(
				"resource ledger event identity cannot be null");
		}
		if (producer != eventProducer) {
			throw new IllegalArgumentException(
				"resource ledger producer does not match projectile producer");
		}
		if (eventId != null) {
			throw new IllegalStateException(
				"resource ledger is already bound to an event");
		}
		eventId = newEventId;
	}

	public ProjectileLaunchSpecification.Producer getProducer() {
		return producer;
	}

	public Coverage getCoverage() {
		return coverage;
	}

	public synchronized State getState() {
		return state;
	}

	public synchronized UUID getEventId() {
		return eventId;
	}

	public synchronized List<ItemCost> getItemCosts() {
		return Collections.unmodifiableList(
			new ArrayList<ItemCost>(itemCosts));
	}

	public synchronized List<Recovery> getRecoveries() {
		return Collections.unmodifiableList(
			new ArrayList<Recovery>(recoveries));
	}

	public synchronized List<ExperienceAward> getExperienceAwards() {
		return Collections.unmodifiableList(
			new ArrayList<ExperienceAward>(experienceAwards));
	}

	private void requireOpenTracked() {
		if (coverage != Coverage.TRACKED_LAUNCH || state != State.OPEN) {
			throw new IllegalStateException(
				"resource ledger is not open tracked launch state");
		}
	}
}
