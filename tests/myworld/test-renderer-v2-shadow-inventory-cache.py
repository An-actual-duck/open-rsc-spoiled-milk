#!/usr/bin/env python3
"""Exercise exact renderer shadow-inventory cache ownership and invalidation."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
WORLD_FRAME_SOURCE = (
    ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DWorldChunkFrame.java"
)
WORLD_RENDERER_SOURCE = ROOT / "PC_Client/src/orsc/OpenGLWorldChunkRenderer.java"

FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.lang.reflect.Field;
import java.util.Arrays;

public final class Renderer3DShadowInventoryCacheFixture {
	private Renderer3DShadowInventoryCacheFixture() {
	}

	public static void main(String[] args) throws Exception {
		Renderer3DWorldChunkFrame.ChunkMesh world = worldChunk(101L);
		Renderer3DWorldChunkFrame.ShadowCaster gameObject =
			caster(Renderer3DModelKind.GAME_OBJECT, 64, 64);
		Renderer3DWorldChunkFrame.ChunkMesh animatedA =
			objectChunk(gameObject, 1001L, 0);
		Renderer3DWorldChunkFrame.ChunkMesh animatedB =
			objectChunk(gameObject, 2002L, 0);

		Renderer3DWorldChunkFrame frameA = frame(world, animatedA);
		Renderer3DWorldChunkFrame frameB = frame(world, animatedB);
		assertFalse(
			booleanField(animatedA, "shadowCasterInventorySignatureKnown"),
			"chunk signature remains lazy before diagnostics");
		assertFalse(
			booleanField(frameA, "objectShadowCasterSignatureKnown"),
			"frame signature remains lazy before diagnostics");
		assertEquals(
			animatedA.getShadowCasterInventorySignature(),
			animatedB.getShadowCasterInventorySignature(),
			"equivalent caster signatures");
		assertEquals(
			frameA.getObjectShadowCasterSignature(),
			frameB.getObjectShadowCasterSignature(),
			"animation-only mesh changes preserve aggregate caster signature");
		assertTrue(
			booleanField(animatedA, "shadowCasterInventorySignatureKnown"),
			"chunk signature computed on demand");
		assertTrue(
			booleanField(frameA, "objectShadowCasterSignatureKnown"),
			"frame signature computed on demand");

		OpenGLWorldChunkRenderer renderer = new OpenGLWorldChunkRenderer(
			null,
			null,
			null,
			true,
			true,
			true,
			true,
			true,
			true,
			true);
		RemasterShadowInventory first = renderer.inspectRemasterShadowInventory(frameA);
		RemasterShadowInventory repeated = renderer.inspectRemasterShadowInventory(frameA);
		RemasterShadowInventory animationOnly =
			renderer.inspectRemasterShadowInventory(frameB);
		assertSame(first, repeated, "same frame cache hit");
		assertSame(first, animationOnly, "animation-only geometry cache hit");
		assertEquals(1, first.receiverChunks, "receiver chunks");
		assertEquals(1, first.receiverTriangles, "receiver triangles");
		assertEquals(1, first.totalCasters, "total casters");
		assertEquals(1, first.gameObjectCasters, "game-object casters");

		Renderer3DWorldChunkFrame.ShadowCaster wallObject =
			caster(Renderer3DModelKind.WALL_OBJECT, 64, 64);
		Renderer3DWorldChunkFrame changedCasterFrame =
			frame(world, objectChunk(wallObject, 2002L, 0));
		assertNotEquals(
			frameB.getObjectShadowCasterSignature(),
			changedCasterFrame.getObjectShadowCasterSignature(),
			"caster kind invalidates aggregate signature");
		RemasterShadowInventory changedCaster =
			renderer.inspectRemasterShadowInventory(changedCasterFrame);
		assertNotSame(first, changedCaster, "caster change invalidates cache");
		assertEquals(0, changedCaster.gameObjectCasters, "changed game-object count");
		assertEquals(1, changedCaster.wallObjectCasters, "changed wall-object count");

		Renderer3DWorldChunkFrame changedPlaneFrame =
			frame(world, objectChunk(wallObject, 2002L, -1));
		assertNotEquals(
			changedCasterFrame.getObjectShadowCasterSignature(),
			changedPlaneFrame.getObjectShadowCasterSignature(),
			"object plane invalidates aggregate signature");
		RemasterShadowInventory changedPlane =
			renderer.inspectRemasterShadowInventory(changedPlaneFrame);
		assertNotSame(changedCaster, changedPlane, "plane change invalidates cache");

		Renderer3DWorldChunkFrame changedWorldFrame =
			frame(worldChunk(202L), objectChunk(wallObject, 2002L, -1));
		RemasterShadowInventory changedWorld =
			renderer.inspectRemasterShadowInventory(changedWorldFrame);
		assertNotSame(changedPlane, changedWorld, "world signature invalidates cache");

		renderer.clearResidentWorldSession();
		RemasterShadowInventory afterClear =
			renderer.inspectRemasterShadowInventory(changedWorldFrame);
		assertNotSame(changedWorld, afterClear, "session clear invalidates cache");
		renderer.close();
		System.out.println("PASS: renderer-v2 shadow inventory cache");
	}

	private static Renderer3DWorldChunkFrame frame(
		Renderer3DWorldChunkFrame.ChunkMesh world,
		Renderer3DWorldChunkFrame.ChunkMesh object) {
		return Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(world, object));
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh worldChunk(long signature) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			0,
			50,
			50,
			0,
			0,
			new int[] {0, 0, 0, 128, 0, 0, 0, 0, 128},
			new float[] {0.0f, 1.0f, 0.0f},
			new float[] {0.0f, 0.0f, 1.0f},
			new int[] {0, 0, 0},
			new int[] {0, 1, 2},
			new int[] {-2},
			new int[] {8},
			new Renderer3DModelKind[] {Renderer3DModelKind.TERRAIN},
			1,
			0,
			0,
			signature);
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh objectChunk(
		Renderer3DWorldChunkFrame.ShadowCaster caster,
		long meshSignature,
		int plane) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			plane,
			50,
			50,
			0,
			0,
			new int[0],
			new float[0],
			new float[0],
			new int[0],
			new int[0],
			new int[0],
			new int[0],
			new Renderer3DModelKind[0],
			new Renderer3DWorldChunkFrame.ShadowCaster[] {caster},
			0,
			0,
			0,
			true,
			Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,
			meshSignature);
	}

	private static Renderer3DWorldChunkFrame.ShadowCaster caster(
		Renderer3DModelKind kind,
		int x,
		int z) {
		return new Renderer3DWorldChunkFrame.ShadowCaster(
			kind,
			x - 32,
			0,
			z,
			x + 32,
			z,
			128,
			64,
			144,
			true,
			x - 32,
			x + 32,
			z - 32,
			z + 32);
	}

	private static boolean booleanField(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.getBoolean(owner);
	}

	private static void assertSame(Object expected, Object actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label);
		}
	}

	private static void assertNotSame(Object first, Object second, String label) {
		if (first == second) {
			throw new AssertionError(label);
		}
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertEquals(long expected, long actual, String label) {
		if (expected != actual) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertNotEquals(long first, long second, String label) {
		if (first == second) {
			throw new AssertionError(label);
		}
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}

	private static void assertFalse(boolean condition, String label) {
		if (condition) {
			throw new AssertionError(label);
		}
	}
}
"""


