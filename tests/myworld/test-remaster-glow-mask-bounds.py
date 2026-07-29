#!/usr/bin/env python3
"""Exercise precomputed renderer chunk/frame bounds used by glow masks."""

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
GLOW_BUILDER_SOURCE = ROOT / "PC_Client/src/orsc/RemasterGlowMaskBuilder.java"

FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.util.Arrays;
import java.util.Collections;

public final class RemasterGlowMaskBoundsFixture {
	private RemasterGlowMaskBoundsFixture() {
	}

	public static void main(String[] args) {
		aggregatesExactChunkBounds();
		translatesPresentationBounds();
		preservesEmptyBounds();
		preservesGlowMaskCoordinates();
		supportsEmitterOnlyFrames();
		System.out.println("PASS: remaster glow mask precomputed bounds");
	}

	private static void aggregatesExactChunkBounds() {
		Renderer3DWorldChunkFrame.ChunkMesh first = chunk(
			new int[] {
				10, 1, 20,
				100, 2, 200,
			},
			null,
			1L);
		Renderer3DWorldChunkFrame.ChunkMesh second = chunk(
			new int[] {
				-50, 3, 500,
				60, 4, -80,
			},
			null,
			2L);
		Renderer3DWorldChunkFrame frame =
			Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(first, second));

