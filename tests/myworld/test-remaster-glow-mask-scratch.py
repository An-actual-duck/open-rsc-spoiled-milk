#!/usr/bin/env python3
"""Exercise presenter-owned remaster glow-mask accumulation scratch reuse."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"

FIXTURE = r"""
package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;

public final class RemasterGlowMaskScratchFixture {
	private RemasterGlowMaskScratchFixture() {
	}

	public static void main(String[] args) throws Exception {
		RemasterGlowMaskBuilder builder = new RemasterGlowMaskBuilder();
		Field redField = scratchField("scratchRed");
		Field greenField = scratchField("scratchGreen");
		Field blueField = scratchField("scratchBlue");
		Object redScratch = redField.get(builder);
		Object greenScratch = greenField.get(builder);
		Object blueScratch = blueField.get(builder);

		RemasterGlowMaskBuild red = builder.build(frame(0xff0000, 11L));
		assertRebuild(red, "red");
		assertChannels(red.mask, true, false, false, "red mask");

		RemasterGlowMaskBuild blue = builder.build(frame(0x0000ff, 12L));
		assertRebuild(blue, "blue");
		assertChannels(blue.mask, false, false, true, "blue mask after red");
		assertSame(redScratch, redField.get(builder), "red scratch identity");
		assertSame(greenScratch, greenField.get(builder), "green scratch identity");
		assertSame(blueScratch, blueField.get(builder), "blue scratch identity");

		RemasterGlowMaskBuild redCached = builder.build(frame(0xff0000, 11L));
		assertTrue(redCached != null && redCached.cacheHit && !redCached.rebuild,
			"red cache hit");
		assertChannels(redCached.mask, true, false, false, "cached red mask");

		builder.clear();
		RemasterGlowMaskBuild green = builder.build(frame(0x00ff00, 13L));
		assertRebuild(green, "green after clear");
		assertChannels(green.mask, false, true, false, "green mask after cache clear");
		assertSame(redScratch, redField.get(builder), "clear retains red scratch");
		assertSame(greenScratch, greenField.get(builder), "clear retains green scratch");
		assertSame(blueScratch, blueField.get(builder), "clear retains blue scratch");

		assertTrue(builder.build(null) == null, "null frame");
		assertTrue(builder.build(Renderer3DWorldChunkFrame.EMPTY) == null, "empty frame");
		System.out.println("PASS: remaster glow mask scratch reuse");
	}

	private static Renderer3DWorldChunkFrame frame(int color, long signature) {
		int[] vertexCoords = new int[] {
			0, 0, 0,
			128, 0, 0,
			0, 0, 128,
		};
		Renderer3DWorldChunkFrame.GlowEmitter emitter =
			new Renderer3DWorldChunkFrame.GlowEmitter(
				Renderer3DModelKind.GAME_OBJECT,
				64,
				0,
				64,
				256,
				color,
				255);
		Renderer3DWorldChunkFrame.ChunkMesh chunk =
			new Renderer3DWorldChunkFrame.ChunkMesh(
				0,
				50,
				50,
				0,
				0,
				vertexCoords,
				new float[3],
				new float[3],
				new int[3],
				new int[] {0, 1, 2},
				new int[] {-1},
				new int[] {0},
				new Renderer3DModelKind[] {Renderer3DModelKind.GAME_OBJECT},
				new Renderer3DWorldChunkFrame.ShadowCaster[0],
				new Renderer3DWorldChunkFrame.GlowEmitter[] {emitter},
				0,
				0,
				0,
				true,
				Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,
				signature);
		return Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk));
	}

	private static Field scratchField(String name) throws Exception {
		Field field = RemasterGlowMaskBuilder.class.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static void assertRebuild(RemasterGlowMaskBuild build, String label) {
		assertTrue(build != null && build.mask != null, label + " build");
		assertTrue(!build.cacheHit && build.rebuild, label + " rebuild flags");
		assertTrue(build.mask.visiblePixels > 0, label + " visible pixels");
	}

	private static void assertChannels(
		RemasterGlowMask mask,
		boolean expectRed,
		boolean expectGreen,
		boolean expectBlue,
		String label) {
		ByteBuffer pixels = mask.pixels();
		long red = 0L;
		long green = 0L;
		long blue = 0L;
		while (pixels.remaining() >= 4) {
			red += pixels.get() & 0xff;
			green += pixels.get() & 0xff;
			blue += pixels.get() & 0xff;
			pixels.get();
		}
		assertTrue((red > 0L) == expectRed, label + " red=" + red);
		assertTrue((green > 0L) == expectGreen, label + " green=" + green);
		assertTrue((blue > 0L) == expectBlue, label + " blue=" + blue);
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


def require(source: str, fragment: str) -> None:
    if fragment not in source:
        raise AssertionError(
            f"RemasterGlowMaskBuilder.java is missing {fragment!r}"
        )


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    source = (
        ROOT / "PC_Client/src/orsc/RemasterGlowMaskBuilder.java"
    ).read_text(encoding="utf-8")
    for fragment in (
        "private final float[] scratchRed",
        "private final float[] scratchGreen",
        "private final float[] scratchBlue",
        "Arrays.fill(scratchRed, 0.0f);",
        "Arrays.fill(scratchGreen, 0.0f);",
        "Arrays.fill(scratchBlue, 0.0f);",
    ):
        require(source, fragment)

    build_mask = source.split(
        "private RemasterGlowMask buildMask(", 1
    )[1].split("private void accumulateEmitter(", 1)[0]
    if "new float[" in build_mask:
        raise AssertionError("buildMask still allocates per-rebuild float scratch")

    with tempfile.TemporaryDirectory(prefix="remaster-glow-mask-scratch-") as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "RemasterGlowMaskScratchFixture.java"
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
                "glow-mask scratch fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.RemasterGlowMaskScratchFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "glow-mask scratch fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
