package com.openrsc.server.model.world.region;

import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldRegionKey;
import com.openrsc.server.model.world.coordinate.WorldRegionWindow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative world-space/level-qualified runtime entity membership.
 *
 * <p>This index deliberately owns no terrain or collision state. Packed
 * {@link Region} containers remain the compatibility terrain backend until
 * native layered terrain storage is separately enabled.</p>
 */
public final class LayeredSpatialEntityIndex {
	private final Object lock = new Object();
	private final Map<WorldRegionKey, LinkedHashSet<Entity>> regions =
		new java.util.HashMap<WorldRegionKey, LinkedHashSet<Entity>>();
	private final IdentityHashMap<Entity, WorldLocation> memberships =
		new IdentityHashMap<Entity, WorldLocation>();
	private long version;
	private long objectVersion;

	public void synchronize(
		final Entity entity,
		final WorldLocation expectedPrevious,
		final WorldLocation target) {
		Entity checkedEntity = Objects.requireNonNull(entity, "entity");
		WorldLocation checkedTarget = Objects.requireNonNull(target, "target");
		synchronized (lock) {
			WorldLocation current = memberships.get(checkedEntity);
			if (current == null) {
				if (expectedPrevious != null) {
					throw new IllegalStateException(
						"Layered entity membership is missing before movement");
				}
				add(checkedEntity, checkedTarget);
				return;
			}
			if (expectedPrevious != null && !current.equals(expectedPrevious)) {
				throw new IllegalStateException(
					"Layered entity membership differs from expected movement origin");
			}
			if (current.equals(checkedTarget)) {
				return;
			}
			removeFromRegion(checkedEntity, current);
			addToRegion(checkedEntity, checkedTarget);
			memberships.put(checkedEntity, checkedTarget);
			advanceVersion(checkedEntity);
		}
	}

	public void remove(
		final Entity entity,
		final WorldLocation expectedLocation) {
		Entity checkedEntity = Objects.requireNonNull(entity, "entity");
		WorldLocation checkedLocation = Objects.requireNonNull(
			expectedLocation, "expectedLocation");
		synchronized (lock) {
			WorldLocation current = memberships.get(checkedEntity);
			if (!checkedLocation.equals(current)) {
				throw new IllegalStateException(
					"Layered entity removal differs from indexed membership");
			}
			removeFromRegion(checkedEntity, current);
			memberships.remove(checkedEntity);
			advanceVersion(checkedEntity);
		}
	}

	public void requireMembership(
		final Entity entity,
		final WorldLocation expectedLocation) {
		synchronized (lock) {
			if (!Objects.requireNonNull(expectedLocation, "expectedLocation")
				.equals(memberships.get(
					Objects.requireNonNull(entity, "entity")))) {
				throw new IllegalStateException(
					"Layered entity authority differs from spatial membership");
			}
		}
	}

	public Snapshot snapshot(final WorldRegionWindow window) {
		WorldRegionWindow checked = Objects.requireNonNull(window, "window");
		if (checked.getRegionCount()
			> RegionManager.MAX_LAYERED_REGIONS_PER_INTEREST_OWNER) {
			throw new IllegalArgumentException(
				"Layered spatial window exceeds its bounded region count");
		}
		synchronized (lock) {
			List<Entity> entities = new ArrayList<Entity>();
			for (int regionX = checked.getMinRegionX();
				regionX <= checked.getMaxRegionX(); regionX++) {
				for (int regionY = checked.getMinRegionY();
					regionY <= checked.getMaxRegionY(); regionY++) {
					Collection<Entity> members = regions.get(
						new WorldRegionKey(
							checked.getWorldSpace(), checked.getLevel(),
							regionX, regionY));
					if (members != null) {
						entities.addAll(members);
					}
				}
			}
			return new Snapshot(
				checked, version, objectVersion, entities);
		}
	}

	public int getMembershipCount() {
		synchronized (lock) {
			return memberships.size();
		}
	}

	public void clear() {
		synchronized (lock) {
			regions.clear();
			memberships.clear();
			version++;
			objectVersion++;
		}
	}

	private void add(
		final Entity entity,
		final WorldLocation location) {
		addToRegion(entity, location);
		memberships.put(entity, location);
		advanceVersion(entity);
	}

	private void advanceVersion(final Entity entity) {
		version++;
		if (entity instanceof GameObject) {
			objectVersion++;
		}
	}

	private void addToRegion(
		final Entity entity,
		final WorldLocation location) {
		WorldRegionKey key = WorldRegionKey.from(location);
		LinkedHashSet<Entity> members = regions.get(key);
		if (members == null) {
			members = new LinkedHashSet<Entity>();
			regions.put(key, members);
		}
		if (!members.add(entity)) {
			throw new IllegalStateException(
				"Layered entity is already present in its target region");
		}
	}

	private void removeFromRegion(
		final Entity entity,
		final WorldLocation location) {
		WorldRegionKey key = WorldRegionKey.from(location);
		LinkedHashSet<Entity> members = regions.get(key);
		if (members == null || !members.remove(entity)) {
			throw new IllegalStateException(
				"Layered entity is absent from its indexed region");
		}
		if (members.isEmpty()) {
			regions.remove(key);
		}
	}

	public static final class Snapshot {
		private final WorldRegionWindow window;
		private final long version;
		private final long objectVersion;
		private final List<Entity> entities;

		private Snapshot(
			final WorldRegionWindow window,
			final long version,
			final long objectVersion,
			final List<Entity> entities) {
			this.window = window;
			this.version = version;
			this.objectVersion = objectVersion;
			this.entities = Collections.unmodifiableList(
				new ArrayList<Entity>(entities));
		}

		public WorldRegionWindow getWindow() {
			return window;
		}

		public long getVersion() {
			return version;
		}

		public long getObjectVersion() {
			return objectVersion;
		}

		public List<Entity> getEntities() {
			return entities;
		}
	}
}
