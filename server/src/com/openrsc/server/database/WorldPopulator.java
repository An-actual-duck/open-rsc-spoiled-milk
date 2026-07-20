package com.openrsc.server.database;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementManifest;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementDependencyInventory;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPlacementDependencyInventory.DependencyKind;
import com.openrsc.server.model.world.coordinate.LayeredPackedRegionAuthoredPopulationOutcome;
import com.openrsc.server.util.SystemUtil;
import com.openrsc.server.util.WorldNpcEditFiles;
import com.openrsc.server.util.WorldSceneryEditFiles;
import com.openrsc.server.util.rsc.Formulae;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;

import static org.apache.logging.log4j.util.Unbox.box;

public final class WorldPopulator {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private final World world;

	private final ArrayList<GameObjectLoc> gameobjlocs = new ArrayList<>();

	private final ArrayList<NPCLoc> npclocs = new ArrayList<>();

	private final ArrayList<ItemLoc> itemlocs = new ArrayList<>();

	private volatile LayeredPackedRegionAuthoredConstructionInventory
		authoredConstructionInventory =
			LayeredPackedRegionAuthoredConstructionInventory.empty();
	private volatile LayeredPackedRegionAuthoredPlacementManifest
		authoredPlacementManifest =
			LayeredPackedRegionAuthoredPlacementManifest.empty();
	private volatile LayeredPackedRegionAuthoredPlacementDependencyInventory
		authoredPlacementDependencies =
			LayeredPackedRegionAuthoredPlacementDependencyInventory.empty();
	private volatile LayeredPackedRegionAuthoredPopulationOutcome
		authoredPopulationOutcome =
			LayeredPackedRegionAuthoredPopulationOutcome.empty();

	public WorldPopulator(final World world) {
		this.world = world;
	}

	@SuppressWarnings("unchecked")
	public void populateWorld() {
		gameobjlocs.clear();
		npclocs.clear();
		itemlocs.clear();
		long constructionGeneration = Math.incrementExact(
			authoredConstructionInventory.getGeneration());
		LayeredPackedRegionAuthoredConstructionInventory.Builder
			constructionInventory =
				LayeredPackedRegionAuthoredConstructionInventory.builder(
					constructionGeneration);
		LayeredPackedRegionAuthoredPlacementManifest.Builder
			placementManifest =
				LayeredPackedRegionAuthoredPlacementManifest.builder(
					constructionGeneration);
		LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			placementDependencies =
				LayeredPackedRegionAuthoredPlacementDependencyInventory.builder(
					constructionGeneration);
		LayeredPackedRegionAuthoredPopulationOutcome.Builder populationOutcome =
			LayeredPackedRegionAuthoredPopulationOutcome.builder(
				constructionGeneration);
		try {
			// LOAD OBJECTS //
			int countOBJ = 0;
			String authenticSceneryFile, authenticBoundaryFile, authenticGroundItemsFile, authenticMobFile;
			if (getWorld().getServer().getConfig().BASED_MAP_DATA == 14) {
				authenticSceneryFile = "/defs/locs/SceneryLocs14.json";
				authenticBoundaryFile = "/defs/locs/BoundaryLocs14.json";
				authenticGroundItemsFile = "/defs/locs/GroundItems14.json";
				authenticMobFile = "/defs/locs/NpcLocs14.json";
			} else if (getWorld().getServer().getConfig().BASED_MAP_DATA == 27) {
				authenticSceneryFile = "/defs/locs/SceneryLocs27.json";
				authenticBoundaryFile = "/defs/locs/BoundaryLocs27.json";
				authenticGroundItemsFile = "/defs/locs/GroundItems27.json";
				authenticMobFile = "/defs/locs/NpcLocs27.json";
			} else {
				authenticSceneryFile = "/defs/locs/SceneryLocs.json";
				authenticBoundaryFile = "/defs/locs/BoundaryLocs.json";
				authenticGroundItemsFile = "/defs/locs/GroundItems.json";
				authenticMobFile = "/defs/locs/NpcLocs.json";
			}
			loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + authenticBoundaryFile, LocType.Boundary);
			loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + authenticSceneryFile, LocType.Scenery);
			loadCustomLocs(LocType.Boundary);
			loadCustomLocs(LocType.Scenery);
			applyMyWorldSceneryRemovals();
			loadMyWorldSceneryLocs();
			// SceneryObject objects[] = getWorld().getServer().getDatabase().getObjects();
			// for (SceneryObject object : objects) {
			for (GameObjectLoc loc : gameobjlocs) {
				GameObjectLoc object = loc;

				// Point point = new Point(object.x, object.y);
				if (Formulae.isP2P(false, object.getLocation().getX(), object.getLocation().getY())
					&& !getWorld().getServer().getConfig().MEMBER_WORLD
					&& !getWorld().getServer().getEntityHandler().getGameObjectDef(object.getId()).description.contains("members server")) {
					continue;
				}
				GameObject obj = new GameObject(getWorld(), object.location, object.id,
					object.direction, object.type);

				LayeredAuthoredPlacementIdentity supersededIdentity =
					collidingAuthoredObjectIdentity(obj);
				getWorld().registerGameObject(obj);
				recordConstruction(
					constructionInventory,
					obj.getType() == 0 ? ConstructionKind.SCENERY
						: ConstructionKind.BOUNDARY,
					obj.getX(), obj.getY());
				recordObjectPlacement(placementManifest, obj);
				LayeredAuthoredPlacementIdentity objectIdentity =
					placementManifest.getLastRecordedIdentity();
				object.assignAuthoredPlacementIdentity(objectIdentity);
				assignObjectIdentity(obj, objectIdentity);
				if (supersededIdentity != null) {
					populationOutcome.recordSupersession(
						supersededIdentity, objectIdentity);
				}
				recordObjectDependency(placementDependencies, obj);
				if (obj.getType() == 0) { // no wall objects allowed
					getWorld().addSceneryLoc(obj.getLocation(), obj.getID());
				}
				countOBJ++;
			}
			LOGGER.info("Loaded {}", box(countOBJ) + " Objects.");

