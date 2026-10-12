package com.openrsc.server.io;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.external.DoorDef;
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
	}
	private NativeLayeredWorldPlacementPreflight() {}

	public static void validate(NativeLayeredWorldPackageCatalog catalog, final EntityHandler handler,
		final int restrictedItemId) {
		validate(catalog, new Definitions() {
			public boolean npc(int id) { return handler.getNpcDef(id) != null; }
			public boolean item(int id) { return handler.getItemDef(id) != null
				&& (restrictedItemId <= -1 || id <= restrictedItemId); }
			public GameObjectDef scenery(int id) { return handler.getGameObjectDef(id); }
			public DoorDef boundary(int id) { return handler.getDoorDef(id); }
		});
	}

	public static void validate(NativeLayeredWorldPackageCatalog catalog, Definitions definitions) {
		for (NativeLayeredWorldPackage loaded : catalog.getPackages()) {
			for (NativeLayeredPlacementSet set : loaded.getPlacementSets().values()) {
				for (NativeLayeredNpcPlacement npc : set.getNpcs()) {
					if (!definitions.npc(npc.getNpcId())) unavailable("NPC", npc.getNpcId());
					requireTerrain(loaded, npc.getStart(), npc.getPlacementId());
					// Loader already checked every roam sector; also check the
					// actual compatibility carrier bounds before any constructor.
					requireTerrain(loaded, at(npc.getStart(), npc.getMinX(), npc.getMinY()), npc.getPlacementId());
					requireTerrain(loaded, at(npc.getStart(), npc.getMaxX(), npc.getMaxY()), npc.getPlacementId());
				}
				for (NativeLayeredGroundItemPlacement item : set.getGroundItems()) {
					if (!definitions.item(item.getItemId())) unavailable("item", item.getItemId());
					requireTerrain(loaded, item.getLocation(), item.getPlacementId());
				}
				for (NativeLayeredSceneryPlacement scenery : set.getScenery()) {
					GameObjectDef definition = definitions.scenery(scenery.getSceneryId());
					if (definition == null) unavailable("scenery", scenery.getSceneryId());
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
							requireTerrain(loaded, at(origin, Math.addExact(origin.getCoordinate().getX(), x),
								Math.addExact(origin.getCoordinate().getY(), y)), scenery.getPlacementId());
						}
					}
				}
				for (NativeLayeredBoundaryPlacement boundary : set.getBoundaries()) {
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
}
