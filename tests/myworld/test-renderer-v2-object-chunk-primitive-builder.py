#!/usr/bin/env python3
"""Exercise primitive object-chunk mesh accumulation and exact output."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
RSMODEL_SOURCE = ROOT / "Client_Base/src/orsc/graphics/three/RSModel.java"

FIXTURE = r"""
package orsc.graphics.three;

public final class Renderer3DObjectChunkPrimitiveBuilderFixture {
	private static final int LEGACY_TRANSPARENT = 12345678;

	private Renderer3DObjectChunkPrimitiveBuilderFixture() {
	}

	public static void main(String[] args) {
		RSModel model = new RSModel(4, 1);
		int northWest = model.insertVertex(0, 0, 0);
		int northEast = model.insertVertex(128, 0, 0);
		int southEast = model.insertVertex(128, 128, 128);
		int southWest = model.insertVertex(0, 128, 128);
		int face = model.insertFace(
			4,
			new int[] {northWest, northEast, southEast, southWest},
			7,
			-2,
			false);
		assertEquals(0, face, "face index");
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		model.setRenderer3DMaterialFamily(Renderer3DMaterialFamily.SCENERY);
		model.setRenderer3DGlowEmitter(0xff8800, 64, 192);

		RSModel ignored = new RSModel(3, 1);
		int a = ignored.insertVertex(0, 0, 0);
		int b = ignored.insertVertex(64, 0, 0);
		int c = ignored.insertVertex(0, 0, 64);
		ignored.insertFace(3, new int[] {a, b, c}, 9, 9, false);
		ignored.setRenderer3DModelKind(Renderer3DModelKind.TERRAIN);

		Renderer3DWorldChunkFrame.ChunkMesh chunk =
			RSModel.buildRenderer3DObjectChunkMesh(
				-2,
				44,
				55,
				1024,
				2048,
				new RSModel[] {model, ignored},
				2,
				Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS);

		assertEquals(-2, chunk.getPlane(), "plane");
		assertEquals(44, chunk.getCenterSectionX(), "center section x");
		assertEquals(55, chunk.getCenterSectionY(), "center section y");
		assertEquals(12, chunk.getVertexCount(), "triangulated vertex count");
		assertEquals(12, chunk.getIndexCount(), "index count");
		assertEquals(4, chunk.getTriangleCount(), "triangle count");
		assertEquals(1, chunk.getShadowCasterCount(), "shadow caster count");
		assertEquals(1, chunk.getGlowEmitterCount(), "glow emitter count");
		assertEquals(
			Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,
			chunk.getChunkRole(),
			"chunk role");

		for (int index = 0; index < chunk.getIndexCount(); index++) {
			assertEquals(index, chunk.getIndex(index), "sequential index " + index);
		}
		assertTriangle(
			chunk,
			0,
			7,
			LEGACY_TRANSPARENT,
			Renderer3DModelKind.GAME_OBJECT,
			Renderer3DMaterialFamily.SCENERY);
		assertTriangle(
			chunk,
			1,
			7,
			LEGACY_TRANSPARENT,
			Renderer3DModelKind.GAME_OBJECT,
			Renderer3DMaterialFamily.SCENERY);
		assertTriangle(
			chunk,
			2,
			LEGACY_TRANSPARENT,
			8,
			Renderer3DModelKind.GAME_OBJECT,
			Renderer3DMaterialFamily.SCENERY);
		assertTriangle(
			chunk,
			3,
			LEGACY_TRANSPARENT,
			8,
			Renderer3DModelKind.GAME_OBJECT,
			Renderer3DMaterialFamily.SCENERY);

		int[][] expectedVertices = new int[][] {
			{0, 0, 0},
			{128, 0, 0},
			{128, 128, 128},
			{0, 0, 0},
			{128, 128, 128},
			{0, 128, 128},
			{0, 0, 0},
			{128, 128, 128},
			{128, 0, 0},
			{0, 0, 0},
			{0, 128, 128},
			{128, 128, 128},
		};
		for (int vertex = 0; vertex < expectedVertices.length; vertex++) {
			for (int axis = 0; axis < 3; axis++) {
				assertEquals(
					expectedVertices[vertex][axis],
					chunk.getVertexCoord(vertex * 3 + axis),
					"vertex " + vertex + " axis " + axis);
			}
			assertFinite(chunk.getVertexTextureU(vertex), "texture u " + vertex);
			assertFinite(chunk.getVertexTextureV(vertex), "texture v " + vertex);
		}

		Renderer3DWorldChunkFrame.ChunkMesh repeated =
			RSModel.buildRenderer3DObjectChunkMesh(
				-2,
				44,
				55,
				1024,
				2048,
				new RSModel[] {model},
				1,
				Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS);
		assertEquals(chunk.getSignature(), repeated.getSignature(), "stable signature");

		Renderer3DWorldChunkFrame.ChunkMesh empty =
			RSModel.buildRenderer3DObjectChunkMesh(
				0,
				0,
				0,
				0,
				0,
				null,
				99);
		assertEquals(0, empty.getVertexCount(), "empty vertex count");
		assertEquals(0, empty.getTriangleCount(), "empty triangle count");
		System.out.println("PASS: renderer-v2 object chunk primitive builder");
	}

	private static void assertTriangle(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle,
		int texture,
		int fallback,
		Renderer3DModelKind kind,
		Renderer3DMaterialFamily family) {
		assertEquals(texture, chunk.getTriangleTexture(triangle), "texture " + triangle);
		assertEquals(
			fallback,
			chunk.getTriangleFallbackColor(triangle),
			"fallback " + triangle);
		if (chunk.getTriangleModelKind(triangle) != kind) {
			throw new AssertionError("model kind " + triangle);
		}
		if (chunk.getTriangleMaterialFamily(triangle) != family) {
			throw new AssertionError("material family " + triangle);
		}
	}

	private static void assertFinite(float value, String label) {
		if (Float.isNaN(value) || Float.isInfinite(value)) {
			throw new AssertionError(label + ": " + value);
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

    source = RSMODEL_SOURCE.read_text(encoding="utf-8")
    builder_source = source.split(
        "private static final class ObjectChunkMeshBuilder", 1
    )[1].split("private long signature(", 1)[0]
    for fragment in (
        "private final IntArrayBuilder vertexCoords",
        "private final FloatArrayBuilder vertexTextureU",
        "private final FloatArrayBuilder vertexTextureV",
        "int[] vertexArray = vertexCoords.toArray();",
        "float[] textureUArray = vertexTextureU.toArray();",
    ):
        require(builder_source, fragment)
    for forbidden in (
        "List<Integer>",
        "List<Float>",
        "Integer.valueOf(",
        "Float.valueOf(",
    ):
        if forbidden in builder_source:
            raise AssertionError(
                f"boxed numeric object-chunk accumulation remains: {forbidden!r}"
            )

    with tempfile.TemporaryDirectory(
        prefix="renderer-object-chunk-primitive-builder-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc/graphics/three"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "Renderer3DObjectChunkPrimitiveBuilderFixture.java"
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
                "object chunk primitive-builder fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.graphics.three.Renderer3DObjectChunkPrimitiveBuilderFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "object chunk primitive-builder fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
