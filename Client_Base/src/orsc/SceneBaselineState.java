package orsc;

import orsc.graphics.three.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns scene-baseline page storage, legacy parity, and diagnostic history.
 * Packet decoding and baseline materialization remain in {@link PacketHandler}.
 */
final class SceneBaselineState {
	private static final int PAGE_NONE = 0;
	private static final int PAGE_SCENERY = 1;
	private static final int PAGE_WALLS = 2;
	private static final long PARITY_REFRESH_MILLIS = 500L;
	private static final long SCENE_BASELINE_STALE_MILLIS = 15000L;
	private static final int RECENT_SCENE_SYNC_LOG_LIMIT = 5;
	private static final String SCENE_SYNC_LOG_PREFIX = "SCENE_SYNC_RECENT";

	private final Map<Integer, boolean[]> receivedPageIndexes = new HashMap<Integer, boolean[]>();
	private final Map<Integer, Integer> expectedPages = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> receivedPages = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> duplicatePages = new HashMap<Integer, Integer>();
	private final Map<Integer, Integer> receivedRecords = new HashMap<Integer, Integer>();
	private final Map<Integer, Map<Integer, List<Record>>> receivedPageRecords =
		new HashMap<Integer, Map<Integer, List<Record>>>();
	private List<Record> storedSceneryRecords = new ArrayList<Record>();
	private List<Record> storedWallRecords = new ArrayList<Record>();
	private int staticSceneKey = 0;
	private int storedStaticSceneKey = 0;
	private int incompleteSceneResets = 0;
	private int completedBaselines = 0;
	private long baselineStartedMillis = 0L;
	private long lastPacketMillis = 0L;
	private long lastParityCheckMillis = 0L;
	private int lastParityLegacySignature = 0;
	private String lastParityLine = "sceneBase parity waiting";
	private int protocolVersion = 0;
	private int serverTick = 0;
	private int localX = 0;
	private int localY = 0;
	private int scenery = 0;
	private int walls = 0;
	private int groundItems = 0;
	private int objectViewDistance = 0;
	private int packets = 0;
	private int pages = 0;
	private int records = 0;
	private int lastPruneKey = 0;
	private int legacyPrunedScenery = 0;
	private int legacyPrunedWalls = 0;
	private int appliedLegacyBaselines = 0;
	private final String[] recentSceneSyncLines = new String[RECENT_SCENE_SYNC_LOG_LIMIT];
	private int recentSceneSyncNext = 0;
	private int recentSceneSyncCount = 0;
	private int lastLoggedSceneIssueSignature = 0;
	private String scopeIdentity = "legacy";

