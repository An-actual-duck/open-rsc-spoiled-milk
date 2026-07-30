#!/usr/bin/env python3
"""Exercise renderer-v2 sprite clip-mask pooling and state reset behavior."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
DEPTH_FRAME_SOURCE = (
    ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DDepthFrame.java"
)

FIXTURE = r"""
package orsc.graphics.three;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.EnumSet;

public final class Renderer3DSpriteClipPoolFixture {
	private Renderer3DSpriteClipPoolFixture() {
	}

	public static void main(String[] args) throws Exception {
		reusesAndClearsMaskState();
		choosesSmallestAdequateStorage();
		boundsRetainedStorage();
		avoidsStorageForEmptyFrames();
		System.out.println("PASS: renderer-v2 sprite clip pool");
	}

	private static void reusesAndClearsMaskState() throws Exception {
		clearPool();
		Rendered full = renderWithAnchor(100, 80, 0, 0, 100, 80);
		boolean[] fullMask = mask(full.depthFrame);
		assertTrue(fullMask[index(full.depthFrame, 50, 40)], "first mask center");
		full.release();

		Renderer3DFrame sparseFrame = frame(100, 80);
		addAnchor(sparseFrame, 10, 10, 0, 0);
		addAnchor(sparseFrame, 70, 50, 0, 0);
		Rendered sparse = render(sparseFrame);
		assertSame(fullMask, mask(sparse.depthFrame), "mask array should be reused");
		assertTrue(maskAt(sparse.depthFrame, 10, 10), "first sparse anchor");
		assertTrue(maskAt(sparse.depthFrame, 70, 50), "second sparse anchor");
		assertTrue(!maskAt(sparse.depthFrame, 40, 30), "stale mask gap cleared");

		int localGapRow = 30 - intField(sparse.depthFrame, "bufferOriginY");
		assertEquals(-1, rowMinX(sparse.depthFrame)[localGapRow], "gap row minimum reset");
		assertEquals(-1, rowMaxX(sparse.depthFrame)[localGapRow], "gap row maximum reset");

		sparse.release();
		int retainedAfterRelease = pool().size();
		sparse.release();
		assertEquals(retainedAfterRelease, pool().size(), "idempotent release");
	}

	private static void choosesSmallestAdequateStorage() throws Exception {
		clearPool();
		Rendered small = renderWithAnchor(40, 30, 0, 0, 40, 30);
		Rendered large = renderWithAnchor(100, 80, 0, 0, 100, 80);
		boolean[] smallMask = mask(small.depthFrame);
		boolean[] largeMask = mask(large.depthFrame);
		small.release();
		large.release();

		Rendered medium = renderWithAnchor(60, 40, 0, 0, 60, 40);
		assertSame(largeMask, mask(medium.depthFrame),
			"medium mask should use the only adequate storage");
		Rendered tiny = renderWithAnchor(20, 15, 0, 0, 20, 15);
		assertSame(smallMask, mask(tiny.depthFrame),
			"tiny mask should use the smallest adequate storage");
		medium.release();
		tiny.release();
	}

	private static void boundsRetainedStorage() throws Exception {
		clearPool();
		Rendered[] inFlight = new Rendered[6];
		boolean[][] masks = new boolean[inFlight.length][];
		for (int i = 0; i < inFlight.length; i++) {
			int width = 30 + i * 10;
			int height = 20 + i * 5;
			inFlight[i] = renderWithAnchor(width, height, 0, 0, width, height);
			masks[i] = mask(inFlight[i].depthFrame);
		}
		for (Rendered rendered : inFlight) {
			rendered.release();
		}

		ArrayDeque<Object> pool = pool();
		assertEquals(3, pool.size(), "retained storage pool bound");
		for (Object buffers : pool) {
			boolean[] retainedMask = (boolean[]) field(buffers, "mask").get(buffers);
			assertTrue(
				retainedMask == masks[3] || retainedMask == masks[4] || retainedMask == masks[5],
				"pool should retain the three largest storages");
		}
	}

	private static void avoidsStorageForEmptyFrames() throws Exception {
		clearPool();
		Rendered empty = render(frame(100, 80));
		assertTrue(mask(empty.depthFrame) == null, "empty frame mask");
		assertTrue(objectField(empty.depthFrame, "spriteClipStorage", "buffers") == null,
			"empty frame must not acquire clip storage");
		empty.release();
		assertEquals(0, pool().size(), "empty frame must not retain clip storage");
	}

	private static Rendered renderWithAnchor(
		int width,
		int height,
		int drawX,
		int drawY,
		int drawWidth,
		int drawHeight) {
		Renderer3DFrame frame = frame(width, height);
		addAnchor(frame, drawX, drawY, drawWidth, drawHeight);
		return render(frame);
	}

