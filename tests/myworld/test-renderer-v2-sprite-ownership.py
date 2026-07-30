#!/usr/bin/env python3
"""Prove renderer-v2 sprite commands retain their exact scene anchor owner."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
RENDERER_2D_FRAME = ROOT / "Client_Base/src/orsc/graphics/Renderer2DFrame.java"
GRAPHICS_CONTROLLER = (
    ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"
)
SCENE = ROOT / "Client_Base/src/orsc/graphics/three/Scene.java"
COMPOSITE_BUILDER = ROOT / "PC_Client/src/orsc/OpenGLCompositeSceneBuilder.java"

FIXTURE = r"""
package orsc;

import com.openrsc.client.model.Sprite;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.RendererSpriteTransform;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DTextureData;

public final class Renderer3DSpriteOwnershipFixture {
	private Renderer3DSpriteOwnershipFixture() {
	}

	public static void main(String[] args) throws Exception {
		Renderer3DFrame geometryFrame = newGeometryFrame();
		Method addAnchor = Renderer3DFrame.class.getDeclaredMethod(
			"addSpriteAnchor",
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
			int.class,
			boolean.class);
		addAnchor.setAccessible(true);
		int firstIndex = ((Integer) addAnchor.invoke(
			geometryFrame,
			Integer.valueOf(11),
			Integer.valueOf(5001),
			Integer.valueOf(31),
			Integer.valueOf(100),
			Integer.valueOf(400),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(400),
			Integer.valueOf(20),
			Integer.valueOf(20),
			Integer.valueOf(10),
			Integer.valueOf(10),
			Integer.valueOf(8),
			Integer.valueOf(8),
			Integer.valueOf(256),
			Integer.valueOf(0),
			Boolean.TRUE)).intValue();
		int secondIndex = ((Integer) addAnchor.invoke(
			geometryFrame,
			Integer.valueOf(12),
			Integer.valueOf(5001),
			Integer.valueOf(32),
			Integer.valueOf(200),
			Integer.valueOf(400),
			Integer.valueOf(0),
			Integer.valueOf(0),
			Integer.valueOf(400),
			Integer.valueOf(20),
			Integer.valueOf(20),
			Integer.valueOf(10),
			Integer.valueOf(10),
			Integer.valueOf(8),
			Integer.valueOf(8),
			Integer.valueOf(256),
			Integer.valueOf(0),
			Boolean.TRUE)).intValue();
		assertEquals(0, firstIndex, "first anchor index");
		assertEquals(1, secondIndex, "second anchor index");

		Frame frame = Frame.fromImage(
			new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB),
			1.0f,
			ScaledWindow.ScalingAlgorithm.INTEGER_SCALING,
			new FrameBufferPool(),
			Renderer2DFrame.empty(32, 32),
			geometryFrame,
			null);
		try {
			Renderer2DFrame.SpriteCommand owned =
				command(secondIndex, 200, 7);
			assertTrue(
				geometryFrame.recordWorldSpriteLayer(secondIndex, 200, owned),
				"record exact renderer snapshot layer");
			Renderer3DFrame.WorldSpriteSnapshot snapshot =
				geometryFrame.getWorldSpriteSnapshot(secondIndex);
			assertTrue(snapshot != null, "renderer snapshot exists");
			assertEquals(secondIndex, snapshot.getAnchorIndex(), "snapshot anchor index");
			assertEquals(1, snapshot.getLayerCount(), "snapshot layer count");
			assertTrue(snapshot.getLayer(0) == owned, "indexed snapshot layer");
			assertTrue(snapshot.getLayer(-1) == null, "negative layer rejected");
			assertTrue(snapshot.getLayer(1) == null, "past-end layer rejected");
			assertTrue(snapshot.ownsLayer(owned), "snapshot owns exact layer");
			assertTrue(
				OpenGLCompositeSceneBuilder.canUseOwnedWorldSpriteSnapshots(
					frame,
					new Renderer2DFrame.SpriteCommand[] { owned }),
				"renderer snapshot direct path");
			WorldSpriteCommand ownedWorld =
				OpenGLCompositeSceneBuilder.buildWorldSpriteCommand(frame, owned);
			assertEquals(12, ownedWorld.anchor.getFaceId(), "owned anchor face");
			assertEquals(200, ownedWorld.legacyDrawOrder, "owned draw order");
			assertEquals("owner-anchor", ownedWorld.anchorMatch.mode, "owner match mode");
			assertEquals(0, ownedWorld.anchorMatch.score, "owner match score");

			Renderer2DFrame.SpriteCommand invalidOwner =
				command(secondIndex, 999, 8);
			assertTrue(
				!OpenGLCompositeSceneBuilder.canUseOwnedWorldSpriteSnapshots(
					frame,
					new Renderer2DFrame.SpriteCommand[] {
						invalidOwner,
						owned,
					}),
				"invalid owner selects compatibility path");
			WorldSpriteCommand fallbackWorld =
				OpenGLCompositeSceneBuilder.buildWorldSpriteCommand(
					frame,
					invalidOwner);
			assertEquals(11, fallbackWorld.anchor.getFaceId(), "fallback anchor face");
			assertEquals(
				"strict-id-bounds",
				fallbackWorld.anchorMatch.mode,
				"fallback match mode");

			List<OpenGLCompositeSceneCommand> ordered =
				OpenGLCompositeSceneBuilder.buildSceneCommands(
					frame,
					new Renderer2DFrame.SpriteCommand[] {
						invalidOwner,
						owned,
					});
			assertEquals(2, ordered.size(), "scene command count");
			assertEquals(
				100,
				ordered.get(0).legacyDrawOrder,
				"legacy fallback ordering");
			assertEquals(
				200,
				ordered.get(1).legacyDrawOrder,
				"exact owner ordering");

			Renderer2DFrame.SpriteCommand outOfSequence =
				command(secondIndex, 200, 6);
			assertTrue(
				geometryFrame.recordWorldSpriteLayer(
					secondIndex,
					200,
					outOfSequence),
				"record sequence-corruption fixture");
			assertTrue(
				!OpenGLCompositeSceneBuilder.canUseOwnedWorldSpriteSnapshots(
					frame,
					new Renderer2DFrame.SpriteCommand[] {
						owned,
						outOfSequence,
					}),
				"out-of-sequence layers select compatibility path");
		} finally {
			frame.release();
		}

