#!/usr/bin/env python3
"""Exercise client game/wall instance storage and its compatibility boundary."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
OWNER = ROOT / "Client_Base/src/orsc/ClientSceneInstanceStore.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PACKET_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
BASELINE = ROOT / "Client_Base/src/orsc/SceneBaselineState.java"


FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.RSModel;

public final class ClientSceneInstanceStoreFixture {
	private ClientSceneInstanceStoreFixture() {
	}

	public static void main(String[] args) {
		defaultsAndGrowth();
		identityFlagsAndShrinkCleanup();
		pendingCompactionAndFrameMarks();
	}

	private static void defaultsAndGrowth() {
		assertEquals(20000, ClientSceneInstanceStore.WALL_OBJECT_KEY_BASE, "wall key base");
		assertEquals(20000, ClientSceneInstanceStore.GAME_OBJECT_INITIAL_CAPACITY,
			"game initial capacity");
		assertEquals(5000, ClientSceneInstanceStore.WALL_OBJECT_INITIAL_CAPACITY,
			"wall initial capacity");
		assertEquals(130, ClientSceneInstanceStore.growCapacity(2, 3), "small growth formula");
		assertEquals(7500, ClientSceneInstanceStore.growCapacity(5000, 5001),
			"half-capacity growth formula");
		assertEquals(Integer.MAX_VALUE,
			ClientSceneInstanceStore.growCapacity(Integer.MAX_VALUE - 16, Integer.MAX_VALUE),
			"overflow-safe growth fallback");

		ClientSceneInstanceStore store = new ClientSceneInstanceStore(2, 1);
		assertEquals(0, store.getGameObjectCount(), "empty game count");
		assertEquals(0, store.getWallObjectCount(), "empty wall count");
		assertEquals(0L, store.getGameObjectRevision(), "empty game revision");
		store.setGameObjectCount(0);
		store.setWallObjectCount(0);
		store.clearPendingAreaLoadMarks();
		store.clearFrameMarks();

		store.setGameObjectX(2, 31);
		store.setGameObjectZ(2, 47);
		store.setGameObjectId(2, 53);
		assertEquals(130, store.getGameObjectCapacity(), "game growth boundary");
		assertEquals(31, store.getGameObjectX(2), "grown game x");
		assertEquals(47, store.getGameObjectZ(2), "grown game z");
		assertEquals(3L, store.getGameObjectRevision(), "identity fields advance revision");
		store.setGameObjectX(2, 31);
		store.setGameObjectZ(2, 47);
		store.setGameObjectId(2, 53);
		assertEquals(3L, store.getGameObjectRevision(), "unchanged identity preserves revision");
		store.setGameObjectCount(3);
		assertEquals(4L, store.getGameObjectRevision(), "count change advances revision");
		store.setGameObjectCount(3);
		assertEquals(4L, store.getGameObjectRevision(), "unchanged count preserves revision");

		store.setWallObjectX(1, 53);
		store.setWallObjectZ(1, 59);
		assertEquals(129, store.getWallObjectCapacity(), "wall growth boundary");
		assertEquals(53, store.getWallObjectX(1), "grown wall x");
		assertEquals(59, store.getWallObjectZ(1), "grown wall z");
	}

	private static void identityFlagsAndShrinkCleanup() {
		ClientSceneInstanceStore store = new ClientSceneInstanceStore(2, 2);
		RSModel gameModel = model();
		store.setGameObjectX(1, 101);
		store.setGameObjectZ(1, 202);
		store.setGameObjectId(1, 303);
		store.setGameObjectDirection(1, 4);
		store.setGameObjectModel(1, gameModel);
		store.setGameObjectMaterialized(1, true);
		store.setGameObjectPendingAreaLoad(1, true);
		store.setGameObjectCount(2);
		assertEquals(101, store.getGameObjectX(1), "game x identity");
		assertEquals(202, store.getGameObjectZ(1), "game z identity");
		assertEquals(303, store.getGameObjectId(1), "game id identity");
		assertEquals(4, store.getGameObjectDirection(1), "game direction identity");
		assertSame(gameModel, store.getGameObjectModel(1), "game model identity");
		assertEquals(Renderer3DModelKind.GAME_OBJECT, gameModel.getRenderer3DModelKind(),
			"game model kind");
		assertEquals(1, gameModel.key, "game model key");

		RSModel wallModel = model();
		store.setWallObjectX(1, 404);
		store.setWallObjectZ(1, 505);
		store.setWallObjectId(1, 606);
		store.setWallObjectDirection(1, 3);
		store.setWallObjectModel(1, wallModel);
		store.setWallObjectMaterialized(1, true);
		store.setWallObjectPendingAreaLoad(1, true);
		store.setWallObjectCount(2);
		assertEquals(404, store.getWallObjectX(1), "wall x identity");
		assertEquals(505, store.getWallObjectZ(1), "wall z identity");
		assertEquals(606, store.getWallObjectId(1), "wall id identity");
		assertEquals(3, store.getWallObjectDirection(1), "wall direction identity");
		assertSame(wallModel, store.getWallObjectModel(1), "wall model identity");
		assertEquals(Renderer3DModelKind.WALL_OBJECT, wallModel.getRenderer3DModelKind(),
			"wall model kind");
		assertEquals(ClientSceneInstanceStore.WALL_OBJECT_KEY_BASE + 1, wallModel.key,
			"wall model key");

		store.setGameObjectCount(1);
		assertEquals(null, store.getGameObjectModel(1), "game shrink nulls model");
		assertTrue(!store.isGameObjectMaterialized(1), "game shrink clears materialized");
		assertTrue(!store.isGameObjectPendingAreaLoad(1), "game shrink clears pending");
		store.setWallObjectCount(1);
		assertEquals(null, store.getWallObjectModel(1), "wall shrink nulls model");
		assertTrue(!store.isWallObjectMaterialized(1), "wall shrink clears materialized");
		assertTrue(!store.isWallObjectPendingAreaLoad(1), "wall shrink clears pending");

		store.setGameObjectCount(0);
		store.setGameObjectCount(0);
		store.setWallObjectCount(0);
		store.setWallObjectCount(0);
	}

	private static void pendingCompactionAndFrameMarks() {
		ClientSceneInstanceStore store = new ClientSceneInstanceStore(4, 4);
		RSModel[] gameModels = {model(), model(), model(), model()};
		for (int i = 0; i < gameModels.length; i++) {
			store.setGameObjectX(i, 10 + i);
			store.setGameObjectZ(i, 20 + i);
			store.setGameObjectId(i, 30 + i);
			store.setGameObjectDirection(i, i);
			store.setGameObjectModel(i, gameModels[i]);
			store.setGameObjectMaterialized(i, true);
			store.setGameObjectPendingAreaLoad(i, i == 1 || i == 3);
		}
		store.setGameObjectCount(4);

		RSModel[] wallModels = {model(), model(), model()};
		for (int i = 0; i < wallModels.length; i++) {
			store.setWallObjectX(i, 40 + i);
			store.setWallObjectZ(i, 50 + i);
			store.setWallObjectId(i, 60 + i);
			store.setWallObjectDirection(i, i);
			store.setWallObjectModel(i, wallModels[i]);
			store.setWallObjectMaterialized(i, false);
			store.setWallObjectPendingAreaLoad(i, i == 0 || i == 2);
		}
		store.setWallObjectCount(3);

		store.retainPendingAreaLoadInstances();
		assertEquals(2, store.getGameObjectCount(), "compacted game count");
		assertEquals(11, store.getGameObjectX(0), "first compacted game row");
		assertEquals(13, store.getGameObjectX(1), "second compacted game row");
		assertSame(gameModels[1], store.getGameObjectModel(0), "first compacted game model");
		assertSame(gameModels[3], store.getGameObjectModel(1), "second compacted game model");
		assertEquals(0, gameModels[1].key, "first compacted game key");
		assertEquals(1, gameModels[3].key, "second compacted game key");
		assertTrue(!store.isGameObjectMaterialized(0), "first compacted game dematerialized");
		assertTrue(!store.isGameObjectMaterialized(1), "second compacted game dematerialized");
		assertTrue(!store.isGameObjectPendingAreaLoad(0), "first compacted game pending cleared");
		assertTrue(!store.isGameObjectPendingAreaLoad(1), "second compacted game pending cleared");
		assertEquals(null, store.getGameObjectModel(2), "discarded game model cleared");

		assertEquals(2, store.getWallObjectCount(), "compacted wall count");
		assertEquals(40, store.getWallObjectX(0), "same-index retained wall row");
		assertSame(wallModels[0], store.getWallObjectModel(0),
			"same-index retained wall keeps model");
		assertEquals(42, store.getWallObjectX(1), "moved retained wall row");
		assertEquals(null, store.getWallObjectModel(1), "moved retained wall clears model");
		assertTrue(!store.isWallObjectPendingAreaLoad(0), "first compacted wall pending cleared");
		assertTrue(!store.isWallObjectPendingAreaLoad(1), "second compacted wall pending cleared");
		assertEquals(null, store.getWallObjectModel(2), "discarded wall model cleared");

		store.markGameObjectForFrame(0);
		store.markWallObjectForFrame(1);
		assertTrue(store.isGameObjectFrameMarked(0), "game frame mark");
		assertTrue(store.isWallObjectFrameMarked(1), "wall frame mark");
		store.clearFrameMarks();
		assertTrue(!store.isGameObjectFrameMarked(0), "game frame mark reset");
		assertTrue(!store.isWallObjectFrameMarked(1), "wall frame mark reset");

		store.setGameObjectPendingAreaLoad(0, true);
		store.setWallObjectPendingAreaLoad(0, true);
		store.clearPendingAreaLoadMarks();
		assertTrue(!store.isGameObjectPendingAreaLoad(0), "game pending bulk reset");
		assertTrue(!store.isWallObjectPendingAreaLoad(0), "wall pending bulk reset");
	}

	private static RSModel model() {
		return new RSModel(4, 1);
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}

	private static void assertSame(Object expected, Object actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected same instance");
		}
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
		}
	}
}
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def assert_source_boundaries() -> None:
    owner = OWNER.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    packet_handler = PACKET_HANDLER.read_text(encoding="utf-8")
    baseline = BASELINE.read_text(encoding="utf-8")

    require("final class ClientSceneInstanceStore" in owner, "scene instance owner missing")
    require("private final ClientSceneInstanceStore sceneInstanceStore" in mudclient,
            "mudclient must hold one scene instance store")
    for declaration in (
        "int[] gameObjectInstanceX",
        "int[] gameObjectInstanceZ",
        "RSModel[] gameObjectInstanceModel",
        "int[] wallObjectInstanceX",
        "int[] wallObjectInstanceZ",
        "RSModel[] wallObjectInstanceModel",
    ):
        require(declaration not in mudclient, f"mudclient still owns {declaration}")

    require("return this.sceneInstanceStore.getGameObjectCount();" in mudclient,
            "game count facade must delegate")
    require("this.sceneInstanceStore.setGameObjectCount(i);" in mudclient,
            "game count mutation facade must delegate")
    require("return this.sceneInstanceStore.getWallObjectCount();" in mudclient,
            "wall count facade must delegate")
    require("this.sceneInstanceStore.setWallObjectCount(i);" in mudclient,
            "wall count mutation facade must delegate")
    require("prepareGameObjectInstanceModel(getGameObjectInstanceID(i), m)" in mudclient,
            "game model preparation must remain outside the store")

    for forbidden in (
        "EntityHandler",
        "PacketHandler",
        "SceneBaselineState",
        "import orsc.graphics.three.World;",
        "import orsc.graphics.three.Scene;",
    ):
        require(forbidden not in owner, f"store absorbed forbidden authority: {forbidden}")
    require("groundItem" not in owner, "ground-item storage must remain out of scope")

    packet_sequence = (
        "mc.setGameObjectInstanceX(instanceIndex, xTile);",
        "mc.setGameObjectInstanceZ(instanceIndex, zTile);",
        "mc.setGameObjectInstanceID(instanceIndex, id);",
        "mc.setGameObjectInstanceDir(instanceIndex, dir);",
        "mc.setGameObjectInstanceModel(instanceIndex, m);",
        "mc.setGameObjectInstanceMaterialized(instanceIndex, false);",
        "mc.setGameObjectInstancePendingAreaLoad(instanceIndex, mc.isAreaLoadPending());",
        "mc.materializeGameObjectInstance(instanceIndex);",
        "mc.setGameObjectInstanceCount(instanceIndex + 1);",
    )
    packet_game_objects = packet_handler[
        packet_handler.index("private void showGameObjects(int length)"):
        packet_handler.index("private void applyExpandedGameObjectPickBounds")
    ]
    positions = [packet_game_objects.find(statement) for statement in packet_sequence]
    require(all(position >= 0 for position in positions), "game packet mutation sequence missing")
    require(positions == sorted(positions), "game packet mutation order changed")
    require("mc.setWallObjectInstancePendingAreaLoad(instanceIndex, mc.isAreaLoadPending());"
            in packet_handler, "wall packet pending-area mutation missing")
    require("mc.getGameObjectInstanceCount()" in baseline
            and "mc.getWallObjectInstanceCount()" in baseline,
            "scene baseline must remain behind the mudclient facade")


def run_fixture() -> None:
    require(CLIENT_JAR.is_file(), "client jar missing; run ./scripts/build-client.sh first")
    with tempfile.TemporaryDirectory(prefix="scene-instance-store-") as temp_name:
        temp = Path(temp_name)
        source = temp / "orsc/ClientSceneInstanceStoreFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(CLIENT_JAR), "-d", str(temp), str(source)],
            check=True,
            cwd=ROOT,
        )
        subprocess.run(
            ["java", "-cp", f"{temp}:{CLIENT_JAR}", "orsc.ClientSceneInstanceStoreFixture"],
            check=True,
            cwd=ROOT,
        )


def main() -> None:
    assert_source_boundaries()
    run_fixture()
    print("PASS: client scene instance storage, growth, cleanup, and boundaries are stable")


if __name__ == "__main__":
    main()
