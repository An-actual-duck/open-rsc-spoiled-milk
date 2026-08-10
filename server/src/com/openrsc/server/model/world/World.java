package com.openrsc.server.model.world;

import com.openrsc.server.Server;
import com.openrsc.server.ServerConfiguration;
import com.openrsc.server.avatargenerator.AvatarGenerator;
import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcDrops;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.content.clan.ClanManager;
import com.openrsc.server.content.market.Market;
import com.openrsc.server.content.minigame.combatodyssey.CombatOdysseyData;
import com.openrsc.server.content.minigame.monsterslayer.CombatOdysseyMigration;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData;
import com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerTaskService;
import com.openrsc.server.content.minigame.fishingtrawler.FishingTrawler;
import com.openrsc.server.content.minigame.fishingtrawler.FishingTrawler.TrawlerBoat;
import com.openrsc.server.content.party.PartyManager;
import com.openrsc.server.content.Summoning;
import com.openrsc.server.database.impl.mysql.queries.logging.PMLog;
import com.openrsc.server.database.impl.mysql.queries.player.login.PlayerOnlineFlagQuery;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc.GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.io.NativeLayeredGroundItemPlacement;
import com.openrsc.server.io.WorldLoader;
import com.openrsc.server.model.GlobalMessage;
import com.openrsc.server.model.HostileProjectileCollision;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.UnregisterForcefulness;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Group;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerSettings;
import com.openrsc.server.model.snapshot.Snapshot;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.net.ConnectionAttachment;
import com.openrsc.server.net.PcapLogger;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.MiniGameInterface;
import com.openrsc.server.plugins.QuestInterface;
import com.openrsc.server.util.EntityList;
import com.openrsc.server.util.IPTracker;
import com.openrsc.server.util.PathfindingDebug;
import com.openrsc.server.util.PlayerList;
import com.openrsc.server.util.SimpleSubscriber;
import com.openrsc.server.util.ThreadSafeIPTracker;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.MessageType;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class World implements SimpleSubscriber<FishingTrawler>, Runnable {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	/**
	 * Avatar generator upon logout save to PNG.
	 */
	private final AvatarGenerator avatarGenerator;

	/**
	 * IP filtering for wilderness entry
	 */
	private final IPTracker<String> wildernessIPTracker;

	private boolean telegrabEnabled = true;

	public boolean EVENT = false;
	public int EVENT_X = -1, EVENT_Y = -1;
	public int EVENT_COMBAT_MIN = -1, EVENT_COMBAT_MAX = -1;
	public int membersWildStart = 48;
	public int membersWildMax = 56;
	public int godSpellsStart = 1;
	public int godSpellsMax = 5;
	public int eventChestRadius = 4;
	public GameObject eventChest = null;

	private final Server server;
	private final RegionManager regionManager;
	private final EntityList<Npc> npcs;
	private final PlayerList players;

	//Maximum bank items allowed
	private final int maxBankSize;
	private final List<QuestInterface> quests;
	private final List<MiniGameInterface> minigames;
	private final List<Shop> shops;
	private final PartyManager partyManager;
	private final ClanManager clanManager;
	private final Market market;
	private final WorldLoader worldLoader;
	private final CombatOdysseyData combatOdysseyData;
	private MonsterSlayerData monsterSlayerData;
	private MonsterSlayerTaskService monsterSlayerTaskService;
	private CombatOdysseyMigration.LegacyData monsterSlayerLegacyData;
	private final HashMap<Point, Integer> sceneryLocs;
	private final ConcurrentMap<TrawlerBoat, FishingTrawler> fishingTrawler;
	private final AuthoredGroundItemRegistry<GroundItem> authoredGroundItems;
	private final AuthoredLayeredGroundItemRegistry<GroundItem>
		nativeLayeredGroundItems;

	private final ConcurrentMap<Player, Boolean> playerUnderAttackMap;
	private final ConcurrentMap<Npc, Boolean> npcUnderAttackMap;
	private final Queue<GlobalMessage> globalMessageQueue = new LinkedList<>();
	private PathfindingDebug pathfindingDebug = null;
	public NpcDrops npcDrops;
	private final Deque<Snapshot> snapshots;

	public static final AttributeKey<ConnectionAttachment> attachment = AttributeKey.valueOf("conn-attachment");

	public World(final Server server) {
		this.server = server;
		this.npcs = new EntityList<>(4000);
		this.players = new PlayerList(2000);
		this.sceneryLocs = new HashMap<>();
		this.npcDrops = new NpcDrops(this);
		this.quests = new CopyOnWriteArrayList<>();
		this.minigames = new CopyOnWriteArrayList<>();
		this.shops = new CopyOnWriteArrayList<>();
		this.wildernessIPTracker = new ThreadSafeIPTracker<>();
		this.playerUnderAttackMap = new ConcurrentHashMap<>();
		this.npcUnderAttackMap = new ConcurrentHashMap<>();
		this.fishingTrawler = new ConcurrentHashMap<>();
		this.authoredGroundItems = new AuthoredGroundItemRegistry<>();
		this.nativeLayeredGroundItems =
			new AuthoredLayeredGroundItemRegistry<GroundItem>();
		this.snapshots = new LinkedList<>();
		this.worldLoader = new WorldLoader(this);
		this.regionManager = new RegionManager(this);
		this.clanManager = new ClanManager(this);
		this.partyManager = new PartyManager(this);
		this.combatOdysseyData = new CombatOdysseyData(this);

		final ServerConfiguration config = server.getConfig();
		this.avatarGenerator = config.AVATAR_GENERATOR ? new AvatarGenerator(this) : null;
		this.market = config.SPAWN_AUCTION_NPCS ? new Market(this) : null;
		this.maxBankSize = config.MEMBER_WORLD ? (config.WANT_CUSTOM_BANKS ? ItemId.maxCustom : 192) : 48;

		// Turn off god spells in open pk worlds
		if(config.WANT_OPENPK_POINTS) {
			godSpellsStart = 60;
			godSpellsMax = 60;
		}
	}

	/**
	 * Returns double-ended queue for snapshots.
	 */
	public synchronized Deque<Snapshot> getSnapshots() {
		return snapshots;
	}

	/**
	 * Add entry to snapshots
	 */
	public void addEntryToSnapshots(Snapshot snapshot) {
		getSnapshots().offerFirst(snapshot);
	}

	public int countNpcs() {
		return getNpcs().size();
	}

	public int countPlayers() {
		return getPlayers().size();
	}

	public void delayedRemoveObject(final GameObject object, final int delay) {
		getServer().getGameEventHandler().add(new SingleEvent(this, null, delay, "Delayed Remove Object") {
			@Override
			public GameTickEventSpatialAffinity getSpatialAffinity() {
				return GameTickEventSpatialAffinity.exactFixedLocation(
					object.getX(), object.getY());
			}

			@Override
			public GameTickEventRestorationState getRestorationState() {
				return GameTickEventRestorationState.sceneryRemove(
					detachSceneryState(object));
			}

			public void action() {
				unregisterGameObject(object);
			}
		});
	}

	/**
	 * Adds a DelayedEvent that will spawn a GameObject
	 */
	public void delayedSpawnObject(final GameObjectLoc loc, final int respawnTime, final boolean forceFullBlock) {
		getServer().getGameEventHandler().add(new SingleEvent(this, null, respawnTime, "Delayed Spawn Object") {
			@Override
			public GameTickEventSpatialAffinity getSpatialAffinity() {
				return GameTickEventSpatialAffinity.exactFixedLocation(
					loc.getX(), loc.getY());
			}

			@Override
			public GameTickEventRestorationState getRestorationState() {
				return GameTickEventRestorationState.scenerySpawn(
					detachSceneryState(loc), forceFullBlock);
			}

			public void action() {
				registerGameObject(new GameObject(getWorld(), loc), forceFullBlock);
			}
		});
	}

	public void delayedSpawnObject(final GameObjectLoc loc, final int respawnTime) {
		this.delayedSpawnObject(loc, respawnTime, false);
	}

	private static SceneryState detachSceneryState(final GameObject object) {
		return SceneryState.of(
			object.getID(), object.getLoc().getPermId(),
			object.getX(), object.getY(), object.getDirection(), object.getType(),
			object.getOwner(), object.getRuntimeAttributeCount(),
			detachAuthoredPlacement(object.getAuthoredPlacementIdentity()));
	}

	private static SceneryState detachSceneryState(final GameObjectLoc loc) {
		return SceneryState.of(
			loc.getId(), loc.getPermId(), loc.getX(), loc.getY(),
			loc.getDirection(), loc.getType(), loc.getOwner(), 0,
			detachAuthoredPlacement(loc.getAuthoredPlacementIdentity()));
	}

	private static AuthoredPlacementState detachAuthoredPlacement(
		final LayeredAuthoredPlacementIdentity identity) {
		return identity == null ? null : AuthoredPlacementState.of(
			identity.getGeneration(), identity.getPackedRegionX(),
			identity.getPackedRegionY(), identity.getSourceOrdinal(),
			AuthoredConstructionKind.valueOf(
				identity.getConstructionKind().name()));
	}

	public Npc getNpc(final int idx) {
		try {
			return getNpcs().get(idx);
		} catch (final Exception e) {
			return null;
		}
	}

	public Npc getNpc(final int id, final int minX, final int maxX, final int minY, final int maxY) {
		for (final Npc npc : getNpcs()) {
			boolean exists = !npc.isRemoved() && !npc.isRespawning();
			if (npc.getID() == id && npc.getX() >= minX && npc.getX() <= maxX && npc.getY() >= minY
				&& npc.getY() <= maxY && exists) {
				return npc;
			}
		}
		return null;
	}

	public Npc getNpc(final int id, final int minX, final int maxX, final int minY, final int maxY, final boolean notNull) {
		for (final Npc npc : getNpcs()) {
			if (npc.getID() == id && npc.getX() >= minX && npc.getX() <= maxX && npc.getY() >= minY
				&& npc.getY() <= maxY) {
				if (!npc.inCombat()) {
					return npc;
				}
			}
		}
		return null;
	}

	public Npc getNpcById(final int id) {
		for (final Npc npc : getNpcs()) {
			if (npc.getID() == id) {
				return npc;
			}
		}
		return null;
	}

	public Npc getNpcByUUID(final UUID id) {
		for (final Npc npc : getNpcs()) {
			if (npc.getUUID().equals(id)) {
				return npc;
			}
		}
		return null;
	}

	/**
	 * Gets the list of npcs on the server
	 */
	public EntityList<Npc> getNpcs() {
		return npcs;
	}

	/**
	 * Gets a Player by their server index
	 */
	public Player getPlayer(final int idx) {
		return players.get(idx);
	}

	/**
	 * Gets a player by their username hash
	 */
	public Player getPlayer(final long usernameHash) {
		return players.getPlayerByHash(usernameHash);
	}

	/**
	 * Removes a player by their username hash
	 */
	public Player removePlayer(final long usernameHash) {
		return players.removePlayerByHash(usernameHash);
	}

	/**
	 * Gets a player by their ID
	 */
	public Player getPlayerID(final int databaseID) {
		for (final Player player : getPlayers()) {
			if (player.getDatabaseID() == databaseID) {
				return player;
			}
		}
		return null;
	}

	/**
	 * Gets a player by their UUID
	 */
	public Player getPlayerByUUID(final UUID uuid) {
		for (final Player player : getPlayers()) {
			if (player.getUUID().equals(uuid)) {
				return player;
			}
		}
		return null;
	}

	public EntityList<Player> getPlayers() {
		return players;
	}

	/**
	 * Get list of players by IP
	 */
	public EntityList<Player> getPlayers(String ip) {
		return players.stream().filter(p -> p.getCurrentIP().equals(ip)).collect(Collectors.toCollection(EntityList::new));
	}

	/**
	 * Gets a random online player
	 * @return
	 */
	public Player getRandomPlayer() {
		if(!players.isEmpty()) {
			List<Integer> indices = new ArrayList<>(players.indices());
			int randomIndex = (int)(Math.random() * indices.size());
			return players.get(indices.get(randomIndex));
		}
		return null;
	}
	/**
	 * Gets the player at or above the PID requested
	 * @return
	 */
	public Player getNextPlayer(final int pid, final int excludePid) {
		if(!players.isEmpty()) {
			List<Integer> indices = new ArrayList<>(players.indices());
			for (int pidSearch = pid; pidSearch < pid + getServer().getConfig().MAX_PLAYERS; pidSearch++) {
				int pidSearchMod = pidSearch % getServer().getConfig().MAX_PLAYERS;
				if (indices.contains(pidSearchMod) && pidSearchMod != excludePid) {
					return players.get(pidSearchMod);
				}
			}
		}
		return null;
	}

	/**
	 * Finds a specific quest by ID
	 *
	 * @param q
	 * @return
	 * @throws IllegalArgumentException when a quest by that ID isn't found
	 */
	public QuestInterface getQuest(final int q) throws IllegalArgumentException {
		for (final QuestInterface quest : this.getQuests()) {
			if (quest.getQuestId() == q) {
				return quest;
			}
		}
		throw new IllegalArgumentException("No quest found");
	}

	/**
	 * Finds a specific miniquest/minigame by ID
	 *
	 * @param m
	 * @return
	 * @throws IllegalArgumentException when a quest by that ID isn't found
	 */
	public MiniGameInterface getMiniGame(final int m) throws IllegalArgumentException {
		for (final MiniGameInterface minigame : getMiniGames()) {
			if (minigame.getMiniGameId() == m) {
				return minigame;
			}
		}
		throw new IllegalArgumentException("No mini-game found");
	}

	public List<QuestInterface> getQuests() {
		return quests;
	}

	public List<MiniGameInterface> getMiniGames() {
		return minigames;
	}

	public List<Shop> getShops() {
		return shops;
	}

	public boolean hasNpc(final Npc n) {
		return getNpcs().contains(n);
	}
	/*
	 * Note to self - Remove CollidingWallObject, Remove getWallGameObject, And others if this doesn't work in long run.
	 * Classes - viewArea, world, region, gameObjectAction, GameObjectWallAction, ItemUseOnObject
	 */

	public boolean hasPlayer(final Player player) {
		return getPlayers().contains(player);
	}

	public boolean isLoggedIn(final long usernameHash) {
		final Player friend = getPlayer(usernameHash);
		if (friend != null) {
			return friend.loggedIn();
		}
		return false;
	}

	public void load() {
		try {
			getClanManager().initialize();
			getPartyManager().initialize();
			if (getMarket() != null) {
				getMarket().start();
			}
			getRegionManager().load();
			getWorldLoader().getWorldPopulator().populateWorld();
			getRegionManager().populateNativeLayeredPlacements();
			getNpcDrops().load();

			if (PathValidation.DEBUG) {
				pathfindingDebug = new PathfindingDebug(this);
			}

			if (getServer().getConfig().WANT_COMBAT_ODYSSEY) {
				getCombatOdyssey().load();
			}
			if (getServer().getConfig().WANT_MYWORLD) {
				setMonsterSlayerData(MonsterSlayerData.loadForWorld(this));
				setMonsterSlayerTaskService(new MonsterSlayerTaskService(getMonsterSlayerData()));
				setMonsterSlayerLegacyData(CombatOdysseyMigration.LegacyData.load(Paths.get(
					getServer().getConfig().CONFIG_DIR, "defs", "extras", "CombatOdyssey.json")));
			}
		} catch (final Exception e) {
			LOGGER.error("Error in World load()", e);
			if (getServer().getConfig().WANT_LAYERED_NATIVE_TERRAIN_PACKAGE) {
				throw new IllegalStateException(
					"Native layered world load failed closed", e);
			}
		}
	}

	public void unloadPlayers() {
		LOGGER.info("unloadPlayers requested");
		for (final Player p : getPlayers()) {
			unregisterPlayer(p);
		}
	}

	public void unload() {
		LOGGER.info("Saving clans for shutdown");
		if (getServer().getConfig().WANT_CLANS) {
			getClanManager().saveClans();
		}
		LOGGER.info("Processing Market for shutdown");
		if (getMarket() != null) {
			// Finish processing world market.
			getMarket().run();
		}
		LOGGER.info("Saving players for shutdown...");
		for (final Player p : getPlayers()) {
			p.unregister(UnregisterForcefulness.FORCED, "Server shutting down.");
		}
		LOGGER.info("Players saved");

		if (pathfindingDebug != null) {
			pathfindingDebug.destroy();
			pathfindingDebug = null;
		}

		getClanManager().uninitialize();
		getPartyManager().uninitialize();
		getWorldLoader().unloadWorld();
		if (getMarket() != null) {
			getMarket().stop();
		}
		getRegionManager().unload();
		authoredGroundItems.reset();
		nativeLayeredGroundItems.reset();
		getNpcDrops().unload();
		npcs.clear();
		sceneryLocs.clear();
		players.clear();
		snapshots.clear();
		wildernessIPTracker.clear();
		playerUnderAttackMap.clear();
		npcUnderAttackMap.clear();
		globalMessageQueue.clear();
		fishingTrawler.clear();

		EVENT = false;
		EVENT_X = -1;
		EVENT_Y = -1;
		EVENT_COMBAT_MIN = -1;
		EVENT_COMBAT_MAX = -1;
		membersWildStart = 48;
		membersWildMax = 56;
		godSpellsStart = 1;
		godSpellsMax = 5;
	}

	public void registerGameObject(final GameObject o) {
		registerGameObject(o, false);
	}

	private void registerGameObject(
		final GameObject o,
		final boolean forceFullBlock) {
		if (getRegionManager().hasNativeLayeredGameObjectIdentity(o)) {
			if (!getRegionManager().prepareNativeLayeredGameObject(o)) {
				return;
			}
			GameObject current =
				getRegionManager().findNativeLayeredGameObject(o);
			if (current == o) {
				throw new IllegalStateException(
					"Native layered GameObject instance is already active");
			}
			applyGameObjectTransaction(current, o, forceFullBlock);
			return;
		}
		Point objectCoordinates = Point.location(
			o.getLoc().getX(), o.getLoc().getY());
		final GameObject collidingObject = o.getType() == 0
			? getRegionManager().getRegion(objectCoordinates)
				.getGameObject(objectCoordinates, null)
			: getRegionManager().getRegion(objectCoordinates)
				.getWallGameObject(
					objectCoordinates, o.getLoc().getDirection());
		applyGameObjectTransaction(
			collidingObject, o, forceFullBlock);
	}

	private void applyHostileProjectileCollision(
		final GameObject object,
		final boolean add) {
		if (object == null || object.getID() == 1147) {
			return;
		}
		final int direction = object.getDirection();
		if (object.isScenery()) {
			if (!HostileProjectileCollision.blocksScenery(
					object.getGameObjectDef())) {
				return;
			}
			final int width;
			final int height;
			if (direction == 0 || direction == 4) {
				width = object.getGameObjectDef().getWidth();
				height = object.getGameObjectDef().getHeight();
			} else {
				width = object.getGameObjectDef().getHeight();
				height = object.getGameObjectDef().getWidth();
			}
			for (int x = object.getX(); x < object.getX() + width; x++) {
				for (int y = object.getY(); y < object.getY() + height; y++) {
					updateHostileProjectileSceneryCollision(
						x,
						y,
						direction,
						object.getGameObjectDef().getType(),
						add);
				}
			}
			return;
		}
		if (object.getDoorDef().getDoorType() == 1) {
			updateHostileProjectileBoundaryCollision(
				object.getX(), object.getY(), direction, add);
		}
	}

	private void updateHostileProjectileSceneryCollision(final int x, final int y, final int dir,
														 final int objectType, final boolean add) {
		if (objectType != 2) {
			updateHostileProjectileCollision(x, y, CollisionFlag.FULL_BLOCK_C, add);
			return;
		}
		if (dir == 0) {
			updateHostileProjectileCollision(x, y, CollisionFlag.WALL_EAST, add);
			if (getTile(x - 1, y) != null) {
				updateHostileProjectileCollision(x - 1, y, CollisionFlag.WALL_WEST, add);
			}
		} else if (dir == 2) {
			updateHostileProjectileCollision(x, y, CollisionFlag.WALL_SOUTH, add);
			if (getTile(x, y + 1) != null) {
				updateHostileProjectileCollision(x, y + 1, CollisionFlag.WALL_NORTH, add);
			}
		} else if (dir == 4) {
			updateHostileProjectileCollision(x, y, CollisionFlag.WALL_WEST, add);
			if (getTile(x + 1, y) != null) {
				updateHostileProjectileCollision(x + 1, y, CollisionFlag.WALL_EAST, add);
			}
		} else if (dir == 6) {
			updateHostileProjectileCollision(x, y, CollisionFlag.WALL_NORTH, add);
			if (getTile(x, y - 1) != null) {
				updateHostileProjectileCollision(x, y - 1, CollisionFlag.WALL_SOUTH, add);
			}
		}
	}

	private void updateHostileProjectileBoundaryCollision(final int x, final int y, final int dir,
														  final boolean add) {
		if (dir == 0) {
			updateHostileProjectileCollision(x, y, CollisionFlag.WALL_NORTH, add);
			if (getTile(x, y - 1) != null) {
				updateHostileProjectileCollision(x, y - 1, CollisionFlag.WALL_SOUTH, add);
			}
		} else if (dir == 1) {
			updateHostileProjectileCollision(x, y, CollisionFlag.WALL_EAST, add);
			if (getTile(x - 1, y) != null) {
				updateHostileProjectileCollision(x - 1, y, CollisionFlag.WALL_WEST, add);
			}
		} else if (dir == 2) {
			updateHostileProjectileCollision(x, y, CollisionFlag.FULL_BLOCK_A, add);
		} else if (dir == 3) {
			updateHostileProjectileCollision(x, y, CollisionFlag.FULL_BLOCK_B, add);
		}
	}

	private void updateHostileProjectileCollision(final int x, final int y, final int flags,
												  final boolean add) {
		final TileValue tile = getMutableTile(x, y);
		if (add) {
			tile.addHostileProjectileCollision(flags);
		} else {
			tile.removeHostileProjectileCollision(flags);
		}
	}

	private GameTickEventRestorationCollisionFootprintPlanner.Result
		planGameObjectCollision(
		final GameObject object,
		final Operation operation,
		final boolean forceFullBlock) {
		Point currentLocation = object.getLocation();
		int objectX = currentLocation == null
			? object.getLoc().getX() : currentLocation.getX();
		int objectY = currentLocation == null
			? object.getLoc().getY() : currentLocation.getY();
		Definition definition = null;
		if (object.getID() != 1147 || operation != Operation.REGISTER) {
			if (object.isScenery()) {
				definition = Definition.scenery(
					object.getGameObjectDef().getType(),
					object.getGameObjectDef().getWidth(),
					object.getGameObjectDef().getHeight(),
					object.getGameObjectDef().getName(),
					Constants.objectsProjectileClipAllowed);
			} else {
				definition = Definition.boundary(
					object.getDoorDef().getDoorType(),
					object.getDoorDef().getName(),
					Constants.objectsProjectileClipAllowed);
			}
		}
		return GameTickEventRestorationCollisionFootprintPlanner.plan(
				operation,
				ConstructorState.of(
					object.getID(), objectX, objectY,
					object.getDirection(), object.getType()),
				definition, forceFullBlock,
				WorldBounds.of(Constants.MAX_WIDTH, Constants.MAX_HEIGHT));
	}

	/**
	 * Projects one detached GameObject collision footprint before an ordered
	 * Region transaction acquires any runtime boundary.
	 */
	public GameTickEventRestorationCollisionFootprintPlanner.Result
		projectGameObjectCollisionFootprint(
		final GameObject object,
		final Operation operation,
		final boolean forceFullBlock) {
		return planGameObjectCollision(object, operation, forceFullBlock);
	}

	private void applyGameObjectTransaction(
		final GameObject oldObject,
		final GameObject newObject,
		final boolean forceFullBlock) {
		GameTickEventRestorationCollisionFootprintPlanner.Result
			oldUnregister = oldObject == null ? null
				: planGameObjectCollision(
					oldObject, Operation.UNREGISTER, false);
		GameTickEventRestorationCollisionFootprintPlanner.Result
			oldRollbackRegister = oldObject == null ? null
				: planGameObjectCollision(
					oldObject, Operation.REGISTER, false);
		GameTickEventRestorationCollisionFootprintPlanner.Result newRegister =
			newObject == null ? null : planGameObjectCollision(
				newObject, Operation.REGISTER, forceFullBlock);
		final boolean nativeLayered = (oldObject != null
				&& getRegionManager().isNativeLayeredGameObject(oldObject))
			|| (newObject != null
				&& getRegionManager().isNativeLayeredGameObject(newObject));
		if (nativeLayered) {
			getRegionManager().applyNativeLayeredGameObjectTransaction(
				oldObject, oldUnregister, oldRollbackRegister,
				newObject, newRegister);
			return;
		}
		getRegionManager().applyObjectMembershipAndCollisionTransaction(
			oldObject, oldUnregister, oldRollbackRegister,
			newObject, newRegister);
		applyHostileProjectileCollision(oldObject, false);
		applyHostileProjectileCollision(newObject, true);
	}

	public void registerItem(final GroundItem i) {
		registerItem(i, i.getConfig().GAME_TICK * 200);
	}

	/**
	 * Registers one authored ground item for a location definition. Unlike
	 * ordinary drops, repeated population and respawn callbacks for the same
	 * authored tile resolve to the existing instance.
	 */
	public GroundItem registerAuthoredGroundItem(final ItemLoc loc) {
		return registerAuthoredGroundItem(loc, null);
	}

	public GroundItem registerAuthoredGroundItem(final ItemLoc loc, final Long expectedGeneration) {
		if (getServer().getConfig().RESTRICT_ITEM_ID > ItemId.NOTHING.id()
			&& loc.getId() > getServer().getConfig().RESTRICT_ITEM_ID) {
			return null;
		}
		if (expectedGeneration == null) {
			return authoredGroundItems.register(loc.getX(), loc.getY(), () -> new GroundItem(this, loc));
		}
		return authoredGroundItems.registerForGeneration(loc.getX(), loc.getY(), expectedGeneration,
			() -> new GroundItem(this, loc));
	}

	/**
	 * Releases the current authored instance and returns the world-generation
	 * token required by its delayed replacement.
	 */
	public long removeAuthoredGroundItem(final GroundItem item) {
		final ItemLoc loc = item.getLoc();
		if (loc == null) {
			return AuthoredGroundItemRegistry.NO_GENERATION;
		}
		return authoredGroundItems.remove(loc.getX(), loc.getY(), item);
	}

	public GroundItem registerNativeLayeredGroundItem(
		final NativeLayeredGroundItemPlacement placement) {
		return registerNativeLayeredGroundItem(placement, null);
	}

	public GroundItem registerNativeLayeredGroundItem(
		final NativeLayeredGroundItemPlacement placement,
		final Long expectedGeneration) {
		if (getServer().getConfig().RESTRICT_ITEM_ID > ItemId.NOTHING.id()
			&& placement.getItemId()
				> getServer().getConfig().RESTRICT_ITEM_ID) {
			return null;
		}
		final GroundItem item;
		if (expectedGeneration == null) {
			item = nativeLayeredGroundItems.register(
				placement.getLocation(),
				() -> new GroundItem(this, placement));
		} else {
			item = nativeLayeredGroundItems.registerForGeneration(
				placement.getLocation(),
				expectedGeneration,
				() -> new GroundItem(this, placement));
		}
		if (item != null) {
			getRegionManager().markNativeLayeredPlacement(
				item,
				placement.getPlacementId(),
				RegionManager.NATIVE_LAYERED_GROUND_ITEM_KIND);
		}
		return item;
	}

	public long removeNativeLayeredGroundItem(final GroundItem item) {
		NativeLayeredGroundItemPlacement placement =
			item.getNativeLayeredPlacement();
		if (placement == null) {
			return AuthoredLayeredGroundItemRegistry.NO_GENERATION;
		}
		return nativeLayeredGroundItems.remove(
			placement.getLocation(), item);
	}

	public boolean retireNativeLayeredGroundItem(final GroundItem item) {
		NativeLayeredGroundItemPlacement placement =
			item.getNativeLayeredPlacement();
		return placement != null
			&& nativeLayeredGroundItems.retire(
				placement.getLocation(), item);
	}

	public GroundItem findNativeLayeredGroundItem(
		final WorldLocation location) {
		return nativeLayeredGroundItems.find(location);
	}

	public boolean hasNativeLayeredGroundItemPlacement(
		final WorldLocation location) {
		return nativeLayeredGroundItems.containsPlacement(location);
	}

	public void registerItem(final GroundItem i, final int delayTime) {
		try {
			if (Summoning.tryLootGoblinCollectGroundItem(i)) {
				i.remove();
				return;
			}
			if (i.getLoc() == null) {
				getServer().getGameEventHandler().add(new SingleEvent(this, null, delayTime, "Register Item") {
					public void action() {
						if (!i.isRemoved()) {
							unregisterItem(i);
						}
					}
				});
			}
		} catch (Exception e) {
			i.remove();
			LOGGER.error("Exception in registerItem", e);
		}
	}

	public Npc registerNpc(final Npc n) {
		final NPCLoc npc = n.getLoc();
		if (npc.startX < npc.minX || npc.startX > npc.maxX || npc.startY < npc.minY || npc.startY > npc.maxY
			|| (getTile(n.getWorldLocation()).overlay & 64) != 0) {
			LOGGER.error("Broken Npc: <id>" + npc.id + "</id><startX>" + npc.startX + "</startX><startY>"
				+ npc.startY + "</startY>");
		}

		getNpcs().add(n);
		return n;
	}

	public boolean registerPlayer(final Player player) {
		if (!getPlayers().contains(player)) {
			player.setBusy(false);

			getPlayers().add(player);
			player.updateRegion();
			getServer().getGameLogger().run(new PlayerOnlineFlagQuery(getServer(), player.getDatabaseID(), player.getCurrentIP(), true));

			for (Player other : getPlayers()) {
				other.getSocial().alertOfLogin(player);
			}
			getClanManager().checkAndAttachToClan(player);
			getPartyManager().checkAndAttachToParty(player);

			if (player.getCache().hasKey("skull_remaining") && (player.getCache().getLong("skull_remaining") > 0)) {
				player.addSkull(player.getCache().getLong("skull_remaining"));
				player.setSkullTimer(player.getCache().getLong("skull_remaining"));
			}

			if (player.getCache().hasKey("charge_remaining") && (player.getCache().getLong("charge_remaining") > 0)) {
				player.addCharge(player.getCache().getLong("charge_remaining"));
				player.setChargeTimer(player.getCache().getLong("charge_remaining"));
			}

			return true;
		}
		return false;
	}

	public void registerQuest(final QuestInterface quest) {
		if (quest.getQuestName() == null) {
			throw new IllegalArgumentException("Quest name cannot be null");
		} else if (quest.getQuestName().length() > 40) {
			throw new IllegalArgumentException("Quest name cannot be longer then 40 characters");
		}
		for (final QuestInterface q : getQuests()) {
			if (q.getQuestId() == quest.getQuestId()) {
				throw new IllegalArgumentException("Quest ID must be unique");
			}
		}

		if (!getServer().getConfig().WANT_CUSTOM_QUESTS
		&& quest.getQuestId() > Quests.LEGENDS_QUEST)
			return;

		getQuests().add(quest);
	}

	public void registerMiniGame(final MiniGameInterface minigame) {
		if (minigame.getMiniGameName() == null) {
			throw new IllegalArgumentException("Minigame name cannot be null");
		} else if (minigame.getMiniGameName().length() > 40) {
			throw new IllegalArgumentException("Minigame name cannot be longer then 40 characters");
		}
		for (final MiniGameInterface m : getMiniGames()) {
			if (m.getMiniGameId() == minigame.getMiniGameId()) {
				System.out.println(minigame.getMiniGameId());
				throw new IllegalArgumentException("MiniGame ID must be unique");
			}
		}
		getMiniGames().add(minigame);
	}

	public void replaceGameObject(final GameObject old, final GameObject _new) {
		if (getRegionManager().isNativeLayeredGameObject(old)) {
			getRegionManager().inheritNativeLayeredGameObjectIdentity(
				old, _new);
		}
		LayeredAuthoredPlacementIdentity authoredIdentity =
			old.getAuthoredPlacementIdentity();
		if (authoredIdentity != null) {
			_new.getLoc().assignAuthoredPlacementIdentity(authoredIdentity);
			_new.assignAuthoredPlacementIdentity(authoredIdentity);
		}
		applyGameObjectTransaction(old, _new, false);
	}

	public void sendKilledUpdate(final long killedHash, final long killerHash, final int type) {
		for (final Player player : getPlayers()) {
			ActionSender.sendKillUpdate(player, killedHash, killerHash, type);
		}
	}

	public void sendModAnnouncement(final String string) {
		for (final Player player : getPlayers()) {
			if (player.isMod()) {
				player.playerServerMessage(MessageType.BROADCAST, "[@cya@SERVER@whi@]: " + string);
			}
		}
	}

	public void sendWorldAnnouncement(final String msg) {
		if (getServer().getConfig().WANT_GLOBAL_CHAT) {
			for (final Player player : getPlayers()) {
				player.playerServerMessage(MessageType.GLOBAL_CHAT, "@gre@[Global] @whi@" + msg);
			}
		}
	}

	public void sendWorldMessage(final String msg) {
		for (final Player player : getPlayers()) {
			player.playerServerMessage(MessageType.BROADCAST, msg);
		}
	}

	/**
	 * Removes an object from the server
	 */
	public void unregisterGameObject(final GameObject o) {
		applyGameObjectTransaction(o, null, false);
	}

	public GlobalMessage getNextGlobalMessage() {
		return globalMessageQueue.poll();
	}

	public void addGlobalMessage(final GlobalMessage privateMessage) {
		getGlobalMessageQueue().add(privateMessage);
	}

	/**
	 * Removes an item from the server
	 */
	public void unregisterItem(final GroundItem i) {
		i.remove();
	}

	/**
	 * Removes an npc from the server
	 */
	public void unregisterNpc(final Npc n) {
		if (hasNpc(n)) {
			getNpcs().remove(n);
		}
		n.superRemove();
	}

	/**
	 * Removes a player from the server and saves their account
	 */
	public void unregisterPlayer(final Player player) {
		try {
			getServer().getWorldEditorSessions().closeFor(player);
			if (getServer().getLoginExecutor() != null) {
				getServer().getGameLogger().addQuery(new PlayerOnlineFlagQuery(getServer(), player.getDatabaseID(), false));
				// We handle avatar generation code exceptions separately, they are not a critical part of the logout process.
				try {
					if (avatarGenerator != null) {
						avatarGenerator.generateAvatar(player.getDatabaseID(), player.getSettings().getAppearance(), player.getWornItems());
					}
				} catch (final Exception e){
					LOGGER.error("Error generating avatar: ", e);
				}
			}
			player.resetSceneryMorph();
			player.logout();
			LOGGER.info("Unregistered " + player.getUsername() + " from player list.");

			if (player.getChannel() == null) {
				LOGGER.warn("Warning: getChannel() is already null for " + player.getUsername());
			}

			if (getServer().getConfig().WANT_PCAP_LOGGING) {
				if (player.getChannel() != null && player.getChannel().attr(attachment).get() != null) {
					PcapLogger pcap = player.getChannel().attr(attachment).get().pcapLogger.get();

					getServer().getPcapLogger().addJob(pcap::exportPCAP);
					LOGGER.info("Wrote out pcap for " + player.getUsername() + " at " + pcap.fname);
				}
			}

			getServer().getPacketFilter().removeLoggedInPlayer(player.getCurrentIP(), player.getUsernameHash());

			// close the channel after a safe amount of time for the logout packet to reach the player
			// does not matter if player logs back in while this still hasn't been destroyed, it's just to free memory.
			player.getWorld().getServer().getGameEventHandler().add(
				new DelayedEvent(player.getWorld(), null, 2500, "Free channel memory") {
				public void run() {
					try {
						Channel playerChannel = player.getChannel();
						if (playerChannel != null) {
							if (playerChannel.hasAttr(attachment)) {
								playerChannel.attr(attachment).set(null);
							}
							player.close();
							getServer().getPacketFilter().removePlayerConnPacket(playerChannel);
						}
					} catch (Exception e) {
						LOGGER.error("Exception in freeing channel memory", e);
					} finally {
						player.unsetChannel();
						stop();
					}
				}
			});
		} catch (final Exception e) {
			LOGGER.error("Exception in unregisterPlayer", e);
		}
	}

	public void unregisterQuest(final QuestInterface quest) {
		if (getQuests().contains(quest)) {
			getQuests().remove(quest);
		}
	}

	public void unregisterMiniGame(final MiniGameInterface minigame) {
		if (getMiniGames().contains(minigame)) {
			getMiniGames().remove(minigame);
		}
	}

	/**
	 * Are the given coords within the world boundaries
	 */
	public boolean withinWorld(final int x, final int y) {
		return getRegionManager().withinWorld(x, y);
	}

	public TileValue getTile(final int x, final int y) {
		return getRegionManager().getTile(x, y);
	}

	public TileValue getMutableTile(final int x, final int y) {
		return getRegionManager().getMutableTile(x, y);
	}

	public TileValue getTile(final Point point) {
		return getRegionManager().getTile(point);
	}

	public TileValue getTile(final WorldLocation location) {
		return getRegionManager().getTile(location);
	}

	public boolean canYield(final Item item) {
		boolean notYieldable = this.server.getConfig().RESTRICT_ITEM_ID >= 0 && this.server.getConfig().RESTRICT_ITEM_ID < item.getCatalogId();
		return !notYieldable;
	}

	public FishingTrawler getFishingTrawler(final TrawlerBoat boat) {
		FishingTrawler trawlerInstance = fishingTrawler.get(boat);
		if (trawlerInstance != null && !trawlerInstance.shouldRemove()) {
			return trawlerInstance;
		} else {
			trawlerInstance = new FishingTrawler(this, boat);
			trawlerInstance.register(this);
			fishingTrawler.put(boat, trawlerInstance);
			getServer().getGameEventHandler().add(trawlerInstance);
			return trawlerInstance;
		}
	}

	public FishingTrawler getFishingTrawler(final Player player) {
		if (fishingTrawler.get(TrawlerBoat.EAST) != null && fishingTrawler.get(TrawlerBoat.EAST).getPlayers().contains(player)) {
			return fishingTrawler.get(TrawlerBoat.EAST);
		}
		if (fishingTrawler.get(TrawlerBoat.WEST) != null && fishingTrawler.get(TrawlerBoat.WEST).getPlayers().contains(player)) {
			return fishingTrawler.get(TrawlerBoat.WEST);
		}
		return null;
	}

	// notified when event is stopped to deallocate reference
	@Override
	public void update(final FishingTrawler ctx) {
		if (ctx != null && ctx.getPlayers().size() == 0) {
			fishingTrawler.remove(ctx.getBoat());
		}
	}

	public void produceUnderAttack(final Player player) {
		getPlayersUnderAttack().put(player, true);
	}

	public void produceUnderAttack(final Npc n) {
		getNpcsUnderAttack().put(n, true);
	}

	public boolean checkUnderAttack(final Player player) {
		return getPlayersUnderAttack().getOrDefault(player, false);
	}

	public boolean checkUnderAttack(final Npc n) {
		return getNpcsUnderAttack().getOrDefault(n, false);
	}

	public void releaseUnderAttack(final Player player) {
		if (getPlayersUnderAttack().containsKey(player)) {
			getPlayersUnderAttack().remove(player);
		}
	}

	public void releaseUnderAttack(final Npc n) {
		if (getNpcsUnderAttack().containsKey(n)) {
			getNpcsUnderAttack().remove(n);
		}
	}

	public Map<Player, Boolean> getPlayersUnderAttack() {
		return playerUnderAttackMap;
	}

	public Map<Npc, Boolean> getNpcsUnderAttack() {
		return npcUnderAttackMap;
	}

	public synchronized WorldLoader getWorldLoader() {
		return worldLoader;
	}

	public final Server getServer() {
		return server;
	}

	public final IPTracker<String> getWildernessIPTracker() {
		return wildernessIPTracker;
	}

	public synchronized RegionManager getRegionManager() {
		return regionManager;
	}

	public synchronized CombatOdysseyData getCombatOdyssey() {
		return combatOdysseyData;
	}

	/** Validated foundation data; null before MyWorld startup loading completes. */
	public synchronized MonsterSlayerData getMonsterSlayerData() {
		return monsterSlayerData;
	}

	/** Immutable legacy decoder data paired with the validated Slayer definitions. */
	public synchronized CombatOdysseyMigration.LegacyData getMonsterSlayerLegacyData() {
		return monsterSlayerLegacyData;
	}

	public synchronized MonsterSlayerTaskService getMonsterSlayerTaskService() {
		return monsterSlayerTaskService;
	}

	private synchronized void setMonsterSlayerData(MonsterSlayerData data) {
		monsterSlayerData = data;
	}

	private synchronized void setMonsterSlayerLegacyData(
			CombatOdysseyMigration.LegacyData data) {
		monsterSlayerLegacyData = data;
	}

	private synchronized void setMonsterSlayerTaskService(MonsterSlayerTaskService service) {
		monsterSlayerTaskService = service;
	}

	public synchronized Market getMarket() {
		return market;
	}

	public synchronized PartyManager getPartyManager() {
		return partyManager;
	}

	public synchronized ClanManager getClanManager() {
		return clanManager;
	}

	public synchronized NpcDrops getNpcDrops() {
		return npcDrops;
	}

	public boolean isTelegrabEnabled() {
		return telegrabEnabled;
	}

	public Queue<GlobalMessage> getGlobalMessageQueue() {
		return globalMessageQueue;
	}

	public void addSceneryLoc(final Point point, final Integer id) {
		sceneryLocs.put(point, id);
	}

	public Integer getSceneryLoc(final Point point) {
		return sceneryLocs.getOrDefault(point, -1);
	}

	@Override
	public void run() {
	}

	public int getMaxBankSize() {
		return maxBankSize;
	}

	public long processGlobalMessageQueue() {
		return getServer().bench(() -> {
			GlobalMessage gm;
			while ((gm = getServer().getWorld().getNextGlobalMessage()) != null) {
				for (final Player player : getPlayers()) {
					if (player == gm.getPlayer()) {
						player.getWorld().getServer().getGameLogger().addQuery(new PMLog(player.getWorld(), player.getUsername(), gm.getMessage(),
							"Global$"));
						if (player.getCache().hasKey("private_message_global")) {
							ActionSender.sendPrivateMessageSent(gm.getPlayer(), -1L, gm.getMessage(), true);
						} else {
							ActionSender.sendMessage(player, null, MessageType.GLOBAL_CHAT, formatGlobalQuestMessage(gm, player), 0, "");
						}
					} else {
						if (!player.getBlockGlobalFriend()) {
							boolean blockNone = player.getSettings().getPrivacySetting(PlayerSettings.PRIVACY_BLOCK_PRIVATE_MESSAGES, player.isUsingCustomClient())
								== PlayerSettings.BlockingMode.None.id();
							boolean blockNonFriend = player.getSettings().getPrivacySetting(PlayerSettings.PRIVACY_BLOCK_PRIVATE_MESSAGES, player.isUsingCustomClient())
								== PlayerSettings.BlockingMode.NonFriends.id();
							if ((blockNone || blockNonFriend) && !player.getSocial().isIgnoring(gm.getPlayer().getUsernameHash()) || gm.getPlayer().isMod()) {
								if (player.getCache().hasKey("private_message_global")) {
									ActionSender.sendPrivateMessageReceived(player, gm.getPlayer(), gm.getMessage(), true);
								} else {
									ActionSender.sendMessage(player, null, MessageType.GLOBAL_CHAT, formatGlobalQuestMessage(gm, player), 0, "");
								}
							}
						}
					}
				}
			}
		});
	}

	private String formatGlobalQuestMessage(GlobalMessage gm, Player playerSentTo) {
		StringBuilder returnMessage = new StringBuilder();

		String globalMessageColor = "@cya@";
		if (playerSentTo.getCache().hasKey("global_message_color")) {
			globalMessageColor = playerSentTo.getCache().getString("global_message_color");
		}

		returnMessage.append(globalMessageColor);
		returnMessage.append("Global$");

		// moderators get a prefix
		String groupPrefix = Group.getGlobalMessageName(gm.getPlayer().getGroupID());
		if (!groupPrefix.equals("")) {
			returnMessage.append("@ora@[");
			if (gm.getPlayer().getGroupID() == Group.PLAYER_MOD) {
				returnMessage.append("@whi@");
			} else {
				returnMessage.append("@yel@");
			}
			returnMessage.append(groupPrefix);
			returnMessage.append("@ora@]");
		} else {
			returnMessage.append("@ora@");
		}

		// username is added
		returnMessage.append("[@gre@");
		returnMessage.append(gm.getPlayer().getUsername());
		returnMessage.append("@ora@]: ");
		returnMessage.append(globalMessageColor);

		// actual message appended, with stripped positional codes
		returnMessage.append(gm.getMessage().replaceAll("~...~", ""));

		return returnMessage.toString();
	}
}
