package com.openrsc.server.io;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.DoorDef;
import com.openrsc.server.external.TileDef;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.event.rsc.GameTickEventRestorationTransientRollbackSnapshot;
import com.openrsc.server.model.world.coordinate.LayeredCompatibilityPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;

/** Read-only whole-catalog preflight; never creates or registers gameplay entities. */
public final class NativeLayeredWorldPlacementPreflight {
	public interface Definitions {
		boolean npc(int id);
		boolean item(int id);
		GameObjectDef scenery(int id);
		DoorDef boundary(int id);
		default TileDef tile(int id) { return null; }
	}
	private NativeLayeredWorldPlacementPreflight() {}

	public static void validateNpcCapacity(NativeLayeredWorldPackageCatalog catalog, int availableSlots) {
		long required = 0;
		for (NativeLayeredWorldPackage loaded : catalog.getPackages()) required += loaded.getNpcPlacementCount();
		if (availableSlots < 0 || required > availableSlots) throw new IllegalStateException(
			"Native layered NPC catalog exceeds available registration capacity: required=" + required + " available=" + availableSlots);
	}

	public static void validate(NativeLayeredWorldPackageCatalog catalog, final EntityHandler handler,
		final int restrictedItemId) {
		validate(catalog, new Definitions() {
			public boolean npc(int id) { return handler.getNpcDef(id) != null; }
			public boolean item(int id) { return handler.getItemDef(id) != null
				&& (restrictedItemId <= -1 || id <= restrictedItemId); }
			public GameObjectDef scenery(int id) { return handler.getGameObjectDef(id); }
			public DoorDef boundary(int id) { return handler.getDoorDef(id); }
			public TileDef tile(int id) { return handler.getTileDef(id); }
		});
	}