	void recordPacket(
		int protocolVersion,
		int serverTick,
		String scopeIdentity,
		int localX,
		int localY,
		int scenery,
		int walls,
		int groundItems,
		int objectViewDistance,
		int sceneryHash,
		int wallsHash,
		int groundItemsHash,
		int pageCategory,
		int pageIndex,
		int pageTotal,
		int recordsRead,
		List<Record> pageRecords) {
		long now = System.currentTimeMillis();
		int nextStaticSceneKey = staticSceneKey(
			protocolVersion,
			scopeIdentity,
			scenery,
			walls,
			groundItems,
			objectViewDistance,
			sceneryHash,
			wallsHash,
			groundItemsHash);
		if (nextStaticSceneKey != staticSceneKey) {
			if (staticSceneKey != 0 && packets > 0 && !hasCompleteBaseline()) {
				incompleteSceneResets++;
			}
			staticSceneKey = nextStaticSceneKey;
			resetPageState();
			baselineStartedMillis = now;
		}
		if (baselineStartedMillis == 0L) {
			baselineStartedMillis = now;
		}
		lastPacketMillis = now;

		this.protocolVersion = protocolVersion;
		this.serverTick = serverTick;
		this.scopeIdentity = scopeIdentity;
		this.localX = localX;
		this.localY = localY;
		this.scenery = scenery;
		this.walls = walls;
		this.groundItems = groundItems;
		this.objectViewDistance = objectViewDistance;
		this.packets++;
		expectedPages.put(PAGE_SCENERY, pageTotal(scenery));
		expectedPages.put(PAGE_WALLS, pageTotal(walls));

		if (!isStaticCategory(pageCategory) || pageTotal <= 0) {
			return;
		}

		this.pages++;
		this.records += recordsRead;
		expectedPages.put(pageCategory, pageTotal);
		receivedRecords.put(pageCategory, receivedRecords.getOrDefault(pageCategory, 0) + recordsRead);

		boolean[] categoryPages = receivedPageIndexes.get(pageCategory);
		if (categoryPages == null || categoryPages.length != pageTotal) {
			categoryPages = new boolean[pageTotal];
			receivedPageIndexes.put(pageCategory, categoryPages);
			receivedPages.put(pageCategory, 0);
			duplicatePages.put(pageCategory, 0);
		}

		if (pageIndex < 0 || pageIndex >= categoryPages.length) {
			duplicatePages.put(pageCategory, duplicatePages.getOrDefault(pageCategory, 0) + 1);
			return;
		}

		if (categoryPages[pageIndex]) {
			duplicatePages.put(pageCategory, duplicatePages.getOrDefault(pageCategory, 0) + 1);
			return;
		}

		categoryPages[pageIndex] = true;
		receivedPages.put(pageCategory, receivedPages.getOrDefault(pageCategory, 0) + 1);
		storePageRecords(pageCategory, pageIndex, pageRecords);
		if (hasCompleteBaseline()) {
			rebuildStoredBaseline();
		}
	}

	void resetForScopeChange(String nextScopeIdentity) {
		staticSceneKey = 0;
		storedStaticSceneKey = 0;
		resetPageState();
		scopeIdentity = nextScopeIdentity == null ? "none" : nextScopeIdentity;
		lastParityCheckMillis = 0L;
		lastParityLegacySignature = 0;
		lastParityLine = "scene sync match waiting";
	}

	String summary() {
		String[] lines = summaryLines(null);
		return lines[0] + " | " + lines[1];
	}

	String[] summaryLines(mudclient mc) {
		String baselineState = baselineState();
		return new String[] {
			"scene sync " + baselineState
				+ " | objects/walls/items " + scenery + "/" + walls + "/" + groundItems
				+ " | range " + objectViewDistance
				+ " | transfer pages " + pages + "/" + expectedStaticPages()
				+ " | records " + records,
			"scene sync stored objects/walls " + storedSceneryRecords.size() + "/" + storedWallRecords.size()
				+ " | duplicate pages " + duplicatePageTotal()
				+ " | reset/done " + incompleteSceneResets + "/" + completedBaselines
				+ " | pruned objects/walls " + legacyPrunedScenery + "/" + legacyPrunedWalls
				+ " | applied " + appliedLegacyBaselines,
			parityLine(mc)
		};
	}

	void recordSceneDiagnostics(mudclient mc) {
		String parity = parityLine(mc);
		String line = buildRecentSceneSyncLine(parity);
		rememberSceneSyncLine(line);
		int issueSignature = sceneIssueSignature(parity);
		if (issueSignature != 0 && issueSignature != lastLoggedSceneIssueSignature) {
			lastLoggedSceneIssueSignature = issueSignature;
			logRecentSceneSyncLines();
		}
	}

	void pruneLegacyListsOutsideSyncRange(mudclient mc) {
		if (mc == null || !hasStoredCompleteBaseline() || objectViewDistance <= 0) {
			return;
		}
		int legacySignature = legacySceneSignature(mc);
		int pruneKey = staticSceneKey;
		pruneKey = pruneKey * 31 + localX;
		pruneKey = pruneKey * 31 + localY;
		pruneKey = pruneKey * 31 + legacySignature;
		if (pruneKey == lastPruneKey) {
			return;
		}

		legacyPrunedScenery += pruneGameObjectsOutsideSyncRange(mc);
		legacyPrunedWalls += pruneWallObjectsOutsideSyncRange(mc);
		lastPruneKey = staticSceneKey;
		lastPruneKey = lastPruneKey * 31 + localX;
		lastPruneKey = lastPruneKey * 31 + localY;
		lastPruneKey = lastPruneKey * 31 + legacySceneSignature(mc);
		lastParityCheckMillis = 0L;
		lastParityLegacySignature = 0;
	}

