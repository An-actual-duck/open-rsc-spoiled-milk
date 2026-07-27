package com.openrsc.server.content.worldedit;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.model.world.coordinate.NativeLayeredGameObjectIdentity;
import com.openrsc.server.io.WorldEditorTerrainArchive;
import com.openrsc.server.io.WorldEditorTerrainSaveFiles;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Single-owner, strictly sequenced editor session and bounded server-lifetime terrain draft. */
public final class WorldEditorSessionManager {
	public static final int TERRAIN_DRAFT_LIMIT = 4096;
	private final SecureRandom random;
	private final WorldEditStorageContext storage;
	private Session active;
	private WorldEditorTerrainArchive terrainArchive;
	private Path terrainArchivePath;
	private String terrainBaseSha256;
	private final Map<String,WorldEditorTerrainArchive.Snapshot> terrainDraft =
		new LinkedHashMap<String,WorldEditorTerrainArchive.Snapshot>();
	private final Map<NativeTileKey,NativeLayeredTerrainTile> nativeTerrainBase =
		new LinkedHashMap<NativeTileKey,NativeLayeredTerrainTile>();
	private final Map<NativeTileKey,NativeLayeredTerrainTile> nativeTerrainOverlay =
		new LinkedHashMap<NativeTileKey,NativeLayeredTerrainTile>();
	private final Map<NativeTileKey,NativeLayeredTerrainTile> nativeTerrainSaved =
		new LinkedHashMap<NativeTileKey,NativeLayeredTerrainTile>();
	private final Set<NativeTileKey> nativeTerrainDirty =
		new HashSet<NativeTileKey>();
	private final Set<WorldMapSectorId> nativeTerrainGrowth =
		new java.util.LinkedHashSet<WorldMapSectorId>();
	private final Set<WorldMapSectorId> nativeTerrainGrowthSaved =
		new java.util.LinkedHashSet<WorldMapSectorId>();
	private final Map<WorldMapSectorId,NativeLayeredTerrainSector>
		nativeTerrainLiveSectors =
			new LinkedHashMap<WorldMapSectorId,NativeLayeredTerrainSector>();
	private final Map<Integer,NativeLevelCreation> nativeLevelCreations =
		new LinkedHashMap<Integer,NativeLevelCreation>();
	private final Map<Integer,NativeLevelCreation> nativeLevelCreationsSaved =
		new LinkedHashMap<Integer,NativeLevelCreation>();
	private final Map<NativeSceneryKey,NativeSceneryState> nativeSceneryBase =
		new LinkedHashMap<NativeSceneryKey,NativeSceneryState>();
	private final Map<NativeSceneryKey,NativeSceneryState> nativeSceneryOverlay =
		new LinkedHashMap<NativeSceneryKey,NativeSceneryState>();
	private final Map<NativeSceneryKey,NativeSceneryState> nativeScenerySaved =
		new LinkedHashMap<NativeSceneryKey,NativeSceneryState>();
	private final Set<NativeSceneryKey> nativeSceneryDirty =
		new HashSet<NativeSceneryKey>();
	private final Map<NativeNpcKey,NativeNpcState> nativeNpcBase =
		new LinkedHashMap<NativeNpcKey,NativeNpcState>();
	private final Map<NativeNpcKey,NativeNpcState> nativeNpcOverlay =
		new LinkedHashMap<NativeNpcKey,NativeNpcState>();
	private final Map<NativeNpcKey,NativeNpcState> nativeNpcSaved =
		new LinkedHashMap<NativeNpcKey,NativeNpcState>();
	private final Set<NativeNpcKey> nativeNpcDirty =
		new HashSet<NativeNpcKey>();
	private String nativeTerrainBaseManifestSha256;
	private long nativeTerrainSceneRevision;
	public WorldEditorSessionManager() { this(null, new SecureRandom()); }
	public WorldEditorSessionManager(WorldEditStorageContext storage) { this(storage, new SecureRandom()); }
	WorldEditorSessionManager(SecureRandom random) { this(null, random); }
	WorldEditorSessionManager(WorldEditStorageContext storage, SecureRandom random) { this.storage = storage; this.random = random; }

	public synchronized OpenResult open(Player player, boolean enabled) {
		if (!enabled) return OpenResult.denied("The in-game world editor is disabled on this server.");
		if (player == null || !player.isAdmin()) return OpenResult.denied("Administrator authorization is required.");
		if (active != null && active.ownerHash != player.getUsernameHash()) return OpenResult.denied("Another administrator owns the active editor session.");
		if (active == null) {
			long id;
			do { id = random.nextLong(); } while (id == 0L);
			active = new Session(id, player.getUsernameHash());
		}
		return OpenResult.opened(active.id, active.nextSequence);
	}

	public synchronized Validation validate(Player player, long id, int sequence) {
		if (player == null || !player.isAdmin() || active == null || active.ownerHash != player.getUsernameHash() || active.id != id)
			return Validation.denied("Editor session is not active or is not owned by this administrator.");
		if (sequence != active.nextSequence) return Validation.denied("Editor request sequence mismatch.");
		active.nextSequence++;
		return Validation.accepted(active.nextSequence);
	}

