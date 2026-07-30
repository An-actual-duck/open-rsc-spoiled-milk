#!/usr/bin/env python3
"""Exercise renderer-v2 world-face pooling and state reset behavior."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"

FIXTURE = r"""
package orsc.graphics.three;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;

public final class Renderer3DWorldFacePoolFixture {
	private Renderer3DWorldFacePoolFixture() {
	}

	public static void main(String[] args) throws Exception {
		reusesAndResetsFaceState();
		keepsVertexCountsSeparate();
		boundsRetainedFrameStorage();
		System.out.println("PASS: renderer-v2 world face pool");
	}

	private static void reusesAndResetsFaceState() {
		RSModel firstModel = model(
			Renderer3DModelKind.TERRAIN,
			new int[][] {
				{0, 0, 100},
				{100, 0, 100},
				{0, 100, 100},
			});
		Renderer3DFrame firstFrame = frame();
		firstFrame.addWorldFace(
			0,
			10,
			0,
			123,
			1,
			200,
			firstModel,
			new int[] {0, 1, 2},
			3,
			new int[] {11, 12, 13},
			new int[] {21, 22, 23});
		firstFrame.recordLegacyDrawOrder(0, 10, 47);
		firstFrame.recordLegacyClippedGeometry(
			0,
			10,
			new int[] {0, 100, 100, 0},
			new int[] {0, 0, 100, 100},
			new int[] {100, 100, 100, 100},
			new int[] {10, 20, 20, 10},
			new int[] {10, 10, 20, 20},
			new int[] {31, 32, 33, 34},
			new int[] {41, 42, 43, 44},
			4);

		Renderer3DFrame.FaceCommand firstFace = onlyFace(firstFrame);
		assertEquals(47, firstFace.getLegacyDrawOrder(), "first draw order");
		assertEquals(4, firstFace.getRenderVertexCount(), "first clipped count");
		assertTrue(hasNonZero(firstFace.getTextureU()), "textured face u values");
		assertEquals(1, firstFrame.getWorldFaceCount(Renderer3DModelKind.TERRAIN),
			"first terrain count");
		assertUnmodifiable(firstFrame.getWorldFaces());
		firstFrame.release();
		firstFrame.release();

		RSModel secondModel = model(
			Renderer3DModelKind.WALL,
			new int[][] {
				{7, 8, 109},
				{117, 18, 119},
				{27, 128, 129},
			});
		Renderer3DFrame secondFrame = frame();
		secondFrame.addWorldFace(
			0,
			20,
			-1,
			456,
			2,
			300,
			secondModel,
			new int[] {0, 1, 2},
			3,
			new int[] {51, 52, 53},
			null);
		Renderer3DFrame.FaceCommand secondFace = onlyFace(secondFrame);

		assertSame(firstFace, secondFace, "same-sized face command should be reused");
		assertEquals(-1, secondFace.getLegacyDrawOrder(), "draw order reset");
		assertEquals(3, secondFace.getRenderVertexCount(), "clipped geometry reset");
		assertEquals(0, secondFrame.getWorldFaceCount(Renderer3DModelKind.TERRAIN),
			"terrain count reset");
		assertEquals(1, secondFrame.getWorldFaceCount(Renderer3DModelKind.WALL),
			"wall count populated");
		assertArrayEquals(new int[] {7, 117, 27}, secondFace.getCameraX(),
			"camera x reset");
		assertArrayEquals(new int[] {51, 52, 53}, secondFace.getRenderLight(),
			"light reset");
		assertArrayEquals(new int[] {0, 0, 0}, secondFace.getRenderBaseLight(),
			"missing base light reset");
		assertAllZero(secondFace.getTextureU(), "texture u reset");
		assertAllZero(secondFace.getTextureV(), "texture v reset");
		secondFrame.recordLegacyDrawOrder(0, 10, 70);
		assertEquals(-1, secondFace.getLegacyDrawOrder(), "stale face lookup removed");
		secondFrame.recordLegacyDrawOrder(0, 20, 71);
		assertEquals(71, secondFace.getLegacyDrawOrder(), "new face lookup populated");
		secondFrame.release();
	}