	List<Record> snapshotStoredSceneryRecords() {
		return new ArrayList<Record>(storedSceneryRecords);
	}

	List<Record> snapshotStoredWallRecords() {
		return new ArrayList<Record>(storedWallRecords);
	}

	int legacyApplyKey(mudclient mc) {
		int hash = staticSceneKey;
		hash = hash * 31 + mc.getMidRegionBaseX();
		hash = hash * 31 + mc.getMidRegionBaseZ();
		hash = hash * 31 + storedSceneryRecords.size();
		hash = hash * 31 + storedWallRecords.size();
		return hash;
	}

	void recordLegacyBaselineApplied() {
		appliedLegacyBaselines++;
		lastParityCheckMillis = 0L;
		lastParityLegacySignature = 0;
	}

	boolean hasStoredCompleteBaseline() {
		return storedStaticSceneKey == staticSceneKey
			&& storedSceneryRecords.size() == scenery
			&& storedWallRecords.size() == walls;
	}

	boolean isBaselineOriginLoaded(mudclient mc) {
		return World.isLocalTile(
			localX - mc.getMidRegionBaseX(),
			localY - mc.getMidRegionBaseZ());
	}

	private void resetPageState() {
		receivedPageIndexes.clear();
		expectedPages.clear();
		receivedPages.clear();
		duplicatePages.clear();
		receivedRecords.clear();
		receivedPageRecords.clear();
		storedSceneryRecords = new ArrayList<Record>();
		storedWallRecords = new ArrayList<Record>();
		storedStaticSceneKey = 0;
		lastPacketMillis = 0L;
		lastPruneKey = 0;
		packets = 0;
		pages = 0;
		records = 0;
	}

	private void storePageRecords(int pageCategory, int pageIndex, List<Record> pageRecords) {
		Map<Integer, List<Record>> categoryRecords = receivedPageRecords.get(pageCategory);
		if (categoryRecords == null) {
			categoryRecords = new HashMap<Integer, List<Record>>();
			receivedPageRecords.put(pageCategory, categoryRecords);
		}
		categoryRecords.put(pageIndex, new ArrayList<Record>(pageRecords));
	}

	private boolean hasCompleteBaseline() {
		return categoryComplete(PAGE_SCENERY) && categoryComplete(PAGE_WALLS);
	}

	private boolean categoryComplete(int pageCategory) {
		int expected = expectedPages.getOrDefault(pageCategory, 0);
		return receivedPages.getOrDefault(pageCategory, 0) >= expected;
	}

	private void rebuildStoredBaseline() {
		boolean wasComplete = hasStoredCompleteBaseline();
		storedSceneryRecords = flattenRecords(PAGE_SCENERY);
		storedWallRecords = flattenRecords(PAGE_WALLS);
		storedStaticSceneKey = staticSceneKey;
		if (!wasComplete && hasStoredCompleteBaseline()) {
			completedBaselines++;
		}
	}

	private List<Record> flattenRecords(int pageCategory) {
		List<Record> records = new ArrayList<Record>();
		Map<Integer, List<Record>> categoryRecords = receivedPageRecords.get(pageCategory);
		if (categoryRecords == null) {
			return records;
		}
		int expected = expectedPages.getOrDefault(pageCategory, 0);
		for (int i = 0; i < expected; i++) {
			List<Record> pageRecords = categoryRecords.get(i);
			if (pageRecords != null) {
				records.addAll(pageRecords);
			}
		}
		return records;
	}

