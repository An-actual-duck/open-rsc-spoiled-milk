#!/usr/bin/env python3
"""Exercise presentation-independent resident VBO cache identity."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
CHUNK_RENDERER = ROOT / "PC_Client/src/orsc/OpenGLWorldChunkRenderer.java"

FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ResidentChunkStorageIdentityFixture {
	private ResidentChunkStorageIdentityFixture() {
	}

	public static void main(String[] arguments) {
		Renderer3DWorldChunkFrame.ChunkMesh source =
			worldChunk(0, 10, 10, 100, 200, 101L);
		Renderer3DWorldChunkFrame.ChunkMesh rebased =
			source.rebasePresentation(-6144, 6144);
		Renderer3DWorldChunkFrame.ChunkMesh shiftedIdentity =
			worldChunk(0, 11, 9, 6244, -5944, 101L);

		WorldChunkBufferKey sourceStorage =
			WorldChunkBufferKey.from(source, true);
		WorldChunkBufferKey rebasedStorage =
			WorldChunkBufferKey.from(rebased, true);
		WorldChunkBufferKey shiftedStorage =
			WorldChunkBufferKey.from(shiftedIdentity, true);
		assertEquals(sourceStorage, rebasedStorage,
			"draw-offset rebase keeps resident storage identity");
		assertEquals(sourceStorage, shiftedStorage,
			"presentation center and origin do not split immutable storage");
		assertEquals(sourceStorage.hashCode(), rebasedStorage.hashCode(),
			"equal storage keys have equal hashes");

		Map<WorldChunkBufferKey, String> resident =
			new LinkedHashMap<WorldChunkBufferKey, String>();
		resident.put(sourceStorage, "resident");
		assertEquals("resident", resident.get(rebasedStorage),
			"rebased frame resolves the existing resident buffer");

		assertNotEquals(
			WorldChunkBufferKey.from(source, false),
			WorldChunkBufferKey.from(shiftedIdentity, false),
			"fixed-function identity remains presentation-specific");
		assertEquals(
			WorldChunkBufferKey.from(source),
			WorldChunkBufferKey.from(source, false),
			"legacy overload retains positional behavior");
		assertNotEquals(sourceStorage,
			WorldChunkBufferKey.from(
				worldChunk(0, 10, 10, 100, 200, 102L), true),
			"different immutable storage does not alias");
		assertNotEquals(sourceStorage,
			WorldChunkBufferKey.from(
				worldChunk(1, 10, 10, 100, 200, 101L), true),
			"planes remain isolated");
		assertNotEquals(sourceStorage,
			WorldChunkBufferKey.from(
				objectChunk(
					Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,
					101L), true),
			"world and object storage remain isolated");
		assertNotEquals(
			WorldChunkBufferKey.from(
				objectChunk(
					Renderer3DWorldChunkFrame.CHUNK_ROLE_STATIC_OBJECTS,
					201L), true),
			WorldChunkBufferKey.from(
				objectChunk(
					Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,
					201L), true),
			"static and animated object roles remain isolated");

		System.out.println("PASS: resident chunk storage identity");
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh worldChunk(
		int plane,
		int centerX,
		int centerY,
		int originX,
		int originZ,
		long signature) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			plane,
			centerX,
			centerY,
			originX,
			originZ,
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
		int role,
		long signature) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			0,
			10,
			10,
			100,
			200,
			new int[0],
			new float[0],
			new float[0],
			new int[0],
			new int[0],
			new int[0],
			new int[0],
			new Renderer3DModelKind[0],
			new Renderer3DWorldChunkFrame.ShadowCaster[0],
			0,
			0,
			0,
			true,
			role,
			signature);
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label);
		}
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(label);
		}
	}

	private static void assertNotEquals(Object first, Object second, String label) {
		if (first == null ? second == null : first.equals(second)) {
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

    renderer_source = CHUNK_RENDERER.read_text(encoding="utf-8")
    for fragment in (
        "boolean drawOffsetStorage",
        "drawOffsetSupported ? chunk.getStorageSignature() : 0L",
        "if (drawOffsetStorage) {",
        "return storageSignature == key.storageSignature;",
        "WorldChunkBufferKey.from(\n\t\t\t\tchunk,\n\t\t\t\tdrawOffsetSupported)",
        "WorldChunkBufferKey.from(\n\t\t\t\t\tchunk,\n\t\t\t\t\tshaderActive)",
    ):
        require(renderer_source, fragment)

    with tempfile.TemporaryDirectory(
        prefix="resident-chunk-storage-identity-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "ResidentChunkStorageIdentityFixture.java"
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
                "resident chunk storage fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.ResidentChunkStorageIdentityFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "resident chunk storage fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
