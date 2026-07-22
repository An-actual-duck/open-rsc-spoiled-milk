package com.openrsc.server.model.world.region;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Entity;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredProvenanceObservation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Region {
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * The RegionManager this Region belongs to
	 */
	private final RegionManager regionManager;
	/**
	 * A list of players in this region.
	 */
	final private Multimap<Point, Player> players = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of NPCs in this region.
	 */
	final private Multimap<Point, Npc> npcs = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of objects in this region.
	 */
	final private Multimap<Point, GameObject> objects = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of objects in this region.
	 */
	final private Multimap<Point, GroundItem> items = Multimaps.synchronizedMultimap(LinkedHashMultimap.create());

	/**
	 * A list of tiles in this region.
	 */
	private volatile TileValue[][] tiles;

	/**
	 * The constant tile value used for this region.
	 */
	private volatile TileValue tile;

	/**
	 * The X index of this region
	 */
	private final int regionX;

	/**
	 * The Y index of this region
	 */
	private final int regionY;

	/** Dormant shared boundary for future object/collision transactions. */
	private final RegionObjectCollisionMutationBoundary
		objectCollisionMutationBoundary;

	/**
	 * This constructor is used to create a blank region
	 *
	 * @param regionManager
	 * @param regionX
	 * @param regionY
	 */
	public Region(final RegionManager regionManager, final int regionX, final int regionY) {
		this.regionManager = regionManager;
		this.regionX = regionX;
		this.regionY = regionY;
		this.objectCollisionMutationBoundary =
			new RegionObjectCollisionMutationBoundary(regionX, regionY);

		this.tiles = new TileValue[Constants.REGION_SIZE][Constants.REGION_SIZE];
		this.tile = null;

		for (int i = 0; i < Constants.REGION_SIZE; i++) {
			for (int j = 0; j < Constants.REGION_SIZE; j++) {
				tiles[i][j] = new TileValue();
			}
		}
	}

	public void unload() {
		players.clear();
		npcs.clear();
		objects.clear();
		items.clear();
		tiles = null;
		tile = null;
	}

	/**
	 * Captures entity counts and tile-storage presence without exposing the
	 * collections or their contents. This is read-only retirement evidence.
	 */
	RetirementContentsSnapshot captureRetirementContentsSnapshot() {
		synchronized (players) {
			synchronized (npcs) {
				synchronized (objects) {
					synchronized (items) {
						List<DynamicObjectSnapshot> dynamicObjects =
							new ArrayList<DynamicObjectSnapshot>();
						for (GameObject object : objects.values()) {
							if (object.getAuthoredPlacementIdentity() == null) {
								dynamicObjects.add(new DynamicObjectSnapshot(object));
							}
						}
						Collections.sort(dynamicObjects,
							DynamicObjectSnapshot.ORDER);
						return new RetirementContentsSnapshot(
							tiles != null || tile != null,
							players.size(), npcs.size(), objects.size(), items.size(),
							dynamicObjects, countCollisionProductTiles());
					}
				}
			}
		}
	}

	/**
	 * Counts tiles with current collision products without copying or exposing a
	 * TileValue. Mutation ownership is not captured, so callers must classify
	 * this count as partial evidence.
	 */
	private int countCollisionProductTiles() {
		TileValue shared = tile;
		if (shared != null) {
			return shared.hasCollisionProductState()
				? Constants.REGION_SIZE * Constants.REGION_SIZE : 0;
		}
		TileValue[][] expanded = tiles;
		if (expanded == null) {
			return -1;
		}
		int count = 0;
		for (int x = 0; x < Constants.REGION_SIZE; x++) {
			for (int y = 0; y < Constants.REGION_SIZE; y++) {
				TileValue value = expanded[x][y];
				if (value != null && value.hasCollisionProductState()) {
					count++;
				}
			}
		}
		return count;
	}

	/** Records detached authored identity metadata for active objects/items. */
	void recordAuthoredProvenance(
		final LayeredPackedRegionAuthoredProvenanceObservation.Builder builder) {
		if (builder == null) {
			throw new NullPointerException("builder");
		}
		synchronized (objects) {
			for (GameObject object : objects.values()) {
				if (object.getAuthoredPlacementIdentity() != null) {
					builder.recordRuntimeInstance(
						object.getAuthoredPlacementIdentity(), object.getID(),
						regionX, regionY, true);
				}
			}
		}
		synchronized (items) {
			for (GroundItem item : items.values()) {
				if (item.getAuthoredPlacementIdentity() != null) {
					builder.recordRuntimeInstance(
						item.getAuthoredPlacementIdentity(), item.getID(),
						regionX, regionY, true);
				}
			}
		}
	}

	/** Immutable Region-local counts; never an entity or tile handle. */
	static final class RetirementContentsSnapshot {
		private final boolean tileStorageAvailable;
		private final int playerCount;
		private final int npcCount;
		private final int objectCount;
		private final int groundItemCount;
		private final List<DynamicObjectSnapshot> dynamicObjects;
		private final int collisionProductTileCount;

		private RetirementContentsSnapshot(
			final boolean tileStorageAvailable,
			final int playerCount,
			final int npcCount,
			final int objectCount,
			final int groundItemCount,
			final List<DynamicObjectSnapshot> dynamicObjects,
			final int collisionProductTileCount) {
			this.tileStorageAvailable = tileStorageAvailable;
			this.playerCount = playerCount;
			this.npcCount = npcCount;
			this.objectCount = objectCount;
			this.groundItemCount = groundItemCount;
			this.dynamicObjects = Collections.unmodifiableList(
				new ArrayList<DynamicObjectSnapshot>(dynamicObjects));
			this.collisionProductTileCount = collisionProductTileCount;
		}

		boolean isTileStorageAvailable() { return tileStorageAvailable; }
		int getPlayerCount() { return playerCount; }
		int getNpcCount() { return npcCount; }
		int getObjectCount() { return objectCount; }
		int getGroundItemCount() { return groundItemCount; }
		int getDynamicObjectCount() { return dynamicObjects.size(); }
		List<DynamicObjectSnapshot> getDynamicObjects() {
			return dynamicObjects;
		}
		int getCollisionProductTileCount() {
			return collisionProductTileCount;
		}
	}

	/** Detached constructor-state evidence for one identity-less object. */
	static final class DynamicObjectSnapshot {
		private static final Comparator<DynamicObjectSnapshot> ORDER =
			new Comparator<DynamicObjectSnapshot>() {
				@Override
				public int compare(
					final DynamicObjectSnapshot left,
					final DynamicObjectSnapshot right) {
					int compared = Integer.compare(left.y, right.y);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.x, right.x);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.type, right.type);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.direction, right.direction);
					if (compared != 0) { return compared; }
					compared = Integer.compare(left.objectId, right.objectId);
					if (compared != 0) { return compared; }
					compared = Integer.compare(
						left.permanentObjectId, right.permanentObjectId);
					if (compared != 0) { return compared; }
					if (left.owner == null) { return right.owner == null ? 0 : -1; }
					if (right.owner == null) { return 1; }
					compared = left.owner.compareTo(right.owner);
					return compared != 0 ? compared : Integer.compare(
						left.runtimeAttributeCount,
						right.runtimeAttributeCount);
				}
			};

		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;

		private DynamicObjectSnapshot(final GameObject object) {
			this.objectId = object.getID();
			this.permanentObjectId = object.getLoc().getPermId();
			this.x = object.getX();
			this.y = object.getY();
			this.direction = object.getDirection();
			this.type = object.getType();
			this.owner = object.getOwner();
			this.runtimeAttributeCount = object.getRuntimeAttributeCount();
		}

		int getObjectId() { return objectId; }
		int getPermanentObjectId() { return permanentObjectId; }
		int getX() { return x; }
		int getY() { return y; }
		int getDirection() { return direction; }
		int getType() { return type; }
		String getOwner() { return owner; }
		int getRuntimeAttributeCount() { return runtimeAttributeCount; }
	}

	/**
	 * Copies every object in one exact collision slot while holding the Region's
	 * object monitor. The returned values contain no entity handles.
	 */
	RestorationTargetSlotSnapshot captureRestorationTargetSlotSnapshot(
		final int x,
		final int y,
		final int type,
		final int direction) {
		if (x < 0 || y < 0 || (type != 0 && type != 1)
			|| direction < 0 || direction > 7
			|| x / Constants.REGION_SIZE != regionX
			|| y / Constants.REGION_SIZE != regionY) {
			throw new IllegalArgumentException(
				"Restoration target slot is outside this Region");
		}
		Point location = Point.location(x, y);
		List<RestorationTargetObjectSnapshot> snapshots =
			new ArrayList<RestorationTargetObjectSnapshot>();
		synchronized (objects) {
			for (GameObject object : objects.get(location)) {
				if (object.getType() == type
					&& (type == 0 || object.getDirection() == direction)) {
					snapshots.add(
						new RestorationTargetObjectSnapshot(object));
				}
			}
		}
		return new RestorationTargetSlotSnapshot(snapshots);
	}

	/**
	 * Compares one exact restoration slot while the Region object monitor is
	 * genuinely held. The returned snapshot contains counts and a closed state
	 * only; it retains no entity, collection, Region, or monitor handle and is
	 * stale as soon as the boundary is released.
	 */
	RestorationTargetBoundarySnapshot
		captureRestorationTargetBoundarySnapshot(
			final RestorationTargetMatchRequirement requirement,
			final boolean targetBindingComplete) {
		RestorationTargetMatchRequirement checked =
			Objects.requireNonNull(requirement, "requirement");
		if (checked.getX() / Constants.REGION_SIZE != regionX
			|| checked.getY() / Constants.REGION_SIZE != regionY) {
			throw new IllegalArgumentException(
				"Restoration target boundary is outside this Region");
		}
		Point location = Point.location(checked.getX(), checked.getY());
		synchronized (objects) {
			int slotObjectCount = 0;
			int exactRestorationSceneryCount = 0;
			int exactAuthoredIdentityCount = 0;
			for (GameObject object : objects.get(location)) {
				if (object.getType() != checked.getType()
					|| (checked.getType() != 0
						&& object.getDirection() != checked.getDirection())) {
					continue;
				}
				slotObjectCount++;
				exactRestorationSceneryCount +=
					checked.matchesRestorationScenery(object) ? 1 : 0;
				exactAuthoredIdentityCount +=
					checked.matchesAuthoredIdentity(object) ? 1 : 0;
			}
			RestorationTargetBoundaryState state =
				RestorationTargetBoundaryState.classify(
					slotObjectCount, exactRestorationSceneryCount,
					exactAuthoredIdentityCount, targetBindingComplete);
			return new RestorationTargetBoundarySnapshot(
				slotObjectCount, exactRestorationSceneryCount,
				exactAuthoredIdentityCount, state,
				Thread.holdsLock(objects));
		}
	}

	/** Detached constructor/provenance scalars used only during comparison. */
	static final class RestorationTargetMatchRequirement {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;
		private final long authoredGeneration;
		private final int authoredPackedRegionX;
		private final int authoredPackedRegionY;
		private final int authoredSourceOrdinal;
		private final String authoredConstructionKind;

		private RestorationTargetMatchRequirement(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final long authoredGeneration,
			final int authoredPackedRegionX,
			final int authoredPackedRegionY,
			final int authoredSourceOrdinal,
			final String authoredConstructionKind) {
			if (objectId < 0 || permanentObjectId < 0 || x < 0 || y < 0
				|| direction < 0 || direction > 7
				|| (type != 0 && type != 1)
				|| runtimeAttributeCount < 0 || authoredGeneration < 0L
				|| (authoredGeneration == 0L
					&& (authoredPackedRegionX != -1
						|| authoredPackedRegionY != -1
						|| authoredSourceOrdinal != 0
						|| authoredConstructionKind != null))
				|| (authoredGeneration > 0L
					&& (authoredPackedRegionX < 0
						|| authoredPackedRegionY < 0
						|| authoredSourceOrdinal <= 0
						|| authoredConstructionKind == null
						|| authoredConstructionKind.isEmpty()))) {
				throw new IllegalArgumentException(
					"Restoration target match requirement is invalid");
			}
			this.objectId = objectId;
			this.permanentObjectId = permanentObjectId;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
			this.owner = owner;
			this.runtimeAttributeCount = runtimeAttributeCount;
			this.authoredGeneration = authoredGeneration;
			this.authoredPackedRegionX = authoredPackedRegionX;
			this.authoredPackedRegionY = authoredPackedRegionY;
			this.authoredSourceOrdinal = authoredSourceOrdinal;
			this.authoredConstructionKind = authoredConstructionKind;
		}

		static RestorationTargetMatchRequirement of(
			final int objectId,
			final int permanentObjectId,
			final int x,
			final int y,
			final int direction,
			final int type,
			final String owner,
			final int runtimeAttributeCount,
			final long authoredGeneration,
			final int authoredPackedRegionX,
			final int authoredPackedRegionY,
			final int authoredSourceOrdinal,
			final String authoredConstructionKind) {
			return new RestorationTargetMatchRequirement(
				objectId, permanentObjectId, x, y, direction, type, owner,
				runtimeAttributeCount, authoredGeneration, authoredPackedRegionX,
				authoredPackedRegionY, authoredSourceOrdinal,
				authoredConstructionKind);
		}

		private boolean matchesRestorationScenery(final GameObject object) {
			return object.getID() == objectId
				&& object.getLoc().getPermId() == permanentObjectId
				&& object.getX() == x && object.getY() == y
				&& object.getDirection() == direction
				&& object.getType() == type
				&& Objects.equals(object.getOwner(), owner)
				&& object.getRuntimeAttributeCount() == runtimeAttributeCount;
		}

		private boolean matchesAuthoredIdentity(final GameObject object) {
			LayeredAuthoredPlacementIdentity identity =
				object.getAuthoredPlacementIdentity();
			return identity != null && authoredGeneration > 0L
				&& identity.getGeneration() == authoredGeneration
				&& identity.getPackedRegionX() == authoredPackedRegionX
				&& identity.getPackedRegionY() == authoredPackedRegionY
				&& identity.getSourceOrdinal() == authoredSourceOrdinal
				&& identity.getConstructionKind().name().equals(
					authoredConstructionKind);
		}

		int getX() { return x; }
		int getY() { return y; }
		int getDirection() { return direction; }
		int getType() { return type; }
	}

	/** Exact-slot counts and classification produced inside the object monitor. */
	static final class RestorationTargetBoundarySnapshot {
		private final int slotObjectCount;
		private final int exactRestorationSceneryCount;
		private final int exactAuthoredIdentityCount;
		private final RestorationTargetBoundaryState observedTargetState;
		private final boolean objectBoundaryHeldDuringClassification;

		private RestorationTargetBoundarySnapshot(
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final RestorationTargetBoundaryState observedTargetState,
			final boolean objectBoundaryHeldDuringClassification) {
			if (!objectBoundaryHeldDuringClassification) {
				throw new IllegalStateException(
					"Target classification escaped the Region object boundary");
			}
			this.slotObjectCount = slotObjectCount;
			this.exactRestorationSceneryCount =
				exactRestorationSceneryCount;
			this.exactAuthoredIdentityCount = exactAuthoredIdentityCount;
			this.observedTargetState = Objects.requireNonNull(
				observedTargetState, "observedTargetState");
			this.objectBoundaryHeldDuringClassification = true;
		}

		int getSlotObjectCount() { return slotObjectCount; }
		int getExactRestorationSceneryCount() {
			return exactRestorationSceneryCount;
		}
		int getExactAuthoredIdentityCount() {
			return exactAuthoredIdentityCount;
		}
		RestorationTargetBoundaryState getObservedTargetState() {
			return observedTargetState;
		}
		boolean isObjectBoundaryHeldDuringClassification() {
			return objectBoundaryHeldDuringClassification;
		}
	}

	/** Closed state classified without returning the objects used to derive it. */
	static enum RestorationTargetBoundaryState {
		EMPTY,
		EXACT_RESTORATION_SCENERY_PRESENT,
		EXACT_AUTHORED_TRANSIENT_PRESENT,
		MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
		AMBIGUOUS_OCCUPANCY;

		private static RestorationTargetBoundaryState classify(
			final int slotObjectCount,
			final int exactRestorationSceneryCount,
			final int exactAuthoredIdentityCount,
			final boolean targetBindingComplete) {
			if (slotObjectCount == 0) { return EMPTY; }
			if (slotObjectCount > 1) { return AMBIGUOUS_OCCUPANCY; }
			if (targetBindingComplete
				&& exactRestorationSceneryCount == 1
				&& exactAuthoredIdentityCount == 1) {
				return EXACT_RESTORATION_SCENERY_PRESENT;
			}
			if (targetBindingComplete && exactAuthoredIdentityCount == 1) {
				return EXACT_AUTHORED_TRANSIENT_PRESENT;
			}
			return MISMATCHED_OR_IDENTITYLESS_OCCUPANT;
		}
	}

	/** Exact-slot detached values; collection and members are immutable. */
	static final class RestorationTargetSlotSnapshot {
		private final List<RestorationTargetObjectSnapshot> objects;

		private RestorationTargetSlotSnapshot(
			final List<RestorationTargetObjectSnapshot> objects) {
			this.objects = Collections.unmodifiableList(
				new ArrayList<RestorationTargetObjectSnapshot>(objects));
		}

		List<RestorationTargetObjectSnapshot> getObjects() { return objects; }
		int getObjectCount() { return objects.size(); }
	}

	/** Constructor and authored-identity scalars copied from one slot object. */
	static final class RestorationTargetObjectSnapshot {
		private final int objectId;
		private final int permanentObjectId;
		private final int x;
		private final int y;
		private final int direction;
		private final int type;
		private final String owner;
		private final int runtimeAttributeCount;
		private final long authoredGeneration;
		private final int authoredPackedRegionX;
		private final int authoredPackedRegionY;
		private final int authoredSourceOrdinal;
		private final String authoredConstructionKind;

		private RestorationTargetObjectSnapshot(final GameObject object) {
			this.objectId = object.getID();
			this.permanentObjectId = object.getLoc().getPermId();
			this.x = object.getX();
			this.y = object.getY();
			this.direction = object.getDirection();
			this.type = object.getType();
			this.owner = object.getOwner();
			this.runtimeAttributeCount = object.getRuntimeAttributeCount();
			LayeredAuthoredPlacementIdentity identity =
				object.getAuthoredPlacementIdentity();
			this.authoredGeneration = identity == null
				? 0L : identity.getGeneration();
			this.authoredPackedRegionX = identity == null
				? -1 : identity.getPackedRegionX();
			this.authoredPackedRegionY = identity == null
				? -1 : identity.getPackedRegionY();
			this.authoredSourceOrdinal = identity == null
				? 0 : identity.getSourceOrdinal();
			this.authoredConstructionKind = identity == null
				? null : identity.getConstructionKind().name();
		}

		int getObjectId() { return objectId; }
		int getPermanentObjectId() { return permanentObjectId; }
		int getX() { return x; }
		int getY() { return y; }
		int getDirection() { return direction; }
		int getType() { return type; }
		String getOwner() { return owner; }
		int getRuntimeAttributeCount() { return runtimeAttributeCount; }
		boolean hasAuthoredIdentity() { return authoredGeneration > 0L; }
		long getAuthoredGeneration() { return authoredGeneration; }
		int getAuthoredPackedRegionX() { return authoredPackedRegionX; }
		int getAuthoredPackedRegionY() { return authoredPackedRegionY; }
		int getAuthoredSourceOrdinal() { return authoredSourceOrdinal; }
		String getAuthoredConstructionKind() {
			return authoredConstructionKind;
		}
	}

	/**
	 * Gets the list of players.
	 *
	 * @return The list of players.
	 */
	public Collection<Player> getPlayers() {
		return players.values();
	}

	/**
	 * Gets the list of NPCs.
	 *
	 * @return The list of NPCs.
	 */
	protected Collection<Npc> getNpcs() {
		return npcs.values();
	}

	/**
	 * Gets the list of objects.
	 *
	 * @return The list of objects.
	 */
	public Collection<GameObject> getGameObjects() {
		synchronized (objects) {
			return Collections.unmodifiableList(
				new ArrayList<GameObject>(objects.values()));
		}
	}

	protected Collection<GroundItem> getGroundItems() {
		return items.values();
	}

	public void removeEntity(Entity entity) {
		removeEntity(entity.getLocation(), entity);
	}

	public void removeEntity(Point location, final Entity entity) {
		if (entity.isPlayer()) {
			players.remove(location, entity);
		} else if (entity.isNpc()) {
			npcs.remove(location, entity);
		} else if (entity instanceof GameObject) {
			throw new IllegalStateException(
				"GameObject membership removal requires its ordered collision transaction");
		} else if (entity instanceof GroundItem) {
			items.remove(location, entity);
		}
	}

	public void addEntity(final Entity entity) {
		if (entity.isRemoved()) {
			return;
		}
		switch (entity.getEntityType()) {
			case PLAYER:
				players.put(entity.getLocation(), (Player) entity);
				break;
			case NPC:
				npcs.put(entity.getLocation(), (Npc) entity);
				break;
			case GAME_OBJECT:
				throw new IllegalStateException(
					"GameObject membership registration requires its ordered collision transaction");
			case GROUND_ITEM:
				items.put(entity.getLocation(), (GroundItem) entity);
				break;
		}
	}

	private String stringifyEntities(String title, Multimap<Point, ? extends Entity> multimap) {
		StringBuilder sb = new StringBuilder(2000);
		sb.append(title).append(":\n");
		for (Entity entity : multimap.values()) {
			sb.append("\t").append(entity).append("\n");
		}
		return sb.toString();
	}

	public String toString() {
		return toString(true, true, true, true);
	}

	public String toString(final boolean debugPlayers, final boolean debugNpcs, final boolean debugItems, final boolean debugObjects) {
		final StringBuilder sb = new StringBuilder(2000);
		if (debugPlayers) {
			sb.append(stringifyEntities("Players", players)).append("\n");
		}
		if (debugNpcs) {
			sb.append(stringifyEntities("Npcs", npcs)).append("\n");
		}
		if (debugItems) {
			sb.append(stringifyEntities("Items", items)).append("\n");
		}
		if (debugObjects) {
			sb.append(stringifyEntities("Objects", objects)).append("\n");
		}
		return sb.toString();
	}

	private GameObject getGameObject(Point location, Entity observer, GameObjectType type, Integer direction) {
		synchronized (objects) {
			return objects.get(location)
				.stream()
				.filter(obj -> type == null || obj.getGameObjectType() == type)
				.filter(obj -> observer == null || !obj.isInvisibleTo(observer))
				.filter(obj -> direction == null || obj.getDirection() == direction)
				.findFirst()
				.orElse(null);
		}
	}

	Object getGameObjectTransactionMonitor() { return objects; }

	boolean containsGameObjectIdentityUnderTransaction(
		final GameObject object) {
		requireGameObjectTransactionBoundary();
		for (GameObject candidate : objects.get(object.getLocation())) {
			if (candidate == object) { return true; }
		}
		return false;
	}

	GameObject getCollidingGameObjectUnderTransaction(
		final Point location,
		final GameObjectType type,
		final int direction) {
		requireGameObjectTransactionBoundary();
		for (GameObject candidate : objects.get(location)) {
			if (candidate.getGameObjectType() == type
				&& (type == GameObjectType.SCENERY
					|| candidate.getDirection() == direction)) {
				return candidate;
			}
		}
		return null;
	}

	boolean addGameObjectUnderTransaction(final GameObject object) {
		requireGameObjectTransactionBoundary();
		return objects.put(object.getLocation(), object);
	}

	boolean removeGameObjectUnderTransaction(final GameObject object) {
		requireGameObjectTransactionBoundary();
		return objects.remove(object.getLocation(), object);
	}

	private void requireGameObjectTransactionBoundary() {
		if (!objectCollisionMutationBoundary.isHeldByCurrentThread()
			|| !Thread.holdsLock(objects)) {
			throw new IllegalStateException(
				"GameObject membership escaped its ordered Region boundary");
		}
	}

	public GameObject getGameObject(Point location) {
		return getGameObject(location, null, null, null);
	}

	public GameObject getGameObject(Point location, Entity entity) {
		return getGameObject(location, entity, GameObjectType.SCENERY, null);
	}

	public GameObject getWallGameObject(Point location, int direction) {
		return getGameObject(location, null, GameObjectType.BOUNDARY, direction);
	}

	public GameObject getWallGameObject(Point location, Entity entity) {
		return getGameObject(location, entity, GameObjectType.BOUNDARY, null);
	}

	public Npc getNpc(Point location, Entity observer) {
		return npcs.get(location)
			.stream()
			.filter(npc -> observer == null || !npc.isInvisibleTo(observer))
			.findFirst()
			.orElse(null);
	}

	public Player getPlayer(int x, int y, Entity observer, boolean includeSelf) {
		return players.get(new Point(x, y))
			.stream()
			.filter(player -> observer == null || !player.isInvisibleTo(observer))
			.filter(player -> observer == null || (!includeSelf || player.equals(observer)))
			.findFirst()
			.orElse(null);
	}

	public Player getPlayer(int x, int y, Entity observer) {
		return getPlayer(x, y, observer, true);
	}

	public GroundItem getItem(final int id, final Point location, final Entity observer) {
		return items.get(location)
				.stream()
				.filter(item -> id == item.getID())
				.filter(item -> observer == null || !item.isInvisibleTo(observer))
				.findFirst()
				.orElse(null);
	}

	public TileValue getTileValue(final int regionX, final int regionY) {
		TileValue shared = tile;
		TileValue[][] expanded = tiles;
		return shared != null ? shared : expanded[regionX][regionY];
	}

	/** Expands a uniform region before returning a tile that will be mutated. */
	public TileValue getMutableTileValue(final int regionX, final int regionY) {
		TileValue[][] expanded = tiles;
		if (expanded != null) {
			return expanded[regionX][regionY];
		}
		synchronized (this) {
			if (tile != null) {
				TileValue shared = tile;
				expanded = new TileValue[Constants.REGION_SIZE][Constants.REGION_SIZE];
				for (int x = 0; x < Constants.REGION_SIZE; x++) {
					for (int y = 0; y < Constants.REGION_SIZE; y++) {
						expanded[x][y] = shared.copy();
					}
				}
				tiles = expanded;
				tile = null;
			}
			return tiles[regionX][regionY];
		}
	}

	public TileValue getTileValue(final Point regionPoint) {
		return getTileValue(regionPoint.getX(), regionPoint.getY());
	}

	public RegionManager getRegionManager() {
		return regionManager;
	}

	RegionObjectCollisionMutationBoundary
		getObjectCollisionMutationBoundary() {
		return objectCollisionMutationBoundary;
	}

	public int getRegionX() {
		return regionX;
	}

	public int getRegionY() {
		return regionY;
	}

	public void checkRegionValues() {
		boolean allTilesEqual = true;
		final TileValue firstTile = tiles[0][0];
		for (int i = 0; i < Constants.REGION_SIZE && allTilesEqual; i++) {
			for (int j = 0; j < Constants.REGION_SIZE && allTilesEqual; j++) {
				allTilesEqual = allTilesEqual && firstTile.equals(tiles[i][j]);
			}
		}

		if (allTilesEqual) {
			tile = tiles[0][0];
			tiles = null;
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(obj == this) {
			return true;
		}
		if(obj instanceof Region) {
			Region other = (Region) obj;
			return other.regionX == regionX && other.regionY == regionY;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return new Point(regionX, regionY).hashCode();
	}
}