	private static Rendered render(Renderer3DFrame frame) {
		Renderer3DDepthFrame depthFrame = Renderer3DDepthFrame.render(
			frame,
			EnumSet.noneOf(Renderer3DModelKind.class));
		frame.setDepthFrame(depthFrame);
		return new Rendered(frame, depthFrame);
	}

	private static Renderer3DFrame frame(int width, int height) {
		return new Renderer3DFrame(
			0,
			2400,
			2200,
			width,
			height,
			width / 2,
			height / 2,
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

	private static void addAnchor(
		Renderer3DFrame frame,
		int drawX,
		int drawY,
		int drawWidth,
		int drawHeight) {
		frame.addSpriteAnchor(
			0,
			0,
			0,
			0,
			100,
			0,
			0,
			100,
			0,
			0,
			drawX,
			drawY,
			drawWidth,
			drawHeight,
			100,
			0,
			true);
	}

	private static boolean maskAt(Renderer3DDepthFrame depthFrame, int x, int y)
		throws Exception {
		int originX = intField(depthFrame, "bufferOriginX");
		int originY = intField(depthFrame, "bufferOriginY");
		int width = intField(depthFrame, "bufferWidth");
		int localX = x - originX;
		int localY = y - originY;
		return mask(depthFrame)[localY * width + localX];
	}

	private static int index(Renderer3DDepthFrame depthFrame, int x, int y)
		throws Exception {
		int originX = intField(depthFrame, "bufferOriginX");
		int originY = intField(depthFrame, "bufferOriginY");
		int width = intField(depthFrame, "bufferWidth");
		return (y - originY) * width + (x - originX);
	}

	private static boolean[] mask(Renderer3DDepthFrame depthFrame) throws Exception {
		return (boolean[]) field(Renderer3DDepthFrame.class, "spriteClipMask").get(depthFrame);
	}

	private static int[] rowMinX(Renderer3DDepthFrame depthFrame) throws Exception {
		return (int[]) field(Renderer3DDepthFrame.class, "spriteClipRowMinX").get(depthFrame);
	}

	private static int[] rowMaxX(Renderer3DDepthFrame depthFrame) throws Exception {
		return (int[]) field(Renderer3DDepthFrame.class, "spriteClipRowMaxX").get(depthFrame);
	}

	private static int intField(Object target, String name) throws Exception {
		return field(target.getClass(), name).getInt(target);
	}

	private static Object objectField(Object target, String ownerField, String nestedField)
		throws Exception {
		Object owner = field(target.getClass(), ownerField).get(target);
		return field(owner.getClass(), nestedField).get(owner);
	}

	@SuppressWarnings("unchecked")
	private static ArrayDeque<Object> pool() throws Exception {
		return (ArrayDeque<Object>) field(
			Renderer3DDepthFrame.class,
			"AVAILABLE_SPRITE_CLIP_BUFFERS").get(null);
	}

	private static void clearPool() throws Exception {
		pool().clear();
	}

	private static Field field(Object target, String name) throws Exception {
		return field(target.getClass(), name);
	}

	private static Field field(Class<?> type, String name) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
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

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}

	private static final class Rendered {
		private final Renderer3DFrame frame;
		private final Renderer3DDepthFrame depthFrame;

		private Rendered(Renderer3DFrame frame, Renderer3DDepthFrame depthFrame) {
			this.frame = frame;
			this.depthFrame = depthFrame;
		}

		private void release() {
			frame.release();
		}
	}
}
"""


def require(source: str, fragment: str) -> None:
    if fragment not in source:
        raise AssertionError(
            f"Renderer3DDepthFrame.java is missing {fragment!r}"
        )


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    source = DEPTH_FRAME_SOURCE.read_text(encoding="utf-8")
    for fragment in (
        "private static final int MAX_RETAINED_SPRITE_CLIP_BUFFERS = 3;",
        "private static synchronized SpriteClipBuffers acquireSpriteClipBuffers(",
        "candidate.canHold(requiredPixels, requiredRows)",
        "candidate.retainedBytes() < selected.retainedBytes()",
        "Arrays.fill(mask, 0, maskPixels, false);",
        "Arrays.fill(rowMinX, 0, maskHeight, -1);",
        "Arrays.fill(rowMaxX, 0, maskHeight, -1);",
        "spriteClipStorage.release();",
    ):
        require(source, fragment)

    clip_builder = source.split(
        "private static SpriteClipMask from(", 1
    )[1].split("private static SpriteClipMask full(", 1)[0]
    for allocation in ("new boolean[", "new int[maskHeight]"):
        if allocation in clip_builder:
            raise AssertionError(
                f"SpriteClipMask.from still allocates per-frame storage: {allocation}"
            )

    with tempfile.TemporaryDirectory(prefix="renderer-sprite-clip-pool-") as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc/graphics/three"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "Renderer3DSpriteClipPoolFixture.java"
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
                "sprite clip pool fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.graphics.three.Renderer3DSpriteClipPoolFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "sprite clip pool fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