		System.out.println("PASS: renderer-v2 exact sprite ownership");
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

	private static Renderer2DFrame.SpriteCommand command(
		int anchorIndex,
		int drawOrder,
		int sequence) {
		Sprite sprite = new Sprite(new int[64], 8, 8);
		return new Renderer2DFrame.SpriteCommand(
			sprite,
			10,
			10,
			8,
			8,
			0,
			0,
			8,
			8,
			0,
			0,
			1 << 16,
			1 << 16,
			Renderer2DFrame.SpriteCommand.FULL_ALPHA,
			RendererSpriteTransform.IDENTITY,
			10 << 16,
			10 << 16,
			false,
			false,
			5001,
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

	private static void assertEquals(
		String expected,
		String actual,
		String label) {
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

    renderer_2d_frame = RENDERER_2D_FRAME.read_text(encoding="utf-8")
    graphics_controller = GRAPHICS_CONTROLLER.read_text(encoding="utf-8")
    scene = SCENE.read_text(encoding="utf-8")
    composite_builder = COMPOSITE_BUILDER.read_text(encoding="utf-8")
    require(
        renderer_2d_frame,
        "private final int sceneSpriteAnchorIndex;",
        "sprite command owner index",
    )
    require(
        renderer_2d_frame,
        "public int getSceneSpriteDrawOrder()",
        "sprite command owner draw-order accessor",
    )
    require(
        graphics_controller,
        "public final void drawSceneEntity(",
        "scene-owned draw boundary",
    )
    require(
        graphics_controller,
        "renderer2DSceneSpriteAnchorIndex = previousAnchorIndex;",
        "scene owner scope restoration",
    )
    require(
        scene,
        "spriteAnchorIndex = geometryFrame.addSpriteAnchor(",
        "scene anchor index capture",
    )
    require(
        composite_builder,
        'return new WorldSpriteAnchorMatch("owner-anchor", 0);',
        "exact owner match classification",
    )
    require(
        composite_builder,
        "Renderer3DFrame.SpriteAnchor ownedAnchor = findOwnedSpriteAnchor(frame, command);",
        "constant-time owner lookup before legacy fallback",
    )
    require(
        renderer_2d_frame,
        "private final int sceneSpriteAnchorIndex;",
        "captured sprite owner metadata",
    )
    renderer_3d_frame = (
        ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DFrame.java"
    ).read_text(encoding="utf-8")
    presenter = (
        ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
    ).read_text(encoding="utf-8")
    require(
        renderer_3d_frame,
        "public static final class WorldSpriteSnapshot",
        "frame-owned world sprite snapshot",
    )
    require(
        renderer_3d_frame,
        "public boolean recordWorldSpriteLayer(",
        "exact layer attachment",
    )
    require(
        composite_builder,
        "static boolean canUseOwnedWorldSpriteSnapshots(",
        "snapshot completeness guard",
    )
    require(
        presenter,
        "drawOpenGLOwnedWorldSpriteSnapshots(",
        "direct snapshot presentation path",
    )

    with tempfile.TemporaryDirectory(
        prefix="renderer-sprite-ownership-"
    ) as raw_temp:
        temp = Path(raw_temp)
        source_dir = temp / "orsc"
        source_dir.mkdir(parents=True)
        fixture = source_dir / "Renderer3DSpriteOwnershipFixture.java"
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
                "sprite ownership fixture compile failed:\n"
                + compile_result.stdout
                + compile_result.stderr
            )

        run_result = subprocess.run(
            [
                "java",
                "-cp",
                f"{temp}:{CLIENT_JAR}",
                "orsc.Renderer3DSpriteOwnershipFixture",
            ],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        if run_result.returncode != 0:
            raise AssertionError(
                "sprite ownership fixture failed:\n"
                + run_result.stdout
                + run_result.stderr
            )
        print(run_result.stdout.strip())


if __name__ == "__main__":
    main()