	public synchronized boolean close(Player player, long id, int sequence) {
		if (!validate(player, id, sequence).accepted) return false;
		active = null;
		return true;
	}
	public synchronized void closeFor(Player player) {
		if (player != null && active != null && active.ownerHash == player.getUsernameHash()) active = null;
	}
	public synchronized boolean hasActiveSession() { return active != null; }
	public synchronized boolean ownsActiveSession(Player player){return player!=null&&player.isAdmin()&&active!=null&&active.ownerHash==player.getUsernameHash();}
	public synchronized WorldEditorTerrainArchive.Snapshot inspectTerrain(Player player, int x, int y, int plane) throws IOException {
		WorldEditorTerrainArchive.Snapshot archived=inspectArchivedTerrain(player,x,y,plane);
		WorldEditorTerrainArchive.Snapshot drafted=terrainDraft.get(terrainKey(x,y,plane));
		return drafted==null?archived:drafted;
	}
	public synchronized WorldEditorTerrainArchive.Snapshot paintTerrain(Player player, int x, int y, int plane,
		int fieldMask, int elevation, int groundTexture, int groundOverlay,int roofTexture,
		int horizontalWall,int verticalWall,int diagonal) throws IOException {
		if(fieldMask<=0||(fieldMask&~127)!=0)throw new IllegalArgumentException("Select at least one supported terrain field.");
		if(!rawByte(elevation)||!rawByte(groundTexture)||!rawByte(groundOverlay)||!rawByte(roofTexture)
			||!rawByte(horizontalWall)||!rawByte(verticalWall))throw new IllegalArgumentException("Terrain byte values must be from 0 to 255.");
		WorldEditorTerrainArchive.Snapshot archived=inspectArchivedTerrain(player,x,y,plane);
		String key=terrainKey(x,y,plane);
		WorldEditorTerrainArchive.Snapshot current=terrainDraft.containsKey(key)?terrainDraft.get(key):archived;
		WorldEditorTerrainArchive.Snapshot painted=current.paint(fieldMask,elevation,groundTexture,groundOverlay,roofTexture,horizontalWall,verticalWall,diagonal);
		if(painted.sameRawTile(archived))terrainDraft.remove(key);
		else {
			if(!terrainDraft.containsKey(key)&&terrainDraft.size()>=TERRAIN_DRAFT_LIMIT)throw new IllegalStateException("Terrain draft limit reached.");
			terrainDraft.put(key,painted);
		}
		return painted;
	}
	public synchronized TerrainStrokeResult paintTerrainStroke(Player player,int[][] requestedTiles,int plane,
		int fieldMask,int elevation,int groundTexture,int groundOverlay,int roofTexture,
		int horizontalWall,int verticalWall,int diagonal) throws IOException {
		validateTerrainPaint(fieldMask,elevation,groundTexture,groundOverlay,roofTexture,horizontalWall,verticalWall);
		int[][] coordinates=WorldEditorTerrainStroke.validateTiles(requestedTiles);
		List<WorldEditorTerrainArchive.Snapshot> before=new ArrayList<WorldEditorTerrainArchive.Snapshot>(coordinates.length);
		List<WorldEditorTerrainArchive.Snapshot> after=new ArrayList<WorldEditorTerrainArchive.Snapshot>(coordinates.length);
		List<WorldEditorTerrainArchive.Snapshot> archived=new ArrayList<WorldEditorTerrainArchive.Snapshot>(coordinates.length);
		boolean[] draftedBefore=new boolean[coordinates.length],draftedAfter=new boolean[coordinates.length];int at=0;
		for(int[] coordinate:coordinates){
			WorldEditorTerrainArchive.Snapshot base=inspectArchivedTerrain(player,coordinate[0],coordinate[1],plane);
			String key=terrainKey(coordinate[0],coordinate[1],plane);
			WorldEditorTerrainArchive.Snapshot current=terrainDraft.containsKey(key)?terrainDraft.get(key):base;
			WorldEditorTerrainArchive.Snapshot painted=current.paint(fieldMask,elevation,groundTexture,groundOverlay,roofTexture,horizontalWall,verticalWall,diagonal);
			draftedBefore[at]=terrainDraft.containsKey(key);draftedAfter[at]=!painted.sameRawTile(base);at++;
			archived.add(base);before.add(current);after.add(painted);
		}
		int projectedDraftSize=WorldEditorTerrainStroke.projectedDraftSize(terrainDraft.size(),draftedBefore,draftedAfter);
		if(projectedDraftSize>TERRAIN_DRAFT_LIMIT)throw new IllegalStateException("Terrain draft limit reached.");
		for(int i=0;i<coordinates.length;i++){
			String key=terrainKey(coordinates[i][0],coordinates[i][1],plane);
			if(after.get(i).sameRawTile(archived.get(i)))terrainDraft.remove(key);else terrainDraft.put(key,after.get(i));
		}
		return new TerrainStrokeResult(before,after);
	}

	public synchronized NativeTerrainSnapshot inspectNativeTerrain(
		Player player, WorldLocation location) {
		NativeLayeredTerrainTile base = nativeBaseTile(player, location);
		NativeTileKey key = new NativeTileKey(location);
		NativeLayeredTerrainTile current = nativeTerrainOverlay.get(key);
		return new NativeTerrainSnapshot(location, current == null ? base : current);
	}

	public synchronized boolean hasNativeTerrainDraft(
		WorldLocation location) {
		return location != null
			&& nativeTerrainGrowth.contains(WorldMapSectorId.from(location));
	}

	public synchronized Optional<NativeLayeredTerrainSector>
		findNativeTerrainSector(
			NativeLayeredWorldPackage owner,
			WorldMapSectorId identity) {
		if (owner == null || identity == null) {
			return Optional.empty();
		}
		Optional<NativeLayeredTerrainSector> source = owner.findSector(identity);
		if (source.isPresent()) return source;
		return Optional.ofNullable(nativeTerrainLiveSectors.get(identity));
	}

	public synchronized long nativeTerrainSceneRevision() {
		return nativeTerrainSceneRevision;
	}

	public synchronized NativeTerrainStrokeResult paintNativeTerrainStroke(
		Player player,
		int[][] requestedTiles,
		int level,
		int fieldMask,
		int elevation,
		int groundTexture,
		int groundOverlay,
		int roofTexture,
		int horizontalWall,
		int verticalWall,
		int diagonal) {
		requireNativeTerrainAuthoring(player, level);
		validateTerrainPaint(
			fieldMask, elevation, groundTexture, groundOverlay, roofTexture,
			horizontalWall, verticalWall);
		int[][] coordinates = WorldEditorTerrainStroke.validateTiles(requestedTiles);
		ensureNativePaintCoverage(player, coordinates, level);
		WorldSpaceId worldSpace = player.getLayeredLocation().getWorldSpace();
		List<NativeTerrainSnapshot> before =
			new ArrayList<NativeTerrainSnapshot>(coordinates.length);
		List<NativeTerrainSnapshot> after =
			new ArrayList<NativeTerrainSnapshot>(coordinates.length);
		List<NativeTileKey> keys =
			new ArrayList<NativeTileKey>(coordinates.length);
		List<NativeLayeredTerrainTile> bases =
			new ArrayList<NativeLayeredTerrainTile>(coordinates.length);
		int projected = nativeTerrainOverlay.size();
		for (int[] coordinate : coordinates) {
			WorldLocation location = new WorldLocation(
				worldSpace,
				new WorldCoordinate(coordinate[0], coordinate[1], level));
			NativeLayeredTerrainTile base = nativeBaseTile(player, location);
			NativeTileKey key = new NativeTileKey(location);
			NativeLayeredTerrainTile current = nativeTerrainOverlay.get(key);
			if (current == null) current = base;
			NativeLayeredTerrainTile painted = paintNativeTile(
				current, fieldMask, elevation, groundTexture, groundOverlay,
				roofTexture, horizontalWall, verticalWall, diagonal);
			boolean existed = nativeTerrainOverlay.containsKey(key);
			boolean remains = !painted.equals(base);
			if (!existed && remains) projected++;
			else if (existed && !remains) projected--;
			keys.add(key);
			bases.add(base);
			before.add(new NativeTerrainSnapshot(location, current));
			after.add(new NativeTerrainSnapshot(location, painted));
		}
		if (projected > TERRAIN_DRAFT_LIMIT) {
			throw new IllegalStateException("Terrain draft limit reached.");
		}
		for (int index = 0; index < keys.size(); index++) {
			NativeTileKey key = keys.get(index);
			NativeLayeredTerrainTile painted = after.get(index).tile;
			if (painted.equals(bases.get(index))) nativeTerrainOverlay.remove(key);
			else nativeTerrainOverlay.put(key, painted);
			refreshNativeDirty(key);
		}
		return new NativeTerrainStrokeResult(before, after);
	}

	public synchronized NativeLayeredTerrainTile resolveNativeTerrainTile(
		WorldLocation location, NativeLayeredTerrainTile source) {
		NativeLayeredTerrainTile drafted =
			nativeTerrainOverlay.get(new NativeTileKey(location));
		if (drafted != null) return drafted;
		if (source != null) return source;
		NativeLayeredTerrainSector live =
			nativeTerrainLiveSectors.get(WorldMapSectorId.from(location));
		if (live == null) return null;
		WorldCoordinate coordinate = location.getCoordinate();
		return live.getTile(coordinate.getLocalX(), coordinate.getLocalY());
	}

