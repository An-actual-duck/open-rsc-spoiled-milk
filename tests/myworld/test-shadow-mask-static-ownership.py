#!/usr/bin/env python3
"""Exercise static-only terrain-shadow ownership across animated frames."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
SHADOW_BUILDER = ROOT / "PC_Client/src/orsc/RemasterShadowMaskBuilder.java"

FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ShadowMaskStaticOwnershipFixture {
	private ShadowMaskStaticOwnershipFixture() {
	}

	public static void main(String[] arguments) {
		Renderer3DWorldChunkFrame first = frame(
			50, 0, 101L, caster(64), caster(88), 1001L);
		Renderer3DWorldChunkFrame other = frame(
			51, 2048, 202L, caster(2112), caster(2140), 2002L);
		Renderer3DWorldChunkFrame animatedReturn = frame(
			50, 0, 101L, caster(64), caster(112), 3003L);
		Renderer3DWorldChunkFrame staticChange = frame(
			50, 0, 101L, caster(96), caster(88), 1001L);

		long firstSignature =
			RemasterShadowMaskBuilder.remasterShadowWorldSignature(first);
		long returnSignature =
			RemasterShadowMaskBuilder.remasterShadowWorldSignature(animatedReturn);
		assertEquals(firstSignature, returnSignature,
			"animated changes do not invalidate static shadow ownership");
		assertNotEquals(firstSignature,
			RemasterShadowMaskBuilder.remasterShadowWorldSignature(other),
			"different static world invalidates shadow ownership");
		assertNotEquals(firstSignature,
			RemasterShadowMaskBuilder.remasterShadowWorldSignature(staticChange),
			"changed static caster invalidates shadow ownership");

		RemasterShadowMaskBuilder builder = new RemasterShadowMaskBuilder();
		RemasterShadowMaskBuild firstBuild = build(builder, first);
		RemasterShadowMaskBuild otherBuild = build(builder, other);
		RemasterShadowMaskBuild returnBuild = build(builder, animatedReturn);
		assertNotNull(firstBuild, "first static mask");
		assertNotNull(otherBuild, "other static mask");
		assertNotNull(returnBuild, "return static mask");
		assertEquals(1, firstBuild.softSceneryCasterCount,
			"animated caster is absent from directional mask");
		assertEquals(1, firstBuild.contactCasterCount,
			"animated caster is absent from contact mask");
		assertTrue(returnBuild.cacheHit, "return uses cached static mask");
		assertFalse(returnBuild.rebuild, "return does not rebuild for animation state");
		assertSame(firstBuild.mask, returnBuild.mask,
			"return resolves the original static mask instance");
		assertEquals(10285, firstBuild.mask.visiblePixels,
			"shadow mask visible-pixel parity");
		assertEquals(250842434521131199L, pixelHash(firstBuild.mask.pixels()),
			"shadow mask byte parity");
		System.out.println("PASS: terrain shadow masks have stable static ownership");
	}

	private static long pixelHash(ByteBuffer pixels) {
		long hash = 0xcbf29ce484222325L;
		while (pixels.hasRemaining()) {
			hash ^= pixels.get() & 0xffL;
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static RemasterShadowMaskBuild build(
		RemasterShadowMaskBuilder builder,
		Renderer3DWorldChunkFrame frame) {
		return builder.build(
			frame,
			RemasterShadowRoofCoverage.from(frame),
			RemasterShadowMaskBuilder.remasterShadowWorldSignature(frame));
	}

	private static Renderer3DWorldChunkFrame frame(
		int center,
		int origin,
		long worldSignature,
		Renderer3DWorldChunkFrame.ShadowCaster staticCaster,
		Renderer3DWorldChunkFrame.ShadowCaster animatedCaster,
		long animatedSignature) {
		return Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(
			worldChunk(center, origin, worldSignature),
			objectChunk(center, origin, staticCaster, 5001L,
				Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS),
			objectChunk(center, origin, animatedCaster, animatedSignature,
				Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS)));
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh worldChunk(
		int center,
		int origin,
		long signature) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			0,
			center,
			center,
			origin,
			origin,
			new int[] {
				origin, 0, origin,
				origin + 256, 0, origin,
				origin, 0, origin + 256
			},
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
		int center,
		int origin,
		Renderer3DWorldChunkFrame.ShadowCaster caster,
		long signature,
		int role) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			0,
			center,
			center,
			origin,
			origin,
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
			role,
			signature);
	}

	private static Renderer3DWorldChunkFrame.ShadowCaster caster(int center) {
		return new Renderer3DWorldChunkFrame.ShadowCaster(
			Renderer3DModelKind.GAME_OBJECT,
			center - 32,
			0,
			center,
			center + 32,
			center,
			128,
			64,
			144,
			true,
			center - 32,
			center + 32,
			center - 32,
			center + 32);
	}

	private static void assertSame(Object expected, Object actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label);
		}
	}

	private static void assertNotNull(Object value, String label) {
		if (value == null) {
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
			throw new AssertionError(label);
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

    builder_source = SHADOW_BUILDER.read_text(encoding="utf-8")
    for fragment in (
        "chunkFrame.getStaticPresentationChunkCount()",
        "chunk.getShadowCasterInventorySignature()",
        "CHUNK_ROLE_ANIMATED_OBJECTS",
        "including them made every boundary return miss",
        "RemasterTerrainShadowCasterGrid",
        "casterGrid.get(remasterShadowMaskCell(x), remasterShadowMaskCell(z))",
    ):
        require(builder_source, fragment)
    if "Map<Long, List<RemasterTerrainShadowCaster>> casterGrid" in builder_source:
        raise AssertionError("shadow-mask raster lookup regressed to boxed map keys")

    with tempfile.TemporaryDirectory(prefix="shadow-mask-static-ownership-") as raw:
        temp = Path(raw)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "ShadowMaskStaticOwnershipFixture.java"
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
                "shadow mask fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-Dspoiledmilk.remasterLightAzimuth=135",
                "-Dspoiledmilk.remasterLightElevation=45",
                "-Dspoiledmilk.remasterShadowLightSmoothingMillis=0",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.ShadowMaskStaticOwnershipFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "shadow mask fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