	private boolean isStaticCategory(int pageCategory) {
		return pageCategory == PAGE_SCENERY || pageCategory == PAGE_WALLS;
	}

	private int pageTotal(int recordCount) {
		return (recordCount + 63) / 64;
	}

	private int staticSceneKey(
		int protocolVersion,
		String scopeIdentity,
		int scenery,
		int walls,
		int groundItems,
		int objectViewDistance,
		int sceneryHash,
		int wallsHash,
		int groundItemsHash) {
		int hash = protocolVersion;
		hash = hash * 31 + (scopeIdentity == null ? 0 : scopeIdentity.hashCode());
		hash = hash * 31 + scenery;
		hash = hash * 31 + walls;
		hash = hash * 31 + groundItems;
		hash = hash * 31 + objectViewDistance;
		hash = hash * 31 + sceneryHash;
		hash = hash * 31 + wallsHash;
		hash = hash * 31 + groundItemsHash;
		return hash;
	}

	private int sceneIdentity(final int a, final int b, final int c, final int d, final int e) {
		int hash = 0x811C9DC5;
		hash = mixSceneIdentity(hash, a);
		hash = mixSceneIdentity(hash, b);
		hash = mixSceneIdentity(hash, c);
		hash = mixSceneIdentity(hash, d);
		hash = mixSceneIdentity(hash, e);
		return hash;
	}

	private int mixSceneIdentity(final int hash, final int value) {
		return (hash ^ value) * 0x01000193;
	}

	private int addSceneIdentity(final int summary, final int identity) {
		return summary + identity + Integer.rotateLeft(identity, 16);
	}

	private String buildRecentSceneSyncLine(String parity) {
		return "v" + protocolVersion
			+ " tick " + serverTick
			+ " scope " + scopeIdentity
			+ " origin " + localX + "," + localY
			+ " state " + baselineState()
			+ " static " + scenery + "/" + walls + "/" + groundItems
			+ " stored " + storedSceneryRecords.size() + "/" + storedWallRecords.size()
			+ " pages " + pages + "/" + expectedStaticPages()
			+ " records " + records
			+ " scenery " + compactPageSummary(PAGE_SCENERY)
			+ " walls " + compactPageSummary(PAGE_WALLS)
			+ " reset/done " + incompleteSceneResets + "/" + completedBaselines
			+ " pruned " + legacyPrunedScenery + "/" + legacyPrunedWalls
			+ " applied " + appliedLegacyBaselines
			+ " | " + parity;
	}

	private int sceneIssueSignature(String parity) {
		int issueSignature = 0;
		if ("stale".equals(baselineState())) {
			issueSignature = issueSignature * 31 + 1;
		}
		if (duplicatePageTotal() > 0) {
			issueSignature = issueSignature * 31 + duplicatePageTotal();
		}
		if (parity.indexOf("scene sync match ok") < 0
			&& parity.indexOf("scene sync match waiting") < 0) {
			issueSignature = issueSignature * 31 + parity.hashCode();
		}
		return issueSignature;
	}

	private void rememberSceneSyncLine(String line) {
		recentSceneSyncLines[recentSceneSyncNext] = line;
		recentSceneSyncNext = (recentSceneSyncNext + 1) % RECENT_SCENE_SYNC_LOG_LIMIT;
		if (recentSceneSyncCount < RECENT_SCENE_SYNC_LOG_LIMIT) {
			recentSceneSyncCount++;
		}
	}

	private void logRecentSceneSyncLines() {
		logSceneSyncLine(SCENE_SYNC_LOG_PREFIX
			+ " latest issue; last " + recentSceneSyncCount + " scene sync snapshots:");
		for (int i = 0; i < recentSceneSyncCount; i++) {
			int index = recentSceneSyncNext - recentSceneSyncCount + i;
			if (index < 0) {
				index += RECENT_SCENE_SYNC_LOG_LIMIT;
			}
			logSceneSyncLine(SCENE_SYNC_LOG_PREFIX + " " + recentSceneSyncLines[index]);
		}
	}