	public synchronized byte[] copyNativeTerrainSectorWireBytes(
		NativeLayeredTerrainSector source) {
		byte[] bytes = source.copyWireBytes();
		WorldMapSectorId identity = source.getIdentity();
		for (Map.Entry<NativeTileKey,NativeLayeredTerrainTile> entry
			: nativeTerrainOverlay.entrySet()) {
			NativeTileKey key = entry.getKey();
			if (!identity.getWorldSpace().equals(key.worldSpace)
				|| identity.getLevel() != key.level
				|| identity.getSectorX() != Math.floorDiv(
					key.x, NativeLayeredTerrainSector.SIZE)
				|| identity.getSectorY() != Math.floorDiv(
					key.y, NativeLayeredTerrainSector.SIZE)) {
				continue;
			}
			int localX = Math.floorMod(key.x, NativeLayeredTerrainSector.SIZE);
			int localY = Math.floorMod(key.y, NativeLayeredTerrainSector.SIZE);
			int offset = (localX * NativeLayeredTerrainSector.SIZE + localY) * 10;
			writeNativeTile(bytes, offset, entry.getValue());
		}
		return bytes;
	}

	public synchronized String nativeTerrainSectorSha256(
		NativeLayeredTerrainSector source) {
		return sha256(copyNativeTerrainSectorWireBytes(source));
	}

	public synchronized WorldMapSectorId queueNativeTerrainSectorGrowth(
		Player player, int worldX, int worldY, int level) {
		requireNativeTerrainAuthoring(player, level);
		if (player.getLayeredLocation().getCoordinate().getLevel() != level) {
			throw new IllegalArgumentException(
				"Allocate terrain on the currently active signed level.");
		}
		NativeLayeredWorldPackage owner = nativeOwner(player, player.getLayeredLocation());
		int sectorX = Math.floorDiv(worldX, NativeLayeredTerrainSector.SIZE);
		int sectorY = Math.floorDiv(worldY, NativeLayeredTerrainSector.SIZE);
		WorldMapSectorId requested = new WorldMapSectorId(
			player.getLayeredLocation().getWorldSpace(), level, sectorX, sectorY);
		if (findNativeTerrainSector(owner, requested).isPresent()) {
			throw new IllegalArgumentException(
				"That terrain sector is already allocated or queued.");
		}
		if (!hasAllocatedNeighbor(owner, requested)) {
			throw new IllegalArgumentException(
				"New terrain must share an edge with allocated terrain.");
		}
		addNativeTerrainSector(requested);
		nativeTerrainSceneRevision++;
		return requested;
	}

	public synchronized NativeTerrainProvisionResult
		provisionNativeNavigationTarget(
			Player player, int worldX, int worldY, int level) {
		requireNativeDraftSession(player);
		requireBuilderCoordinate(worldX, worldY);
		WorldSpaceId worldSpace =
			player.getLayeredLocation().getWorldSpace();
		WorldLocation destination = new WorldLocation(
			worldSpace, new WorldCoordinate(worldX, worldY, level));
		if (player.getWorld().getRegionManager()
				.hasNativeLayeredTerrain(destination)) {
			return NativeTerrainProvisionResult.existing(destination);
		}
		if (isSourceLevel(level)) {
			throw new IllegalArgumentException(
				"Accepted source levels cannot be expanded in this Builder draft.");
		}
		NativeLayeredWorldPackage owner =
			player.getWorld().getRegionManager()
				.getNativeLayeredWorldPackage();
		if (owner == null) {
			throw new IllegalStateException(
				"Layered Builder package is unavailable.");
		}
		if (nativeTerrainBaseManifestSha256 == null) {
			nativeTerrainBaseManifestSha256 = owner.getManifestSha256();
		} else if (!nativeTerrainBaseManifestSha256.equals(
				owner.getManifestSha256())) {
			throw new IllegalStateException(
				"Layered terrain draft crossed a package-manifest boundary.");
		}
		boolean createdLevel =
			!owner.declaresLevel(worldSpace, level)
				&& !nativeLevelCreations.containsKey(Integer.valueOf(level));
		if (createdLevel) {
			nativeLevelCreations.put(
				Integer.valueOf(level),
				new NativeLevelCreation(
					level, worldX, worldY,
					defaultLevelName(level), defaultLevelRole(level)));
		}
		int centerSectorX =
			Math.floorDiv(worldX, NativeLayeredTerrainSector.SIZE);
		int centerSectorY =
			Math.floorDiv(worldY, NativeLayeredTerrainSector.SIZE);
		Set<WorldMapSectorId> added =
			new java.util.LinkedHashSet<WorldMapSectorId>();
		for (int sectorX = centerSectorX - 1;
			sectorX <= centerSectorX + 1; sectorX++) {
			for (int sectorY = centerSectorY - 1;
				sectorY <= centerSectorY + 1; sectorY++) {
				WorldMapSectorId identity = new WorldMapSectorId(
					worldSpace, level, sectorX, sectorY);
				if (!findNativeTerrainSector(owner, identity).isPresent()) {
					added.add(identity);
				}
			}
		}
		if (added.isEmpty()) {
			throw new IllegalStateException(
				"Navigation target has no terrain but its work area is already allocated.");
		}
		if (nativeTerrainGrowth.size() + added.size() > 64) {
			if (createdLevel) nativeLevelCreations.remove(Integer.valueOf(level));
			throw new IllegalStateException(
				"Terrain sector-growth draft limit reached.");
		}
		for (WorldMapSectorId identity : added) {
			addNativeTerrainSector(identity);
		}
		for (int x = worldX - 1; x <= worldX + 1; x++) {
			for (int y = worldY - 1; y <= worldY + 1; y++) {
				if (x < 0 || x > 32767 || y < 0 || y > 32767) continue;
				WorldLocation location = new WorldLocation(
					worldSpace, new WorldCoordinate(x, y, level));
				if (!added.contains(WorldMapSectorId.from(location))) continue;
				NativeLayeredTerrainTile base =
					nativeBaseTile(owner, location);
				NativeTileKey key = new NativeTileKey(location);
				NativeLayeredTerrainTile floor =
					new NativeLayeredTerrainTile(
						base.getElevation(), 1, 0, 0, 0, 0, 0);
				nativeTerrainOverlay.put(key, floor);
				refreshNativeDirty(key);
			}
		}
		nativeTerrainSceneRevision++;
		return new NativeTerrainProvisionResult(
			destination, createdLevel, added.size());
	}

	public synchronized GameObject placeNativeScenery(
		Player player, int sceneryId, int x, int y) {
		WorldLocation location = activeNativeSceneryLocation(player, x, y);
		if (player.getWorld().getRegionManager().findInteractionScenery(
				Point.location(x, y), player) != null) {
			throw new IllegalArgumentException(
				"There is already scenery in that spot.");
		}
		NativeSceneryKey key = new NativeSceneryKey(location);
		captureNativeSceneryBase(key, null);
		NativeSceneryState base = nativeSceneryBase.get(key);
		String placementId = base == null
			? nativeSceneryPlacementId(location) : base.placementId;
		GameObject object = new GameObject(
			player.getWorld(),
			new GameObjectLoc(sceneryId, x, y, 0, 0));
		object.setInitialWorldLocation(location);
		NativeLayeredWorldPackage owner = nativeOwner(player, location);
		player.getWorld().getRegionManager().markNativeLayeredPlacement(
			object,
			owner.getPackageId(),
			placementId,
			com.openrsc.server.model.world.region.RegionManager
				.NATIVE_LAYERED_SCENERY_KIND);
		player.getWorld().registerGameObject(object);
		recordNativeScenery(key, NativeSceneryState.from(object));
		return object;
	}

