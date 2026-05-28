#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ENTITY_LIST = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "util" / "EntityList.java"
GAME_STATE_UPDATER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "GameStateUpdater.java"
SERVER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "Server.java"
ACTION_SENDER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "ActionSender.java"
PLAYER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "player" / "Player.java"
NPC = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "Npc.java"
MOB = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "Mob.java"
MOBS_UPDATE_STRUCT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "struct" / "outgoing" / "MobsUpdateStruct.java"
PAYLOAD_235_GENERATOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "generators" / "impl" / "Payload235Generator.java"
BENCHMARK_MATRIX = ROOT / "tools" / "benchmarks" / "benchmark-foundation-matrix.sh"
NPC_BEHAVIOR = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "NpcBehavior.java"
REGION_MANAGER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "world" / "region" / "RegionManager.java"
GAME_EVENT_HANDLER = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "handler" / "GameEventHandler.java"
SERVER_CONFIGURATION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "ServerConfiguration.java"
PLAYER_SAVE_REQUEST = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "login" / "PlayerSaveRequest.java"
PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "Path.java"
PATH_VALIDATION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "PathValidation.java"
WALKING_QUEUE = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "WalkingQueue.java"
WALK_TO_MOB_ACTION = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "action" / "WalkToMobAction.java"
NPC_COMMAND = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "NpcCommand.java"
PVM_MELEE_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "combat" / "PvmMeleeEvent.java"
MYWORLD_CONF = ROOT / "server" / "myworld.conf"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_snippet(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet not in text:
        fail(f"{label} missing expected snippet {snippet!r}")


def reject_snippet(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet in text:
        fail(f"{label} still contains forbidden snippet {snippet!r}")


def main() -> None:
    require_snippet(ENTITY_LIST, "public void forEachLive(final Consumer<? super T> action)", "EntityList live iteration API")
    require_snippet(ENTITY_LIST, "final Object[] liveEntities = entities;", "EntityList direct backing-array iteration")

    require_snippet(GAME_STATE_UPDATER, "world.getPlayers().forEachLive(Player::resetAfterUpdate);", "cleanup player live iteration")
    require_snippet(GAME_STATE_UPDATER, "world.getNpcs().forEachLive(Npc::resetAfterUpdate);", "cleanup NPC live iteration")
    require_snippet(GAME_STATE_UPDATER, "npcs.forEachLive(n -> {", "NPC processing live iteration")
    require_snippet(GAME_STATE_UPDATER, "final ArrayDeque<Damage> npcsNeedingHitsUpdate = new ArrayDeque<>();", "NPC appearance damage queue")
    require_snippet(GAME_STATE_UPDATER, "final ArrayDeque<ChatMessage> npcMessagesNeedingDisplayed = new ArrayDeque<>();", "NPC appearance chat queue")
    require_snippet(GAME_STATE_UPDATER, "private static Map.Entry<Integer, Integer> bit(final int value, final int bits)", "bit entry helper")
    require_snippet(MOBS_UPDATE_STRUCT, "public static final class BitUpdate implements Map.Entry<Integer, Integer>", "bit update entry type")
    require_snippet(MOBS_UPDATE_STRUCT, "private final int key;", "bit update primitive key")
    require_snippet(MOBS_UPDATE_STRUCT, "public int getRawKey()", "bit update primitive getter")
    require_snippet(PAYLOAD_235_GENERATOR, "bit.getRawKey()", "235 generator primitive bit update path")
    require_snippet(GAME_STATE_UPDATER, "new ArrayList<>(1 + (localNpcCount * 3) + (Math.min(255, visibleNpcCount) * 5))", "NPC coordinate list presizing")
    require_snippet(GAME_STATE_UPDATER, "new ArrayList<>(4 + (localPlayerCount * 3) + (Math.min(255, visiblePlayerCount) * 5))", "player coordinate list presizing")
    require_snippet(BENCHMARK_MATRIX, "run_scenario short 20 5", "benchmark matrix short scenario")
    require_snippet(BENCHMARK_MATRIX, "run_scenario soak 300 30", "benchmark matrix soak scenario")
    require_snippet(BENCHMARK_MATRIX, "run_scenario players 120 10 64", "benchmark matrix player-load scenario")

    require_snippet(SERVER, "getWorld().getNpcs().forEachLive(npc -> {", "server tick NPC debug cleanup live iteration")
    require_snippet(SERVER, "world.getNpcs().forEachLive(npc -> {", "custom walk-speed NPC live iteration")
    require_snippet(SERVER, "initializeBenchmarkSyntheticPlayers();", "benchmark synthetic player bootstrap")
    require_snippet(SERVER, "syntheticPlayers=", "benchmark summary synthetic player count")
    require_snippet(SERVER, "avgUpdateClientsMsPrecise=", "benchmark precise update-client timing")
    require_snippet(SERVER, "avgUpdatePlayersMsPrecise=", "benchmark update player coordinate timing")
    require_snippet(SERVER, "avgUpdateNpcsMsPrecise=", "benchmark update NPC coordinate timing")
    require_snippet(SERVER, "avgProcessNpcBehaviorMsPrecise=", "benchmark NPC behavior timing")
    require_snippet(SERVER, "avgProcessNpcMovementMsPrecise=", "benchmark NPC movement timing")
    require_snippet(SERVER, "avgNpcBehaviorRoamMsPrecise=", "benchmark NPC roam timing")
    require_snippet(SERVER, "avgNpcRoamEligibilityMsPrecise=", "benchmark NPC roam eligibility timing")
    require_snippet(SERVER, "avgNpcRoamAggroScanMsPrecise=", "benchmark NPC roam aggro-scan timing")
    require_snippet(SERVER, "avgNpcRoamTackleScanMsPrecise=", "benchmark NPC roam tackle-scan timing")
    require_snippet(SERVER, "avgNpcRoamRandomWalkMsPrecise=", "benchmark NPC roam random-walk timing")
    require_snippet(SERVER, "saveRequests=", "benchmark save request count")
    require_snippet(SERVER, "saveLogoutRequests=", "benchmark save logout request count")
    require_snippet(SERVER, "avgSaveQueueMsPrecise=", "benchmark save queue timing")
    require_snippet(SERVER, "avgSaveProcessMsPrecise=", "benchmark save process timing")
    require_snippet(SERVER, "recordPlayerSaveTiming", "save timing recorder")
    require_snippet(SERVER, "public boolean isFoundationBenchmarkEnabled()", "benchmark-gated deep attribution")
    require_snippet(SERVER, "nanosToMillisPrecise", "benchmark precise timing formatter")
    require_snippet(GAME_STATE_UPDATER, "recordUpdatePlayers(() -> updatePlayers(player));", "packet section player timing")
    require_snippet(GAME_STATE_UPDATER, "recordUpdateNpcs(() -> updateNpcs(player));", "packet section NPC timing")
    require_snippet(GAME_STATE_UPDATER, "private static int mobCoordOffset", "primitive mob coordinate offset helper")
    require_snippet(GAME_STATE_UPDATER, "final Collection<Player> visiblePlayers = playerToUpdate.getViewArea().getPlayersInView();", "player update visible-player reuse")
    require_snippet(GAME_STATE_UPDATER, "final Collection<GameObject> visibleGameObjects = player.getViewArea().getGameObjectsInView();", "packet object view reuse")
    require_snippet(GAME_STATE_UPDATER, "recordUpdateWallObjects(() -> updateWallObjects(player, visibleGameObjects));", "wall object update view reuse")
    require_snippet(GAME_STATE_UPDATER, "final boolean hasPlayers = getServer().getWorld().getPlayers().size() > 0;", "per-tick player-presence check")
    require_snippet(GAME_STATE_UPDATER, "behaviorDuration[0] += getServer().bench(() -> n.updateBehavior(hasPlayers));", "NPC behavior attribution")
    require_snippet(GAME_STATE_UPDATER, "movementDuration[0] += getServer().bench(n::updateMovementOnly);", "NPC movement attribution")
    require_snippet(GAME_STATE_UPDATER, "if (!getServer().isFoundationBenchmarkEnabled())", "low-overhead NPC processing path")
    require_snippet(NPC_BEHAVIOR, "if (!server.isFoundationBenchmarkEnabled())", "low-overhead NPC behavior path")
    require_snippet(NPC_BEHAVIOR, "incrementLastNpcBehaviorRoamDuration", "NPC behavior roam attribution")
    require_snippet(NPC_BEHAVIOR, "incrementLastNpcBehaviorAggroDuration", "NPC behavior aggro attribution")
    require_snippet(NPC_BEHAVIOR, "this.plaguedSheep =", "cached plagued sheep roam check")
    require_snippet(NPC_BEHAVIOR, "this.gnomeBaller =", "cached gnome baller roam check")
    require_snippet(NPC, "public void updateBehavior(final boolean hasPlayers)", "NPC behavior player-presence overload")
    require_snippet(NPC_BEHAVIOR, "public void tick(final boolean hasPlayers)", "NPC behavior tick player-presence overload")
    require_snippet(NPC_BEHAVIOR, "handleRoam(now, hasPlayers)", "empty-world NPC roam scan guard")
    require_snippet(NPC_BEHAVIOR, "handleRoamProfiled(final long now, final boolean hasPlayers, final Server server)", "profiled NPC roam attribution")
    require_snippet(NPC_BEHAVIOR, "handleRoamEligibility()", "NPC roam eligibility attribution")
    require_snippet(NPC_BEHAVIOR, "handleRoamAggroScan(final long now, final boolean hasPlayers)", "NPC roam aggro-scan attribution")
    require_snippet(NPC_BEHAVIOR, "handleRoamTackleScan(final long now, final boolean hasPlayers)", "NPC roam tackle-scan attribution")
    require_snippet(NPC_BEHAVIOR, "handleRoamRandomWalk(final long now, final boolean hasPlayers)", "NPC roam random-walk attribution")
    require_snippet(NPC_BEHAVIOR, "if (!hasPlayers) {\n\t\t\treturn;\n\t\t}", "empty-world random-roam skip")
    require_snippet(NPC_BEHAVIOR, "hasLocalPlayers(npc)", "unobserved NPC random-roam skip")
    require_snippet(REGION_MANAGER, "public boolean hasLocalPlayers(final Entity entity)", "short-circuit local player lookup")
    require_snippet(GAME_EVENT_HANDLER, "private boolean shouldExecuteDirectly()", "direct default event execution gate")
    require_snippet(GAME_EVENT_HANDLER, "return !getServer().getConfig().WANT_THREADING__BREAK_PID_PRIORITY;", "direct event execution config gate")
    require_snippet(GAME_EVENT_HANDLER, "executeDirectly(eventStore.getNonPlayerEvents(), \"processNonPlayerEvents()\")", "direct non-player event execution")
    require_snippet(GAME_EVENT_HANDLER, "executeDirectly(eventStore.getPlayerEvents(player.getUsernameHash())", "direct player event execution")
    require_snippet(GAME_EVENT_HANDLER, "executor.invokeAll(eventStore.getNonPlayerEvents())", "threaded non-player event fallback")
    require_snippet(SERVER_CONFIGURATION, "public boolean WANT_FORCE_GC_ON_PROFILING;", "forced-GC profiling config field")
    require_snippet(SERVER_CONFIGURATION, "tryReadBool(\"want_force_gc_on_profiling\").orElse(false)", "forced-GC profiling default false")
    require_snippet(GAME_EVENT_HANDLER, "final boolean forcedGc = getServer().getConfig().WANT_FORCE_GC_ON_PROFILING;", "forced-GC profiling gate")
    require_snippet(GAME_EVENT_HANDLER, "if (forcedGc) {\n\t\t\tSystem.gc();\n\t\t}", "forced-GC profiling gated call")
    require_snippet(GAME_EVENT_HANDLER, "Memory\" + memoryMode", "profiling memory mode label")
    require_snippet(MYWORLD_CONF, "want_force_gc_on_profiling: false", "MyWorld forced-GC profiling disabled")
    require_snippet(PATH, "private final Deque<Point> waypoints = new ArrayDeque<>(MAXIMUM_SIZE);", "Path ArrayDeque backing queue")
    reject_snippet(PATH, "LinkedList", "Path linked-list backing queue")
    require_snippet(PATH, "PathValidation.checkAdjacent(mob, lastX, lastY, nextX, xOnlyY)", "Path primitive X-adjacent check")
    require_snippet(PATH, "PathValidation.checkAdjacent(mob, nextX, xOnlyY, nextX, nextY)", "Path primitive zigzag check")
    reject_snippet(PATH, "PathValidation.checkAdjacent(mob, last, new Point", "Path throwaway adjacent destination point")
    require_snippet(PATH_VALIDATION, "final Deque<Point> path = new ArrayDeque<>();", "PathValidation transient path queue")
    require_snippet(PATH_VALIDATION, "new ArrayDeque<>(path.getWaypoints())", "PathValidation existing path copy queue")
    require_snippet(PATH_VALIDATION, "public static boolean checkAdjacentDistance(World world, int startX, int startY, int destX, int destY", "primitive adjacent-distance overload")
    require_snippet(PATH_VALIDATION, "public static boolean checkAdjacent(Mob mob, int startX, int startY, int destX, int destY)", "primitive adjacent overload")
    require_snippet(PATH_VALIDATION, "checkDiagonalPassThroughCollisions(World world, int x, int y, int x_next, int y_next)", "primitive diagonal pass-through helper")
    require_snippet(WALKING_QUEUE, "PathValidation.checkAdjacent(mob, startX, startY, destX, destY)", "WalkingQueue primitive movement adjacent check")
    require_snippet(WALKING_QUEUE, "PathValidation.checkAdjacent(mob, curPoint.getX(), curPoint.getY(), destPoint.getX(), destPoint.getY())", "WalkingQueue primitive next-movement adjacent check")
    reject_snippet(WALKING_QUEUE, "PathValidation.checkAdjacent(mob, new Point", "WalkingQueue throwaway adjacent point")
    require_snippet(MOB, "PathValidation.checkAdjacentDistance(getWorld(), getX(), getY(), mob.getX(), mob.getY(), true, false)", "Mob following primitive adjacent-distance check")
    require_snippet(PLAYER, "PathValidation.checkAdjacentDistance(getWorld(), getX(), getY(), getFollowing().getX(), getFollowing().getY(), true, false)", "Player following primitive adjacent-distance check")
    require_snippet(NPC_BEHAVIOR, "PathValidation.checkAdjacentDistance(npc.getWorld(), checkedPoint.getX(), checkedPoint.getY(), target.getX(), target.getY(), true, false)", "NPC behavior primitive adjacent-distance check")
    require_snippet(WALK_TO_MOB_ACTION, "checkedPoint.getX(), checkedPoint.getY(), mob.getX(), mob.getY()", "walk-to-mob primitive adjacent-distance check")
    require_snippet(NPC_COMMAND, "PathValidation.checkAdjacentDistance(player.getWorld(), player.getX(), player.getY(), affectedNpc.getX(), affectedNpc.getY(), true)", "NPC command primitive adjacent-distance check")
    require_snippet(PVM_MELEE_EVENT, "boolean adjacent = PathValidation.checkAdjacentDistance(attackerMob.getWorld(),", "PVM melee cached primitive adjacent check")
    require_snippet(PVM_MELEE_EVENT, "attackerMob.getX(), attackerMob.getY(), targetMob.getX(), targetMob.getY(), true, false)", "PVM melee primitive adjacent coordinates")
    reject_snippet(PVM_MELEE_EVENT, "PathValidation.checkAdjacentDistance(attackerMob.getWorld(), attackerMob.getLocation()", "PVM melee object adjacent-distance check")
    require_snippet(PLAYER_SAVE_REQUEST, "private final long queuedAtNanos;", "save request enqueue timestamp")
    require_snippet(PLAYER_SAVE_REQUEST, "final long processStartedAtNanos = System.nanoTime();", "save request process timestamp")
    require_snippet(PLAYER_SAVE_REQUEST, "getServer().recordPlayerSaveTiming(this.logout", "save request timing recorder")
    require_snippet(PLAYER, "final Integer knownAppearanceID = knownPlayersAppearanceIDs.get(usernameHash);", "direct appearance cache lookup")
    require_snippet(ACTION_SENDER, "private static final PayloadGenerator<OpcodeOut> PAYLOAD_235_GENERATOR", "cached 235 payload generator")
    require_snippet(ACTION_SENDER, "generator = PAYLOAD_235_GENERATOR;", "cached payload generator dispatch")

    print("PASS: foundation optimization live-iteration guards validated")


if __name__ == "__main__":
    main()
