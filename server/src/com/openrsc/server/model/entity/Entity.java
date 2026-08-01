package com.openrsc.server.model.entity;

import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentitySlot;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.Region;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Entity {

	private final World world;

	private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

	private int id;

	private int index;

	private final AtomicReference<Point> location = new AtomicReference<Point>();

	private final AtomicReference<WorldLocation> worldLocation =
		new AtomicReference<WorldLocation>();

	private final AtomicReference<Region> region = new AtomicReference<Region>();

	private final LayeredAuthoredPlacementIdentitySlot
		authoredPlacementIdentity =
			new LayeredAuthoredPlacementIdentitySlot();

	private volatile boolean removed = false;

	private final EntityType entityType;

	public Entity(
		final World world,
		final EntityType entityType
	) {
		this.world = world;
		this.entityType = entityType;
	}

	public void updateRegion() {
		updateRegion(null, null);
	}

	public synchronized void updateRegion(Point oldLocation) {
		updateRegion(
			oldLocation,
			oldLocation == null ? null
				: LegacyPackedPointAdapter.fromLegacyPoint(oldLocation));
	}

	private synchronized void updateRegion(
		final Point oldLocation,
		final WorldLocation oldWorldLocation) {
		final Region oldRegion = region.get();
		if (oldRegion != null && oldLocation != null) {
			oldRegion.removeEntity(oldLocation, this);
		}

		if (!isRemoved()) {
			if (getWorld().getRegionManager()
					.usesNativeLayeredRegionlessMembership(
						getWorldLocation())) {
				/*
				 * Native package terrain is keyed by signed WorldLocation and
				 * must not borrow the packed surface Region at the same X/Y.
				 * Point remains only a compatibility coordinate carrier.
				 */
				region.set(null);
			} else {
				final Region newRegion = getWorld().getRegionManager()
					.getRegion(getLocation());
				region.set(newRegion);
				newRegion.addEntity(this);
			}
			if (getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
				getWorld().getRegionManager().synchronizeLayeredSpatialMembership(
					this, oldWorldLocation, getWorldLocation());
			}
		}
	}

	public boolean withinRange(final GameObject gameObject, final int radius) {
		if (!sharesSpatialDomain(gameObject)) {
			return false;
		}
		return withinRange(gameObject.closestBound(getLocation()), radius);
	}

	public boolean withinRange(final Entity e, final int radius) {
		return sharesSpatialDomain(e)
			&& withinRange(e.getLocation(), radius);
	}

	public boolean withinRange(final Point point, final int radius) {
		int xDiff = Math.abs(getLocation().getX() - point.getX());
		int yDiff = Math.abs(getLocation().getY() - point.getY());
		return xDiff <= radius && yDiff <= radius;
	}

	public boolean withinRange90Deg(final Entity e, final int radius) {
		return sharesSpatialDomain(e)
			&& withinRange90Deg(e.getLocation(), radius);
	}

	public boolean withinRange90Deg(final Point point, final int radius) {
		int xDiff = Math.abs(getLocation().getX() - point.getX());
		int yDiff = Math.abs(getLocation().getY() - point.getY());
		return xDiff <= radius && yDiff == 0 || xDiff == 0 && yDiff <= radius;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String string) {
		return (T) attributes.get(string);
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(String string, T fail) {
		T object = (T) attributes.get(string);
		if (object != null) {
			return object;
		}
		return fail;
	}

	public void removeAttribute(String string) {
		attributes.remove(string);
	}

	public void setAttribute(String string, Object object) {
		attributes.put(string, object);
	}

	/**
	 * Reports how much opaque runtime metadata is attached without exposing its
	 * keys, values, or backing collection. Region retirement diagnostics use
	 * this only to refuse an incomplete entity-preservation claim.
	 */
	public final int getRuntimeAttributeCount() {
		return attributes.size();
	}

	public final World getWorld() {
		return world;
	}

	public final ServerConfiguration getConfig() {
		return getWorld().getServer().getConfig();
	}

	public final int getID() {
		return id;
	}

	protected final void setID(final int newid) {
		id = newid;
	}

	public final int getIndex() {
		return index;
	}

	public final void setIndex(final int newIndex) {
		index = newIndex;
	}

	public final Point getLocation() {
		return location.get();
	}

	/**
	 * Returns this entity's checked world-space/level-qualified location.
	 *
	 * <p>The legacy Point remains available as a compatibility projection. A
	 * mismatch is always a state error, including while the private authority
	 * gate is disabled.</p>
	 */
	public final WorldLocation getWorldLocation() {
		Point legacy = location.get();
		WorldLocation layered = worldLocation.get();
		if (legacy == null || layered == null) {
			throw new IllegalStateException(
				"Entity world location has not been initialized");
		}
		Point expected = getWorld().getRegionManager()
			.toRuntimeCompatibilityPoint(layered);
		if (expected.getX() != legacy.getX()
			|| expected.getY() != legacy.getY()) {
			throw new IllegalStateException(
				"Entity legacy Point differs from its WorldLocation");
		}
		return layered;
	}

	public final boolean sharesSpatialDomain(final Entity other) {
		if (other == null) {
			return false;
		}
		if (!getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			return true;
		}
		WorldLocation left = getWorldLocation();
		WorldLocation right = other.getWorldLocation();
		return left.getWorldSpace().equals(right.getWorldSpace())
			&& left.getCoordinate().getLevel()
				== right.getCoordinate().getLevel();
	}

	public final LayeredAuthoredPlacementIdentity
		getAuthoredPlacementIdentity() {
		return authoredPlacementIdentity.get();
	}

	public final void assignAuthoredPlacementIdentity(
		final LayeredAuthoredPlacementIdentity identity) {
		authoredPlacementIdentity.assign(identity);
	}

	public void setLocation(final Point point) {
		WorldLocation current = worldLocation.get();
		setWorldLocationInternal(
			getWorld().getRegionManager().fromRuntimeCompatibilityPoint(
				Objects.requireNonNull(point, "point"),
				current,
				false));
	}

	public final void setWorldLocation(final WorldLocation newLocation) {
		if (!getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			throw new IllegalStateException(
				"Layered spatial runtime authority is disabled");
		}
		setWorldLocationInternal(
			Objects.requireNonNull(newLocation, "newLocation"));
	}

	private void setWorldLocationInternal(final WorldLocation newLocation) {
		Point projection = getWorld().getRegionManager()
			.toRuntimeCompatibilityPoint(newLocation);
		Point oldLocation = location.getAndSet(projection);
		WorldLocation oldWorldLocation = worldLocation.getAndSet(newLocation);
		updateRegion(oldLocation, oldWorldLocation);
	}

	public void setInitialLocation(final Point point) {
		// Used when logging in a player in order to not cause exceptions of missing locations while updating the region
		setInitialWorldLocationInternal(
			LegacyPackedPointAdapter.fromLegacyPoint(
				Objects.requireNonNull(point, "point")));
	}

	public final void setInitialWorldLocation(final WorldLocation newLocation) {
		if (!getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			throw new IllegalStateException(
				"Layered spatial runtime authority is disabled");
		}
		setInitialWorldLocationInternal(
			Objects.requireNonNull(newLocation, "newLocation"));
	}

	private void setInitialWorldLocationInternal(
		final WorldLocation newLocation) {
		location.set(getWorld().getRegionManager()
			.toRuntimeCompatibilityPoint(newLocation));
		worldLocation.set(newLocation);
	}

	/** Internal state half of an ordered GameObject membership transaction. */
	protected final synchronized void attachGameObjectTransactionState(
		final Point point,
		final Region targetRegion) {
		if (entityType != EntityType.GAME_OBJECT || location.get() != null
			|| region.get() != null || removed) {
			throw new IllegalStateException(
				"GameObject is not ready for ordered registration");
		}
		location.set(Objects.requireNonNull(point, "point"));
		worldLocation.set(LegacyPackedPointAdapter.fromLegacyPoint(point));
		region.set(Objects.requireNonNull(targetRegion, "targetRegion"));
	}

	/** Restores a failed ordered registration to its exact detached state. */
	protected final synchronized void detachGameObjectTransactionState(
		final Point expectedPoint,
		final Region expectedRegion) {
		if (entityType != EntityType.GAME_OBJECT || removed
			|| !Objects.equals(location.get(), expectedPoint)
			|| region.get() != expectedRegion) {
			throw new IllegalStateException(
				"GameObject registration rollback state is inconsistent");
		}
		location.set(null);
		worldLocation.set(null);
		region.set(null);
	}

	/** Marks membership removal while its ordered collision transaction is held. */
	protected final synchronized void removeGameObjectTransactionState(
		final Region expectedRegion) {
		if (entityType != EntityType.GAME_OBJECT || location.get() == null
			|| region.get() != expectedRegion || removed) {
			throw new IllegalStateException(
				"GameObject is not ready for ordered unregistration");
		}
		removed = true;
	}

	/** Reopens an object whose ordered unregistration was rolled back. */
	protected final synchronized void restoreGameObjectTransactionState(
		final Region expectedRegion) {
		if (entityType != EntityType.GAME_OBJECT || location.get() == null
			|| region.get() != expectedRegion || !removed) {
			throw new IllegalStateException(
				"GameObject unregistration rollback state is inconsistent");
		}
		removed = false;
	}

	public Region getRegion() {
		return region.get();
	}

	public final int getX() {
		return location.get().getX();
	}

	public final int getY() {
		return location.get().getY();
	}

	/**
	 * Normalize the player's Y coordinate by returning the Y value they would be at
	 * if they were on the ground floor (British convention)
	 *
	 * @param yPos The current Y coordinate of the player
	 * @return The player's Y coordinate if they were on the ground floor (British convention)
	 */
	public final int normalizeFloor(final int yPos) {
		// Each floor is 944 tiles apart, so we're subtracting the player's current position
		// by the floor they're on.
		return yPos - 944 * (yPos / 944);
	}

	public boolean isRemoved() {
		return removed;
	}

	protected void setRemoved(final boolean removed) {
		this.removed = removed;
	}

	public void remove() {
		if (getConfig().WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY) {
			final boolean nativeRegionless =
				getWorld().getRegionManager()
					.usesNativeLayeredRegionlessMembership(
						getWorldLocation());
			if (nativeRegionless && region.get() != null) {
				throw new IllegalStateException(
					"Native layered entity unexpectedly occupies a packed Region");
			}
			getWorld().getRegionManager().removeLayeredSpatialMembership(
				this, getWorldLocation());
			if (nativeRegionless) {
				setRemoved(true);
				return;
			}
		}
		if (region.get() == null) {
			throw new IllegalStateException(
				"Packed Region should not be null if remove() is called");
		}
		getRegion().removeEntity(this);
		setRemoved(true);
	}

	public abstract boolean isOn(final int x, final int y);

	public boolean isInvisibleTo(final Entity observer) {
		return false;
	}

	public boolean canSee(final Entity observed) {
		return observed == null
			|| (sharesSpatialDomain(observed) && !observed.isInvisibleTo(this));
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public boolean isPlayer() {
		return entityType == EntityType.PLAYER;
	}

	public boolean isNpc() {
		return entityType == EntityType.NPC;
	}
}