	public synchronized GameObject removeNativeScenery(
		Player player, int x, int y) {
		WorldLocation location = activeNativeSceneryLocation(player, x, y);
		GameObject object =
			player.getWorld().getRegionManager().findInteractionScenery(
				Point.location(x, y), player);
		NativeSceneryState current =
			requireEditableNativeScenery(player, location, object);
		NativeSceneryKey key = new NativeSceneryKey(location);
		captureNativeSceneryBase(key, current);
		player.getWorld().unregisterGameObject(object);
		recordNativeScenery(key, null);
		return object;
	}

	public synchronized GameObject rotateNativeScenery(
		Player player, int x, int y, Integer requestedDirection) {
		WorldLocation location = activeNativeSceneryLocation(player, x, y);
		GameObject object =
			player.getWorld().getRegionManager().findInteractionScenery(
				Point.location(x, y), player);
		NativeSceneryState current =
			requireEditableNativeScenery(player, location, object);
		NativeSceneryKey key = new NativeSceneryKey(location);
		captureNativeSceneryBase(key, current);
		int direction = requestedDirection == null
			? (object.getDirection() + 1) % 8
			: Math.floorMod(requestedDirection.intValue(), 8);
		GameObject replacement = new GameObject(
			player.getWorld(),
			new GameObjectLoc(object.getID(), x, y, direction, 0));
		player.getWorld().replaceGameObject(object, replacement);
		recordNativeScenery(key, NativeSceneryState.from(replacement));
		return replacement;
	}

	public synchronized Npc placeNativeNpc(
		Player player, int npcId, int radius, int x, int y) {
		WorldLocation location = activeNativePlacementLocation(player, x, y);
		if (player.getWorld().getServer().getEntityHandler()
				.getNpcDef(npcId) == null) {
			throw new IllegalArgumentException("Invalid NPC definition ID.");
		}
		if (radius < 0 || radius > 64) {
			throw new IllegalArgumentException("NPC radius must be from 0 to 64.");
		}
		int minX = Math.subtractExact(x, radius);
		int minY = Math.subtractExact(y, radius);
		int maxX = Math.addExact(x, radius);
		int maxY = Math.addExact(y, radius);
		requireNativeNpcTerrainCoverage(player, location, minX, minY, maxX, maxY);
		String placementId = availableNativeNpcPlacementId(player, location);
		NativeNpcKey key = new NativeNpcKey(
			location.getWorldSpace(), placementId);
		captureNativeNpcBase(key, null);
		Npc npc = new Npc(
			player.getWorld(), npcId, x, y, minX, maxX, minY, maxY);
		npc.setWorldLocation(location, true);
		NativeLayeredWorldPackage owner = nativeOwner(player, location);
		player.getWorld().getRegionManager().markNativeLayeredPlacement(
			npc, owner.getPackageId(), placementId,
			RegionManager.NATIVE_LAYERED_NPC_KIND);
		player.getWorld().registerNpc(npc);
		recordNativeNpc(key, NativeNpcState.from(npc));
		return npc;
	}

	public synchronized Npc removeNativeNpc(Player player, Npc npc) {
		int level = player == null ? 0
			: player.getLayeredLocation().getCoordinate().getLevel();
		requireNativeTerrainAuthoring(player, level);
		if (npc == null
			|| !player.getWorld().getRegionManager().isNativeLayeredPlacement(
				npc, RegionManager.NATIVE_LAYERED_NPC_KIND)
			|| npc.getWorldLocation().getCoordinate().getLevel() != level) {
			throw new IllegalArgumentException(
				"Only package-owned NPCs on this Builder-created level are editable.");
		}
		NativeNpcState current = NativeNpcState.from(npc);
		NativeNpcKey key = new NativeNpcKey(
			npc.getWorldLocation().getWorldSpace(), current.placementId);
		captureNativeNpcBase(key, current);
		player.getWorld().unregisterNpc(npc);
		recordNativeNpc(key, null);
		return npc;
	}

	public synchronized WorldEditorLayeredTerrainJournal.SaveResult
		saveNativeTerrainDraft(Player player) throws IOException {
		if (!ownsActiveSession(player)) {
			throw new IllegalStateException(
				"An active world editor session owned by this administrator is required.");
		}
		if (nativeTerrainDirty.isEmpty()
			&& nativeTerrainGrowth.equals(nativeTerrainGrowthSaved)
			&& nativeLevelCreations.equals(nativeLevelCreationsSaved)
			&& nativeSceneryDirty.isEmpty()
			&& nativeNpcDirty.isEmpty()) {
			throw new IllegalStateException("Layered draft is empty.");
		}
		if (nativeTerrainBaseManifestSha256 == null) {
			nativeOwner(player, player.getLayeredLocation());
		}
		List<WorldEditorLayeredTerrainJournal.TileEdit> tiles =
			new ArrayList<WorldEditorLayeredTerrainJournal.TileEdit>(
				nativeTerrainOverlay.size());
		for (Map.Entry<NativeTileKey,NativeLayeredTerrainTile> entry
			: nativeTerrainOverlay.entrySet()) {
			NativeTileKey key = entry.getKey();
			NativeLayeredTerrainTile tile = entry.getValue();
			tiles.add(new WorldEditorLayeredTerrainJournal.TileEdit(
				key.level, key.x, key.y,
				tile.getElevation(), tile.getTexture(), tile.getOverlay(),
				tile.getRoof(), tile.getVerticalWall(),
				tile.getHorizontalWall(), tile.getDiagonalWall()));
		}
		List<WorldEditorLayeredTerrainJournal.SectorGrowth> sectors =
			new ArrayList<WorldEditorLayeredTerrainJournal.SectorGrowth>(
				nativeTerrainGrowth.size());
		for (WorldMapSectorId sector : nativeTerrainGrowth) {
			sectors.add(new WorldEditorLayeredTerrainJournal.SectorGrowth(
				sector.getLevel(), sector.getSectorX(), sector.getSectorY()));
		}
		List<WorldEditorLayeredTerrainJournal.LevelCreation> levels =
			new ArrayList<WorldEditorLayeredTerrainJournal.LevelCreation>(
				nativeLevelCreations.size());
		for (NativeLevelCreation level : nativeLevelCreations.values()) {
			levels.add(new WorldEditorLayeredTerrainJournal.LevelCreation(
				level.level, level.anchorX, level.anchorY,
				level.name, level.role));
		}
		List<WorldEditorLayeredTerrainJournal.SceneryEdit> scenery =
			new ArrayList<WorldEditorLayeredTerrainJournal.SceneryEdit>(
				nativeSceneryOverlay.size());
		for (Map.Entry<NativeSceneryKey,NativeSceneryState> entry
			: nativeSceneryOverlay.entrySet()) {
			NativeSceneryKey key = entry.getKey();
			NativeSceneryState target = entry.getValue();
			NativeSceneryState persisted = target == null
				? nativeSceneryBase.get(key) : target;
			if (persisted == null) {
				throw new IllegalStateException(
					"Layered scenery removal has no persisted identity.");
			}
			scenery.add(new WorldEditorLayeredTerrainJournal.SceneryEdit(
				target == null,
				key.level,
				key.x,
				key.y,
				persisted.placementId,
				persisted.sceneryId,
				persisted.direction));
		}
		List<WorldEditorLayeredTerrainJournal.NpcEdit> npcs =
			new ArrayList<WorldEditorLayeredTerrainJournal.NpcEdit>(
				nativeNpcOverlay.size());
		for (Map.Entry<NativeNpcKey,NativeNpcState> entry
			: nativeNpcOverlay.entrySet()) {
			NativeNpcState target = entry.getValue();
			NativeNpcState persisted = target == null
				? nativeNpcBase.get(entry.getKey()) : target;
			if (persisted == null) {
				throw new IllegalStateException(
					"Layered NPC removal has no persisted identity.");
			}
			npcs.add(new WorldEditorLayeredTerrainJournal.NpcEdit(
				target == null,
				persisted.level,
				persisted.startX,
				persisted.startY,
				persisted.placementId,
				persisted.npcId,
				persisted.minX,
				persisted.minY,
				persisted.maxX,
				persisted.maxY));
		}
		WorldEditStorageContext paths = storage(player);
		Path journal = paths.layeredTerrainDraftJournal();
		paths.validateWorkingAuthoredFile(journal);
		WorldEditorLayeredTerrainJournal.SaveResult saved =
			WorldEditorLayeredTerrainJournal.save(
				journal, nativeTerrainBaseManifestSha256, levels, sectors,
				tiles, scenery, npcs);
		nativeTerrainSaved.clear();
		nativeTerrainSaved.putAll(nativeTerrainOverlay);
		nativeTerrainDirty.clear();
		nativeTerrainGrowthSaved.clear();
		nativeTerrainGrowthSaved.addAll(nativeTerrainGrowth);
		nativeLevelCreationsSaved.clear();
		nativeLevelCreationsSaved.putAll(nativeLevelCreations);
		nativeScenerySaved.clear();
		nativeScenerySaved.putAll(nativeSceneryOverlay);
		nativeSceneryDirty.clear();
		nativeNpcSaved.clear();
		nativeNpcSaved.putAll(nativeNpcOverlay);
		nativeNpcDirty.clear();
		return saved;
	}