	private static void keepsVertexCountsSeparate() {
		Renderer3DFrame triangleFrame = frame();
		triangleFrame.addWorldFace(
			0,
			30,
			-1,
			1,
			0,
			1,
			model(
				Renderer3DModelKind.ROOF,
				new int[][] {{0, 0, 100}, {10, 0, 100}, {0, 10, 100}}),
			new int[] {0, 1, 2},
			3,
			null,
			null);
		Renderer3DFrame.FaceCommand triangle = onlyFace(triangleFrame);
		triangleFrame.release();

		Renderer3DFrame quadFrame = frame();
		quadFrame.addWorldFace(
			0,
			31,
			-1,
			2,
			0,
			2,
			model(
				Renderer3DModelKind.ROOF,
				new int[][] {
					{0, 0, 100},
					{10, 0, 100},
					{10, 10, 100},
					{0, 10, 100},
				}),
			new int[] {0, 1, 2, 3},
			4,
			null,
			null);
		Renderer3DFrame.FaceCommand quad = onlyFace(quadFrame);
		assertTrue(triangle != quad, "different vertex counts must not share commands");
		assertEquals(4, quad.getVertexCount(), "quad vertex count");
		quadFrame.release();
	}

	@SuppressWarnings("unchecked")
	private static void boundsRetainedFrameStorage() throws Exception {
		Renderer3DFrame[] inFlight = new Renderer3DFrame[6];
		for (int i = 0; i < inFlight.length; i++) {
			inFlight[i] = frame();
		}
		for (Renderer3DFrame frame : inFlight) {
			frame.release();
		}

		Field poolField =
			Renderer3DFrame.class.getDeclaredField("AVAILABLE_WORLD_FACE_STORAGES");
		poolField.setAccessible(true);
		ArrayDeque<Object> pool = (ArrayDeque<Object>) poolField.get(null);
		assertTrue(pool.size() <= 3, "retained storage pool must stay bounded");
	}

	private static Renderer3DFrame frame() {
		return new Renderer3DFrame(
			1,
			2400,
			2200,
			960,
			540,
			480,
			270,
			0,
			0,
			0,
			0,
			0,
			0,
			9,
			5,
			new Renderer3DTextureData[0]);
	}

	private static RSModel model(Renderer3DModelKind kind, int[][] coordinates) {
		RSModel model = new RSModel(coordinates.length, 1);
		model.setRenderer3DModelKind(kind);
		for (int vertex = 0; vertex < coordinates.length; vertex++) {
			int index = model.insertVertex(
				coordinates[vertex][0],
				coordinates[vertex][1],
				coordinates[vertex][2]);
			model.vertXRot[index] = coordinates[vertex][0];
			model.vertYRot[index] = coordinates[vertex][1];
			model.vertZRot[index] = coordinates[vertex][2];
			model.vertexParam6[index] = coordinates[vertex][0] / 2;
			model.vertexParam2[index] = coordinates[vertex][1] / 2;
		}
		return model;
	}

	private static Renderer3DFrame.FaceCommand onlyFace(Renderer3DFrame frame) {
		assertEquals(1, frame.getWorldFaces().size(), "face count");
		return frame.getWorldFaces().get(0);
	}

	private static boolean hasNonZero(float[] values) {
		for (float value : values) {
			if (value != 0.0f) {
				return true;
			}
		}
		return false;
	}

	private static void assertAllZero(float[] values, String label) {
		for (float value : values) {
			if (value != 0.0f) {
				throw new AssertionError(label + ": expected zero but found " + value);
			}
		}
	}

	private static void assertUnmodifiable(List<Renderer3DFrame.FaceCommand> faces) {
		try {
			faces.add(null);
			throw new AssertionError("world face view should be unmodifiable");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	private static void assertArrayEquals(int[] expected, int[] actual, String label) {
		if (expected.length != actual.length) {
			throw new AssertionError(label + ": length " + actual.length);
		}
		for (int i = 0; i < expected.length; i++) {
			if (expected[i] != actual[i]) {
				throw new AssertionError(
					label + ": index " + i + " expected " + expected[i]
						+ " but was " + actual[i]);
			}
		}
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertSame(Object expected, Object actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label);
		}
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}
}
"""


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    with tempfile.TemporaryDirectory(prefix="renderer-world-face-pool-") as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc/graphics/three"
        source_dir.mkdir(parents=True)
        source = source_dir / "Renderer3DWorldFacePoolFixture.java"
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")

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
                str(source),
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if compile_result.returncode != 0:
            raise AssertionError(
                "world face pool fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.graphics.three.Renderer3DWorldFacePoolFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "world face pool fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