def require(source: str, fragment: str) -> None:
    if fragment not in source:
        raise AssertionError(f"missing expected source fragment: {fragment!r}")


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    frame_source = WORLD_FRAME_SOURCE.read_text(encoding="utf-8")
    for fragment in (
        "this.objectShadowCasterSignature = shadowCasterSignature;",
        "public long getObjectShadowCasterSignature()",
        "public long getShadowCasterInventorySignature()",
        "caster.getFootprintMaxZ()",
    ):
        require(frame_source, fragment)

    renderer_source = WORLD_RENDERER_SOURCE.read_text(encoding="utf-8")
    inventory_method = renderer_source.split(
        "RemasterShadowInventory inspectRemasterShadowInventory(", 1
    )[1].split("void drawRemasterShadowInventoryDebug", 1)[0]
    for fragment in (
        "chunkFrame.getObjectShadowCasterSignature()",
        "cachedRemasterShadowInventoryWorldSignature == worldSignature",
        "cachedRemasterShadowInventoryObjectCasterSignature == objectCasterSignature",
        "cachedRemasterShadowInventory = RemasterShadowClassifier.inspectInventory(",
    ):
        require(inventory_method, fragment)
    clear_method = renderer_source.split("private void clearResidentResources()", 1)[1]
    require(clear_method, "cachedRemasterShadowInventory = null;")
    require(clear_method, "cachedRemasterShadowInventoryKnown = false;")

    with tempfile.TemporaryDirectory(
        prefix="renderer-shadow-inventory-cache-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "Renderer3DShadowInventoryCacheFixture.java"
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")

        compile_result = subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                str(CLIENT_JAR),
                "-d",
                str(temp),
                str(fixture),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if compile_result.returncode != 0:
            raise AssertionError(
                "shadow inventory cache fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.Renderer3DShadowInventoryCacheFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "shadow inventory cache fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
