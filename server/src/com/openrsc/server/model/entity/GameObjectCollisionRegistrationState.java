package com.openrsc.server.model.entity;

import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionTransactionContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable copy of the exact collision footprint successfully registered for
 * one GameObject. It contains no World, Region, TileValue, or entity handle.
 */
public final class GameObjectCollisionRegistrationState {
	private final int objectId;
	private final int permanentObjectId;
	private final int x;
	private final int y;
	private final int direction;
	private final int type;
	private final List<CollisionContribution> contributions;
	private final List<PackedRegionCoordinate> requiredRegions;

	private GameObjectCollisionRegistrationState(
		final GameObject object,
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint) {
		GameObject checkedObject = Objects.requireNonNull(object, "object");
		GameTickEventRestorationCollisionFootprintPlanner.Result checkedFootprint =
			Objects.requireNonNull(footprint, "footprint");
		if (!checkedFootprint.isFootprintAvailable()
			|| checkedFootprint.getOperation() != Operation.REGISTER
			|| checkedFootprint.isLegacySaturatingUnregister()) {
			throw new IllegalArgumentException(
				"Collision registration state requires an exact register footprint");
		}
		this.objectId = checkedObject.getID();
		this.permanentObjectId = checkedObject.getLoc().getPermId();
		this.x = checkedObject.getLoc().getX();
		this.y = checkedObject.getLoc().getY();
		this.direction = checkedObject.getDirection();
		this.type = checkedObject.getType();
		List<CollisionContribution> copiedContributions =
			new ArrayList<CollisionContribution>(
				checkedFootprint.getContributionTileCount());
		for (GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution contribution
					: checkedFootprint.getContributions()) {
			copiedContributions.add(CollisionContribution.copyOf(contribution));
		}
		this.contributions = Collections.unmodifiableList(copiedContributions);
		List<PackedRegionCoordinate> copiedRegions =
			new ArrayList<PackedRegionCoordinate>(
				checkedFootprint.getRequiredRegionCount());
		for (GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate region
					: checkedFootprint.getRequiredRegions()) {
			copiedRegions.add(PackedRegionCoordinate.copyOf(region));
		}
		this.requiredRegions = Collections.unmodifiableList(copiedRegions);
		if (requiredRegions.isEmpty()) {
			throw new IllegalArgumentException(
				"Collision registration state requires Region coverage");
		}
	}

	public static GameObjectCollisionRegistrationState capture(
		final GameObject object,
		final GameTickEventRestorationCollisionFootprintPlanner.Result footprint) {
		return new GameObjectCollisionRegistrationState(object, footprint);
	}

	public boolean matchesConstructor(final GameObject object) {
		return object != null
			&& object.getID() == objectId
			&& object.getLoc().getPermId() == permanentObjectId
			&& object.getLoc().getX() == x
			&& object.getLoc().getY() == y
			&& object.getDirection() == direction
			&& object.getType() == type;
	}

	public int getObjectId() { return objectId; }
	public int getPermanentObjectId() { return permanentObjectId; }
	public int getX() { return x; }
	public int getY() { return y; }
	public int getDirection() { return direction; }
	public int getType() { return type; }
	public List<CollisionContribution> getContributions() {
		return contributions;
	}
	public int getContributionTileCount() { return contributions.size(); }
	public List<PackedRegionCoordinate> getRequiredRegions() {
		return requiredRegions;
	}
	public int getRequiredRegionCount() { return requiredRegions.size(); }
	public boolean isDetachedPrimitiveCopy() { return true; }
	public boolean isRuntimeHandleRetained() { return false; }
	public boolean isRegionLoadingPerformed() { return false; }
	public boolean isMutationAuthorized() { return false; }
	public boolean isMutationPerformed() { return false; }
	public boolean isArrivalGate() { return false; }
	public boolean isLifecycleAuthority() { return false; }

	/** Exact per-tile contribution copied from the successful registration. */
	public static final class CollisionContribution {
		private final int x;
		private final int y;
		private final int blockingSceneryCount;
		private final int[] dynamicCollisionCounts;
		private final int dynamicProjectileCount;

		private CollisionContribution(
			final GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution source) {
			this.x = source.getX();
			this.y = source.getY();
			this.blockingSceneryCount = source.getBlockingSceneryCount();
			this.dynamicCollisionCounts = source.getDynamicCollisionCounts();
			this.dynamicProjectileCount = source.getDynamicProjectileCount();
		}

		private static CollisionContribution copyOf(
			final GameTickEventRestorationTransientRollbackSnapshot
				.CollisionContribution source) {
			return new CollisionContribution(
				Objects.requireNonNull(source, "collisionContribution"));
		}

		public int getX() { return x; }
		public int getY() { return y; }
		public int getBlockingSceneryCount() {
			return blockingSceneryCount;
		}
		public int[] getDynamicCollisionCounts() {
			return dynamicCollisionCounts.clone();
		}
		public int getDynamicCollisionCount(final int bit) {
			return dynamicCollisionCounts[bit];
		}
		public int getDynamicProjectileCount() {
			return dynamicProjectileCount;
		}
	}

	/** Detached packed Region coordinate copied from the footprint. */
	public static final class PackedRegionCoordinate {
		private final int regionX;
		private final int regionY;

		private PackedRegionCoordinate(
			final GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate source) {
			this.regionX = source.getRegionX();
			this.regionY = source.getRegionY();
		}

		private static PackedRegionCoordinate copyOf(
			final GameTickEventRestorationCollisionTransactionContract
				.PackedRegionCoordinate source) {
			return new PackedRegionCoordinate(
				Objects.requireNonNull(source, "requiredRegion"));
		}

		public int getRegionX() { return regionX; }
		public int getRegionY() { return regionY; }
	}
}
