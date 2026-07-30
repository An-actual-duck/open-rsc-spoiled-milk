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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Renderer3DObjectChunkPrimitiveBuilderFixture {
	private static final int LEGACY_TRANSPARENT = 12345678;

	private Renderer3DObjectChunkPrimitiveBuilderFixture() {
	}

	public static void main(String[] args) throws Exception {
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

		RSModel large = polygonModel(40);
		Class<?> builderType =
			Class.forName("orsc.graphics.three.RSModel$ObjectChunkMeshBuilder");
		Method estimate = builderType.getDeclaredMethod(
			"estimateTriangleCapacity",
			RSModel[].class,
			int.class);
		estimate.setAccessible(true);
		int estimatedTriangles = ((Integer) estimate.invoke(
			null,
			new Object[] {new RSModel[] {large}, Integer.valueOf(1)})).intValue();
		assertEquals(76, estimatedTriangles, "exact large-polygon triangle estimate");
		RSModel oneSided = triangleModel(7, LEGACY_TRANSPARENT);
		int oneSidedTriangles = ((Integer) estimate.invoke(
			null,
			new Object[] {new RSModel[] {oneSided}, Integer.valueOf(1)})).intValue();
		assertEquals(2, oneSidedTriangles, "one-sided legacy duplicate estimate");
		RSModel hidden = triangleModel(LEGACY_TRANSPARENT, LEGACY_TRANSPARENT);
		int hiddenTriangles = ((Integer) estimate.invoke(
			null,
			new Object[] {new RSModel[] {hidden}, Integer.valueOf(1)})).intValue();
		assertEquals(0, hiddenTriangles, "fully transparent estimate");

		Constructor<?> constructor = builderType.getDeclaredConstructor(
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class,
			int.class);
		constructor.setAccessible(true);
		Object exactBuilder = constructor.newInstance(
			Integer.valueOf(0),
			Integer.valueOf(50),
			Integer.valueOf(50),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS),
			Integer.valueOf(estimatedTriangles),
			Integer.valueOf(1));
		Method addModel = builderType.getDeclaredMethod("addModel", RSModel.class);
		addModel.setAccessible(true);
		addModel.invoke(exactBuilder, large);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"vertexCoords",
			estimatedTriangles * 9);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"vertexTextureU",
			estimatedTriangles * 3);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"vertexTextureV",
			estimatedTriangles * 3);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"vertexLights",
			estimatedTriangles * 3);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"indices",
			estimatedTriangles * 3);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"triangleTextures",
			estimatedTriangles);
		assertNumericBuilderCapacity(
			builderType,
			exactBuilder,
			"triangleFallbackColors",
			estimatedTriangles);
		Method build = builderType.getDeclaredMethod("build");
		build.setAccessible(true);
		Renderer3DWorldChunkFrame.ChunkMesh largeChunk =
			(Renderer3DWorldChunkFrame.ChunkMesh) build.invoke(exactBuilder);
		assertEquals(76, largeChunk.getTriangleCount(), "large-polygon triangle count");
		assertEquals(228, largeChunk.getVertexCount(), "large-polygon vertex count");

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

	private static RSModel polygonModel(int vertexCount) {
		RSModel model = new RSModel(vertexCount, 1);
		int[] vertices = new int[vertexCount];
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			double angle = Math.PI * 2.0 * vertex / vertexCount;
			vertices[vertex] = model.insertVertex(
				(int) Math.round(Math.cos(angle) * 128.0),
				0,
				(int) Math.round(Math.sin(angle) * 128.0));
		}
		model.insertFace(vertexCount, vertices, 7, 7, false);
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		model.setRenderer3DMaterialFamily(Renderer3DMaterialFamily.SCENERY);
		return model;
	}

	private static RSModel triangleModel(int frontMaterial, int backMaterial) {
		RSModel model = new RSModel(3, 1);
		int first = model.insertVertex(0, 0, 0);
		int second = model.insertVertex(128, 0, 0);
		int third = model.insertVertex(0, 0, 128);
		model.insertFace(
			3,
			new int[] {first, second, third},
			frontMaterial,
			backMaterial,
			false);
		model.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);
		model.setRenderer3DMaterialFamily(Renderer3DMaterialFamily.SCENERY);
		return model;
	}

	private static void assertNumericBuilderCapacity(
		Class<?> builderType,
		Object builder,
		String fieldName,
		int expected) throws Exception {
		Field field = builderType.getDeclaredField(fieldName);
		field.setAccessible(true);
		Object numericBuilder = field.get(builder);
		Field valuesField = numericBuilder.getClass().getDeclaredField("values");
		valuesField.setAccessible(true);
		Field sizeField = numericBuilder.getClass().getDeclaredField("size");
		sizeField.setAccessible(true);
		assertEquals(expected, Array.getLength(valuesField.get(numericBuilder)), fieldName + " capacity");
		assertEquals(expected, sizeField.getInt(numericBuilder), fieldName + " size");
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
        "private static int estimateTriangleCapacity(RSModel[] models, int modelCount)",
        "new IntArrayBuilder(coordinateCapacity)",
        "new FloatArrayBuilder(vertexCapacity)",
        "new ArrayList<Renderer3DModelKind>(triangleCapacity)",
        "int[] vertexArray = vertexCoords.toArray();",
        "float[] textureUArray = vertexTextureU.toArray();",
    ):
        require(builder_source, fragment)
    require(
        source,
        "int triangleCapacity = "
        "ObjectChunkMeshBuilder.estimateTriangleCapacity(models, limit);",
    )
    if builder_source.count(
        "return size == values.length ? values : Arrays.copyOf(values, size);"
    ) != 2:
        raise AssertionError(
            "exact primitive builders should hand their backing arrays to the "
            "immutable ChunkMesh ownership boundary"
        )
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