	private void logSceneSyncLine(String line) {
		System.out.println(line);
		ClientRuntimeLogger.log(line);
	}

	private int pruneGameObjectsOutsideSyncRange(mudclient mc) {
		int writeIndex = 0;
		int pruned = 0;
		for (int readIndex = 0; readIndex < mc.getGameObjectInstanceCount(); readIndex++) {
			int worldX = mc.getGameObjectInstanceX(readIndex) + mc.getMidRegionBaseX();
			int worldY = mc.getGameObjectInstanceZ(readIndex) + mc.getMidRegionBaseZ();
			if (!insideObjectSyncRange(worldX, worldY)) {
				mc.dematerializeGameObjectInstance(readIndex);
				pruned++;
				continue;
			}

			if (writeIndex != readIndex) {
				mc.setGameObjectInstanceX(writeIndex, mc.getGameObjectInstanceX(readIndex));
				mc.setGameObjectInstanceZ(writeIndex, mc.getGameObjectInstanceZ(readIndex));
				mc.setGameObjectInstanceID(writeIndex, mc.getGameObjectInstanceID(readIndex));
				mc.setGameObjectInstanceDir(writeIndex, mc.getGameObjectInstanceDir(readIndex));
				mc.setGameObjectInstanceModel(writeIndex, mc.getGameObjectInstanceModel(readIndex));
				mc.setGameObjectInstanceMaterialized(writeIndex, mc.isGameObjectInstanceMaterialized(readIndex));
				mc.setGameObjectInstancePendingAreaLoad(writeIndex, mc.isGameObjectInstancePendingAreaLoad(readIndex));
			}
			writeIndex++;
		}
		mc.setGameObjectInstanceCount(writeIndex);
		return pruned;
	}

	private int pruneWallObjectsOutsideSyncRange(mudclient mc) {
		int writeIndex = 0;
		int pruned = 0;
		for (int readIndex = 0; readIndex < mc.getWallObjectInstanceCount(); readIndex++) {
			int worldX = mc.getWallObjectInstanceX(readIndex) + mc.getMidRegionBaseX();
			int worldY = mc.getWallObjectInstanceZ(readIndex) + mc.getMidRegionBaseZ();
			if (!insideObjectSyncRange(worldX, worldY)) {
				mc.dematerializeWallObjectInstance(readIndex);
				pruned++;
				continue;
			}

			if (writeIndex != readIndex) {
				mc.setWallObjectInstanceModel(writeIndex, mc.getWallObjectInstanceModel(readIndex));
				mc.setWallObjectInstanceX(writeIndex, mc.getWallObjectInstanceX(readIndex));
				mc.setWallObjectInstanceZ(writeIndex, mc.getWallObjectInstanceZ(readIndex));
				mc.setWallObjectInstanceDir(writeIndex, mc.getWallObjectInstanceDir(readIndex));
				mc.setWallObjectInstanceID(writeIndex, mc.getWallObjectInstanceID(readIndex));
				mc.setWallObjectInstanceMaterialized(writeIndex, mc.isWallObjectInstanceMaterialized(readIndex));
				mc.setWallObjectInstancePendingAreaLoad(writeIndex, mc.isWallObjectInstancePendingAreaLoad(readIndex));
			}
			writeIndex++;
		}
		mc.setWallObjectInstanceCount(writeIndex);
		return pruned;
	}

	private String parityLine(mudclient mc) {
		if (mc == null || !hasStoredCompleteBaseline()) {
			return "scene sync match waiting";
		}

		long now = System.currentTimeMillis();
		int legacySignature = legacySceneSignature(mc);
		if (now - lastParityCheckMillis < PARITY_REFRESH_MILLIS
			&& legacySignature == lastParityLegacySignature) {
			return lastParityLine;
		}

		lastParityCheckMillis = now;
		lastParityLegacySignature = legacySignature;
		ParityResult sceneryParity = compareStoredToLegacy(storedSceneryRecords, mc, true);
		ParityResult wallParity = compareStoredToLegacy(storedWallRecords, mc, false);
		lastParityLine = "scene sync match " + paritySummary(sceneryParity, wallParity)
			+ " | legacy objects/walls " + mc.getGameObjectInstanceCount() + "/" + mc.getWallObjectInstanceCount();
		return lastParityLine;
	}