			// LOAD NPC LOCS //
			loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + authenticMobFile);
			loadCustomLocs(LocType.NPC);
			applyMyWorldNpcLocationCleanup();
			applyMyWorldNpcRemovals();
			// NpcLocation[] npcLocations = getWorld().getServer().getDatabase().getNpcLocs();
			// for (NpcLocation npcLocation : npcLocations) {
			for (NPCLoc loc : npclocs) {
				NPCLoc n = loc;

				// NPCLoc n = new NPCLoc(npcID,
				//	npcLocation.startX, npcLocation.startY,
				//	npcLocation.minX, npcLocation.maxX,
				//	npcLocation.minY, npcLocation.maxY);

				// if (!getWorld().getServer().getConfig().MEMBER_WORLD) {
				// 	if (getWorld().getServer().getEntityHandler().getNpcDef(n.id).isMembers()) {
				// 		continue;
				// 	}
				// }
				if (Formulae.isP2P(false, n.startX(), n.startY())
					&& !getWorld().getServer().getConfig().MEMBER_WORLD) {
					n = null;
					continue;
				}

				// Don't spawn attackable members NPCs in F2P
				if (getWorld().getServer().getEntityHandler().getNpcDef(n.id).isMembers() &&
					getWorld().getServer().getEntityHandler().getNpcDef(n.id).isAttackable() &&
					!getWorld().getServer().getConfig().MEMBER_WORLD) {
					n = null;
					continue;
				}

				// // if(!Point.inWilderness(n.startX, n.startY) && EntityHandler.getNpcDef(n.id).isAttackable() && n.id != 192 && n.id != 35 && n.id != 196 && n.id != 50 && n.id != 70 && n.id != 136 && n.id != 37) {
				// //	for(int i = 0; i < 1; i++)
				// //		world.registerNpc(new Npc(n));
				// // }

				// Don't load rats if the Mice to Meet You Event is active
				if (getWorld().getServer().getConfig().MICE_TO_MEET_YOU_EVENT && getWorld().getServer().getConfig().WANT_MICE_TO_MEET_YOU_NO_RATS
					&& (n.getId() == NpcId.RAT_LVL8.id()
					|| n.getId() == NpcId.RAT_WITCHES_POTION.id()
					|| n.getId() == NpcId.RAT_LVL13.id()
					|| n.getId() == NpcId.RAT_WMAZEKEY.id())) {
					n = null;
					continue;
				}

				Npc npc = new Npc(getWorld(), n);
				getWorld().registerNpc(npc);
				recordConstruction(
					constructionInventory, ConstructionKind.NPC_SPAWN,
					n.startX(), n.startY());
				recordNpcPlacement(placementManifest, n);
				LayeredAuthoredPlacementIdentity npcIdentity =
					placementManifest.getLastRecordedIdentity();
				n.assignAuthoredPlacementIdentity(npcIdentity);
				npc.assignAuthoredPlacementIdentity(npcIdentity);
				recordNpcDependency(placementDependencies, n);
			}
			LOGGER.info("Loaded {}", box(getWorld().countNpcs()) + " NPC spawns");

			// LOAD GROUND ITEMS //
			int countGI = 0;
			loadItemLocs(getWorld().getServer().getConfig().CONFIG_DIR + authenticGroundItemsFile);
			loadCustomLocs(LocType.GroundItem);
			// FloorItem[] groundItems = getWorld().getServer().getDatabase().getGroundItems();
			// for (FloorItem groundItem : groundItems) {
			for (ItemLoc loc : itemlocs) {
				ItemLoc i = loc;

				// ItemLoc i = new ItemLoc(groundItem.id,
				//	groundItem.x, groundItem.y,
				//	groundItem.amount, groundItem.respawn);

				if (!getWorld().getServer().getConfig().MEMBER_WORLD) {
					if (getWorld().getServer().getEntityHandler().getItemDef(i.id).isMembersOnly()) {
						continue;
					}
				}
				if (Formulae.isP2P(false, i)
					&& !getWorld().getServer().getConfig().MEMBER_WORLD) {
					i = null;
					continue;
				}

				int harvestingSceneryId = harvestingSceneryForGroundItem(i.id);
				if (getWorld().getServer().getConfig().WANT_HARVESTING && harvestingSceneryId >= 0) {
					Point location = Point.location(i.x, i.y);
					GameObject harvestingScenery = new GameObject(
						getWorld(), location, harvestingSceneryId, 0, 0);
					LayeredAuthoredPlacementIdentity supersededIdentity =
						collidingAuthoredObjectIdentity(harvestingScenery);
					getWorld().registerGameObject(harvestingScenery);
					recordConstruction(
						constructionInventory,
						ConstructionKind.HARVESTING_SCENERY,
						i.x, i.y);
					recordHarvestingPlacement(
						placementManifest, i, harvestingScenery);
					LayeredAuthoredPlacementIdentity harvestingIdentity =
						placementManifest.getLastRecordedIdentity();
					i.assignAuthoredPlacementIdentity(harvestingIdentity);
					assignObjectIdentity(
						harvestingScenery, harvestingIdentity);
					if (supersededIdentity != null) {
						populationOutcome.recordSupersession(
							supersededIdentity, harvestingIdentity);
					}
					recordObjectDependency(
						placementDependencies, harvestingScenery,
						ConstructionKind.HARVESTING_SCENERY);
					getWorld().addSceneryLoc(location, harvestingSceneryId);
					continue;
				}

				GroundItem authoredItem =
					getWorld().registerAuthoredGroundItem(i);
				if (authoredItem != null) {
					recordConstruction(
						constructionInventory,
						ConstructionKind.GROUND_ITEM_SPAWN,
						i.x, i.y);
					recordGroundItemPlacement(placementManifest, i);
					LayeredAuthoredPlacementIdentity itemIdentity =
						placementManifest.getLastRecordedIdentity();
					i.assignAuthoredPlacementIdentity(itemIdentity);
					authoredItem.assignAuthoredPlacementIdentity(itemIdentity);
					recordGroundItemDependency(placementDependencies, i);
				}
				countGI++;
			}
			LOGGER.info("Loaded {}", box(countGI) + " grounditems.");

			//Load the in-use ItemID's from the database
			Long inUseItemIds[] = getWorld().getServer().getDatabase().getInUseItemIds();
			for (Long itemId : inUseItemIds)
				getWorld().getServer().getDatabase().getItemIDList().add(itemId);

			LOGGER.info("Loaded {}", box(getWorld().getServer().getDatabase().getItemIDList().size()) + " itemIDs.");
			LayeredPackedRegionAuthoredConstructionInventory completedInventory =
				constructionInventory.build();
			LayeredPackedRegionAuthoredPlacementManifest completedManifest =
				placementManifest.build();
			LayeredPackedRegionAuthoredPlacementDependencyInventory
				completedDependencies = placementDependencies.build();
			LayeredPackedRegionAuthoredPopulationOutcome completedOutcome =
				populationOutcome.build(completedManifest);
			if (!completedManifest.isCountEquivalentTo(completedInventory)) {
				throw new IllegalStateException(
					"Authored placement manifest does not match construction inventory");
			}
			if (!completedDependencies.isAlignedWith(completedManifest)) {
				throw new IllegalStateException(
					"Authored placement dependencies do not align with manifest");
			}
			LOGGER.info(
				"Recorded {} authored population supersessions; {} of {} manifest "
					+ "placements remain final-live expectations.",
				box(completedOutcome.getSupersessionCount()),
				box(completedOutcome.getFinalExpectedPlacementCount()),
				box(completedOutcome.getManifestPlacementCount()));
			LOGGER.info(
				"Indexed {} authored placement dependency envelopes; "
					+ "{} cross packed-source boundaries ({} source references, "
					+ "maximum {} per placement). Object footprints: {} total, "
					+ "{} cross-source, {} references, maximum {}. NPC roaming: "
					+ "{} total, {} cross-source, {} references, maximum {}. "
					+ "Anchor-only: {} total, {} references.",
				box(completedDependencies.getDependencyCount()),
				box(completedDependencies.getCrossSourceDependencyCount()),
				box(completedDependencies.getAffectedSourceReferenceCount()),
				box(completedDependencies.getMaximumAffectedSourceCount()),
				box(completedDependencies.getObjectFootprintDependencyCount()),
				box(completedDependencies.getCrossSourceObjectFootprintCount()),
				box(completedDependencies.getObjectFootprintSourceReferenceCount()),
				box(completedDependencies.getMaximumObjectFootprintSourceCount()),
				box(completedDependencies.getNpcRoamingDependencyCount()),
				box(completedDependencies.getCrossSourceNpcRoamingCount()),
				box(completedDependencies.getNpcRoamingSourceReferenceCount()),
				box(completedDependencies.getMaximumNpcRoamingSourceCount()),
				box(completedDependencies.getAnchorOnlyDependencyCount()),
				box(completedDependencies.getAnchorOnlySourceReferenceCount()));
			authoredPlacementDependencies = completedDependencies;
			authoredPopulationOutcome = completedOutcome;
			authoredPlacementManifest = completedManifest;
			authoredConstructionInventory = completedInventory;

		} catch (Exception e) {
			LOGGER.catching(e);
			SystemUtil.exit(1);
		}
	}

	private void recordConstruction(
		final LayeredPackedRegionAuthoredConstructionInventory.Builder inventory,
		final ConstructionKind kind,
		final int packedX,
		final int packedY) {
		inventory.record(
			kind,
			packedX / Constants.REGION_SIZE,
			packedY / Constants.REGION_SIZE);
	}

	private void recordObjectPlacement(
		final LayeredPackedRegionAuthoredPlacementManifest.Builder manifest,
		final GameObject object) {
		if (object.getType() == 0) {
			manifest.recordScenery(
				object.getX() / Constants.REGION_SIZE,
				object.getY() / Constants.REGION_SIZE,
				object.getID(), object.getLoc().getPermId(),
				object.getX(), object.getY(), object.getDirection(),
				object.getType(), object.getOwner());
		} else {
			manifest.recordBoundary(
				object.getX() / Constants.REGION_SIZE,
				object.getY() / Constants.REGION_SIZE,
				object.getID(), object.getLoc().getPermId(),
				object.getX(), object.getY(), object.getDirection(),
				object.getType(), object.getOwner());
		}
	}

	private void recordNpcPlacement(
		final LayeredPackedRegionAuthoredPlacementManifest.Builder manifest,
		final NPCLoc loc) {
		manifest.recordNpcSpawn(
			loc.startX() / Constants.REGION_SIZE,
			loc.startY() / Constants.REGION_SIZE,
			loc.getId(), loc.startX(), loc.startY(),
			loc.minX(), loc.maxX(), loc.minY(), loc.maxY());
	}

	private void recordGroundItemPlacement(
		final LayeredPackedRegionAuthoredPlacementManifest.Builder manifest,
		final ItemLoc loc) {
		manifest.recordGroundItemSpawn(
			loc.getX() / Constants.REGION_SIZE,
			loc.getY() / Constants.REGION_SIZE,
			loc.getId(), loc.getX(), loc.getY(), loc.getAmount(),
			loc.getRespawnTime(), loc.getNoted());
	}

	private void recordHarvestingPlacement(
		final LayeredPackedRegionAuthoredPlacementManifest.Builder manifest,
		final ItemLoc source,
		final GameObject constructed) {
		manifest.recordHarvestingScenery(
			constructed.getX() / Constants.REGION_SIZE,
			constructed.getY() / Constants.REGION_SIZE,
			source.getId(), constructed.getID(),
			constructed.getLoc().getPermId(),
			constructed.getX(), constructed.getY(),
			constructed.getDirection(), constructed.getType(),
			constructed.getOwner(), source.getAmount(),
			source.getRespawnTime(), source.getNoted());
	}

	private void recordObjectDependency(
		final LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			dependencies,
		final GameObject object) {
		recordObjectDependency(
			dependencies, object,
			object.getType() == 0 ? ConstructionKind.SCENERY
				: ConstructionKind.BOUNDARY);
	}

	private void assignObjectIdentity(
		final GameObject object,
		final LayeredAuthoredPlacementIdentity identity) {
		object.getLoc().assignAuthoredPlacementIdentity(identity);
		object.assignAuthoredPlacementIdentity(identity);
	}

	private LayeredAuthoredPlacementIdentity collidingAuthoredObjectIdentity(
		final GameObject object) {
		Point location = Point.location(
			object.getLoc().getX(), object.getLoc().getY());
		GameObject colliding = object.getType() == 0
			? getWorld().getRegionManager().getRegion(location)
				.getGameObject(location, null)
			: getWorld().getRegionManager().getRegion(location)
				.getWallGameObject(location, object.getLoc().getDirection());
		return colliding == null
			? null : colliding.getAuthoredPlacementIdentity();
	}

	private void recordObjectDependency(
		final LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			dependencies,
		final GameObject object,
		final ConstructionKind kind) {
		Point[] footprint = object.getObjectBoundary();
		int minimumX = Math.min(
			object.getX(), Math.min(
				footprint[0].getX(), footprint[1].getX()));
		int maximumX = Math.max(
			object.getX(), Math.max(
				footprint[0].getX(), footprint[1].getX()));
		int minimumY = Math.min(
			object.getY(), Math.min(
				footprint[0].getY(), footprint[1].getY()));
		int maximumY = Math.max(
			object.getY(), Math.max(
				footprint[0].getY(), footprint[1].getY()));
		recordPlacementDependency(
			dependencies, kind, DependencyKind.OBJECT_FOOTPRINT,
			object.getX(), object.getY(),
			minimumX, maximumX, minimumY, maximumY);
	}

	private void recordNpcDependency(
		final LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			dependencies,
		final NPCLoc loc) {
		int minimumX = Math.min(
			loc.startX(), Math.min(loc.minX(), loc.maxX()));
		int maximumX = Math.max(
			loc.startX(), Math.max(loc.minX(), loc.maxX()));
		int minimumY = Math.min(
			loc.startY(), Math.min(loc.minY(), loc.maxY()));
		int maximumY = Math.max(
			loc.startY(), Math.max(loc.minY(), loc.maxY()));
		recordPlacementDependency(
			dependencies, ConstructionKind.NPC_SPAWN,
			DependencyKind.NPC_ROAMING, loc.startX(), loc.startY(),
			minimumX, maximumX, minimumY, maximumY);
	}

	private void recordGroundItemDependency(
		final LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			dependencies,
		final ItemLoc loc) {
		recordPlacementDependency(
			dependencies, ConstructionKind.GROUND_ITEM_SPAWN,
			DependencyKind.ANCHOR_ONLY, loc.getX(), loc.getY(),
			loc.getX(), loc.getX(), loc.getY(), loc.getY());
	}

	private void recordPlacementDependency(
		final LayeredPackedRegionAuthoredPlacementDependencyInventory.Builder
			dependencies,
		final ConstructionKind kind,
		final DependencyKind dependencyKind,
		final int sourcePackedX,
		final int sourcePackedY,
		final int minimumPackedX,
		final int maximumPackedX,
		final int minimumPackedY,
		final int maximumPackedY) {
		dependencies.record(
			kind, dependencyKind,
			sourcePackedX / Constants.REGION_SIZE,
			sourcePackedY / Constants.REGION_SIZE,
			minimumPackedX, maximumPackedX,
			minimumPackedY, maximumPackedY,
			minimumPackedX / Constants.REGION_SIZE,
			maximumPackedX / Constants.REGION_SIZE,
			minimumPackedY / Constants.REGION_SIZE,
			maximumPackedY / Constants.REGION_SIZE);
	}

	public LayeredPackedRegionAuthoredConstructionInventory
		getAuthoredConstructionInventory() {
		return authoredConstructionInventory;
	}

	public LayeredPackedRegionAuthoredPlacementManifest
		getAuthoredPlacementManifest() {
		return authoredPlacementManifest;
	}

	public LayeredPackedRegionAuthoredPlacementDependencyInventory
		getAuthoredPlacementDependencies() {
		return authoredPlacementDependencies;
	}

	public LayeredPackedRegionAuthoredPopulationOutcome
		getAuthoredPopulationOutcome() {
		return authoredPopulationOutcome;
	}

	private int harvestingSceneryForGroundItem(int itemId) {
		if (itemId == ItemId.CABBAGE.id()) return SceneryId.CABBAGE.id();
		if (itemId == ItemId.CADAVABERRIES.id()) return SceneryId.CADAVABERRY_BUSH.id();
		if (itemId == ItemId.GRAPES.id()) return SceneryId.MYSTERIOUS_GRAPE_VINE.id();
		if (itemId == ItemId.UNIDENTIFIED_GUAM_LEAF.id()) return SceneryId.HERB.id();
		if (itemId == ItemId.REDBERRIES.id()) return SceneryId.REDBERRY_BUSH.id();
		if (itemId == ItemId.ONION.id()) return SceneryId.ONION_PLANT.id();
		if (itemId == ItemId.TOMATO.id()) return SceneryId.TOMATO_PLANT.id();
		if (itemId == ItemId.PUMPKIN.id()) return SceneryId.REGULAR_PUMPKIN.id();
		if (itemId == ItemId.SNAPE_GRASS.id()) return SceneryId.SNAPE_GRASS.id();
		if (itemId == ItemId.WHITE_BERRIES.id()) return SceneryId.WHITEBERRY_BUSH.id();
		if (itemId == ItemId.SEAWEED.id()) return SceneryId.SEA_WEED.id();
		if (itemId == ItemId.DWELLBERRIES.id()) return SceneryId.DWELLBERRY_BUSH.id();
		if (itemId == ItemId.JANGERBERRIES.id()) return SceneryId.JANGERBERRY_BUSH.id();
		return -1;
	}

	public World getWorld() {
		return world;
	}

	private void loadCustomLocs(LocType type) {
		switch (type) {
			case Boundary: {
				if (getWorld().getServer().getConfig().LOCATION_DATA == 2) {
					if (getWorld().getServer().getConfig().WANT_CUSTOM_QUESTS || getWorld().getServer().getConfig().DEATH_ISLAND) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/BoundaryLocsCustomQuest.json", type);
						//loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/BoundaryLocsExpansion.json", type);
					}
				}
				return;
			}
			case Scenery: {
				if (getWorld().getServer().getConfig().LOCATION_DATA == 4) {
					if (getWorld().getServer().getConfig().WANT_OPENPK_POINTS) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsOpenPk.json", type);
					}
				}
				if ((getWorld().getServer().getConfig().LOCATION_DATA == 1 || getWorld().getServer().getConfig().LOCATION_DATA == 2)
					&& getWorld().getServer().getConfig().WANT_FIXED_BROKEN_MECHANICS) {
					loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsDiscontinued.json", type);
				}
				if (getWorld().getServer().getConfig().LOCATION_DATA == 2) {
					if (getWorld().getServer().getConfig().WANT_DECORATED_MOD_ROOM) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsModRoom.json", type);
					}
					if (getWorld().getServer().getConfig().WANT_RUNECRAFT) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsRunecraft.json", type);
					}
					if (getWorld().getServer().getConfig().WANT_HARVESTING) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsHarvesting.json", type);
					}
					if (getWorld().getServer().getConfig().WANT_CUSTOM_QUESTS) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsCustomQuest.json", type);
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsExpansion.json", type);
					}
					if (getWorld().getServer().getConfig().MICE_TO_MEET_YOU_EVENT) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsMiceToMeetYou.json", type);
					}
					if (getWorld().getServer().getConfig().WANT_WOODCUTTING_GUILD) {
						loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsWoodcuttingGuild.json", type);
					}
					loadGameObjLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/SceneryLocsOther.json", type);
				}
				return;
			}
			case NPC: {
				if ((getWorld().getServer().getConfig().LOCATION_DATA == 1 || getWorld().getServer().getConfig().LOCATION_DATA == 2)
					&& getWorld().getServer().getConfig().WANT_FIXED_BROKEN_MECHANICS) {
					loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsDiscontinued.json");
				}
				if (getWorld().getServer().getConfig().LOCATION_DATA == 4) {
					if (getWorld().getServer().getConfig().WANT_PK_BOTS) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsPkBots.json");
					}
					if (getWorld().getServer().getConfig().WANT_OPENPK_POINTS) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsOpenPk.json");
					}
				}
				if (getWorld().getServer().getConfig().LOCATION_DATA == 2) {
					if (getWorld().getServer().getConfig().WANT_DECORATED_MOD_ROOM) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsModRoom.json");
					}
					if (getWorld().getServer().getConfig().WANT_RUNECRAFT) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsRunecraft.json");
					}
					if (getWorld().getServer().getConfig().SPAWN_AUCTION_NPCS) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsAuction.json");
					}
					if (getWorld().getServer().getConfig().SPAWN_IRON_MAN_NPCS) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsIronman.json");
					}
					if (getWorld().getServer().getConfig().WANT_HARVESTING) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsHarvesting.json");
					}
					if (getWorld().getServer().getConfig().WANT_CUSTOM_QUESTS) {
						loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsCustomQuest.json");
						//loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsExpansion.json");
						// If the Ester's Bunnies event isn't active, move all the bunnies to the top floor of Ester's house.
						if (!getWorld().getServer().getConfig().ESTERS_BUNNIES_EVENT) {
							for (NPCLoc loc : npclocs) {
								if (loc.id == NpcId.BUNNY.id()) {
									loc.startX = 317;
									loc.startY = 1607;
									loc.maxX = 319;
									loc.maxY = 1608;
									loc.minX = 314;
									loc.minY = 1603;
								}
							}
						}

						// Remove the Death in Varrock, keep the one on Death Island
						if (!getWorld().getServer().getConfig().MICE_TO_MEET_YOU_EVENT) {
							npclocs.removeIf(npcLoc -> npcLoc.getId() == NpcId.DEATH.id() && npcLoc.startX < 600);
						}

						// Remove the Christmas party NPCs upstairs in the Rising Sun Inn
						if (!getWorld().getServer().getConfig().A_LUMBRIDGE_CAROL) {
							npclocs.removeIf(npcLoc -> npcLoc.startX == 320
								&& (npcLoc.getId() == NpcId.DUKE_OF_LUMBRIDGE.id()
								|| npcLoc.getId() == NpcId.MUM.id()
								|| npcLoc.getId() == NpcId.TRAMP.id()
								|| npcLoc.getId() == NpcId.SHILOP.id()));
							npclocs.removeIf(npcLoc -> npcLoc.getId() == NpcId.PRAETERITUM.id());
							npclocs.removeIf(npcLoc -> npcLoc.getId() == NpcId.PRAESENS.id());
							npclocs.removeIf(npcLoc -> npcLoc.getId() == NpcId.FUTURUM.id());
						}

						// Remove Ash
						if (!getWorld().getServer().getConfig().ARMY_OF_OBSCURITY) {
							npclocs.removeIf(npcLoc -> npcLoc.getId() == NpcId.ASH.id());
						}
					}
					loadNpcLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/NpcLocsOther.json");
					if (getWorld().getServer().getConfig().WANT_MYWORLD) {
						loadNpcLocs(WorldNpcEditFiles.npcLocsPath(
							getWorld().getServer().getWorldEditStorage().configDirectory()).toString());
					}
				}
				return;
			}
			case GroundItem: {
				if (getWorld().getServer().getConfig().LOCATION_DATA == 2) {
					if (getWorld().getServer().getConfig().WANT_HARVESTING) {
						loadItemLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/GroundItemsHarvesting.json");
					}
					if (getWorld().getServer().getConfig().WANT_CUSTOM_QUESTS) {
						loadItemLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/GroundItemsCustomQuest.json");
						//loadItemLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/GroundItemsExpansion.json");
					}
				}

				// Adds Pumpkins to Varrock & a red key so you can do Dragon Slayer
				if (getWorld().getServer().getConfig().MICE_TO_MEET_YOU_EVENT) {
					loadItemLocs(getWorld().getServer().getConfig().CONFIG_DIR + "/defs/locs/GroundItemsMiceToMeetYou.json");
				}

				return;
			}
		}
	}

	private void loadNpcLocs(String filename) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray locDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			JSONObject locObj, start, min, max;
			for (int i = 0; i < locDefs.length(); i++) {
				NPCLoc loc = new NPCLoc();
				locObj = locDefs.getJSONObject(i);
				loc.id = locObj.getInt("id");
				start = locObj.getJSONObject("start");
				loc.startX = start.getInt("X");
				loc.startY = start.getInt("Y");
				min = locObj.getJSONObject("min");
				loc.minX = min.getInt("X");
				loc.minY = min.getInt("Y");
				max = locObj.getJSONObject("max");
				loc.maxX = max.getInt("X");
				loc.maxY = max.getInt("Y");
				// npcs should initially be only max one per tile
				if (npclocs.stream().anyMatch(x -> x.startX == loc.startX && x.startY == loc.startY)) {
					// sometimes may be desired to replace a base npc,
					// in which case the start X and start Y should match
					// commented out since there are about ~ 22 digsite workmen that need to be corrected
					// to not be all same tile
					// npclocs.removeIf(npc -> npc.startX == loc.startX && npc.startY == loc.startY);
				}
				npclocs.add(loc);
			}
			LOGGER.info("Loaded " + locDefs.length() + " npc locations from " + filename);
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void loadItemLocs(String filename) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray locDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			JSONObject locObj, pos;
			for (int i = 0; i < locDefs.length(); i++) {
				ItemLoc loc = new ItemLoc();
				locObj = locDefs.getJSONObject(i);
				loc.id = locObj.getInt("id");
				pos = locObj.getJSONObject("pos");
				loc.x = pos.getInt("X");
				loc.y = pos.getInt("Y");
				loc.amount = locObj.getInt("amount");
				loc.respawnTime = locObj.getInt("respawn");
				if (itemlocs.stream().anyMatch(it -> it.x == loc.x && it.y == loc.y)) {
					// sometimes may be desired to replace a base grounditem,
					// in which case x and y should match
					itemlocs.removeIf(it -> it.x == loc.x && it.y == loc.y);
				}
				itemlocs.add(loc);
			}
			LOGGER.info("Loaded " + locDefs.length() + " grounditem locations from " + filename);
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void loadGameObjLocs(String filename, LocType type) {
		try {
			JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(filename))));
			JSONArray locDefs = object.getJSONArray(JSONObject.getNames(object)[0]);
			JSONObject locObj, pos;
			for (int i = 0; i < locDefs.length(); i++) {
				GameObjectLoc loc = new GameObjectLoc();
				locObj = locDefs.getJSONObject(i);
				loc.id = locObj.getInt("id");
				pos = locObj.getJSONObject("pos");
				loc.location = new Point(pos.getInt("X"), pos.getInt("Y"));
				loc.direction = locObj.getInt("direction");
				if (type == LocType.Scenery) {
					loc.type = 0;
				} else if (type == LocType.Boundary) {
					loc.type = 1;
				}
				gameobjlocs.add(loc);
			}
			LOGGER.info("Loaded " + locDefs.length() + " scenery locations from " + filename);
		}
		catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void loadOptionalGameObjLocs(String filename, LocType type) {
		if (Files.exists(Paths.get(filename))) {
			loadGameObjLocs(filename, type);
		}
	}

	private void applyMyWorldSceneryRemovals() {
		if (!getWorld().getServer().getConfig().WANT_MYWORLD) {
			return;
		}

		try {
			Set<String> removals = WorldSceneryEditFiles.readSceneryRemovalKeys(
				WorldSceneryEditFiles.sceneryRemovalsPath(getWorld().getServer().getWorldEditStorage().configDirectory())
			);
			if (removals.isEmpty()) {
				return;
			}

			int before = gameobjlocs.size();
			gameobjlocs.removeIf(loc -> loc.type == 0 && loc.location != null
				&& removals.contains(WorldSceneryEditFiles.sceneryKey(loc.getX(), loc.getY())));
			LOGGER.info("Applied " + (before - gameobjlocs.size()) + " MyWorld scenery removals.");
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private void loadMyWorldSceneryLocs() {
		if (!getWorld().getServer().getConfig().WANT_MYWORLD) {
			return;
		}
		loadOptionalGameObjLocs(
			WorldSceneryEditFiles.sceneryLocsPath(getWorld().getServer().getWorldEditStorage().configDirectory()).toString(),
			LocType.Scenery
		);
	}

	private void applyMyWorldNpcLocationCleanup() {
		if (!getWorld().getServer().getConfig().WANT_MYWORLD) {
			return;
		}

		ArrayList<NPCLoc> filteredLocs = new ArrayList<>();
		ArrayList<int[]> bankerClusters = new ArrayList<>();
		for (NPCLoc loc : npclocs) {
			if (isTutorialIslandNpcLoc(loc)) {
				continue;
			}
			if (isBankerNpc(loc.getId()) && !keepBankerNpcLoc(loc, bankerClusters)) {
				continue;
			}
			filteredLocs.add(loc);
		}
		npclocs.clear();
		npclocs.addAll(filteredLocs);
	}

	private void applyMyWorldNpcRemovals() {
		if (!getWorld().getServer().getConfig().WANT_MYWORLD) {
			return;
		}
		try {
			Set<String> removals = WorldNpcEditFiles.readNpcRemovalKeys(
				WorldNpcEditFiles.npcRemovalsPath(getWorld().getServer().getWorldEditStorage().configDirectory())
			);
			if (removals.isEmpty()) {
				return;
			}
			int before = npclocs.size();
			npclocs.removeIf(loc -> removals.contains(WorldNpcEditFiles.npcKey(loc)));
			LOGGER.info("Applied {} MyWorld NPC removals.", before - npclocs.size());
		} catch (Exception e) {
			LOGGER.error(e);
		}
	}

	private boolean keepBankerNpcLoc(NPCLoc loc, ArrayList<int[]> bankerClusters) {
		for (int[] cluster : bankerClusters) {
			int centerX = cluster[0] / cluster[2];
			int centerY = cluster[1] / cluster[2];
			if (Math.abs(loc.startX - centerX) <= 8 && Math.abs(loc.startY - centerY) <= 8) {
				cluster[0] += loc.startX;
				cluster[1] += loc.startY;
				cluster[2]++;
				if (cluster[3] >= 2) {
					return false;
				}
				cluster[3]++;
				return true;
			}
		}
		bankerClusters.add(new int[]{loc.startX, loc.startY, 1, 1});
		return true;
	}

	private boolean isBankerNpc(int npcId) {
		return npcId == NpcId.BANKER.id()
			|| npcId == NpcId.FAIRY_BANKER.id()
			|| npcId == NpcId.BANKER_ALKHARID.id()
			|| npcId == NpcId.GNOME_BANKER.id()
			|| npcId == NpcId.JUNGLE_BANKER.id();
	}

	private boolean isTutorialIslandNpcLoc(NPCLoc loc) {
		return loc.startX >= 190 && loc.startX <= 245
			&& loc.startY >= 710 && loc.startY <= 760;
	}

	enum LocType {
		Boundary, GroundItem, NPC, Scenery
	}
}
