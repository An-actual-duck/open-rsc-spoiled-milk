#!/usr/bin/env python3
"""Prove ground items have an exact, fallback-safe direct renderer input."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
RENDERER_3D_FRAME = (
    ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DFrame.java"
)
GRAPHICS_CONTROLLER = (
    ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"
)
SCENE = ROOT / "Client_Base/src/orsc/graphics/three/Scene.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
DRAW_CONTROLLER = ROOT / "PC_Client/src/orsc/OpenGLWorldSpriteDrawController.java"
COMPOSITE_BUILDER = ROOT / "PC_Client/src/orsc/OpenGLCompositeSceneBuilder.java"


FIXTURE = r"""
package orsc;

import com.openrsc.client.model.Sprite;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;

public final class Renderer3DGroundItemDirectInputFixture {
	private Renderer3DGroundItemDirectInputFixture() {
	}

	public static void main(String[] args) throws Exception {
		Sprite sprite = new Sprite(new int[] {
			0x112233, 0x223344, 0x334455, 0x445566,
			0x556677, 0x667788, 0x778899, 0x8899aa,
			0x99aabb, 0xaabbcc, 0xbbccdd, 0xccddee,
			0xddeeff, 0x102030, 0x203040, 0x304050,
		}, 4, 4);
		RendererSpriteTransform transform =
			RendererSpriteTransform.legacyMasks(0x887766, 0, 0x334455, 0xffffffff);
		Renderer3DFrame.GroundItemSpriteSource source =
			new Renderer3DFrame.GroundItemSpriteSource(42, 7, false, sprite, transform);

		Renderer3DFrame frame = newGeometryFrame();
		addSubmission(frame, 4, 40042, 20007, source);
		int anchorIndex = addAnchor(frame, 4, 40042, 20007, 9);
		Renderer2DFrame.SpriteCommand direct =
			command(sprite, transform, 10, anchorIndex, 9, -1, 40042);
		Renderer2DFrame.SpriteCommand captured =
			command(sprite, transform, 10, anchorIndex, 9, 12, 40042);
		assertTrue(
			frame.recordDirectGroundItemLayer(anchorIndex, 9, direct),
			"direct input attaches to exact source owner");
		assertTrue(
			frame.recordWorldSpriteLayer(anchorIndex, 9, captured),
			"legacy comparator attaches to exact owner");

		Renderer3DFrame.WorldSpriteSnapshot snapshot =
			frame.getWorldSpriteSnapshot(anchorIndex);
		assertTrue(snapshot.getGroundItemSource() == source, "source object retained");
		assertEquals(42, snapshot.getGroundItemSource().getItemId(), "item id");
		assertEquals(7, snapshot.getGroundItemSource().getGroundItemIndex(), "ground item index");
		assertTrue(snapshot.isDirectGroundItemParityChecked(), "parity checked");
		assertTrue(snapshot.isDirectGroundItemParityExact(), "parity exact");
		assertEquals("", snapshot.getDirectGroundItemMismatchReason(), "no mismatch");
		assertTrue(snapshot.canUseDirectGroundItemLayer(), "direct input active");
		assertTrue(snapshot.getPresentationLayer(0) == direct, "direct presentation authority");
		assertTrue(snapshot.getLayer(0) == captured, "captured fallback retained");

		Frame presentationFrame = Frame.fromImage(
			new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB),
			1.0f,
			ScaledWindow.ScalingAlgorithm.INTEGER_SCALING,
			new FrameBufferPool(),
			Renderer2DFrame.empty(32, 32),
			frame,
			null);
		try {
			assertTrue(
				OpenGLCompositeSceneBuilder.canUseOwnedWorldSpriteSnapshots(
					presentationFrame,
					new Renderer2DFrame.SpriteCommand[] { captured }),
				"direct item remains inside complete snapshot gate");
		} finally {
			presentationFrame.release();
		}

		Renderer3DFrame mismatchFrame = newGeometryFrame();
		addSubmission(mismatchFrame, 5, 40042, 20007, source);
		int mismatchAnchor = addAnchor(mismatchFrame, 5, 40042, 20007, 10);
		Renderer2DFrame.SpriteCommand mismatchDirect =
			command(sprite, transform, 10, mismatchAnchor, 10, -1, 40042);
		Renderer2DFrame.SpriteCommand mismatchCaptured =
			command(sprite, transform, 11, mismatchAnchor, 10, 13, 40042);
		assertTrue(
			mismatchFrame.recordDirectGroundItemLayer(
				mismatchAnchor,
				10,
				mismatchDirect),
			"mismatch direct input attaches");
		assertTrue(
			mismatchFrame.recordWorldSpriteLayer(
				mismatchAnchor,
				10,
				mismatchCaptured),
			"mismatch comparator attaches");
		Renderer3DFrame.WorldSpriteSnapshot mismatchSnapshot =
			mismatchFrame.getWorldSpriteSnapshot(mismatchAnchor);
		assertTrue(mismatchSnapshot.isDirectGroundItemParityChecked(), "mismatch checked");
		assertTrue(!mismatchSnapshot.isDirectGroundItemParityExact(), "mismatch rejected");
		assertEquals(
			"destination-origin",
			mismatchSnapshot.getDirectGroundItemMismatchReason(),
			"mismatch diagnosis");
		assertTrue(
			mismatchSnapshot.getPresentationLayer(0) == mismatchCaptured,
			"legacy fallback remains visual authority");

		Renderer2DFrame.SpriteCommand noted =
			command(sprite, transform, 10, 0, 0, 1, -1);
		assertTrue(
			OpenGLCompositeSceneBuilder.isLegacyGroundItemSpriteCommand(noted),
			"noted ground-item sprite classification");