	private String paritySummary(ParityResult sceneryParity, ParityResult wallParity) {
		if (sceneryParity.matches() && wallParity.matches()) {
			return "ok";
		}
		return "objects " + sceneryParity.compactSummary() + " walls " + wallParity.compactSummary();
	}

	private int expectedStaticPages() {
		return expectedPages.getOrDefault(PAGE_SCENERY, 0) + expectedPages.getOrDefault(PAGE_WALLS, 0);
	}

	private int duplicatePageTotal() {
		return duplicatePages.getOrDefault(PAGE_SCENERY, 0) + duplicatePages.getOrDefault(PAGE_WALLS, 0);
	}

	private int legacySceneSignature(mudclient mc) {
		int signature = mc.getMidRegionBaseX();
		signature = signature * 31 + mc.getMidRegionBaseZ();
		signature = signature * 31 + mc.getGameObjectInstanceCount();
		for (int i = 0; i < mc.getGameObjectInstanceCount(); i++) {
			signature = addSceneIdentity(signature, sceneIdentity(
				mc.getGameObjectInstanceID(i),
				mc.getGameObjectInstanceX(i) + mc.getMidRegionBaseX(),
				mc.getGameObjectInstanceZ(i) + mc.getMidRegionBaseZ(),
				mc.getGameObjectInstanceDir(i),
				0));
		}
		signature = signature * 31 + mc.getWallObjectInstanceCount();
		for (int i = 0; i < mc.getWallObjectInstanceCount(); i++) {
			signature = addSceneIdentity(signature, sceneIdentity(
				mc.getWallObjectInstanceID(i),
				mc.getWallObjectInstanceX(i) + mc.getMidRegionBaseX(),
				mc.getWallObjectInstanceZ(i) + mc.getMidRegionBaseZ(),
				mc.getWallObjectInstanceDir(i),
				0));
		}
		return signature;
	}

	private ParityResult compareStoredToLegacy(List<Record> storedRecords, mudclient mc, boolean scenery) {
		Map<Long, Integer> expected = new HashMap<Long, Integer>();
		for (Record record : storedRecords) {
			incrementKey(expected, sceneRecordKey(record.id, record.x, record.y, record.direction));
		}

		int legacyCount = scenery ? mc.getGameObjectInstanceCount() : mc.getWallObjectInstanceCount();
		int legacyExtra = 0;
		int legacyExtraInsideSyncRange = 0;
		int legacyExtraOutsideSyncRange = 0;
		for (int i = 0; i < legacyCount; i++) {
			int id = scenery ? mc.getGameObjectInstanceID(i) : mc.getWallObjectInstanceID(i);
			int x = (scenery ? mc.getGameObjectInstanceX(i) : mc.getWallObjectInstanceX(i))
				+ mc.getMidRegionBaseX();
			int y = (scenery ? mc.getGameObjectInstanceZ(i) : mc.getWallObjectInstanceZ(i))
				+ mc.getMidRegionBaseZ();
			int direction = scenery ? mc.getGameObjectInstanceDir(i) : mc.getWallObjectInstanceDir(i);
			long key = sceneRecordKey(id, x, y, direction);
			Integer count = expected.get(key);
			if (count == null || count == 0) {
				legacyExtra++;
				if (insideObjectSyncRange(x, y)) {
					legacyExtraInsideSyncRange++;
				} else {
					legacyExtraOutsideSyncRange++;
				}
			} else if (count == 1) {
				expected.remove(key);
			} else {
				expected.put(key, count - 1);
			}
		}

		int baselineMissingFromLegacy = 0;
		for (Integer count : expected.values()) {
			baselineMissingFromLegacy += count;
		}
		return new ParityResult(
			legacyExtra,
			baselineMissingFromLegacy,
			legacyExtraInsideSyncRange,
			legacyExtraOutsideSyncRange);
	}

