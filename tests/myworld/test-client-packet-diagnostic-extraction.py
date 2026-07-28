#!/usr/bin/env python3
"""Exercise B08 movement-diagnostic and scene-baseline ownership."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[2]
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
MOVEMENT_DIAGNOSTICS = ROOT / "Client_Base/src/orsc/MovementSnapshotDiagnostics.java"
SCENE_BASELINE_STATE = ROOT / "Client_Base/src/orsc/SceneBaselineState.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def ordered(source: str, snippets: tuple[str, ...], message: str) -> None:
    positions = [source.index(snippet) for snippet in snippets]
    require(positions == sorted(positions), message)


def method(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    finish = source.index(end, begin)
    return source[begin:finish]


def run_fixture(
    name: str,
    production_sources: list[Path],
    fixture_sources: dict[str, str],
) -> str:
    javac = shutil.which("javac")
    java = shutil.which("java")
    require(javac is not None and java is not None, "Java compiler/runtime are required")
    with tempfile.TemporaryDirectory(prefix=f"{name}-") as directory:
        output = Path(directory)
        source_paths = []
        for filename, source in fixture_sources.items():
            path = output / filename
            path.write_text(textwrap.dedent(source), encoding="utf-8")
            source_paths.append(path)
        result = subprocess.run(
            [
                javac,
                "-source",
                "8",
                "-target",
                "8",
                "-d",
                str(output),
                *[str(path) for path in production_sources],
                *[str(path) for path in source_paths],
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        require(result.returncode == 0, f"{name} compile failed:\n{result.stderr}")
        result = subprocess.run(
            [java, "-cp", str(output), f"orsc.{name}"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        require(result.returncode == 0, f"{name} failed:\n{result.stderr}")
        return result.stdout.strip()


def verify_source_ownership_and_order() -> None:
    packet = PACKET_HANDLER.read_text(encoding="utf-8")
    movement = MOVEMENT_DIAGNOSTICS.read_text(encoding="utf-8")
    scene = SCENE_BASELINE_STATE.read_text(encoding="utf-8")

    for moved_symbol in (
        "class MovementPacketFingerprint",
        "class MovementPacketDebugState",
        "class MovementSnapshotParity",
        "class MovementSnapshotDebugState",
        "class SceneBaselineRecord",
        "class SceneBaselineDebugState",
        "RECENT_MOVE_CACHE_LOG_LIMIT",
        "RECENT_SCENE_SYNC_LOG_LIMIT",
        "receivedPageRecords",
        "storedSceneryRecords",
    ):
        require(moved_symbol not in packet, f"PacketHandler still owns {moved_symbol}")

    require("final class MovementSnapshotDiagnostics" in movement, "movement owner missing")
    require("final class SceneBaselineState" in scene, "scene-baseline owner missing")
    require(
        "private final MovementSnapshotDiagnostics movementSnapshotDiagnostics" in packet,
        "PacketHandler movement delegate missing",
    )
    require(
        "private final SceneBaselineState sceneBaselineState" in packet,
        "PacketHandler scene-baseline delegate missing",
    )
    require("private void addBaselineGameObject(" in packet, "scene mutation left PacketHandler")
    require("private void addBaselineWallObject(" in packet, "wall mutation left PacketHandler")

    movement_update = method(packet, "private void updateMovement()", "private void updateMovementSnapshot(")
    ordered(
        movement_update,
        (
            "int localX = packetsIncoming.getShort();",
            "int localZ = packetsIncoming.getShort();",
            "int localDirection = packetsIncoming.getUnsignedByte();",
            "movementSnapshotDiagnostics.createFingerprint",
            "mc.applyCustomMovementUpdate(localX, localZ, localDirection);",
            "int playerCount = packetsIncoming.getShort();",
            "fingerprint.addPlayer(serverIndex, x, z, direction);",
            "mc.applyCustomPlayerMovementUpdate(serverIndex, x, z, direction);",
            "int npcCount = packetsIncoming.getShort();",
            "fingerprint.addNpc(serverIndex, x, z, direction);",
            "mc.applyCustomNpcMovementUpdate(serverIndex, x, z, direction);",
            "movementSnapshotDiagnostics.recordMovementUpdate(fingerprint);",
            "MovementTimingDiagnostics.recordMovementPacket(playerCount, npcCount);",
        ),
        "movement update decode/mutation order changed",
    )

    snapshot = method(packet, "private void updateMovementSnapshot(", "private void updateWorldTime(")
    ordered(
        snapshot,
        (
            "protocolVersion = packetsIncoming.getUnsignedByte();",
            "serverTick = packetsIncoming.get32();",
            "sequence = packetsIncoming.get32();",
            "localX = packetsIncoming.getShort();",
            "localZ = packetsIncoming.getShort();",
            "localDirection = packetsIncoming.getUnsignedByte();",
            "stageFrame.setLocal(localX, localZ, localDirection);",
            "mc.applyCustomMovementUpdate(localX, localZ, localDirection);",
            "parity.checkLocal(mc, localX, localZ, localDirection);",
            "playerCount = packetsIncoming.getShort();",
            "snapshotFingerprint.addPlayer(serverIndex, x, z, direction);",
            "stageFrame.addPlayer(serverIndex, x, z, direction);",
            "mc.applyCustomPlayerMovementUpdate(serverIndex, x, z, direction);",
            "parity.checkPlayer(mc, serverIndex, x, z, direction);",
            "npcCount = packetsIncoming.getShort();",
            "snapshotFingerprint.addNpc(serverIndex, x, z, direction);",
            "stageFrame.addNpc(serverIndex, x, z, direction);",
            "mc.applyCustomNpcMovementUpdate(serverIndex, x, z, direction);",
            "parity.checkNpc(mc, serverIndex, x, z, direction);",
            "movementSnapshotStage.replaceFromSnapshot(stageFrame, mc);",
            "movementSnapshotDiagnostics.recordSnapshot(",
            "MovementTimingDiagnostics.recordMovementSnapshot(",
            "packetsIncoming.packetEnd = length;",
        ),
        "movement snapshot decode/mutation order changed",
    )

    scene_packet = method(packet, "private void updateSceneBaseline(", "private void applyCompleteSceneBaselineToLegacyLists()")
    ordered(
        scene_packet,
        (
            "protocolVersion = packetsIncoming.getUnsignedByte();",
            "serverTick = packetsIncoming.get32();",
            "localX = packetsIncoming.getShort();",
            "localY = packetsIncoming.getShort();",
            "pageCategory = packetsIncoming.getUnsignedByte();",
            "pageIndex = packetsIncoming.getShort();",
            "pageTotal = packetsIncoming.getShort();",
            "int recordCount = packetsIncoming.getShort();",
            "pageRecords.add(new SceneBaselineState.Record",
            "sceneBaselineState.recordPacket(",
            "sceneBaselineState.pruneLegacyListsOutsideSyncRange(mc);",
            "applyCompleteSceneBaselineToLegacyLists();",
            "sceneBaselineState.recordSceneDiagnostics(mc);",
        ),
        "scene-baseline decode/apply order changed",
    )
    require(
        scene_packet.index("sceneBaselineState.matchAtomicFence(")
        < scene_packet.index("sceneBaselineState.recordPacket("),
        "atomic scene fence must be intercepted before baseline aggregation",
    )
    require(
        scene_packet.count("packetsIncoming.packetEnd = length;") == 2,
        "atomic fence and normal baseline paths must both consume the packet",
    )


def verify_movement_diagnostics() -> None:
    character = r"""
        package orsc;

        final class ORSCharacter {
            int serverIndex;
            int waypointIndexCurrent;
            int animationNext;
            final int[] waypointsX = new int[10];
            final int[] waypointsZ = new int[10];
        }
    """
    client = r"""
        package orsc;

        final class mudclient {
            private final int baseX;
            private final int baseZ;
            private final int tileSize;
            ORSCharacter local;
            ORSCharacter[] players = new ORSCharacter[0];
            ORSCharacter[] npcs = new ORSCharacter[0];

            mudclient(int baseX, int baseZ, int tileSize) {
                this.baseX = baseX;
                this.baseZ = baseZ;
                this.tileSize = tileSize;
            }

            ORSCharacter getLocalPlayer() { return local; }
            int getPlayerCount() { return players.length; }
            ORSCharacter getPlayer(int index) { return players[index]; }
            int getNpcCount() { return npcs.length; }
            ORSCharacter getNpc(int index) { return npcs[index]; }
            int getTileSize() { return tileSize; }
            int getMidRegionBaseX() { return baseX; }
            int getMidRegionBaseZ() { return baseZ; }
            String describeCustomNpcMovementDebug(int serverIndex) { return "npc#" + serverIndex; }
        }
    """
    stage = r"""
        package orsc;

        final class MovementSnapshotStage {
            static final class Result {
                final boolean ready;
                final int players;
                final int npcs;
                final int checked;
                final int mismatches;
                final String firstMismatch;
                final long lastPacketMillis;

                Result(boolean ready, int players, int npcs, int checked, int mismatches, String firstMismatch) {
                    this.ready = ready;
                    this.players = players;
                    this.npcs = npcs;
                    this.checked = checked;
                    this.mismatches = mismatches;
                    this.firstMismatch = firstMismatch;
                    this.lastPacketMillis = System.currentTimeMillis();
                }
            }
        }
    """
    logger = r"""
        package orsc;

        final class ClientRuntimeLogger {
            static final StringBuilder lines = new StringBuilder();
            static void log(String line) { lines.append(line).append('\n'); }
        }
    """
    fixture = r"""
        package orsc;

        public final class MovementDiagnosticsFixture {
            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            private static ORSCharacter character(
                    int serverIndex, int baseX, int baseZ, int tileSize,
                    int worldX, int worldZ, int direction) {
                ORSCharacter value = new ORSCharacter();
                value.serverIndex = serverIndex;
                value.animationNext = direction;
                value.waypointsX[0] = 64 + (worldX - baseX) * tileSize;
                value.waypointsZ[0] = 64 + (worldZ - baseZ) * tileSize;
                return value;
            }

            public static void main(String[] args) {
                int baseX = 1000;
                int baseZ = 2000;
                int tileSize = 128;
                mudclient client = new mudclient(baseX, baseZ, tileSize);
                client.local = character(7, baseX, baseZ, tileSize, 1005, 2006, 3);
                client.players = new ORSCharacter[] {
                    character(11, baseX, baseZ, tileSize, 1007, 2008, 4)
                };
                client.npcs = new ORSCharacter[] {
                    character(22, baseX, baseZ, tileSize, 1009, 2010, 5)
                };

                MovementSnapshotDiagnostics diagnostics = new MovementSnapshotDiagnostics();
                String[] waiting = diagnostics.summaryLines();
                require("move snap waiting".equals(waiting[0]), "initial snapshot summary");
                require("move cache waiting".equals(waiting[1]), "initial cache summary");

                MovementSnapshotDiagnostics.Fingerprint update =
                    diagnostics.createFingerprint(1005, 2006, 3);
                update.addPlayer(11, 1007, 2008, 4);
                update.addNpc(22, 1009, 2010, 5);
                diagnostics.recordMovementUpdate(update);

                MovementSnapshotDiagnostics.Fingerprint snapshot =
                    diagnostics.createFingerprint(1005, 2006, 3);
                snapshot.addPlayer(11, 1007, 2008, 4);
                snapshot.addNpc(22, 1009, 2010, 5);
                MovementSnapshotDiagnostics.CacheParity parity = diagnostics.createCacheParity();
                parity.checkLocal(client, 1005, 2006, 3);
                parity.checkPlayer(client, 11, 1007, 2008, 4);
                parity.checkNpc(client, 22, 1009, 2010, 5);
                diagnostics.recordSnapshot(
                    1, 10, 20, 1005, 2006, 3, 1, 1, 2,
                    parity, snapshot,
                    new MovementSnapshotStage.Result(true, 1, 1, 3, 0, ""));
                String[] matching = diagnostics.summaryLines();
                require(matching[0].contains("wire ok"), "matching wire summary");
                require(matching[1].contains("cache ok c3"), "matching cache summary");
                require(matching[1].contains("stage ok c3"), "matching stage summary");

                MovementSnapshotDiagnostics.CacheParity mismatch = diagnostics.createCacheParity();
                mismatch.checkLocal(client, 1015, 2016, 3);
                diagnostics.recordSnapshot(
                    1, 11, 21, 1015, 2016, 3, 0, 0, 0,
                    mismatch, diagnostics.createFingerprint(1015, 2016, 3),
                    new MovementSnapshotStage.Result(true, 0, 0, 1, 1, "stage-marker"));
                String[] mismatching = diagnostics.summaryLines();
                require(mismatching[0].contains("wire bad total 1"), "wire mismatch summary");
                require(mismatching[1].contains("cache bad"), "cache mismatch summary");
                require(mismatching[1].contains("stage bad"), "stage mismatch summary");
                require(ClientRuntimeLogger.lines.toString().contains("MOVEMENT_CACHE_RECENT"),
                    "bounded mismatch history log");
                System.out.println("movement-diagnostics-ok");
            }
        }
    """
    output = run_fixture(
        "MovementDiagnosticsFixture",
        [MOVEMENT_DIAGNOSTICS],
        {
            "ORSCharacter.java": character,
            "mudclient.java": client,
            "MovementSnapshotStage.java": stage,
            "ClientRuntimeLogger.java": logger,
            "MovementDiagnosticsFixture.java": fixture,
        },
    )
    require(output.endswith("movement-diagnostics-ok"), "movement diagnostics fixture did not complete")


def verify_scene_baseline_state() -> None:
    world = r"""
        package orsc.graphics.three;

        public final class World {
            public static boolean isLocalTile(int x, int y) { return true; }
        }
    """
    logger = r"""
        package orsc;

        final class ClientRuntimeLogger {
            static final StringBuilder lines = new StringBuilder();
            static void log(String line) { lines.append(line).append('\n'); }
        }
    """
    client = r"""
        package orsc;

        final class mudclient {
            private final int[] objectIds = new int[8];
            private final int[] objectX = new int[8];
            private final int[] objectZ = new int[8];
            private final int[] objectDir = new int[8];
            private final Object[] objectModels = new Object[8];
            private final boolean[] objectMaterialized = new boolean[8];
            private final boolean[] objectPending = new boolean[8];
            private final int[] wallIds = new int[8];
            private final int[] wallX = new int[8];
            private final int[] wallZ = new int[8];
            private final int[] wallDir = new int[8];
            private final Object[] wallModels = new Object[8];
            private final boolean[] wallMaterialized = new boolean[8];
            private final boolean[] wallPending = new boolean[8];
            private int objectCount;
            private int wallCount;

            int getMidRegionBaseX() { return 0; }
            int getMidRegionBaseZ() { return 0; }
            int getGameObjectInstanceCount() { return objectCount; }
            void setGameObjectInstanceCount(int count) { objectCount = count; }
            int getGameObjectInstanceID(int i) { return objectIds[i]; }
            void setGameObjectInstanceID(int i, int value) { objectIds[i] = value; }
            int getGameObjectInstanceX(int i) { return objectX[i]; }
            void setGameObjectInstanceX(int i, int value) { objectX[i] = value; }
            int getGameObjectInstanceZ(int i) { return objectZ[i]; }
            void setGameObjectInstanceZ(int i, int value) { objectZ[i] = value; }
            int getGameObjectInstanceDir(int i) { return objectDir[i]; }
            void setGameObjectInstanceDir(int i, int value) { objectDir[i] = value; }
            Object getGameObjectInstanceModel(int i) { return objectModels[i]; }
            void setGameObjectInstanceModel(int i, Object value) { objectModels[i] = value; }
            boolean isGameObjectInstanceMaterialized(int i) { return objectMaterialized[i]; }
            void setGameObjectInstanceMaterialized(int i, boolean value) { objectMaterialized[i] = value; }
            boolean isGameObjectInstancePendingAreaLoad(int i) { return objectPending[i]; }
            void setGameObjectInstancePendingAreaLoad(int i, boolean value) { objectPending[i] = value; }
            void dematerializeGameObjectInstance(int i) {}

            int getWallObjectInstanceCount() { return wallCount; }
            void setWallObjectInstanceCount(int count) { wallCount = count; }
            int getWallObjectInstanceID(int i) { return wallIds[i]; }
            void setWallObjectInstanceID(int i, int value) { wallIds[i] = value; }
            int getWallObjectInstanceX(int i) { return wallX[i]; }
            void setWallObjectInstanceX(int i, int value) { wallX[i] = value; }
            int getWallObjectInstanceZ(int i) { return wallZ[i]; }
            void setWallObjectInstanceZ(int i, int value) { wallZ[i] = value; }
            int getWallObjectInstanceDir(int i) { return wallDir[i]; }
            void setWallObjectInstanceDir(int i, int value) { wallDir[i] = value; }
            Object getWallObjectInstanceModel(int i) { return wallModels[i]; }
            void setWallObjectInstanceModel(int i, Object value) { wallModels[i] = value; }
            boolean isWallObjectInstanceMaterialized(int i) { return wallMaterialized[i]; }
            void setWallObjectInstanceMaterialized(int i, boolean value) { wallMaterialized[i] = value; }
            boolean isWallObjectInstancePendingAreaLoad(int i) { return wallPending[i]; }
            void setWallObjectInstancePendingAreaLoad(int i, boolean value) { wallPending[i] = value; }
            void dematerializeWallObjectInstance(int i) {}
        }
    """
    fixture = r"""
        package orsc;

        import java.util.ArrayList;
        import java.util.List;

        public final class SceneBaselineStateFixture {
            private static void require(boolean condition, String message) {
                if (!condition) throw new AssertionError(message);
            }

            private static int sceneIdentity(
                    int a, int b, int c, int d, int e) {
                int hash = 0x811C9DC5;
                hash = (hash ^ a) * 0x01000193;
                hash = (hash ^ b) * 0x01000193;
                hash = (hash ^ c) * 0x01000193;
                hash = (hash ^ d) * 0x01000193;
                return (hash ^ e) * 0x01000193;
            }

            private static int addIdentity(int summary, int identity) {
                return summary + identity + Integer.rotateLeft(identity, 16);
            }

            private static void record(
                    SceneBaselineState state, int category,
                    List<SceneBaselineState.Record> records) {
                state.recordPacket(
                    5, 10, 100, 200, 1, 1, 0, 1,
                    101, 202, 303, category, 0, 1, records.size(), records);
            }

            public static void main(String[] args) {
                SceneBaselineState state = new SceneBaselineState();
                require(state.summary().contains("objects/walls/items 0/0/0"), "initial empty summary");

                List<SceneBaselineState.Record> scenery = new ArrayList<SceneBaselineState.Record>();
                scenery.add(new SceneBaselineState.Record(10, 100, 200, 2, 0));
                List<SceneBaselineState.Record> walls = new ArrayList<SceneBaselineState.Record>();
                walls.add(new SceneBaselineState.Record(20, 101, 200, 3, 0));
                record(state, 1, scenery);
                record(state, 2, walls);
                require(state.hasStoredCompleteBaseline(), "completed baseline storage");
                require(state.snapshotStoredSceneryRecords().size() == 1, "stored scenery count");
                require(state.snapshotStoredWallRecords().size() == 1, "stored wall count");
                List<SceneBaselineState.Record> copy = state.snapshotStoredSceneryRecords();
                copy.clear();
                require(state.snapshotStoredSceneryRecords().size() == 1, "defensive scenery snapshot");

                mudclient client = new mudclient();
                client.setGameObjectInstanceID(0, 10);
                client.setGameObjectInstanceX(0, 100);
                client.setGameObjectInstanceZ(0, 200);
                client.setGameObjectInstanceDir(0, 2);
                client.setGameObjectInstanceCount(1);
                client.setWallObjectInstanceID(0, 20);
                client.setWallObjectInstanceX(0, 101);
                client.setWallObjectInstanceZ(0, 200);
                client.setWallObjectInstanceDir(0, 3);
                client.setWallObjectInstanceCount(1);
                require(state.summaryLines(client)[2].contains("scene sync match ok"), "matching legacy parity");
                require(state.isBaselineOriginLoaded(client), "baseline origin check");
                require(state.storedBaselineMatchesLegacy(client),
                    "stored baseline exact-match shortcut");

                int sceneryFenceHash = addIdentity(
                    0, sceneIdentity(10, 2, 100, 200, 0));
                int wallFenceHash = addIdentity(
                    0, sceneIdentity(20, (1 << 8) | 3, 101, 200, 0));
                SceneBaselineState.AtomicFenceResult fence =
                    state.matchAtomicFence(
                        client, 1, 1, sceneryFenceHash, wallFenceHash);
                require(fence.matches(), "matching atomic fence");
                require(fence.summary().contains("match expected/actual"),
                    "atomic fence diagnostic");
                require(!state.matchAtomicFence(
                    client, 1, 1, sceneryFenceHash + 1, wallFenceHash
                ).matches(), "atomic fence hash mismatch");

                client.setGameObjectInstanceID(1, 99);
                client.setGameObjectInstanceX(1, 300);
                client.setGameObjectInstanceZ(1, 400);
                client.setGameObjectInstanceDir(1, 0);
                client.setGameObjectInstanceCount(2);
                state.pruneLegacyListsOutsideAtomicFenceRange(
                    client, 100, 200, 1);
                require(client.getGameObjectInstanceCount() == 1,
                    "atomic-fence out-of-range object pruning");
                require(state.matchAtomicFence(
                    client, 1, 1, sceneryFenceHash, wallFenceHash
                ).matches(), "atomic fence after stale-window pruning");

                record(state, 2, walls);
                state.recordLegacyBaselineApplied();
                String[] summary = state.summaryLines(client);
                require(summary[0].contains("scene sync complete"), "complete summary");
                require(summary[1].contains("duplicate pages 1"), "duplicate page accounting");
                require(summary[1].contains("pruned objects/walls 1/0"), "prune accounting");
                require(summary[1].contains("applied 1"), "apply accounting");
                require(summary[2].contains("scene sync match ok"), "post-prune parity");
                System.out.println("scene-baseline-ok");
            }
        }
    """
    output = run_fixture(
        "SceneBaselineStateFixture",
        [SCENE_BASELINE_STATE],
        {
            "World.java": world,
            "ClientRuntimeLogger.java": logger,
            "mudclient.java": client,
            "SceneBaselineStateFixture.java": fixture,
        },
    )
    require(output == "scene-baseline-ok", "scene baseline fixture did not complete")


def main() -> None:
    verify_source_ownership_and_order()
    verify_movement_diagnostics()
    verify_scene_baseline_state()
    print("PASS: PacketHandler delegates movement diagnostics and scene-baseline state")


if __name__ == "__main__":
    main()