		System.out.println("PASS: renderer-v2 direct ground-item input parity and fallback");
	}

	private static Renderer3DFrame newGeometryFrame() throws Exception {
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
			Integer.valueOf(0),
			Integer.valueOf(2400),
			Integer.valueOf(2200),
			Integer.valueOf(32),
			Integer.valueOf(32),
			Integer.valueOf(16),
			Integer.valueOf(16),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(9),
			Integer.valueOf(5),
			new Renderer3DTextureData[0]);
	}

	private static void addSubmission(
		Renderer3DFrame frame,
		int faceId,
		int spriteId,
		int pickIndex,
		Renderer3DFrame.GroundItemSpriteSource source) throws Exception {
		Method method = Renderer3DFrame.class.getDeclaredMethod(
			"addSpriteSubmission",
			int.class, int.class, int.class,
			int.class, int.class, int.class,
			int.class, int.class,
			int.class, int.class, int.class,
			int.class, int.class,
			int.class, int.class, int.class, int.class,
			int.class, int.class,
			boolean.class, String.class,
			Renderer3DFrame.GroundItemSpriteSource.class);
		method.setAccessible(true);
		method.invoke(
			frame,
			Integer.valueOf(faceId),
			Integer.valueOf(spriteId),
			Integer.valueOf(pickIndex),
			Integer.valueOf(128),
			Integer.valueOf(-10),
			Integer.valueOf(128),
			Integer.valueOf(96),
			Integer.valueOf(64),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(400),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(10),
			Integer.valueOf(10),
			Integer.valueOf(8),
			Integer.valueOf(8),
			Integer.valueOf(256),
			Integer.valueOf(0),
			Boolean.TRUE,
			"projected",
			source);
	}

	private static int addAnchor(
		Renderer3DFrame frame,
		int faceId,
		int spriteId,
		int pickIndex,
		int drawOrder) throws Exception {
		Method method = Renderer3DFrame.class.getDeclaredMethod(
			"addSpriteAnchor",
			int.class, int.class, int.class, int.class, int.class,
			int.class, int.class, int.class, int.class, int.class,
			int.class, int.class, int.class, int.class, int.class,
			int.class, boolean.class);
		method.setAccessible(true);
		return ((Integer) method.invoke(
			frame,
			Integer.valueOf(faceId),
			Integer.valueOf(spriteId),
			Integer.valueOf(pickIndex),
			Integer.valueOf(drawOrder),
			Integer.valueOf(400),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(400),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(10),
			Integer.valueOf(10),
			Integer.valueOf(8),
			Integer.valueOf(8),
			Integer.valueOf(256),
			Integer.valueOf(0),
			Boolean.TRUE)).intValue();
	}

	private static Renderer2DFrame.SpriteCommand command(
		Sprite sprite,
		RendererSpriteTransform transform,
		int x,
		int anchorIndex,
		int drawOrder,
		int sequence,
		int spriteId) {
		return new Renderer2DFrame.SpriteCommand(
			sprite,
			x,
			10,
			8,
			8,
			0,
			0,
			4,
			4,
			0,
			0,
			1 << 15,
			1 << 15,
			Renderer2DFrame.SpriteCommand.FULL_ALPHA,
			transform,
			x << 16,
			x << 16,
			false,
			false,
			spriteId,
			anchorIndex,
			drawOrder,
			Renderer2DFrame.Phase.SCENE,
			sequence);
	}

	private static void assertEquals(int expected, int actual, String label) {
		if (expected != actual) {
			throw new AssertionError(
				label + ": expected " + expected + " but was " + actual);
		}
	}

	private static void assertEquals(String expected, String actual, String label) {
		if (!expected.equals(actual)) {
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


def require(source: str, fragment: str, label: str) -> None:
    if fragment not in source:
        raise AssertionError(f"missing {label}: {fragment}")


def main() -> None:
    if not CLIENT_JAR.exists():
        raise AssertionError(
            f"missing {CLIENT_JAR}; run ./scripts/build-client.sh first"
        )

    renderer_3d_frame = RENDERER_3D_FRAME.read_text(encoding="utf-8")
    graphics_controller = GRAPHICS_CONTROLLER.read_text(encoding="utf-8")
    scene = SCENE.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    draw_controller = DRAW_CONTROLLER.read_text(encoding="utf-8")
    composite_builder = COMPOSITE_BUILDER.read_text(encoding="utf-8")
    require(
        renderer_3d_frame,
        "public static final class GroundItemSpriteSource",
        "typed ground-item renderer source",
    )
    require(
        renderer_3d_frame,
        "public boolean recordDirectGroundItemLayer(",
        "direct layer ownership boundary",
    )
    require(
        renderer_3d_frame,
        "directGroundItemMismatchReason(",
        "field-level parity comparator",
    )
    require(
        graphics_controller,
        "buildRenderer2DSceneSpriteCommand(",
        "direct command projection builder",
    )
    require(
        scene,
        "this.graphics.resolveGroundItemRendererSource(",
        "projected source resolution",
    )
    require(
        mudclient,
        "resolveGroundItemRendererSource(",
        "item sprite and mask resolution",
    )
    require(
        draw_controller,
        "snapshot.getPresentationLayer(layerIndex)",
        "parity-gated direct presentation",
    )
    require(
        composite_builder,
        "command.getLegacySpriteId() == -1",
        "noted item classification",
    )

    with tempfile.TemporaryDirectory(
        prefix="renderer-ground-item-direct-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "Renderer3DGroundItemDirectInputFixture.java"
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
                "direct ground-item fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.Renderer3DGroundItemDirectInputFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "direct ground-item fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
