#!/usr/bin/env python3
"""Exercise precomputed renderer texture references and cache invalidation."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
FRAME_SOURCE = ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DFrame.java"
WORLD_FRAME_SOURCE = (
    ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DWorldChunkFrame.java"
)
TEXTURE_CACHE_SOURCE = ROOT / "PC_Client/src/orsc/OpenGLWorldTextureCache.java"

FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class OpenGLWorldTextureReferenceFixture {
	private static final int LEGACY_TRANSPARENT_TEXTURE = 12345678;

	private OpenGLWorldTextureReferenceFixture() {
	}

	public static void main(String[] args) throws Exception {
		Renderer3DTextureData texture0 = texture(0, 0x224466);
		Renderer3DTextureData texture1 = texture(1, 0x446688);
		Renderer3DTextureData texture2 = texture(2, 0x6688aa);
		Renderer3DTextureData texture3 = texture(3, 0x88aacc);
		Renderer3DTextureData texture3Changed = texture(3, 0xaaccff);

		Renderer3DTextureData[] sourceCatalog = new Renderer3DTextureData[] {
			texture0,
			texture1,
			texture2,
			texture3,
		};
		Renderer3DFrame frame = frame(sourceCatalog);
		sourceCatalog[0] = texture3Changed;
		assertSame(texture0, frame.getTexture(0), "frame snapshots source catalog");
		assertEquals(4, frame.getTextureCount(), "texture catalog count");

		Renderer3DFrame sameCatalog = frame(new Renderer3DTextureData[] {
			texture0,
			texture1,
			texture2,
			texture3,
		});
		Renderer3DFrame changedUnreferencedTexture = frame(new Renderer3DTextureData[] {
			texture0,
			texture1,
			texture2,
			texture3Changed,
		});
		assertEquals(
			frame.getTextureCatalogSignature(),
			sameCatalog.getTextureCatalogSignature(),
			"stable catalog signature");
		assertNotEquals(
			frame.getTextureCatalogSignature(),
			changedUnreferencedTexture.getTextureCatalogSignature(),
			"catalog signature invalidates conservatively");

		Renderer3DWorldChunkFrame.ChunkMesh first = chunk(
			new int[] {0, 1, LEGACY_TRANSPARENT_TEXTURE, 1, -1},
			new int[] {0, 0, 2, 0, 0},
			101L);
		Renderer3DWorldChunkFrame.ChunkMesh second = chunk(
			new int[] {2, 0, 2},
			new int[] {0, 0, 0},
			202L);
		assertReferences(
			first,
			new int[] {0, 1, 2, LEGACY_TRANSPARENT_TEXTURE},
			"chunk references");

		Renderer3DWorldChunkFrame world = Renderer3DWorldChunkFrame.fromChunks(
			Arrays.asList(first, second));
		assertReferences(
			world,
			new int[] {0, 1, 2, LEGACY_TRANSPARENT_TEXTURE},
			"frame references");
		Renderer3DWorldChunkFrame sameWorld = Renderer3DWorldChunkFrame.fromChunks(
			Arrays.asList(first, second));
		Renderer3DWorldChunkFrame changedWorld = Renderer3DWorldChunkFrame.fromChunks(
			Arrays.asList(first, chunk(new int[] {2, 0, 2}, new int[] {0, 0, 0}, 203L)));
		assertEquals(
			world.getTextureReferenceSignature(),
			sameWorld.getTextureReferenceSignature(),
			"stable world texture-reference signature");
		assertNotEquals(
			world.getTextureReferenceSignature(),
			changedWorld.getTextureReferenceSignature(),
			"chunk identity invalidates texture-reference signature");

		OpenGLWorldTextureCache cache = new OpenGLWorldTextureCache(null);
		long signature = cacheSignature(cache, frame, world);
		assertEquals(
			signature,
			cacheSignature(cache, sameCatalog, sameWorld),
			"stable cache signature");
		assertNotEquals(
			signature,
			cacheSignature(cache, changedUnreferencedTexture, world),
			"catalog update invalidates cache signature");
		assertNotEquals(
			signature,
			cacheSignature(cache, frame, changedWorld),
			"world update invalidates cache signature");

		frame.release();
		sameCatalog.release();
		changedUnreferencedTexture.release();
		System.out.println("PASS: OpenGL world texture-reference cache");
	}

	private static Renderer3DTextureData texture(int textureId, int color)
		throws Exception {
		Method factory = Renderer3DTextureData.class.getDeclaredMethod(
			"fromLegacyResource",
			int.class,
			int.class,
			int[].class);
		factory.setAccessible(true);
		int[] pixels = new int[64 * 64];
		Arrays.fill(pixels, color);
		return (Renderer3DTextureData) factory.invoke(null, textureId, 0, pixels);
	}

	private static Renderer3DFrame frame(Renderer3DTextureData[] textures)
		throws Exception {
		Constructor<Renderer3DFrame> constructor =
			Renderer3DFrame.class.getDeclaredConstructor(
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				int.class,
				Renderer3DTextureData[].class);
		constructor.setAccessible(true);
		return constructor.newInstance(
			0,
			0,
			0,
			512,
			346,
			256,
			173,
			0,
			0,
			0,
			0,
			0,
			0,
			9,
			5,
			textures);
	}

	private static Renderer3DWorldChunkFrame.ChunkMesh chunk(
		int[] triangleTextures,
		int[] triangleFallbackColors,
		long signature) {
		return new Renderer3DWorldChunkFrame.ChunkMesh(
			0,
			50,
			50,
			0,
			0,
			new int[0],
			new float[0],
			new float[0],
			new int[0],
			new int[0],
			triangleTextures,
			triangleFallbackColors,
			new Renderer3DModelKind[triangleTextures.length],
			0,
			0,
			0,
			signature);
	}

	private static long cacheSignature(
		OpenGLWorldTextureCache cache,
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame world) throws Exception {
		Method method = OpenGLWorldTextureCache.class.getDeclaredMethod(
			"chunkTextureUploadSignature",
			Renderer3DFrame.class,
			Renderer3DWorldChunkFrame.class);
		method.setAccessible(true);
		return ((Long) method.invoke(cache, frame, world)).longValue();
	}

	private static void assertReferences(
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int[] expected,
		String label) {
		assertEquals(expected.length, chunk.getReferencedTextureCount(), label + " count");
		for (int index = 0; index < expected.length; index++) {
			assertEquals(expected[index], chunk.getReferencedTextureId(index), label + " id");
		}
	}

	private static void assertReferences(
		Renderer3DWorldChunkFrame frame,
		int[] expected,
		String label) {
		assertEquals(expected.length, frame.getReferencedTextureCount(), label + " count");
		for (int index = 0; index < expected.length; index++) {
			assertEquals(expected[index], frame.getReferencedTextureId(index), label + " id");
		}
	}

	private static void assertSame(Object expected, Object actual, String label) {
		if (expected != actual) {
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

    frame_source = FRAME_SOURCE.read_text(encoding="utf-8")
    for fragment in (
        ": textures.clone();",
        "this.textureCatalogSignature = textureCatalogSignature(this.textures);",
        "public long getTextureCatalogSignature()",
    ):
        require(frame_source, fragment)

    world_source = WORLD_FRAME_SOURCE.read_text(encoding="utf-8")
    for fragment in (
        "this.referencedTextureIds = collectReferencedTextureIds(",
        "textureReferences.add(chunk.getReferencedTextureId(index));",
        "public long getTextureReferenceSignature()",
    ):
        require(world_source, fragment)

    cache_source = TEXTURE_CACHE_SOURCE.read_text(encoding="utf-8")
    chunk_upload = cache_source.split(
        "WorldTextureUploadStats uploadReferencedTextures(\n"
        "\t\tRenderer3DFrame frame,",
        1,
    )[1].split(
        "private long chunkTextureUploadSignature",
        1,
    )[0]
    require(chunk_upload, "chunkFrame.getReferencedTextureCount()")
    require(chunk_upload, "chunkFrame.getReferencedTextureId(index)")
    if "getTriangleTexture(" in chunk_upload:
        raise AssertionError("chunk texture upload still scans every triangle")

    signature_builder = cache_source.split(
        "private long chunkTextureUploadSignature", 1
    )[1].split("private long mixSignature", 1)[0]
    require(signature_builder, "frame.getTextureCatalogSignature()")
    require(signature_builder, "chunkFrame.getTextureReferenceSignature()")
    if "getTriangleTexture(" in signature_builder:
        raise AssertionError("texture cache signature still scans every triangle")
    if "mixTextureSignature" in cache_source:
        raise AssertionError("obsolete per-texture signature scan remains")

    with tempfile.TemporaryDirectory(
        prefix="opengl-world-texture-reference-cache-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "OpenGLWorldTextureReferenceFixture.java"
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
                "texture-reference fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.OpenGLWorldTextureReferenceFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "texture-reference fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
