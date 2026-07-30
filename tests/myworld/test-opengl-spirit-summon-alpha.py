#!/usr/bin/env python3
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ALPHA_HELPER = ROOT / "PC_Client/src/orsc/OpenGLSpriteAlpha.java"
TEXTURE_BUILDER = ROOT / "PC_Client/src/orsc/OpenGLSpriteTextureBuilder.java"
WORLD_SPRITE_CONTROLLER = ROOT / "PC_Client/src/orsc/OpenGLWorldSpriteDrawController.java"
PRESENTER = ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
GRAPHICS_CONTROLLER = ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label} missing expected snippet: {needle!r}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise AssertionError(f"{label} still contains forbidden snippet: {needle!r}")


def verify_alpha_math() -> None:
    harness = """\
package orsc;

public final class OpenGLSpriteAlphaTestHarness {
    public static void main(String[] args) {
        assertRgba("single spirit layer", 0xC0804099,
            OpenGLSpriteAlpha.blendStraightRgbaOver(0, 0xC08040, 0x99));
        assertRgba("two matching spirit layers", 0xC08040D6,
            OpenGLSpriteAlpha.blendStraightRgbaOver(0xC0804099, 0xC08040, 0x99));
        assertRgba("zero-alpha source", 0x204060FF,
            OpenGLSpriteAlpha.blendStraightRgbaOver(0x204060FF, 0xFFFFFF, 0));
        assertRgba("opaque source", 0x102030FF,
            OpenGLSpriteAlpha.blendStraightRgbaOver(0xA0B0C080, 0x102030, 255));
    }

    private static void assertRgba(String label, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected 0x"
                + Integer.toHexString(expected) + " but got 0x" + Integer.toHexString(actual));
        }
    }
}
"""
    with tempfile.TemporaryDirectory(prefix="spirit-summon-alpha-") as temp_dir:
        temp = Path(temp_dir)
        package_dir = temp / "orsc"
        classes_dir = temp / "classes"
        package_dir.mkdir()
        classes_dir.mkdir()
        harness_path = package_dir / "OpenGLSpriteAlphaTestHarness.java"
        harness_path.write_text(harness, encoding="utf-8")
        subprocess.run(
            ["javac", "-d", str(classes_dir), str(ALPHA_HELPER), str(harness_path)],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes_dir), "orsc.OpenGLSpriteAlphaTestHarness"],
            check=True,
        )


def main() -> None:
    texture_builder = TEXTURE_BUILDER.read_text(encoding="utf-8")
    controller = WORLD_SPRITE_CONTROLLER.read_text(encoding="utf-8")
    presenter = PRESENTER.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    graphics_controller = GRAPHICS_CONTROLLER.read_text(encoding="utf-8")

    require(
        mudclient,
        "npc != null && npc.spiritSummon ? 0x99FFFFFF : 0xFFFFFFFF",
        "armor spirit summon 60-percent opacity",
    )
    require(
        graphics_controller,
        "private boolean canCaptureRenderer2DTransformedSpriteReplay() {\n"
        "\t\treturn canReplaceRenderer2DSceneSprite()\n"
        "\t\t\t|| canCaptureRenderer2DOpenGLWorldOverlayReplay();\n"
        "\t}",
        "partial-alpha scene sprite transform capture",
    )
    require(
        graphics_controller,
        "!transform.canReplayOverSoftwareFrame()\n"
        "\t\t\t\t&& !canCaptureRenderer2DTransformedSpriteReplay()",
        "transformed sprite capture gate",
    )
    forbid(
        graphics_controller,
        "!transform.canReplayOverSoftwareFrame()\n"
        "\t\t\t\t&& !canCaptureRenderer2DOpenGLWorldOverlayReplay()",
        "scene spirit sprite transform rejection",
    )

    require(
        texture_builder,
        "rgba = (replayRgb << 8) | Renderer2DFrame.SpriteCommand.FULL_ALPHA;",
        "direct sprite texture alpha ownership",
    )
    require(
        texture_builder,
        "OpenGLSpriteAlpha.blendStraightRgbaOver(",
        "layered character straight-alpha composition",
    )
    forbid(
        texture_builder,
        "rgba = (replayRgb << 8) | alpha;",
        "direct sprite texture double opacity",
    )
    controller_alpha_expression = (
        "command.getAlpha() / (float) Renderer2DFrame.SpriteCommand.FULL_ALPHA"
    )
    if controller.count(controller_alpha_expression) < 2:
        raise AssertionError(
            "anchored and unanchored world sprite draws must both preserve command opacity"
        )
    require(
        presenter,
        "textureData,\n\t\t\tcommand.getAlpha() / (float) Renderer2DFrame.SpriteCommand.FULL_ALPHA))",
        "direct overlay draw opacity",
    )
    require(
        presenter,
        "drawSpriteRegion(command, region, region.getU0(), region.getU1(), region.getV0(), region.getV1(), alpha);",
        "dynamic sprite draw opacity",
    )
    verify_alpha_math()


if __name__ == "__main__":
    main()