		assertTrue(first.hasVertexBounds(), "first chunk bounds");
		assertBounds(first, 10, 100, 20, 200, "first chunk");
		assertBounds(second, -50, 60, -80, 500, "second chunk");
		assertTrue(frame.hasVertexBounds(), "frame bounds");
		assertBounds(frame, -50, 100, -80, 500, "combined frame");
	}

	private static void translatesPresentationBounds() {
		Renderer3DWorldChunkFrame.ChunkMesh source = chunk(
			new int[] {
				10, 1, 20,
				100, 2, 200,
			},
			null,
			3L);
		Renderer3DWorldChunkFrame.ChunkMesh translated =
			source.rebasePresentation(1000, -200);
		assertBounds(source, 10, 100, 20, 200, "unmodified source");
		assertBounds(translated, 1010, 1100, -180, 0, "translated chunk");
		assertEquals(1010, translated.getVertexCoord(0), "translated vertex x");
		assertEquals(-180, translated.getVertexCoord(2), "translated vertex z");
		assertBounds(
			Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(translated)),
			1010,
			1100,
			-180,
			0,
			"translated frame");
	}

	private static void preservesEmptyBounds() {
		Renderer3DWorldChunkFrame.ChunkMesh empty = chunk(new int[0], null, 4L);
		Renderer3DWorldChunkFrame frame =
			Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(empty));
		assertTrue(!empty.hasVertexBounds(), "empty chunk bounds");
		assertTrue(!frame.hasVertexBounds(), "empty frame bounds");
		assertBounds(empty, 0, 0, 0, 0, "empty chunk sentinel");
		assertBounds(frame, 0, 0, 0, 0, "empty frame sentinel");
	}

	private static void preservesGlowMaskCoordinates() {
		Renderer3DWorldChunkFrame.GlowEmitter emitter =
			new Renderer3DWorldChunkFrame.GlowEmitter(
				Renderer3DModelKind.GAME_OBJECT,
				50,
				0,
				70,
				10,
				0xff8040,
				255);
		Renderer3DWorldChunkFrame frame = Renderer3DWorldChunkFrame.fromChunks(
			Collections.singletonList(
				chunk(
					new int[] {
						0, 0, 0,
						100, 0, 200,
						0, 0, 200,
					},
					emitter,
					5L)));
		RemasterGlowMaskBuild build = new RemasterGlowMaskBuilder().build(frame);
		assertTrue(build != null && build.mask != null, "glow mask build");
		assertTrue(build.mask.visiblePixels > 0, "glow mask visibility");
		assertFloatEquals(-728.0f, build.mask.minX, "glow mask min x");
		assertFloatEquals(-708.0f, build.mask.minZ, "glow mask min z");
		assertFloatEquals(1.0f / 1556.0f, build.mask.invSpanX, "glow mask inverse x span");
		assertFloatEquals(1.0f / 1556.0f, build.mask.invSpanZ, "glow mask inverse z span");
	}

	private static void supportsEmitterOnlyFrames() {
		Renderer3DWorldChunkFrame.GlowEmitter emitter =
			new Renderer3DWorldChunkFrame.GlowEmitter(
				Renderer3DModelKind.GAME_OBJECT,
				0,
				0,
				0,
				10,
				0xffffff,
				255);
		Renderer3DWorldChunkFrame frame = Renderer3DWorldChunkFrame.fromChunks(
			Collections.singletonList(chunk(new int[0], emitter, 6L)));
		RemasterGlowMaskBuild build = new RemasterGlowMaskBuilder().build(frame);
		assertTrue(build != null && build.mask != null, "emitter-only glow mask");
		assertFloatEquals(-778.0f, build.mask.minX, "emitter-only min x");
		assertFloatEquals(-778.0f, build.mask.minZ, "emitter-only min z");
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh chunk(
		int[] vertexCoords,
		Renderer3DWorldChunkFrame.GlowEmitter emitter,
		long signature) {
		int vertexCount = vertexCoords.length / 3;
		Renderer3DWorldChunkFrame.GlowEmitter[] emitters = emitter == null
			? new Renderer3DWorldChunkFrame.GlowEmitter[0]
			: new Renderer3DWorldChunkFrame.GlowEmitter[] {emitter};
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			0,
			50,
			50,
			0,
			0,
			vertexCoords,
			new float[vertexCount],
			new float[vertexCount],
			new int[vertexCount],
			new int[0],
			new int[0],
			new int[0],
			new Renderer3DModelKind[0],
			new Renderer3DWorldChunkFrame.ShadowCaster[0],
			emitters,
			0,
			0,
			0,
			false,
			Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD,
			signature);
	}

	private static void assertBounds(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int minX,
		int maxX,
		int minZ,
		int maxZ,
		String label) {
		assertEquals(minX, chunk.getMinVertexX(), label + " min x");
		assertEquals(maxX, chunk.getMaxVertexX(), label + " max x");
		assertEquals(minZ, chunk.getMinVertexZ(), label + " min z");
		assertEquals(maxZ, chunk.getMaxVertexZ(), label + " max z");
	}

	private static void assertBounds(
		Renderer3DWorldChunkFrame frame,
		int minX,
		int maxX,
		int minZ,
		int maxZ,
		String label) {
		assertEquals(minX, frame.getMinVertexX(), label + " min x");
		assertEquals(maxX, frame.getMaxVertexX(), label + " max x");
		assertEquals(minZ, frame.getMinVertexZ(), label + " min z");
		assertEquals(maxZ, frame.getMaxVertexZ(), label + " max z");
	}

	private static void assertFloatEquals(float expected, float actual, String label) {
		if (Math.abs(expected - actual) > 0.000001f) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
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

    world_source = WORLD_FRAME_SOURCE.read_text(encoding="utf-8")
    for fragment in (
        "private void initializeVertexBounds()",
        "this.hasVertexBounds = foundVertexBounds;",
        "public boolean hasVertexBounds()",
        "Math.addExact(source.minVertexX, additionalOffsetX)",
        "Math.addExact(source.minVertexZ, additionalOffsetZ)",
    ):
        require(world_source, fragment)

    glow_source = GLOW_BUILDER_SOURCE.read_text(encoding="utf-8")
    bounds_builder = glow_source.split(
        "private static RemasterGlowMaskBounds from(", 1
    )[1].split("private float spanX()", 1)[0]
    for fragment in (
        "chunkFrame.hasVertexBounds()",
        "chunkFrame.getMinVertexX()",
        "chunkFrame.getMaxVertexX()",
        "chunkFrame.getMinVertexZ()",
        "chunkFrame.getMaxVertexZ()",
    ):
        require(bounds_builder, fragment)
    if "getVertexCoord(" in bounds_builder:
        raise AssertionError("glow bounds still scan every resident vertex")

    with tempfile.TemporaryDirectory(prefix="remaster-glow-mask-bounds-") as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "RemasterGlowMaskBoundsFixture.java"
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
                "glow bounds fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.RemasterGlowMaskBoundsFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "glow bounds fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
