#!/usr/bin/env python3
"""Guard the first server sync modernization fixes."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def extract_between(source: str, start: str, end: str) -> str:
    start_index = source.find(start)
    require(start_index >= 0, f"Missing block start: {start}")
    end_index = source.find(end, start_index)
    require(end_index > start_index, f"Missing block end: {end}")
    return source[start_index:end_index]


def main() -> None:
    known_players = (
        ROOT
        / "server/src/com/openrsc/server/net/rsc/handlers/KnownPlayersHandler.java"
    ).read_text(encoding="utf-8")
    updater = (
        ROOT
        / "server/src/com/openrsc/server/GameStateUpdater.java"
    ).read_text(encoding="utf-8")
    player = (
        ROOT
        / "server/src/com/openrsc/server/model/entity/player/Player.java"
    ).read_text(encoding="utf-8")
    server = (
        ROOT
        / "server/src/com/openrsc/server/Server.java"
    ).read_text(encoding="utf-8")
    profiling = (
        ROOT
        / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
    ).read_text(encoding="utf-8")
    region_manager = (
        ROOT
        / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
    ).read_text(encoding="utf-8")
    region = (
        ROOT
        / "server/src/com/openrsc/server/model/world/region/Region.java"
    ).read_text(encoding="utf-8")
    visibility_snapshot = (
        ROOT
        / "server/src/com/openrsc/server/model/world/region/VisibilitySnapshot.java"
    ).read_text(encoding="utf-8")
    server_config = (
        ROOT
        / "server/src/com/openrsc/server/ServerConfiguration.java"
    ).read_text(encoding="utf-8")
    payload_validator = (
        ROOT
        / "server/src/com/openrsc/server/net/rsc/PayloadValidator.java"
    ).read_text(encoding="utf-8")
    payload_custom_generator = (
        ROOT
        / "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java"
    ).read_text(encoding="utf-8")
    opcode_out = (
        ROOT
        / "server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java"
    ).read_text(encoding="utf-8")
    scene_baseline_struct = (
        ROOT
        / "server/src/com/openrsc/server/net/rsc/struct/outgoing/SceneBaselineStruct.java"
    ).read_text(encoding="utf-8")
    client_packet_handler = (
        ROOT
        / "Client_Base/src/orsc/PacketHandler.java"
    ).read_text(encoding="utf-8")
    client_mudclient = (
        ROOT
        / "Client_Base/src/orsc/mudclient.java"
    ).read_text(encoding="utf-8")
    movement_snapshot_stage = (
        ROOT
        / "Client_Base/src/orsc/MovementSnapshotStage.java"
    ).read_text(encoding="utf-8")
    movement_snapshot_diagnostics = (
        ROOT
        / "Client_Base/src/orsc/MovementSnapshotDiagnostics.java"
    ).read_text(encoding="utf-8")
    scene_baseline_state = (
        ROOT
        / "Client_Base/src/orsc/SceneBaselineState.java"
    ).read_text(encoding="utf-8")
    client_applet = (
        ROOT
        / "PC_Client/src/orsc/ORSCApplet.java"
    ).read_text(encoding="utf-8")
    benchmark_script = (
        ROOT
        / "tools/benchmarks/benchmark-foundation.sh"
    ).read_text(encoding="utf-8")
    myworld_conf = (ROOT / "server/myworld.conf").read_text(encoding="utf-8")
    myworld_host_conf = (ROOT / "server/myworld-host.conf").read_text(
        encoding="utf-8"
    )

    require(
        "player.knownPlayerPids[i] = payload.playerServerIndex[i];" in known_players,
        "known-player PID cache should use server indexes",
    )
    require(
        "player.knownPlayerPids[i] = payload.playerServerAppearanceId[i];"
        not in known_players,
        "known-player PID cache must not use appearance ids",
    )

    appearance_block = extract_between(
        updater,
        "while ((playerNeedingAppearanceUpdate = playersNeedingAppearanceUpdate.poll()) != null)",
        "if (playerNeedingAppearanceUpdate.getPossessing() != null)",
    )
    require(
        "playerNeedingAppearanceUpdate.getAppearanceID()" in appearance_block,
        "appearance update should send the observed player's appearance id",
    )
    require(
        "updatesMain.add((short) player.getAppearanceID());" not in appearance_block,
        "appearance update must not send the viewer's appearance id",
    )

    normal_update_block = extract_between(
        updater,
        "private void sendNormalUpdatePackets(final Player player, final boolean allowTickSnapshotCache)",
        "private void sendWorldTimeIfNeeded",
    )
    require(
        "final VisibilitySnapshot packetVisibility = buildPacketVisibilitySnapshot(player, allowTickSnapshotCache);" in normal_update_block,
        "normal update should build one packet visibility snapshot",
    )
    require(
        "final Collection<Player> visiblePlayers = packetVisibility.getPlayers();" in normal_update_block,
        "normal update should use packet visibility players",
    )
    require(
        "final Collection<Npc> visibleNpcs = packetVisibility.getNpcs();" in normal_update_block,
        "normal update should use packet visibility NPCs",
    )
    require(
        "final Collection<GameObject> visibleSceneryObjects = packetVisibility.getSceneryObjects();" in normal_update_block,
        "normal update should use packet visibility scenery objects",
    )
    require(
        "final Collection<GameObject> visibleWallObjects = packetVisibility.getWallObjects();" in normal_update_block,
        "normal update should use packet visibility wall objects",
    )
    require(
        "recordVisibilityShadowSnapshot(player, packetVisibility, allowTickSnapshotCache);" in normal_update_block,
        "normal update should run shadow visibility comparison after packet visibility is built",
    )
    require(
        "final StaticScenePresentationSnapshot staticPresentation =" in normal_update_block
        and "sendSceneBaselineIfEnabled(\n"
        "\t\t\tplayer,\n"
        "\t\t\tsceneryChanged[0],\n"
        "\t\t\twallsChanged[0],\n"
        "\t\t\tgroundItemsChanged[0],\n"
        "\t\t\tstaticPresentation);" in normal_update_block,
        "normal update should offer the default-off custom static-scene baseline consumer after object change checks",
    )
    require(
        "final boolean skipStaticSceneScan = canSkipStaticSceneScan(player, packetVisibility);" in normal_update_block
        and "final boolean sendLegacyStaticScenePackets = shouldSendLegacyStaticScenePackets(player, skipStaticSceneScan);" in normal_update_block
        and "if (skipStaticSceneScan)" in normal_update_block
        and "storeStaticSceneScanKey(player, packetVisibility);" in normal_update_block,
        "normal update should skip only unchanged static scene scans after baseline takeover",
    )
    require(
        "recordUpdatePlayers(() -> updatePlayers(player, visiblePlayers));" in normal_update_block,
        "player update should use the prefetched visible player collection",
    )
    require(
        "recordUpdateNpcs(() -> updateNpcs(player, visibleNpcs));" in normal_update_block,
        "NPC update should use the prefetched visible NPC collection",
    )
    require(
        "recordUpdateGameObjects(() -> sceneryChanged[0] = updateGameObjects(" in normal_update_block
        and "player, visibleSceneryObjects, sendLegacyStaticScenePackets));" in normal_update_block,
        "scenery update should use the prefetched scenery collection and expose its change flag",
    )
    require(
        "recordUpdateWallObjects(() -> wallsChanged[0] = updateWallObjects(" in normal_update_block
        and "player, visibleWallObjects, sendLegacyStaticScenePackets));" in normal_update_block,
        "wall update should use the prefetched wall collection and expose its change flag",
    )

    packet_visibility_block = extract_between(
        updater,
        "private VisibilitySnapshot buildPacketVisibilitySnapshot(final Player player, final boolean allowTickSnapshotCache)",
        "private VisibilitySnapshot buildLegacyVisibilitySnapshot",
    )
    require(
        "useVisibilitySnapshotInput(player)" in packet_visibility_block,
        "packet visibility should be able to use snapshot input",
    )
    require(
        "VisibilitySnapshotMode.SNAPSHOT" in packet_visibility_block,
        "packet visibility should use snapshot mode when gated on",
    )
    require(
        "VisibilitySnapshotMode.LEGACY" in packet_visibility_block,
        "packet visibility should keep legacy fallback mode",
    )
    require(
        "allowTickSnapshotCache" in packet_visibility_block,
        "packet visibility should pass the tick-cache safety gate",
    )
    require(
        "recordVisibilitySnapshotMetrics(snapshot" in packet_visibility_block,
        "packet visibility should record telemetry",
    )
    legacy_visibility_block = extract_between(
        updater,
        "private VisibilitySnapshot buildLegacyVisibilitySnapshot(final Player player)",
        "private boolean useVisibilitySnapshotInput",
    )
    for snippet in (
        "player.getViewArea().getPlayersInView()",
        "player.getViewArea().getNpcsInView()",
        "player.getViewArea().getGameObjectsInView()",
        "player.getViewArea().getItemsInView()",
    ):
        require(snippet in legacy_visibility_block, f"legacy visibility snapshot missing {snippet}")
    visibility_input_gate = extract_between(
        updater,
        "private boolean useVisibilitySnapshotInput",
        "private void recordVisibilitySnapshotMetrics",
    )
    require(
        "player.isUsingCustomClient()" in visibility_input_gate
        and "WANT_SYNC_VISIBILITY_SNAPSHOT_INPUT" in visibility_input_gate
        and "WANT_SYNC_SCENE_BASELINE" in visibility_input_gate
        and "nativeTerrainSymmetricResidencyEnabled()" in visibility_input_gate,
        "snapshot packet input should be custom-client gated and mandatory for the resident scene contract",
    )
    visibility_metrics_block = extract_between(
        updater,
        "private void recordVisibilitySnapshotMetrics",
        "private void recordVisibilityShadowSnapshot",
    )
    require(
        "addVisibilitySnapshotMetrics(" in visibility_metrics_block,
        "packet visibility should record visibility snapshot telemetry",
    )
    require(
        "packetVisibility.getSceneryCount()" in visibility_metrics_block
        and "packetVisibility.getWallObjectCount()" in visibility_metrics_block,
        "packet visibility metrics should reuse precomputed object counts",
    )
    shadow_block = extract_between(
        updater,
        "private void recordVisibilityShadowSnapshot(",
        "private boolean sameIdentityCollection",
    )
    require(
        "if (!getServer().getConfig().WANT_SYNC_VISIBILITY_SHADOW)" in shadow_block,
        "shadow visibility snapshot must be config gated",
    )
    require(
        "useVisibilitySnapshotInput(player)" in shadow_block,
        "shadow visibility should reverse the comparison when snapshot input is enabled",
    )
    require(
        "VisibilitySnapshotMode.LEGACY" in shadow_block
        and "VisibilitySnapshotMode.SNAPSHOT" in shadow_block,
        "shadow visibility should be able to compare snapshot input against legacy visibility",
    )
    require(
        "allowTickSnapshotCache" in shadow_block,
        "shadow visibility should use the same tick-cache safety gate as packet visibility",
    )
    require(
        "addVisibilityShadowMetrics(" in shadow_block,
        "shadow visibility snapshot should record parity telemetry",
    )
    require(
        "sameIdentityCollection(packetVisibility.getPlayers(), comparisonSnapshot.getPlayers())" in shadow_block,
        "shadow visibility should compare player identity sets",
    )
    require(
        "sameIdentityCollection(packetVisibility.getNpcs(), comparisonSnapshot.getNpcs())" in shadow_block,
        "shadow visibility should compare NPC identity sets",
    )
    update_npcs_block = extract_between(
        updater,
        "protected void updateNpcs(final Player playerToUpdate, final Collection<Npc> visibleNpcs)",
        "protected void updatePlayers",
    )
    require(
        "playerToUpdate.getViewArea().getNpcsInView()" not in update_npcs_block,
        "updateNpcs should not rescan visible NPCs after prefetch",
    )
    update_players_block = extract_between(
        updater,
        "protected void updatePlayers(final Player playerToUpdate, final Collection<Player> visiblePlayers)",
        "public void updateNpcAppearances",
    )
    require(
        "playerToUpdate.getViewArea().getPlayersInView()" not in update_players_block,
        "updatePlayers should not rescan visible players after prefetch",
    )

    outgoing_block = extract_between(
        player,
        "public long processOutgoingPackets()",
        "public void removeSkull()",
    )
    require(
        "channel.write(outgoing);" in outgoing_block,
        "queued outgoing packets should be written before a shared flush",
    )
    require(
        "channel.flush();" in outgoing_block,
        "queued outgoing packets should flush once after writes",
    )
    require(
        "channel.writeAndFlush(outgoing)" not in outgoing_block,
        "queued outgoing packets should not flush each individual packet",
    )
    require(
        "addOutgoingPacketBytes(outgoing.getID(), outgoing.getLength())" in outgoing_block,
        "queued outgoing packets should record payload byte telemetry",
    )

    for snippet in (
        "private final Map<Integer, Long> outgoingPayloadBytesPerPacketOpcode",
        "private long lastOutgoingPayloadBytes = 0;",
        "private long benchmarkOutgoingPayloadBytesTotal = 0;",
        "public Map<Integer, Long> getOutgoingPayloadBytesPerPacketOpcode()",
        "public long getLastOutgoingPayloadBytes()",
        "public void addOutgoingPacketBytes",
        "benchmarkOutgoingPayloadBytesTotal += getLastOutgoingPayloadBytes();",
        '" avgOutgoingPayloadBytes="',
        "this.lastOutgoingPayloadBytes = 0;",
        "private long lastVisibilitySnapshotDuration = 0;",
        "private long lastVisibilitySnapshotSamples = 0;",
        "private long lastVisiblePlayersTotal = 0;",
        "private long lastVisibilityRegionCacheRequests = 0;",
        "private long lastVisibilityObjectCacheRequests = 0;",
        "private long lastVisibilityObjectCacheEntriesCleared = 0;",
        "private long lastVisibilityObjectSnapshotCacheRequests = 0;",
        "private long lastVisibilityObjectSnapshotCacheHits = 0;",
        "private long lastVisibilityObjectSnapshotCacheMisses = 0;",
        "private long benchmarkVisibilitySnapshotTotal = 0;",
        "private long benchmarkVisibilityRegionCacheRequests = 0;",
        "private long benchmarkVisibilityObjectCacheRequests = 0;",
        "private long benchmarkVisibilityObjectCacheEntriesCleared = 0;",
        "private long benchmarkVisibilityObjectSnapshotCacheRequests = 0;",
        "public long getLastVisibilitySnapshotDuration()",
        "public long getLastVisibilitySnapshotSamples()",
        "public long getLastVisiblePlayersTotal()",
        "public long getLastVisibilityRegionCacheRequests()",
        "public long getLastVisibilityObjectCacheRequests()",
        "public long getLastVisibilityObjectCacheEntriesCleared()",
        "public long getLastVisibilityObjectSnapshotCacheRequests()",
        "public long getLastVisibilityObjectSnapshotCacheHits()",
        "public long getLastVisibilityObjectSnapshotCacheMisses()",
        "public synchronized void addVisibilitySnapshotMetrics",
        "public synchronized void recordVisibilityRegionCacheAccess",
        "public synchronized void recordVisibilityObjectCacheAccess",
        "public synchronized void recordVisibilityObjectSnapshotCacheAccess",
        "public synchronized void recordVisibilityObjectCacheClear",
        "this.lastVisibilityObjectCacheEntriesCleared += entriesCleared;",
        "benchmarkVisibilitySnapshotTotal += getLastVisibilitySnapshotDuration();",
        "benchmarkVisibilityRegionCacheRequests += getLastVisibilityRegionCacheRequests();",
        "benchmarkVisibilityObjectCacheRequests += getLastVisibilityObjectCacheRequests();",
        "benchmarkVisibilityObjectCacheEntriesCleared += getLastVisibilityObjectCacheEntriesCleared();",
        "benchmarkVisibilityObjectSnapshotCacheRequests += getLastVisibilityObjectSnapshotCacheRequests();",
        '" avgVisibilitySnapshotMs="',
        '" avgVisiblePlayers="',
        "visibilityRegionCacheRequests=",
        "visibilityObjectCacheRequests=",
        "visibilityObjectCacheEntriesCleared=",
        "visibilityObjectSnapshotCacheRequests=",
        "visibilityObjectSnapshotCacheHits=",
        "visibilityObjectSnapshotCacheMisses=",
        "this.lastVisibilitySnapshotDuration = 0;",
        "this.lastVisibilityRegionCacheRequests = 0;",
        "this.lastVisibilityObjectCacheRequests = 0;",
        "this.lastVisibilityObjectCacheEntriesCleared = 0;",
        "this.lastVisibilityObjectSnapshotCacheRequests = 0;",
        "private long lastVisibilityShadowDuration = 0;",
        "private long lastVisibilityShadowMismatchSamples = 0;",
        "private long benchmarkVisibilityShadowTotal = 0;",
        "public long getLastVisibilityShadowDuration()",
        "public long getLastVisibilityShadowMismatchSamples()",
        "public synchronized void addVisibilityShadowMetrics",
        "benchmarkVisibilityShadowTotal += getLastVisibilityShadowDuration();",
        '" avgVisibilityShadowMs="',
        '" visibilityShadowMismatches="',
        "this.lastVisibilityShadowDuration = 0;",
        "private long lastSceneBaselinePackets = 0;",
        "private long lastSceneBaselinePages = 0;",
        "private long lastSceneBaselineRecords = 0;",
        "private long lastSceneBaselinePayloadBytes = 0;",
        "private long lastMovementSnapshotPackets = 0;",
        "private long lastMovementSnapshotRecords = 0;",
        "private long lastMovementSnapshotPayloadBytes = 0;",
        "private long lastSuppressedLegacySceneryPackets = 0;",
        "private long lastSuppressedLegacySceneryRecords = 0;",
        "private long lastSuppressedLegacyWallPackets = 0;",
        "private long lastSuppressedLegacyWallRecords = 0;",
        "private long benchmarkSceneBaselinePackets = 0;",
        "private long benchmarkSceneBaselinePages = 0;",
        "private long benchmarkSceneBaselineRecords = 0;",
        "private long benchmarkSceneBaselinePayloadBytes = 0;",
        "private long benchmarkMovementSnapshotPackets = 0;",
        "private long benchmarkMovementSnapshotRecords = 0;",
        "private long benchmarkMovementSnapshotPayloadBytes = 0;",
        "private long benchmarkSuppressedLegacySceneryPackets = 0;",
        "private long benchmarkSuppressedLegacySceneryRecords = 0;",
        "private long benchmarkSuppressedLegacyWallPackets = 0;",
        "private long benchmarkSuppressedLegacyWallRecords = 0;",
        "public synchronized void addSceneBaselineMetrics(final int pageRecords, final int payloadBytes)",
        "public synchronized void addMovementSnapshotMetrics(final int records, final int payloadBytes)",
        "public synchronized void addSuppressedLegacyStaticSceneMetrics(final boolean wallPacket, final int records)",
        "benchmarkSceneBaselinePackets += lastSceneBaselinePackets;",
        "benchmarkSceneBaselinePayloadBytes += lastSceneBaselinePayloadBytes;",
        "if (benchmarkTargetTicks > 0 && getCurrentTick() > benchmarkWarmupTicks)",
        "this.benchmarkMovementSnapshotPackets++;",
        "this.benchmarkMovementSnapshotPayloadBytes += payloadBytes;",
        "benchmarkSuppressedLegacySceneryPackets += lastSuppressedLegacySceneryPackets;",
        "benchmarkSuppressedLegacyWallPackets += lastSuppressedLegacyWallPackets;",
        '" sceneBaselinePackets="',
        '" sceneBaselinePages="',
        '" sceneBaselineRecords="',
        '" sceneBaselinePayloadBytes="',
        '" movementSnapshotPackets="',
        '" movementSnapshotRecords="',
        '" movementSnapshotPayloadBytes="',
        '" suppressedLegacySceneryPackets="',
        '" suppressedLegacySceneryRecords="',
        '" suppressedLegacyWallPackets="',
        '" suppressedLegacyWallRecords="',
        "this.lastSceneBaselinePackets = 0;",
        "this.lastSceneBaselinePages = 0;",
        "this.lastSceneBaselineRecords = 0;",
        "this.lastSceneBaselinePayloadBytes = 0;",
        "this.lastMovementSnapshotPackets = 0;",
        "this.lastMovementSnapshotRecords = 0;",
        "this.lastMovementSnapshotPayloadBytes = 0;",
        "this.lastSuppressedLegacySceneryPackets = 0;",
        "this.lastSuppressedLegacyWallPackets = 0;",
    ):
        require(snippet in server, f"server missing outgoing payload byte telemetry: {snippet}")

    for snippet in (
        "=== Outgoing Packets ===",
        "getOutgoingPayloadBytesPerPacketOpcode()",
        '" payload bytes"',
        "=== Visibility Snapshot ===",
        "getLastVisibilitySnapshotSamples()",
        "Average visible: players=",
        "Max visible: players=",
        "Cache: region requests=",
        "entriesCleared=",
        "objectSnapshot requests=",
        "NPC idle throttle skipped:",
        "Shadow snapshot: time=",
        "getLastVisibilityShadowMismatchSamples()",
    ):
        require(snippet in profiling, f"profiling output missing outgoing payload bytes: {snippet}")

    for snippet in (
        "public VisibilitySnapshot buildVisibilitySnapshot(final Mob entity)",
        "private final ConcurrentHashMap<Long, List<Region>> visibleRegionWindowCache;",
        "private final ConcurrentHashMap<Long, List<GameObject>> visibleObjectWindowCache;",
        "private final ConcurrentHashMap<Long, Set<Long>> visibleObjectWindowKeysByRegion;",
        "private final ConcurrentHashMap<Long, VisibleObjectSnapshot> visibleObjectSnapshotCache;",
        "private final ConcurrentHashMap<Long, Set<Long>> visibleObjectSnapshotKeysByRegion;",
        "private final AtomicLong visibleObjectSnapshotSequence;",
        "this.visibleRegionWindowCache = new ConcurrentHashMap<>();",
        "this.visibleObjectWindowCache = new ConcurrentHashMap<>();",
        "this.visibleObjectWindowKeysByRegion = new ConcurrentHashMap<>();",
        "this.visibleObjectSnapshotCache = new ConcurrentHashMap<>();",
        "this.visibleObjectSnapshotKeysByRegion = new ConcurrentHashMap<>();",
        "this.visibleObjectSnapshotSequence = new AtomicLong();",
        "visibleRegionWindowCache.putIfAbsent",
        "visibleObjectWindowCache.putIfAbsent",
        "visibleObjectSnapshotCache.putIfAbsent",
        "indexCacheKeyByRegion(visibleObjectWindowKeysByRegion, cacheKey, objectRegions);",
        "indexCacheKeyByRegion(visibleObjectSnapshotKeysByRegion, cacheKey, objectRegions);",
        "private void indexCacheKeyByRegion",
        "recordVisibilityRegionCacheAccess",
        "recordVisibilityObjectCacheAccess",
        "recordVisibilityObjectCacheClear",
        "private List<Region> buildVisibleRegionWindow",
        "private VisibleObjectSnapshot getVisibleObjectSnapshot",
        "private VisibleObjectSnapshot buildVisibleObjectSnapshot",
        "recordVisibilityObjectSnapshotCacheAccess(true)",
        "recordVisibilityObjectSnapshotCacheAccess(previous != null)",
        "private List<GameObject> getVisibleObjectWindow",
        "private List<GameObject> buildVisibleObjectWindow",
        "public void invalidateVisibleObjectWindowCache()",
        "public void invalidateVisibleObjectWindowCache(final Region changedRegion)",
        "visibleObjectWindowCache.remove(affectedWindowKey)",
        "visibleObjectSnapshotCache.remove(affectedSnapshotKey)",
        "return Collections.unmodifiableList(visible);",
        "private long packRegionWindowKey",
        "private long packRegionCoordinateKey",
        "private long packObjectSnapshotKey",
        "private static final class VisibleObjectSnapshot",
        "private final long cacheKey;",
        "private final long version;",
        "final LinkedHashSet<Player> localPlayers = new LinkedHashSet<>();",
        "final LinkedHashSet<Npc> localNpcs = new LinkedHashSet<>();",
        "final VisibleObjectSnapshot visibleObjects = getVisibleObjectSnapshot(entity.getLocation(), objectRegions);",
        "visibleObjects.cacheKey",
        "visibleObjects.version",
        "visibleObjectSnapshotSequence.incrementAndGet()",
        "final LinkedHashSet<GroundItem> localItems = new LinkedHashSet<>();",
        "final List<Region> mobRegions = getVisibleRegionWindow(entity.getLocation());",
        "final List<Region> objectRegions = getVisibleRegionWindow(entity.getLocation(), getWorld().getServer().getConfig().OBJECT_VIEW_DISTANCE);",
        "return new VisibilitySnapshot(",
    ):
        require(snippet in region_manager, f"region manager missing shadow visibility snapshot: {snippet}")

    require(
        "invalidateVisibleObjectWindowCache(region);" in region_manager,
        "ordered region game-object transactions should invalidate cached visible object windows",
    )
    require(
        "return other.regionX == regionX && other.regionY == regionY;" in region,
        "region equality should compare both region coordinates",
    )
    require(
        "other.regionY == other.getRegionY()" not in region,
        "region equality must not treat all regions with the same X as equal",
    )

    for snippet in (
        "public final class VisibilitySnapshot",
        "private final Collection<Player> players;",
        "private final Collection<Npc> npcs;",
        "private final Collection<GameObject> gameObjects;",
        "private final Collection<GameObject> sceneryObjects;",
        "private final Collection<GameObject> wallObjects;",
        "private final Collection<GroundItem> groundItems;",
        "private final long objectSnapshotKey;",
        "private final long objectSnapshotVersion;",
        "private static GameObjectTypes splitGameObjects",
        "public Collection<Player> getPlayers()",
        "public Collection<Npc> getNpcs()",
        "public Collection<GameObject> getGameObjects()",
        "public Collection<GameObject> getSceneryObjects()",
        "public Collection<GameObject> getWallObjects()",
        "public Collection<GroundItem> getGroundItems()",
        "public int getSceneryCount()",
        "public int getWallObjectCount()",
        "public long getObjectSnapshotKey()",
        "public long getObjectSnapshotVersion()",
    ):
        require(snippet in visibility_snapshot, f"visibility snapshot DTO missing: {snippet}")

    require(
        visibility_snapshot.count("splitGameObjects(gameObjects)") == 1,
        "visibility snapshot should classify scenery and walls in one pass",
    )

    require(
        "public boolean WANT_SYNC_VISIBILITY_SHADOW;" in server_config,
        "server config should expose shadow visibility flag",
    )
    require(
        "public boolean WANT_SYNC_VISIBILITY_SNAPSHOT_INPUT;" in server_config,
        "server config should expose snapshot packet input flag",
    )
    require(
        "public boolean WANT_SYNC_VISIBILITY_TICK_CACHE;" in server_config,
        "server config should expose tick snapshot cache flag",
    )
    require(
        "public boolean WANT_SYNC_SCENE_BASELINE;" in server_config,
        "server config should expose scene baseline packet flag",
    )
    require(
        "public boolean WANT_SYNC_MOVEMENT_SNAPSHOT;" in server_config,
        "server config should expose movement snapshot packet flag",
    )
    require(
        'WANT_SYNC_VISIBILITY_SHADOW = readBoolSystemEnvConfig('
        in server_config,
        "shadow visibility flag should support system/env/config override",
    )
    require(
        '"openrsc.syncVisibilityShadow"' in server_config
        and '"OPENRSC_SYNC_VISIBILITY_SHADOW"' in server_config
        and '"want_sync_visibility_shadow"' in server_config,
        "shadow visibility flag should expose benchmark-safe override names",
    )
    require(
        '"openrsc.syncVisibilitySnapshotInput"' in server_config
        and '"OPENRSC_SYNC_VISIBILITY_SNAPSHOT_INPUT"' in server_config
        and '"want_sync_visibility_snapshot_input"' in server_config,
        "snapshot input flag should expose benchmark-safe override names",
    )
    require(
        '"openrsc.syncVisibilityTickCache"' in server_config
        and '"OPENRSC_SYNC_VISIBILITY_TICK_CACHE"' in server_config
        and '"want_sync_visibility_tick_cache"' in server_config,
        "tick snapshot cache flag should expose benchmark-safe override names",
    )
    require(
        '"openrsc.syncSceneBaseline"' in server_config
        and '"OPENRSC_SYNC_SCENE_BASELINE"' in server_config
        and '"want_sync_scene_baseline"' in server_config,
        "scene baseline flag should expose benchmark-safe override names",
    )
    require(
        '"openrsc.syncMovementSnapshot"' in server_config
        and '"OPENRSC_SYNC_MOVEMENT_SNAPSHOT"' in server_config
        and '"want_sync_movement_snapshot"' in server_config,
        "movement snapshot flag should expose benchmark-safe override names",
    )
    for snippet in (
        "private final Map<Long, CachedVisibilitySnapshot> visibilityTickSnapshotCache = new HashMap<>();",
        "private long visibilityTickSnapshotCacheTick = Long.MIN_VALUE;",
        "recordVisibilityTickSnapshotCacheAccess(true)",
        "recordVisibilityTickSnapshotCacheAccess(false)",
        "private static final class CachedVisibilitySnapshot",
        "sendUpdatePackets(player, true)",
    ):
        require(snippet in updater, f"game updater missing tick snapshot cache path: {snippet}")
    for snippet in (
        "private static final int MOVEMENT_SNAPSHOT_PROTOCOL_VERSION = 1;",
        "private static final int MOVEMENT_SNAPSHOT_FIXED_PAYLOAD_BYTES = 18;",
        "private static final int MOVEMENT_SNAPSHOT_MOB_RECORD_BYTES = 7;",
        "private int movementSnapshotSequence = 0;",
        "public boolean sendMovementSnapshotPacket(final Player player, final List<Player> movedPlayers, final List<Npc> movedNpcs)",
        "!player.isUsingCustomClient() || !getServer().getConfig().WANT_SYNC_MOVEMENT_SNAPSHOT",
        "struct.protocolVersion = layeredProtocol",
        "struct.serverTick = (int)(getServer().getCurrentTick() & 0x7FFFFFFF);",
        "struct.sequence = ++movementSnapshotSequence;",
        "tryFinalizeAndSendPacket(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, struct, player);",
        "getServer().addMovementSnapshotMetrics(",
        "private static boolean isWithinClientLocalTileWindow",
        "CLIENT_LOCAL_TILE_COUNT",
        "if (!isWithinMobPacketRange(player, movedNpc))",
    ):
        require(snippet in updater, f"movement snapshot sender missing: {snippet}")
    movement_window_block = extract_between(
        updater,
        "private static boolean isWithinClientLocalTileWindow",
        "private int safeNPCIndex",
    )
    movement_window_filter_block = extract_between(
        updater,
        "private static boolean isWithinClientLocalTileWindow",
        "private static void updateCustomMovementClientRegion",
    )
    require(
        "currentClientLocalBaseX(viewer)" in movement_window_block
        and "currentClientLocalBaseY(viewer)" in movement_window_block
        and "updateCustomMovementClientRegion" in updater
        and "CLIENT_LOCAL_REGION_RELOAD_RADIUS" in updater
        and "CUSTOM_MOVEMENT_CLIENT_MID_X_ATTRIBUTE" in updater
        and "CUSTOM_MOVEMENT_CLIENT_MID_Y_ATTRIBUTE" in updater,
        "custom movement local window must track the client's live loaded-region base",
    )
    require(
        "clientLocalMidpointForTile(viewer.getX(), CLIENT_LOCAL_PLANE_WIDTH)"
        not in movement_window_filter_block,
        "custom movement local window must not recalculate directly from the viewer's current tile per record",
    )
    scene_baseline_block = extract_between(
        updater,
        "private void sendSceneBaselineIfEnabled",
        "private void recordVisibilitySnapshotMetrics",
    )
    require(
        "!player.isUsingCustomClient() || !getServer().getConfig().WANT_SYNC_SCENE_BASELINE"
        in scene_baseline_block,
        "scene baseline packet must stay custom-client and config gated",
    )
    require(
        "final VisibilitySnapshot baselineVisibility" not in scene_baseline_block
        and "buildSceneBaselineSummary(" in scene_baseline_block,
        "scene baseline path should stay split from dynamic player/NPC visibility snapshots",
    )
    require(
        "tryFinalizeAndSendPacketChecked(\n"
        "\t\t\t\tOpcodeOut.SEND_SCENE_BASELINE, baseline, player)"
        in scene_baseline_block,
        "scene baseline path should transactionally queue its completed DTO",
    )
    for snippet in (
        "private static final int SCENE_BASELINE_PROTOCOL_VERSION = 5;",
        "private static final int LAYERED_PRESENTATION_SCENE_BASELINE_PROTOCOL_VERSION = 8;",
        "private static final int SCENE_BASELINE_PAGE_SIZE = 64;",
        "private static final int SCENE_BASELINE_PAGE_BURST_LIMIT = 4;",
        "private static final int LAYERED_PRESENTATION_SCENE_BASELINE_PAGE_SIZE = 512;",
        "private static final int LAYERED_PRESENTATION_SCENE_BASELINE_PAGE_BURST_LIMIT = 8;",
        "private static final int SCENE_BASELINE_FIXED_PAYLOAD_BYTES = 48;",
        "private static final int SCENE_BASELINE_OBJECT_RECORD_BYTES = 8;",
        "private static final int SCENE_BASELINE_PRESENTATION_HEADER_BYTES = 22;",
        "private static final int SCENE_BASELINE_PRESENTATION_OBJECT_RECORD_BYTES = 12;",
        "private static final int SCENE_BASELINE_PAGE_SCENERY = 1;",
        "private static final int SCENE_BASELINE_PAGE_WALLS = 2;",
        "private static final int SCENE_BASELINE_PAGE_PRESENTATION_SCENERY = 4;",
        "private static final int SCENE_BASELINE_PAGE_PRESENTATION_WALLS = 5;",
        "private static final String SCENE_BASELINE_SUMMARY_ATTRIBUTE = \"scene_baseline_summary\";",
        "private static final String STATIC_SCENE_SCAN_KEY_ATTRIBUTE = \"static_scene_scan_key\";",
        "private boolean canSkipStaticSceneScan(final Player player, final VisibilitySnapshot packetVisibility)",
        "return scanKey.equals(previousScanKey);",
        "private void storeStaticSceneScanKey(final Player player, final VisibilitySnapshot packetVisibility)",
        "private Object staticSceneScanKey(",
        "packetVisibility.getObjectSnapshotVersion() <= 0L",
        "packetVisibility.getObjectSnapshotKey()",
        "hash = hash * 31 + player.getX();",
        "hash = hash * 31 + player.getY();",
        "final SceneBaselineSummary previous = player.getAttribute(SCENE_BASELINE_SUMMARY_ATTRIBUTE);",
        "sceneBaselinePageBurstLimit(current.protocolVersion)",
        "while (sentPages < pageBurstLimit)",
        "sendSceneBaselinePacket(player, current, page)",
        "current.recordSentPage(page);",
        "if (sentPages == 0 && previous != null && current.sameStaticPayload(previous))",
        "player, current, SceneBaselinePage.empty()",
        "private boolean sendSceneBaselinePacket(",
        "page.applyTo(baseline);",
        "page.payloadBytes(current.protocolVersion)",
        "player.setAttribute(SCENE_BASELINE_SUMMARY_ATTRIBUTE, current);",
        "summary.scenery = player.getLocalGameObjects().size();",
        "summary.walls = player.getLocalWallObjects().size();",
        "summary.groundItems = player.getLocalGroundItems().size();",
        "summary.objectViewDistance = getServer().getConfig().OBJECT_VIEW_DISTANCE;",
        "previous != null && !sceneryChanged && previous.scenery == summary.scenery",
        "previous != null && !wallsChanged && previous.walls == summary.walls",
        "previous != null && !groundItemsChanged && previous.groundItems == summary.groundItems",
        "private SceneBaselinePage buildNextSceneBaselinePage(",
        "private SceneBaselinePage buildSceneBaselinePresentationPage(",
        "summary.samePagedScenePayload(previous)",
        "private SceneBaselinePage buildSceneBaselineObjectPage(",
        "private static final class SceneBaselineSummary",
        "private boolean sameStaticPayload(final SceneBaselineSummary other)",
        "private boolean samePagedScenePayload(",
        "objectViewDistance == other.objectViewDistance",
        "private boolean hasSentCompleteStaticBaseline()",
        "private static int pageTotalFor(",
        "private boolean shouldSendLegacyStaticScenePackets(final Player player, final boolean staticSceneScanSkipped)",
        "return !staticSceneScanSkipped;",
        "getServer().addSuppressedLegacyStaticSceneMetrics(false, objectLocs.size());",
        "getServer().addSuppressedLegacyStaticSceneMetrics(true, objectLocs.size());",
        "private static final class SceneBaselinePage",
        "private int recordCount()",
        "private int payloadBytes(final int protocolVersion)",
        "private int sceneIdentity(final int a, final int b, final int c, final int d, final int e)",
        "private int addSceneIdentity(final int summary, final int identity)",
    ):
        require(snippet in updater, f"scene baseline identity summary missing: {snippet}")
    for snippet in (
        "summarizeScenePlayers(",
        "summarizeSceneNpcs(",
        "playersHash == other.playersHash",
        "npcsHash == other.npcsHash",
    ):
        require(snippet not in updater, f"static scene baseline should not depend on dynamic mob summaries: {snippet}")
    for snippet in (
        "SEND_SCENE_BASELINE, // custom",
        "SEND_MOVEMENT_SNAPSHOT, // custom",
        "put(OpcodeOut.SEND_SCENE_BASELINE, SceneBaselineStruct.class); // custom",
        "put(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, MovementSnapshotStruct.class); // custom",
        "put(OpcodeOut.SEND_SCENE_BASELINE, 143); // custom",
        "put(OpcodeOut.SEND_MOVEMENT_SNAPSHOT, 146); // custom",
        "case SEND_SCENE_BASELINE:",
        "case SEND_MOVEMENT_SNAPSHOT:",
        "builder.writeShort(baseline.objectViewDistance);",
        "builder.writeByte((byte) movementSnapshot.protocolVersion);",
        "builder.writeInt(movementSnapshot.serverTick);",
        "builder.writeInt(movementSnapshot.sequence);",
        "builder.writeByte((byte) baseline.pageCategory);",
        "builder.writeShort(baseline.pageIndex);",
        "builder.writeShort(baseline.pageTotal);",
        "builder.writeShort(baseline.objectRecords.size());",
        "for (SceneBaselineStruct.ObjectRecord objectRecord : baseline.objectRecords)",
    ):
        source = opcode_out + payload_validator + payload_custom_generator
        require(snippet in source, f"scene baseline protocol missing: {snippet}")
    for snippet in (
        'put(146, "MOVEMENT_SNAPSHOT");',
        "else if (opcode == 146) updateMovementSnapshot(length);",
        "private void updateMovementSnapshot(int length)",
        "private final MovementSnapshotDiagnostics movementSnapshotDiagnostics",
        "movementSnapshotDiagnostics.recordMovementUpdate(fingerprint);",
        "movementSnapshotDiagnostics.recordSnapshot(",
        "private final MovementSnapshotStage movementSnapshotStage = new MovementSnapshotStage();",
        "MovementSnapshotStage.Frame stageFrame = null;",
        "mc.applyCustomMovementUpdate(localX, localZ, localDirection);",
        "stageFrame.addPlayer(serverIndex, x, z, direction);",
        "stageFrame.addNpc(serverIndex, x, z, direction);",
        "mc.applyCustomPlayerMovementUpdate(serverIndex, x, z, direction);",
        "mc.applyCustomNpcMovementUpdate(serverIndex, x, z, direction);",
        "movementSnapshotStage.replaceFromSnapshot(stageFrame, mc)",
        "MovementSnapshotDiagnostics.CacheParity parity",
        "parity.checkLocal(mc, localX, localZ, localDirection);",
        "parity.checkPlayer(mc, serverIndex, x, z, direction);",
        "parity.checkNpc(mc, serverIndex, x, z, direction);",
        "public String getMovementSnapshotDebugSummaryLine()",
        "public String[] getMovementSnapshotDebugSummaryLines()",
    ):
        require(snippet in client_packet_handler, f"client movement snapshot parsing missing: {snippet}")
    for snippet in (
        "final class MovementSnapshotDiagnostics",
        "static final class Fingerprint",
        "static final class CacheParity",
        "private static final class SnapshotDebugState",
        "packetDebugState.compareSnapshot(snapshotFingerprint)",
        "RECENT_MOVE_CACHE_LOG_LIMIT = 5",
        "MOVE_CACHE_LOG_PREFIX = \"MOVEMENT_CACHE_RECENT\"",
        "rememberMoveCacheLine(buildRecentMoveCacheLine",
        "logRecentMoveCacheLines()",
        "\"wire ok\"",
        "\"cache ok c\" + cacheChecked",
        "\"stage \" + (stageCurrentMismatches == 0 ? \"ok\" : \"bad\")",
    ):
        require(snippet in movement_snapshot_diagnostics, f"client movement snapshot diagnostic missing: {snippet}")
    for snippet in (
        "private ORSCharacter findVisibleNpcByServerIndex(int serverIndex)",
        "private ORSCharacter findVisiblePlayerByServerIndex(int serverIndex)",
        "if (visiblePlayer != null && visiblePlayer != player)",
        "for (int i = 0; i < this.npcCount; i++)",
        "reconcileCustomNpcMovementTarget(visibleNpc)",
        "customNpcMovementTargetValid",
        "CUSTOM_NPC_MOVEMENT_TARGET_TTL_MILLIS",
        "rememberCustomNpcMovementTarget(serverIndex, worldX, worldZ, direction)",
        "public void reconcileCustomNpcMovementTarget(ORSCharacter npc)",
        "public String describeCustomNpcMovementDebug(int serverIndex)",
        "customNpcMovementTargetResult",
        "Arrays.copyOf(this.customNpcMovementTargetValid, capacity)",
    ):
        require(snippet in client_mudclient, f"custom movement visible-cache repair missing: {snippet}")
    for snippet in (
        "final class MovementSnapshotStage",
        "Result replaceFromSnapshot(Frame frame, mudclient mc)",
        "private Result compareToVisibleCache(mudclient mc)",
        "mc.describeCustomNpcMovementDebug(target.serverIndex)",
        "static final class Frame",
        "static final class Result",
    ):
        require(snippet in movement_snapshot_stage, f"client movement snapshot staging helper missing: {snippet}")
    require(
        "activePacketHandler.getMovementSnapshotDebugSummaryLines()" in client_applet
        and "movementSnapshotLines[0]" in client_applet,
        "expanded client debug overlay should expose a compact movement snapshot summary",
    )
    for snippet in (
        "public int protocolVersion;",
        "public int serverTick;",
        "public int players;",
        "public int npcs;",
        "public int scenery;",
        "public int walls;",
        "public int groundItems;",
        "public int objectViewDistance;",
        "public int playersHash;",
        "public int npcsHash;",
        "public int sceneryHash;",
        "public int wallsHash;",
        "public int groundItemsHash;",
        "public int presentationCenterSectorX;",
        "public int presentationCenterSectorY;",
        "public int presentationOuterRadius;",
        "public int presentationInnerRadius;",
        "public int presentationScenery;",
        "public int presentationWalls;",
        "public int presentationSceneryHash;",
        "public int presentationWallsHash;",
        "public int pageCategory;",
        "public int pageIndex;",
        "public int pageTotal;",
        "public List<ObjectRecord> objectRecords = new ArrayList<>();",
        "public static class ObjectRecord",
    ):
        require(snippet in scene_baseline_struct, f"scene baseline struct missing: {snippet}")
    require(
        'put(143, "SCENE_BASELINE");' in client_packet_handler
        and "else if (opcode == 143) updateSceneBaseline(length);" in client_packet_handler
        and "private void updateSceneBaseline(int length)" in client_packet_handler,
        "client should parse and discard scene baseline opcode 143 safely",
    )
    require(
        "if (protocolVersion >= 5 && packetsIncoming.packetEnd + 2 <= length)" in client_packet_handler
        and "objectViewDistance = packetsIncoming.getShort();" in client_packet_handler
        and "if (packetsIncoming.packetEnd + 20 <= length)" in client_packet_handler,
        "client should recognize scene baseline protocol v5 object-range metadata and checksum payload",
    )
    require(
        "SCENE_BASELINE_PAGE_HEADER_BYTES" in client_packet_handler
        and "int recordCount = packetsIncoming.getShort();" in client_packet_handler
        and "final int recordBytes = presentationRecords ? 12 : 8;" in client_packet_handler
        and "validateSceneBaselinePageHeader(" in client_packet_handler
        and "validateSceneBaselineRecord(" in client_packet_handler,
        "client should strictly parse and validate both baseline page products",
    )
    for snippet in (
        "private final SceneBaselineState sceneBaselineState = new SceneBaselineState();",
        "public String getSceneBaselineDebugSummary()",
        "public String[] getSceneBaselineDebugSummaryLines()",
        "List<SceneBaselineState.Record> pageRecords = new ArrayList<SceneBaselineState.Record>();",
        "pageRecords.add(new SceneBaselineState.Record(id, x, y, direction, type));",
        "sceneBaselineState.recordPacket(",
        "sceneBaselineState.pruneLegacyListsOutsideSyncRange(mc);",
        "applyCompleteSceneBaselineToLegacyLists();",
        "private void applyCompleteSceneBaselineToLegacyLists()",
        "private void addBaselineGameObject(SceneBaselineState.Record record)",
        "private void addBaselineWallObject(SceneBaselineState.Record record)",
        "snapshotStoredSceneryRecords()",
        "snapshotStoredWallRecords()",
        "applyCompleteStaticPresentation();",
        "snapshotStoredPresentationSceneryRecords()",
        "snapshotStoredPresentationWallRecords()",
        "recordLegacyBaselineApplied()",
    ):
        require(snippet in client_packet_handler, f"client scene baseline parsing/apply path missing: {snippet}")
    for snippet in (
        "final class SceneBaselineState",
        "private static final long SCENE_BASELINE_STALE_MILLIS = 15000L;",
        "static final class Record",
        "private static final long PARITY_REFRESH_MILLIS = 500L;",
        "private final Map<Integer, Map<Integer, List<Record>>> receivedPageRecords",
        "private List<Record> storedSceneryRecords",
        "private List<Record> storedWallRecords",
        "private List<Record> storedPresentationSceneryRecords",
        "private List<Record> storedPresentationWallRecords",
        "private int objectViewDistance = 0;",
        "private int incompleteSceneResets = 0;",
        "private int completedBaselines = 0;",
        "private long baselineStartedMillis = 0L;",
        "void recordPacket(",
        "incompleteSceneResets++;",
        "private void resetPageState()",
        "private void storePageRecords(",
        "private boolean hasCompleteBaseline()",
        "private boolean hasCompletePresentation()",
        "private void rebuildStoredBaseline()",
        "private void rebuildStoredPresentation()",
        "completedBaselines++;",
        "private List<Record> flattenRecords(int pageCategory)",
        "private String parityLine(mudclient mc)",
        "private int legacySceneSignature(mudclient mc)",
        "private ParityResult compareStoredToLegacy(",
        "private boolean insideObjectSyncRange(int x, int y)",
        "void pruneLegacyListsOutsideSyncRange(mudclient mc)",
        "private int pruneGameObjectsOutsideSyncRange(mudclient mc)",
        "private int pruneWallObjectsOutsideSyncRange(mudclient mc)",
        "mc.dematerializeGameObjectInstance(readIndex);",
        "mc.dematerializeWallObjectInstance(readIndex);",
        "pruned objects/walls",
        "appliedLegacyBaselines",
        "legacyExtraInsideSyncRange",
        "legacyExtraOutsideSyncRange",
        "mc.getGameObjectInstanceX(i) + mc.getMidRegionBaseX()",
        "mc.getWallObjectInstanceX(i) + mc.getMidRegionBaseX()",
        "private long sceneRecordKey(int id, int x, int y, int direction)",
        "private static final class ParityResult",
        "private String baselineState()",
        "boolean hasStoredCompleteBaseline()",
        "boolean hasStoredCompletePresentation()",
        "private boolean isStaticCategory(int pageCategory)",
        "private int staticSceneKey(",
        "private String pageSummary(int pageCategory)",
    ):
        require(snippet in scene_baseline_state, f"client scene baseline state missing: {snippet}")
    require(
        "PacketHandler activePacketHandler = mudclient == null ? null : mudclient.packetHandler;" in client_applet
        and "activePacketHandler.getSceneBaselineDebugSummaryLines()" in client_applet
        and "sceneBaselineLines[0]" in client_applet,
        "expanded client debug overlay should expose a compact scene baseline summary",
    )
    require(
        "static PacketHandler packetHandler;" not in client_applet,
        "expanded client debug overlay should not read a stale static packet handler",
    )
    require(
        "want_sync_scene_baseline: false" in myworld_conf
        and "want_sync_scene_baseline: true" in myworld_host_conf,
        "scene baseline should remain off for ordinary development and be "
        "explicitly enabled by the accepted hosted layered profile",
    )
    require(
        "want_sync_movement_snapshot: true" in myworld_conf
        and "want_sync_movement_snapshot: true" in myworld_host_conf,
        "movement snapshot packet should remain enabled in checked-in server configs",
    )
    for snippet in (
        "visibilityTickSnapshotCacheRequests=",
        "visibilityTickSnapshotCacheHits=",
        "visibilityTickSnapshotCacheMisses=",
        "recordVisibilityTickSnapshotCacheAccess",
        "getLastVisibilityTickSnapshotCacheRequests",
        "benchmarkSyntheticClientVersion = getIntegerSystemProperty(\"openrsc.benchmarkSyntheticClientVersion\", 235);",
        "syntheticClientVersion=\" + benchmarkSyntheticClientVersion",
    ):
        require(snippet in server, f"server missing tick snapshot cache telemetry: {snippet}")
    require(
        'BENCHMARK_EXTRA_JVM_ARGS="${MYWORLD_BENCHMARK_EXTRA_JVM_ARGS:-}"' in benchmark_script,
        "foundation benchmark should accept extra JVM args",
    )
    require(
        "$BENCHMARK_EXTRA_JVM_ARGS" in benchmark_script,
        "foundation benchmark should pass extra JVM args to the server",
    )

    print("PASS: server sync modernization guards validated")


if __name__ == "__main__":
    main()
