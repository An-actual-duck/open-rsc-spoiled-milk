#!/usr/bin/env python3
import pathlib
import shutil
import subprocess
import tempfile
import textwrap


ROOT = pathlib.Path(__file__).resolve().parents[2]
SETTINGS = ROOT / "PC_Client/src/orsc/OpenGLSkySettings.java"
PRESENTER = ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
CHUNKS = ROOT / "PC_Client/src/orsc/OpenGLWorldChunkRenderer.java"
GEOMETRY = ROOT / "PC_Client/src/orsc/WorldSkyGeometry.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def settings_fixture() -> None:
    javac = shutil.which("javac")
    java = shutil.which("java")
    require(javac is not None and java is not None, "Java compiler/runtime are required")
    fixture = textwrap.dedent(
        """
        package orsc;

        public final class OpenGLSkySettingsFixture {
            public static void main(String[] args) {
                require(OpenGLSkySettings.Mode.from(null) == OpenGLSkySettings.Mode.WORLD_DOME,
                    "accepted world dome must be the default");
                require(OpenGLSkySettings.Mode.from("world-dome")
                    == OpenGLSkySettings.Mode.WORLD_DOME, "world dome setting");
                require(OpenGLSkySettings.Mode.from("sphere")
                    == OpenGLSkySettings.Mode.WORLD_DOME, "sphere alias");
                require(OpenGLSkySettings.Mode.from("unknown") == OpenGLSkySettings.Mode.WORLD_DOME,
                    "unknown sky setting must retain accepted default");
                OpenGLSkySettings.setMode(OpenGLSkySettings.Mode.WORLD_DOME);
                require(OpenGLSkySettings.getMode() == OpenGLSkySettings.Mode.WORLD_DOME,
                    "runtime comparison override");
                OpenGLSkySettings.setMode(null);
                require(OpenGLSkySettings.getMode() == OpenGLSkySettings.Mode.WORLD_DOME,
                    "null override must restore accepted default");
            }

            private static void require(boolean condition, String message) {
                if (!condition) {
                    throw new AssertionError(message);
                }
            }
        }
        """
    )
    with tempfile.TemporaryDirectory() as temp_dir:
        temp = pathlib.Path(temp_dir)
        fixture_path = temp / "OpenGLSkySettingsFixture.java"
        fixture_path.write_text(fixture, encoding="utf-8")
        subprocess.run([javac, "-d", str(temp), str(SETTINGS), str(fixture_path)], check=True)
        subprocess.run([java, "-cp", str(temp), "orsc.OpenGLSkySettingsFixture"], check=True)


def main() -> None:
    settings_fixture()
    settings = SETTINGS.read_text(encoding="utf-8")
    presenter = PRESENTER.read_text(encoding="utf-8")
    chunks = CHUNKS.read_text(encoding="utf-8")
    geometry = GEOMETRY.read_text(encoding="utf-8")
    require("worldSkyGeometry.emit(radius,presentation,skyGeometrySink);" in chunks, "native sky must use shared geometry source")
    require('"spoiledmilk.openglSky"' in settings, "system-property sky switch missing")
    require('"SPOILED_MILK_OPENGL_SKY"' in settings, "environment sky switch missing")
    require('WORLD_DOME("world-dome")' in settings, "world-dome mode missing")
    require(
        "worldChunkRenderer.drawWorldAnchoredSky(frame.renderer3DFrame, presentation);" in presenter,
        "presenter must route the comparison through world geometry",
    )
    sky_view_start = chunks.index("private float[] skyViewMatrix(Renderer3DFrame frame)")
    sky_view_end = chunks.index("private void configureFog", sky_view_start)
    sky_view = chunks[sky_view_start:sky_view_end]
    require("getCameraRotationX()" in sky_view, "sky view must follow camera pitch")
    require("getCameraRotationY()" in sky_view, "sky view must follow camera yaw")
    require("getCameraOffset" not in sky_view, "sky view must ignore camera translation")
    require("SKY_DOME_ELEVATION_DEGREES" in geometry, "world sky needs explicit altitude rings")
    require("drawWorldSkyDiagnosticMarker" not in chunks, "pink diagnostic landmarks must be removed")
    require(
        geometry.index("drawWorldSkyStars(radius,stars);")
        < geometry.index("drawWorldSkyClouds(radius,p,clouds);"),
        "clouds must layer over stars during dawn/dusk transitions",
    )
    require(
        geometry.count("drawWorldSkyCloudCluster(radius,") == 12,
        "day sky must distribute twelve cloud groups around the dome",
    )
    require("drawWorldSkyWispyCloud" in geometry, "cloud field must include wispy silhouettes")
    require("drawWorldSkyTowerCloud" in geometry, "cloud field must include taller silhouettes")
    require("for (int star = 0; star < 180; star++)" in geometry, "night sky must use denser fixed fields")
    require("drawWorldSkyMilkyWay(radius,stars);" in geometry, "night sky must include a Milky Way band")
    require("float centerElevation = colorsOnly ? 0.0f : -43.0f" in geometry, "Milky Way stars must follow the tilted band")
    require("visibility * 0.030f" in geometry, "Milky Way haze must remain subtle")
    require("return smoothSkyAmount((0.58f-brightness)/0.40f);" in geometry, "stars must fade with presentation brightness")
    require("return smoothSkyAmount((brightness-0.20f)/0.42f);" in geometry, "clouds must fade with presentation brightness")
    require("presentation.fogRed" in geometry, "horizon must stitch to presentation fog")
    require("frame.getFogDistance() * 0.92f" in chunks, "dome must remain inside the far clip")
    load_matrix_start = chunks.index("private void loadMatrix(int matrixMode, float[] matrix)")
    load_matrix_end = chunks.index("private FloatBuffer putWorldToClipMatrix", load_matrix_start)
    require(
        "if (worldToClipMatrixBuffer == null)" in chunks[load_matrix_start:load_matrix_end],
        "first-frame sky drawing must initialize the shared matrix buffer",
    )
    print("OpenGL world-anchored sky checks passed")


if __name__ == "__main__":
    main()