	private boolean insideObjectSyncRange(int x, int y) {
		if (objectViewDistance <= 0) {
			return true;
		}
		int xDiff = (localX >> 3) - (x >> 3);
		int yDiff = (localY >> 3) - (y >> 3);
		return xDiff <= objectViewDistance
			&& xDiff >= -objectViewDistance
			&& yDiff <= objectViewDistance
			&& yDiff >= -objectViewDistance;
	}

	private void incrementKey(Map<Long, Integer> counts, long key) {
		counts.put(key, counts.getOrDefault(key, 0) + 1);
	}

	private long sceneRecordKey(int id, int x, int y, int direction) {
		return ((long)id & 0xFFFFL) << 48
			| ((long)x & 0xFFFFL) << 32
			| ((long)y & 0xFFFFL) << 16
			| ((long)direction & 0xFFL) << 8;
	}

	private String baselineState() {
		if (hasStoredCompleteBaseline()) {
			return "complete";
		}
		long started = baselineStartedMillis;
		if (started > 0L && System.currentTimeMillis() - started > SCENE_BASELINE_STALE_MILLIS) {
			return "stale";
		}
		return "loading";
	}

	private String verboseSummary() {
		return "sceneBaseline v" + protocolVersion
			+ " tick=" + serverTick
			+ " origin=" + localX + "," + localY
			+ " static=" + scenery + "/" + walls + "/" + groundItems
			+ " packets=" + packets
			+ " pages=" + pages
			+ " records=" + records
			+ " sceneryPages=" + pageSummary(PAGE_SCENERY)
			+ " wallPages=" + pageSummary(PAGE_WALLS);
	}

	private String compactPageSummary(int pageCategory) {
		return receivedPages.getOrDefault(pageCategory, 0)
			+ "/" + expectedPages.getOrDefault(pageCategory, 0)
			+ " dup " + duplicatePages.getOrDefault(pageCategory, 0)
			+ " rec " + receivedRecords.getOrDefault(pageCategory, 0);
	}

	private String pageSummary(int pageCategory) {
		return receivedPages.getOrDefault(pageCategory, 0)
			+ "/" + expectedPages.getOrDefault(pageCategory, 0)
			+ " dup=" + duplicatePages.getOrDefault(pageCategory, 0)
			+ " records=" + receivedRecords.getOrDefault(pageCategory, 0);
	}

	static final class Record {
		final int id;
		final int x;
		final int y;
		final int direction;
		final int type;

		Record(int id, int x, int y, int direction, int type) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.direction = direction;
			this.type = type;
		}
	}

	private static final class ParityResult {
		private final int legacyExtra;
		private final int baselineMissingFromLegacy;
		private final int legacyExtraInsideSyncRange;
		private final int legacyExtraOutsideSyncRange;

		private ParityResult(
			int legacyExtra,
			int baselineMissingFromLegacy,
			int legacyExtraInsideSyncRange,
			int legacyExtraOutsideSyncRange) {
			this.legacyExtra = legacyExtra;
			this.baselineMissingFromLegacy = baselineMissingFromLegacy;
			this.legacyExtraInsideSyncRange = legacyExtraInsideSyncRange;
			this.legacyExtraOutsideSyncRange = legacyExtraOutsideSyncRange;
		}

		private boolean matches() {
			return legacyExtra == 0 && baselineMissingFromLegacy == 0;
		}

		private String compactSummary() {
			if (matches()) {
				return "ok";
			}
			return "+" + legacyExtra
				+ " in/out " + legacyExtraInsideSyncRange + "/" + legacyExtraOutsideSyncRange
				+ " -" + baselineMissingFromLegacy;
		}
	}
}