	public synchronized int nativeTerrainDraftSize() {
		return nativeTerrainDirty.size();
	}

	public synchronized int nativeTerrainGrowthDraftSize() {
		int result = 0;
		for (WorldMapSectorId sector : nativeTerrainGrowth) {
			if (!nativeTerrainGrowthSaved.contains(sector)) result++;
		}
		return result;
	}

	public synchronized int nativeLevelCreationDraftSize() {
		int result = 0;
		for (Map.Entry<Integer,NativeLevelCreation> entry
			: nativeLevelCreations.entrySet()) {
			if (!entry.getValue().equals(
					nativeLevelCreationsSaved.get(entry.getKey()))) result++;
		}
		return result;
	}

	public synchronized int nativeSceneryDraftSize() {
		return nativeSceneryDirty.size();
	}

	public synchronized int nativeNpcDraftSize() {
		return nativeNpcDirty.size();
	}

	public synchronized int terrainDraftSize(){return terrainDraft.size()+nativeTerrainDraftSize();}
	public synchronized int terrainDraftSectorCount(){java.util.HashSet<String> sectors=new java.util.HashSet<String>();for(WorldEditorTerrainArchive.Snapshot tile:terrainDraft.values())sectors.add(tile.coordinates.plane+":"+tile.coordinates.sectorX+":"+tile.coordinates.sectorY);for(NativeTileKey tile:nativeTerrainDirty)sectors.add(tile.level+":"+Math.floorDiv(tile.x,48)+":"+Math.floorDiv(tile.y,48));for(WorldMapSectorId sector:nativeTerrainGrowth)if(!nativeTerrainGrowthSaved.contains(sector))sectors.add(sector.getLevel()+":"+sector.getSectorX()+":"+sector.getSectorY());return sectors.size();}
	public synchronized WorldEditorTerrainSaveFiles.SaveResult saveTerrainDraft(Player player) throws IOException {
		if(!ownsActiveSession(player))throw new IllegalStateException("An active world editor session owned by this administrator is required.");
		if(terrainDraft.isEmpty())throw new IllegalStateException("Terrain draft is empty.");
		if(!player.getConfig().WANT_CUSTOM_LANDSCAPE)throw new IllegalStateException("Durable terrain saving requires Custom_Landscape.orsc.");
		if(terrainArchivePath==null||terrainBaseSha256==null)throw new IllegalStateException("Terrain archive base revision is unavailable.");
		List<WorldEditorTerrainSaveFiles.TileRecord> records=new ArrayList<WorldEditorTerrainSaveFiles.TileRecord>(terrainDraft.size());
		for(WorldEditorTerrainArchive.Snapshot s:terrainDraft.values())records.add(WorldEditorTerrainSaveFiles.TileRecord.of(s.coordinates.worldX,s.coordinates.worldY,s.coordinates.plane,s.elevation,s.groundTexture,s.groundOverlay,s.roofTexture,s.horizontalWall,s.verticalWall,s.diagonal));
		WorldEditStorageContext paths=storage(player);
		Path clientArchive=paths.clientTerrainArchive();
		paths.validateWorkingAuthoredFile(terrainArchivePath);
		paths.validateWorkingAuthoredFile(clientArchive);
		Path backups=paths.terrainBackupDirectory(terrainArchivePath);closeTerrainArchive();
		try{
			WorldEditorTerrainSaveFiles.SaveResult saved=WorldEditorTerrainSaveFiles.save(terrainArchivePath,clientArchive,backups,terrainBaseSha256,records);
			terrainBaseSha256=saved.resultSha256;terrainArchive=new WorldEditorTerrainArchive(terrainArchivePath.toFile());terrainDraft.clear();return saved;
		}catch(IOException|RuntimeException failure){try{terrainArchive=new WorldEditorTerrainArchive(terrainArchivePath.toFile());}catch(IOException reopen){failure.addSuppressed(reopen);}throw failure;}
	}
	private void requireNativeDraftSession(Player player){
		if(player==null||!player.getConfig().WORLD_BUILDER_MODE
			||!player.getConfig().WORLD_BUILDER_LAYERED_REVIEW_MODE
			||!"spoiled-milk-builder-draft".equals(
				player.getConfig().LAYERED_NATIVE_WORLD_RUNTIME_PROFILE)){
			throw new IllegalStateException(
				"Layered terrain authoring requires an isolated Builder draft.");
		}
		if(!ownsActiveSession(player)){
			throw new IllegalStateException(
				"Layered terrain authoring requires the active Builder editor session.");
		}
	}
	private void requireNativeTerrainAuthoring(Player player,int level){
		requireNativeDraftSession(player);
		if(isSourceLevel(level)){
			throw new IllegalArgumentException(
				"This first terrain-authoring slice is restricted to Builder-created levels.");
		}
		if(player.getLayeredLocation().getCoordinate().getLevel()!=level){
			throw new IllegalArgumentException(
				"Paint terrain on the currently active signed level.");
		}
	}
	private static boolean isSourceLevel(int level){
		return level==-1||level==0||level==1||level==2;
	}
	private static void requireBuilderCoordinate(int x,int y){
		if(x<0||x>32767||y<0||y>32767){
			throw new IllegalArgumentException(
				"Builder coordinates must be from 0 to 32767.");
		}
	}
	private static String defaultLevelName(int level){
		if(level<0)return "Underground level "+Long.toString(-(long)level);
		if(level>0)return "Upper level "+Integer.toString(level);
		return "Surface";
	}
	private static String defaultLevelRole(int level){
		if(level<0)return "underground-level-"+Long.toString(-(long)level);
		if(level>0)return "upper-level-"+Integer.toString(level);
		return "surface";
	}
	private void ensureNativePaintCoverage(
		Player player,int[][] coordinates,int level){
		NativeLayeredWorldPackage owner=
			nativeOwner(player,player.getLayeredLocation());
		WorldSpaceId worldSpace=
			player.getLayeredLocation().getWorldSpace();
		Set<WorldMapSectorId> missing=
			new java.util.LinkedHashSet<WorldMapSectorId>();
		for(int[] coordinate:coordinates){
			requireBuilderCoordinate(coordinate[0],coordinate[1]);
			WorldMapSectorId sector=new WorldMapSectorId(
				worldSpace,level,
				Math.floorDiv(coordinate[0],NativeLayeredTerrainSector.SIZE),
				Math.floorDiv(coordinate[1],NativeLayeredTerrainSector.SIZE));
			if(!findNativeTerrainSector(owner,sector).isPresent())missing.add(sector);
		}
		if(missing.isEmpty())return;
		Set<WorldMapSectorId> pending=
			new java.util.LinkedHashSet<WorldMapSectorId>(missing);
		Set<WorldMapSectorId> accepted=
			new java.util.LinkedHashSet<WorldMapSectorId>();
		boolean progressed;
		do{
			progressed=false;
			java.util.Iterator<WorldMapSectorId> iterator=pending.iterator();
			while(iterator.hasNext()){
				WorldMapSectorId sector=iterator.next();
				if(hasAllocatedNeighbor(owner,sector,accepted)){
					accepted.add(sector);
					iterator.remove();
					progressed=true;
				}
			}
		}while(progressed&&!pending.isEmpty());
		if(!pending.isEmpty()){
			throw new IllegalArgumentException(
				"Terrain painting can expand across an allocated edge, "
					+"but cannot create a detached sector.");
		}
		if(nativeTerrainGrowth.size()+accepted.size()>64){
			throw new IllegalStateException(
				"Terrain sector-growth draft limit reached.");
		}
		for(WorldMapSectorId sector:accepted)addNativeTerrainSector(sector);
		nativeTerrainSceneRevision++;
	}
	private boolean hasAllocatedNeighbor(
		NativeLayeredWorldPackage owner,WorldMapSectorId requested){
		for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
			WorldMapSectorId neighbor=new WorldMapSectorId(
				requested.getWorldSpace(),requested.getLevel(),
				requested.getSectorX()+direction[0],
				requested.getSectorY()+direction[1]);
			if(findNativeTerrainSector(owner,neighbor).isPresent())return true;
		}
		return false;
	}
	private boolean hasAllocatedNeighbor(
		NativeLayeredWorldPackage owner,WorldMapSectorId requested,
		Set<WorldMapSectorId> accepted){
		for(int[] direction:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){
			WorldMapSectorId neighbor=new WorldMapSectorId(
				requested.getWorldSpace(),requested.getLevel(),
				requested.getSectorX()+direction[0],
				requested.getSectorY()+direction[1]);
			if(accepted.contains(neighbor)
				||findNativeTerrainSector(owner,neighbor).isPresent())return true;
		}
		return false;
	}
	private void addNativeTerrainSector(WorldMapSectorId identity){
		if(nativeTerrainGrowth.contains(identity))return;
		if(nativeTerrainGrowth.size()>=64){
			throw new IllegalStateException(
				"Terrain sector-growth draft limit reached.");
		}
		nativeTerrainGrowth.add(identity);
		nativeTerrainLiveSectors.put(
			identity,NativeLayeredTerrainSector.worldBuilderVoid(identity));
	}
	private WorldLocation activeNativeSceneryLocation(
		Player player,int x,int y){
		return activeNativePlacementLocation(player,x,y);
	}
	private WorldLocation activeNativePlacementLocation(
		Player player,int x,int y){
		int level=player==null?0
			:player.getLayeredLocation().getCoordinate().getLevel();
		requireNativeTerrainAuthoring(player,level);
		WorldLocation location=new WorldLocation(
			player.getLayeredLocation().getWorldSpace(),
			new WorldCoordinate(x,y,level));
		NativeLayeredWorldPackage owner=nativeOwner(player,location);
		if(!findNativeTerrainSector(
				owner,WorldMapSectorId.from(location)).isPresent()){
			throw new IllegalArgumentException(
				"Authored content must be placed on allocated package terrain.");
		}
		return location;
	}
	private void requireNativeNpcTerrainCoverage(
		Player player,WorldLocation start,int minX,int minY,int maxX,int maxY){
		NativeLayeredWorldPackage owner=nativeOwner(player,start);
		int level=start.getCoordinate().getLevel();
		for(int sectorX=Math.floorDiv(minX,NativeLayeredTerrainSector.SIZE);
			sectorX<=Math.floorDiv(maxX,NativeLayeredTerrainSector.SIZE);sectorX++){
			for(int sectorY=Math.floorDiv(minY,NativeLayeredTerrainSector.SIZE);
				sectorY<=Math.floorDiv(maxY,NativeLayeredTerrainSector.SIZE);sectorY++){
				WorldMapSectorId sector=new WorldMapSectorId(
					start.getWorldSpace(),level,sectorX,sectorY);
				if(!findNativeTerrainSector(owner,sector).isPresent()){
					throw new IllegalArgumentException(
						"NPC roaming bounds must remain on allocated package terrain.");
				}
			}
		}
	}
	private String availableNativeNpcPlacementId(
		Player player,WorldLocation location){
		String prefix=nativeNpcPlacementPrefix(location);
		for(int slot=0;slot<4096;slot++){
			String candidate=prefix+".s"+slot;
			boolean used=false;
			for(Npc npc:player.getWorld().getNpcs()){
				if(candidate.equals(npc.getAttribute(
					RegionManager.NATIVE_LAYERED_PLACEMENT_ID_ATTRIBUTE,""))){
					used=true;break;
				}
			}
			if(!used)return candidate;
		}
		throw new IllegalStateException(
			"NPC placement slots at this tile are exhausted.");
	}
	private NativeSceneryState requireEditableNativeScenery(
		Player player,WorldLocation location,GameObject object){
		if(object==null)throw new IllegalArgumentException(
			"There is no scenery at those coordinates.");
		NativeLayeredGameObjectIdentity identity=
			object.getLoc().getNativeLayeredGameObjectIdentity();
		if(identity==null
			||!"scenery".equals(identity.getKind())
			||!location.equals(identity.getLocation())
			||!player.getWorld().getRegionManager()
				.isNativeLayeredGameObject(object)){
			throw new IllegalArgumentException(
				"Only package-owned scenery on this Builder-created level is editable.");
		}
		return NativeSceneryState.from(object);
	}
	private void captureNativeSceneryBase(
		NativeSceneryKey key,NativeSceneryState state){
		if(!nativeSceneryBase.containsKey(key)){
			nativeSceneryBase.put(key,state);
		}
	}
	private void recordNativeScenery(
		NativeSceneryKey key,NativeSceneryState state){
		NativeSceneryState base=nativeSceneryBase.get(key);
		if(java.util.Objects.equals(base,state))nativeSceneryOverlay.remove(key);
		else nativeSceneryOverlay.put(key,state);
		NativeSceneryState target=nativeSceneryOverlay.containsKey(key)
			?nativeSceneryOverlay.get(key):base;
		NativeSceneryState saved=nativeScenerySaved.containsKey(key)
			?nativeScenerySaved.get(key):base;
		if(java.util.Objects.equals(target,saved))nativeSceneryDirty.remove(key);
		else nativeSceneryDirty.add(key);
	}
	private static String nativeSceneryPlacementId(WorldLocation location){
		WorldCoordinate coordinate=location.getCoordinate();
		return "spoiled-milk.builder.scenery.l"
			+signedToken(coordinate.getLevel())+".x"
			+signedToken(coordinate.getX())+".y"
			+signedToken(coordinate.getY());
	}
	private static String nativeNpcPlacementPrefix(WorldLocation location){
		WorldCoordinate coordinate=location.getCoordinate();
		return "spoiled-milk.builder.npc.l"
			+signedToken(coordinate.getLevel())+".x"
			+signedToken(coordinate.getX())+".y"
			+signedToken(coordinate.getY());
	}
	private void captureNativeNpcBase(
		NativeNpcKey key,NativeNpcState state){
		if(!nativeNpcBase.containsKey(key))nativeNpcBase.put(key,state);
	}
	private void recordNativeNpc(NativeNpcKey key,NativeNpcState state){
		NativeNpcState base=nativeNpcBase.get(key);
		if(java.util.Objects.equals(base,state))nativeNpcOverlay.remove(key);
		else nativeNpcOverlay.put(key,state);
		NativeNpcState target=nativeNpcOverlay.containsKey(key)
			?nativeNpcOverlay.get(key):base;
		NativeNpcState saved=nativeNpcSaved.containsKey(key)
			?nativeNpcSaved.get(key):base;
		if(java.util.Objects.equals(target,saved))nativeNpcDirty.remove(key);
		else nativeNpcDirty.add(key);
	}
	private static String signedToken(int value){
		return value<0?"m"+Long.toString(-(long)value):"p"+Integer.toString(value);
	}
	private NativeLayeredWorldPackage nativeOwner(Player player,WorldLocation location){
		NativeLayeredWorldPackage owner=player.getWorld().getRegionManager()
			.findNativeLayeredWorldPackage(location)
			.orElseThrow(()->new IllegalArgumentException(
				"Terrain tile is not allocated in the layered working package."));
		String manifest=owner.getManifestSha256();
		if(nativeTerrainBaseManifestSha256==null)nativeTerrainBaseManifestSha256=manifest;
		else if(!nativeTerrainBaseManifestSha256.equals(manifest))throw new IllegalStateException(
			"Layered terrain draft crossed a package-manifest boundary.");
		return owner;
	}
	private NativeLayeredTerrainTile nativeBaseTile(Player player,WorldLocation location){
		return nativeBaseTile(nativeOwner(player,location),location);
	}
	private NativeLayeredTerrainTile nativeBaseTile(
		NativeLayeredWorldPackage owner,WorldLocation location){
		NativeTileKey key=new NativeTileKey(location);
		NativeLayeredTerrainTile known=nativeTerrainBase.get(key);
		if(known!=null)return known;
		Optional<NativeLayeredTerrainSector> sector=findNativeTerrainSector(
			owner,WorldMapSectorId.from(location));
		if(!sector.isPresent())throw new IllegalArgumentException(
			"Terrain tile is not allocated in the layered working package.");
		WorldCoordinate coordinate=location.getCoordinate();
		NativeLayeredTerrainTile source=sector.get().getTile(
			coordinate.getLocalX(),coordinate.getLocalY());
		nativeTerrainBase.put(key,source);
		return source;
	}
	private static NativeLayeredTerrainTile paintNativeTile(
		NativeLayeredTerrainTile current,int fieldMask,int elevation,
		int groundTexture,int groundOverlay,int roofTexture,
		int horizontalWall,int verticalWall,int diagonal){
		return new NativeLayeredTerrainTile(
			(fieldMask&1)!=0?elevation:current.getElevation(),
			(fieldMask&2)!=0?groundTexture:current.getTexture(),
			(fieldMask&4)!=0?groundOverlay:current.getOverlay(),
			(fieldMask&8)!=0?roofTexture:current.getRoof(),
			(fieldMask&32)!=0?verticalWall:current.getVerticalWall(),
			(fieldMask&16)!=0?horizontalWall:current.getHorizontalWall(),
			(fieldMask&64)!=0?diagonal:current.getDiagonalWall());
	}
	private void refreshNativeDirty(NativeTileKey key){
		NativeLayeredTerrainTile current=nativeTerrainOverlay.get(key);
		NativeLayeredTerrainTile saved=nativeTerrainSaved.get(key);
		if(current==null?saved==null:current.equals(saved))nativeTerrainDirty.remove(key);
		else nativeTerrainDirty.add(key);
	}
	private static void writeNativeTile(byte[] bytes,int offset,NativeLayeredTerrainTile tile){
		bytes[offset]=(byte)tile.getElevation();bytes[offset+1]=(byte)tile.getTexture();
		bytes[offset+2]=(byte)tile.getOverlay();bytes[offset+3]=(byte)tile.getRoof();
		bytes[offset+4]=(byte)tile.getVerticalWall();bytes[offset+5]=(byte)tile.getHorizontalWall();
		int diagonal=tile.getDiagonalWall();bytes[offset+6]=(byte)(diagonal>>>24);
		bytes[offset+7]=(byte)(diagonal>>>16);bytes[offset+8]=(byte)(diagonal>>>8);
		bytes[offset+9]=(byte)diagonal;
	}
	private static String sha256(byte[] bytes){
		try{
			MessageDigest digest=MessageDigest.getInstance("SHA-256");
			byte[] hash=digest.digest(bytes);StringBuilder value=new StringBuilder(64);
			for(byte item:hash)value.append(String.format("%02x",item&0xff));
			return value.toString();
		}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
	}
	private static void validateTerrainPaint(int fieldMask,int elevation,int groundTexture,int groundOverlay,int roofTexture,int horizontalWall,int verticalWall){
		if(fieldMask<=0||(fieldMask&~127)!=0)throw new IllegalArgumentException("Select at least one supported terrain field.");
		if(!rawByte(elevation)||!rawByte(groundTexture)||!rawByte(groundOverlay)||!rawByte(roofTexture)
			||!rawByte(horizontalWall)||!rawByte(verticalWall))throw new IllegalArgumentException("Terrain byte values must be from 0 to 255.");
	}
	private WorldEditorTerrainArchive.Snapshot inspectArchivedTerrain(Player player, int x, int y, int plane) throws IOException {
		if (terrainArchive == null) {
			terrainArchivePath=storage(player).terrainArchive(player.getConfig());terrainBaseSha256=WorldEditorTerrainSaveFiles.sha256(terrainArchivePath);
			terrainArchive = new WorldEditorTerrainArchive(terrainArchivePath.toFile());
		}
		return terrainArchive.inspect(x, y, plane);
	}
	private void closeTerrainArchive() throws IOException {if(terrainArchive!=null){terrainArchive.close();terrainArchive=null;}}
	private WorldEditStorageContext storage(Player player) throws IOException {
		return storage == null ? WorldEditStorageContext.create(player.getConfig()) : storage;
	}
	private static String terrainKey(int x,int y,int plane){return plane+":"+x+":"+y;}
	private static boolean rawByte(int value){return value>=0&&value<=255;}

	private static final class Session { final long id, ownerHash; int nextSequence=1; Session(long i,long o){id=i;ownerHash=o;} }
	private static final class NativeTileKey {
		final WorldSpaceId worldSpace;final int level,x,y;
		NativeTileKey(WorldLocation location){worldSpace=location.getWorldSpace();WorldCoordinate coordinate=location.getCoordinate();level=coordinate.getLevel();x=coordinate.getX();y=coordinate.getY();}
		@Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NativeTileKey))return false;NativeTileKey key=(NativeTileKey)other;return level==key.level&&x==key.x&&y==key.y&&worldSpace.equals(key.worldSpace);}
		@Override public int hashCode(){int result=worldSpace.hashCode();result=31*result+level;result=31*result+x;return 31*result+y;}
	}
	private static final class NativeSceneryKey {
		final WorldSpaceId worldSpace;final int level,x,y;
		NativeSceneryKey(WorldLocation location){worldSpace=location.getWorldSpace();WorldCoordinate coordinate=location.getCoordinate();level=coordinate.getLevel();x=coordinate.getX();y=coordinate.getY();}
		@Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NativeSceneryKey))return false;NativeSceneryKey key=(NativeSceneryKey)other;return level==key.level&&x==key.x&&y==key.y&&worldSpace.equals(key.worldSpace);}
		@Override public int hashCode(){int result=worldSpace.hashCode();result=31*result+level;result=31*result+x;return 31*result+y;}
	}
	private static final class NativeSceneryState {
		final String placementId;final int sceneryId,direction;
		NativeSceneryState(String placementId,int sceneryId,int direction){this.placementId=placementId;this.sceneryId=sceneryId;this.direction=direction;}
		static NativeSceneryState from(GameObject object){
			NativeLayeredGameObjectIdentity identity=object.getLoc()
				.getNativeLayeredGameObjectIdentity();
			if(identity==null)throw new IllegalArgumentException(
				"Native layered scenery identity is unavailable.");
			return new NativeSceneryState(
				identity.getPlacementId(),object.getID(),object.getDirection());
		}
		@Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NativeSceneryState))return false;NativeSceneryState state=(NativeSceneryState)other;return sceneryId==state.sceneryId&&direction==state.direction&&placementId.equals(state.placementId);}
		@Override public int hashCode(){int result=placementId.hashCode();result=31*result+sceneryId;return 31*result+direction;}
	}
	private static final class NativeNpcKey {
		final WorldSpaceId worldSpace;final String placementId;
		NativeNpcKey(WorldSpaceId worldSpace,String placementId){
			this.worldSpace=worldSpace;this.placementId=placementId;
		}
		@Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NativeNpcKey))return false;NativeNpcKey key=(NativeNpcKey)other;return worldSpace.equals(key.worldSpace)&&placementId.equals(key.placementId);}
		@Override public int hashCode(){return 31*worldSpace.hashCode()+placementId.hashCode();}
	}
	private static final class NativeNpcState {
		final String placementId;final int level,npcId,startX,startY,minX,minY,maxX,maxY;
		NativeNpcState(String placementId,int level,int npcId,int startX,int startY,int minX,int minY,int maxX,int maxY){
			this.placementId=placementId;this.level=level;this.npcId=npcId;
			this.startX=startX;this.startY=startY;this.minX=minX;this.minY=minY;this.maxX=maxX;this.maxY=maxY;
		}
		static NativeNpcState from(Npc npc){
			String placementId=npc.getAttribute(
				RegionManager.NATIVE_LAYERED_PLACEMENT_ID_ATTRIBUTE,"");
			if(placementId.isEmpty())throw new IllegalArgumentException(
				"Native layered NPC identity is unavailable.");
			NPCLoc loc=npc.getLoc();
			return new NativeNpcState(
				placementId,npc.getWorldLocation().getCoordinate().getLevel(),
				npc.getID(),loc.startX,loc.startY,loc.minX,loc.minY,loc.maxX,loc.maxY);
		}
		@Override public boolean equals(Object other){if(this==other)return true;if(!(other instanceof NativeNpcState))return false;NativeNpcState state=(NativeNpcState)other;return level==state.level&&npcId==state.npcId&&startX==state.startX&&startY==state.startY&&minX==state.minX&&minY==state.minY&&maxX==state.maxX&&maxY==state.maxY&&placementId.equals(state.placementId);}
		@Override public int hashCode(){int result=placementId.hashCode();result=31*result+level;result=31*result+npcId;result=31*result+startX;result=31*result+startY;result=31*result+minX;result=31*result+minY;result=31*result+maxX;return 31*result+maxY;}
	}
	private static final class NativeLevelCreation {
		final int level,anchorX,anchorY;final String name,role;
		NativeLevelCreation(
			int level,int anchorX,int anchorY,String name,String role){
			this.level=level;this.anchorX=anchorX;this.anchorY=anchorY;
			this.name=name;this.role=role;
		}
		@Override public boolean equals(Object other){
			if(this==other)return true;
			if(!(other instanceof NativeLevelCreation))return false;
			NativeLevelCreation value=(NativeLevelCreation)other;
			return level==value.level&&anchorX==value.anchorX
				&&anchorY==value.anchorY&&name.equals(value.name)
				&&role.equals(value.role);
		}
		@Override public int hashCode(){
			int result=level;result=31*result+anchorX;result=31*result+anchorY;
			result=31*result+name.hashCode();return 31*result+role.hashCode();
		}
	}
	public static final class NativeTerrainProvisionResult {
		public final WorldLocation destination;
		public final boolean createdLevel;
		public final int allocatedSectorCount;
		private NativeTerrainProvisionResult(
			WorldLocation destination,boolean createdLevel,
			int allocatedSectorCount){
			this.destination=destination;this.createdLevel=createdLevel;
			this.allocatedSectorCount=allocatedSectorCount;
		}
		private static NativeTerrainProvisionResult existing(
			WorldLocation destination){
			return new NativeTerrainProvisionResult(destination,false,0);
		}
		public boolean allocated(){return allocatedSectorCount>0;}
	}
	public static final class NativeTerrainSnapshot {
		public final WorldLocation location;public final NativeLayeredTerrainTile tile;
		private NativeTerrainSnapshot(WorldLocation location,NativeLayeredTerrainTile tile){this.location=location;this.tile=tile;}
	}
	public static final class NativeTerrainStrokeResult {
		public final List<NativeTerrainSnapshot> before,after;
		private NativeTerrainStrokeResult(List<NativeTerrainSnapshot> before,List<NativeTerrainSnapshot> after){this.before=before;this.after=after;}
	}
	public static final class TerrainStrokeResult {
		public final List<WorldEditorTerrainArchive.Snapshot> before,after;
		private TerrainStrokeResult(List<WorldEditorTerrainArchive.Snapshot> b,List<WorldEditorTerrainArchive.Snapshot> a){before=b;after=a;}
	}
	public static final class OpenResult {
		public final boolean opened; public final long sessionId; public final int nextSequence; public final String message;
		private OpenResult(boolean o,long i,int s,String m){opened=o;sessionId=i;nextSequence=s;message=m;}
		static OpenResult opened(long i,int s){return new OpenResult(true,i,s,"");}
		static OpenResult denied(String m){return new OpenResult(false,0,0,m);}
	}
	public static final class Validation {
		public final boolean accepted; public final int nextSequence; public final String message;
		private Validation(boolean a,int n,String m){accepted=a;nextSequence=n;message=m;}
		static Validation accepted(int n){return new Validation(true,n,"");}
		static Validation denied(String m){return new Validation(false,0,m);}
	}
}