	public static void validate(NativeLayeredWorldPackageCatalog catalog, Definitions definitions) {
		for (NativeLayeredWorldPackage loaded : catalog.getPackages()) {
			for (NativeLayeredPlacementSet set : loaded.getPlacementSets().values()) {
				for (NativeLayeredNpcPlacement npc : set.getNpcs()) {
					requireWireId("NPC", npc.getNpcId(), 1023, false);
					if (!definitions.npc(npc.getNpcId())) unavailable("NPC", npc.getNpcId());
					requireTerrain(loaded, npc.getStart(), npc.getPlacementId());
					// Loader already checked every roam sector; also check the
					// actual compatibility carrier bounds before any constructor.
					requireTerrain(loaded, at(npc.getStart(), npc.getMinX(), npc.getMinY()), npc.getPlacementId());
					requireTerrain(loaded, at(npc.getStart(), npc.getMaxX(), npc.getMaxY()), npc.getPlacementId());
				}
				for (NativeLayeredGroundItemPlacement item : set.getGroundItems()) {
					// Bit 15 means removal; 0xff in the first byte means region clear,
					// including the removal form (id + 32768).
					requireWireId("item", item.getItemId(), 0x7eff, false);
					if (!definitions.item(item.getItemId())) unavailable("item", item.getItemId());
					requireTerrain(loaded, item.getLocation(), item.getPlacementId());
				}
				for (NativeLayeredSceneryPlacement scenery : set.getScenery()) {
					requireWireId("scenery", scenery.getSceneryId(), 0xfeff, true);
					GameObjectDef definition = definitions.scenery(scenery.getSceneryId());
					if (definition == null) unavailable("scenery", scenery.getSceneryId());
					if (scenery.getAgilityShortcut() != null) {
						String action = scenery.getAgilityShortcut().getTemplate().action;
						if ((!action.equals(definition.getCommand1()) && !action.equals(definition.getCommand2())) || definition.getType() != 0)
							throw new IllegalStateException("Configured shortcut requires matching generic agility scenery: " + scenery.getPlacementId());
						AgilityShortcutLanding.Terrain landings = new AgilityShortcutLanding.Terrain() {
							public NativeLayeredTerrainTile tile(WorldLocation at) { return loaded.findTile(at).orElse(null); }
							public TileDef definition(int id) { return definitions.tile(id); }
							public boolean collisionClear(WorldLocation at) { return shortcutCollisionClear(catalog, definitions, at); }
						};
						AgilityShortcutLanding.requireSafe(scenery.getAgilityShortcut().getSuccessDestination(), landings);
						AgilityShortcutLanding.requireSafe(scenery.getAgilityShortcut().getFailureDestination(), landings);
					}
					WorldLocation origin = scenery.getLocation();
					requireTerrain(loaded, origin, scenery.getPlacementId());
					verifyCollision(loaded, origin, scenery.getPlacementId(), scenery.getSceneryId(),
						scenery.getDirection(), 0, Definition.scenery(definition.getType(), definition.getWidth(),
							definition.getHeight(), definition.getName(), Constants.objectsProjectileClipAllowed));
					if (definition.getType() != 0) {
						int width = scenery.getDirection() == 0 || scenery.getDirection() == 4
							? definition.getWidth() : definition.getHeight();
						int height = scenery.getDirection() == 0 || scenery.getDirection() == 4
							? definition.getHeight() : definition.getWidth();
						if ((long) width * height > GameTickEventRestorationTransientRollbackSnapshot.MAXIMUM_COLLISION_CONTRIBUTION_TILES) {
							throw new IllegalStateException("NPC-blocking footprint exceeds supported runtime budget: " + scenery.getPlacementId());
						}
						for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
							int tileX = Math.addExact(origin.getCoordinate().getX(), x);
							int tileY = Math.addExact(origin.getCoordinate().getY(), y);
							if (tileX < 0 || tileY < 0 || tileX >= Constants.MAX_WIDTH || tileY >= Constants.MAX_HEIGHT) {
								throw new IllegalStateException("NPC-blocking footprint leaves supported collision world: " + scenery.getPlacementId());
							}
							requireTerrain(loaded, at(origin, tileX, tileY), scenery.getPlacementId());
						}
					}
				}
				for (NativeLayeredBoundaryPlacement boundary : set.getBoundaries()) {
					requireWireId("boundary", boundary.getBoundaryId(), 0xfeff, true);
					DoorDef definition = definitions.boundary(boundary.getBoundaryId());
					if (definition == null) unavailable("boundary", boundary.getBoundaryId());
					requireTerrain(loaded, boundary.getLocation(), boundary.getPlacementId());
					verifyCollision(loaded, boundary.getLocation(), boundary.getPlacementId(), boundary.getBoundaryId(),
						boundary.getDirection(), 1, Definition.boundary(definition.getDoorType(),
							definition.getName(), Constants.objectsProjectileClipAllowed));
				}
			}
		}
	}

	/** All authored placement collision, including objects in another placement set. */
	private static boolean shortcutCollisionClear(NativeLayeredWorldPackageCatalog catalog, Definitions definitions, WorldLocation at) {
		for (NativeLayeredWorldPackage owner : catalog.getPackages()) for (NativeLayeredPlacementSet set : owner.getPlacementSets().values()) {
			if (!set.getWorldSpace().equals(at.getWorldSpace()) || set.getLevel() != at.getCoordinate().getLevel()) continue;
			for (NativeLayeredSceneryPlacement object : set.getScenery()) {
				GameObjectDef def = definitions.scenery(object.getSceneryId());
				if (def == null) return false;
				if (def.getType() == 0) continue;
				int width = object.getDirection() == 0 || object.getDirection() == 4 ? def.getWidth() : def.getHeight();
				int height = object.getDirection() == 0 || object.getDirection() == 4 ? def.getHeight() : def.getWidth();
				long dx = (long)at.getCoordinate().getX() - object.getLocation().getCoordinate().getX();
				long dy = (long)at.getCoordinate().getY() - object.getLocation().getCoordinate().getY();
				if (dx >= 0 && dy >= 0 && dx < width && dy < height) return false;
			}
			for (NativeLayeredBoundaryPlacement boundary : set.getBoundaries()) {
				if (Math.abs((long)boundary.getLocation().getCoordinate().getX() - at.getCoordinate().getX()) <= 1
					&& Math.abs((long)boundary.getLocation().getCoordinate().getY() - at.getCoordinate().getY()) <= 1) return false;
			}
		}
		return true;
	}

	private static void verifyCollision(NativeLayeredWorldPackage loaded, WorldLocation origin,
		String placementId, int id, int direction, int type, Definition definition) {
		GameTickEventRestorationCollisionFootprintPlanner.Result footprint =
			GameTickEventRestorationCollisionFootprintPlanner.plan(Operation.REGISTER,
				ConstructorState.of(id, origin.getCoordinate().getX(), origin.getCoordinate().getY(), direction, type),
				definition, false, WorldBounds.of(Constants.MAX_WIDTH, Constants.MAX_HEIGHT));
		if (!footprint.isFootprintAvailable()) throw new IllegalStateException(
			"Native layered object collision footprint is unavailable for " + placementId + ": " + footprint.getReason());
		for (GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution contribution : footprint.getContributions()) {
			requireTerrain(loaded, at(origin, contribution.getX(), contribution.getY()), placementId);
		}
	}
	private static WorldLocation at(WorldLocation origin, int x, int y) {
		return new WorldLocation(origin.getWorldSpace(), new WorldCoordinate(x, y, origin.getCoordinate().getLevel()));
	}
	private static void requireTerrain(NativeLayeredWorldPackage loaded, WorldLocation location, String id) {
		LayeredCompatibilityPointAdapter.toCompatibilityPoint(location, false, true);
		if (!loaded.findTile(location).isPresent()) throw new IllegalStateException(
			"Native layered placement/footprint leaves its package terrain: " + id + " at " + location);
	}
	private static void unavailable(String family, int id) {
		throw new IllegalStateException("Native layered " + family + " definition is unavailable: " + id);
	}
	private static void requireWireId(String family, int id, int maximum, boolean objectRemovalSentinel) {
		if (id < 0 || id > maximum || (objectRemovalSentinel && id == 60000)) {
			throw new IllegalStateException("Native layered " + family + " definition exceeds supported wire identity or uses a control sentinel: " + id);
		}
	}
}
