#!/usr/bin/env python3
import os
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RELIEF = ROOT / "Client_Base/src/orsc/RendererReliefSettings.java"
COLOR = ROOT / "Client_Base/src/orsc/RendererColorDiagnosticSettings.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
RENDERER_SETTINGS_PANEL = ROOT / "Client_Base/src/orsc/RendererSettingsPanel.java"
RENDERER_PROFILE_APPLIER = ROOT / "Client_Base/src/orsc/RendererProfileApplier.java"
SHADER = ROOT / "PC_Client/src/orsc/OpenGLShaderProgram.java"
SHADOW_MASK = ROOT / "PC_Client/src/orsc/RemasterShadowMaskBuilder.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
FRAME_CAPTURE = ROOT / "PC_Client/src/orsc/OpenGLFrameCapture.java"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label} missing expected snippet: {needle!r}")


def compile_fixture(temp: Path) -> None:
    fixture = textwrap.dedent(
        """
        package orsc;

        import java.util.Properties;

        public final class RendererShadingDiagnosticsFixture {
            public static void main(String[] args) {
                if (args.length > 0 && "persistence".equals(args[0])) {
                    Properties loaded = new Properties();
                    loaded.setProperty("opengl_terrain_relief_level", "20");
                    loaded.setProperty("opengl_object_relief_level", "0");
                    loaded.setProperty("opengl_dimness_level", "10");
                    loaded.setProperty("opengl_contrast_level", "99");
                    loaded.setProperty("opengl_gamma_level", "0");
                    loaded.setProperty("opengl_saturation_level", "99");
                    RendererReliefSettings.loadFromClientSettings(loaded);
                    RendererColorDiagnosticSettings.loadFromClientSettings(loaded);
                    Properties saved = new Properties();
                    RendererReliefSettings.saveToClientSettings(saved);
                    RendererColorDiagnosticSettings.saveToClientSettings(saved);
                    System.out.println(saved.getProperty("opengl_terrain_relief_level"));
                    System.out.println(saved.getProperty("opengl_object_relief_level"));
                    System.out.println(saved.getProperty("opengl_dimness_level"));
                    System.out.println(saved.getProperty("opengl_contrast_level"));
                    System.out.println(saved.getProperty("opengl_gamma_level"));
                    System.out.println(saved.getProperty("opengl_saturation_level"));
                    System.out.println(saved.getProperty("opengl_color_tuning_scale"));
                    System.out.println(saved.getProperty("opengl_relief_tuning_scale"));
                    return;
                }
                if (args.length > 0 && "legacy-relief-default".equals(args[0])) {
                    Properties loaded = new Properties();
                    loaded.setProperty("opengl_terrain_relief_level", "5");
                    loaded.setProperty("opengl_object_relief_level", "5");
                    RendererReliefSettings.loadFromClientSettings(loaded);
                    System.out.println(RendererReliefSettings.getTerrainLevel());
                    System.out.println(RendererReliefSettings.getTerrainStrength());
                    System.out.println(RendererReliefSettings.getObjectLevel());
                    System.out.println(RendererReliefSettings.getObjectStrength());
                    return;
                }
                if (args.length > 0 && "legacy-baseline".equals(args[0])) {
                    Properties loaded = new Properties();
                    loaded.setProperty("opengl_dimness_level", "1");
                    loaded.setProperty("opengl_contrast_level", "1");
                    RendererColorDiagnosticSettings.loadFromClientSettings(loaded);
                    System.out.println(RendererColorDiagnosticSettings.getDimnessLevel());
                    System.out.println(RendererColorDiagnosticSettings.getDimnessMultiplier());
                    System.out.println(RendererColorDiagnosticSettings.getContrastLevel());
                    System.out.println(RendererColorDiagnosticSettings.getContrastMultiplier());
                    return;
                }
                if (args.length > 0 && "centered-endpoints".equals(args[0])) {
                    Properties loaded = new Properties();
                    loaded.setProperty("opengl_color_tuning_scale", "centered-20-v2");
                    loaded.setProperty("opengl_dimness_level", "1");
                    loaded.setProperty("opengl_contrast_level", "20");
                    RendererColorDiagnosticSettings.loadFromClientSettings(loaded);
                    System.out.println(RendererColorDiagnosticSettings.getDimnessLevel());
                    System.out.println(RendererColorDiagnosticSettings.getDimnessMultiplier());
                    System.out.println(RendererColorDiagnosticSettings.getContrastLevel());
                    System.out.println(RendererColorDiagnosticSettings.getContrastMultiplier());
                    return;
                }
                if (args.length > 0 && "previous-centered-baseline".equals(args[0])) {
                    Properties loaded = new Properties();
                    loaded.setProperty("opengl_color_tuning_scale", "centered-21-v1");
                    loaded.setProperty("opengl_dimness_level", "11");
                    loaded.setProperty("opengl_contrast_level", "11");
                    RendererColorDiagnosticSettings.loadFromClientSettings(loaded);
                    System.out.println(RendererColorDiagnosticSettings.getDimnessLevel());
                    System.out.println(RendererColorDiagnosticSettings.getDimnessMultiplier());
                    System.out.println(RendererColorDiagnosticSettings.getContrastLevel());
                    System.out.println(RendererColorDiagnosticSettings.getContrastMultiplier());
                    return;
                }
                System.out.println(RendererReliefSettings.getTerrainMode().id);
                System.out.println(RendererReliefSettings.getTerrainStrength());
                System.out.println(RendererReliefSettings.getTerrainLevel());
                System.out.println(RendererReliefSettings.getObjectMode().id);
                System.out.println(RendererReliefSettings.getObjectStrength());
                System.out.println(RendererReliefSettings.getObjectLevel());
                System.out.println(RendererColorDiagnosticSettings.getDimnessLevel());
                System.out.println(RendererColorDiagnosticSettings.getDimnessMultiplier());
                System.out.println(RendererColorDiagnosticSettings.getContrastLevel());
                System.out.println(RendererColorDiagnosticSettings.getContrastMultiplier());
                System.out.println(RendererColorDiagnosticSettings.getGammaLevel());
                System.out.println(RendererColorDiagnosticSettings.getGammaValue());
                System.out.println(RendererColorDiagnosticSettings.getSaturationLevel());
                System.out.println(RendererColorDiagnosticSettings.getSaturationMultiplier());
            }
        }
        """
    )
    fixture_path = temp / "RendererShadingDiagnosticsFixture.java"
    fixture_path.write_text(fixture, encoding="utf-8")
    subprocess.run(
        ["javac", "-d", str(temp), str(RELIEF), str(COLOR), str(fixture_path)],
        check=True,
        cwd=ROOT,
    )


def run_fixture(temp: Path, overrides: dict[str, str], *args: str) -> list[str]:
    env = os.environ.copy()
    for key in (
        "SPOILED_MILK_OPENGL_RELIEF",
        "SPOILED_MILK_OPENGL_TERRAIN_RELIEF",
        "SPOILED_MILK_OPENGL_OBJECT_RELIEF",
    ):
        env.pop(key, None)
    env.update(overrides)
    result = subprocess.run(
        ["java", "-cp", str(temp), "orsc.RendererShadingDiagnosticsFixture", *args],
        check=True,
        cwd=ROOT,
        env=env,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip().splitlines()


def main() -> None:
    relief = RELIEF.read_text(encoding="utf-8")
    color = COLOR.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    settings_panel = RENDERER_SETTINGS_PANEL.read_text(encoding="utf-8")
    profile_applier = RENDERER_PROFILE_APPLIER.read_text(encoding="utf-8")
    shader = SHADER.read_text(encoding="utf-8")
    shadow_mask = SHADOW_MASK.read_text(encoding="utf-8")
    applet = APPLET.read_text(encoding="utf-8")
    frame_capture = FRAME_CAPTURE.read_text(encoding="utf-8")

    require(relief, 'TERRAIN_RELIEF_ENV = "SPOILED_MILK_OPENGL_TERRAIN_RELIEF"',
            "terrain diagnostic override")
    require(relief, 'OBJECT_RELIEF_ENV = "SPOILED_MILK_OPENGL_OBJECT_RELIEF"',
            "object diagnostic override")
    require(relief, "readRuntimeSetting(RELIEF_PROPERTY, RELIEF_ENV)",
            "legacy shared override compatibility")
    require(shader, '"uniform float uTerrainReliefStrength;\\n"',
            "terrain relief shader ownership")
    require(shader, '"uniform float uObjectReliefStrength;\\n"',
            "object relief shader ownership")
    require(shader, '"\\t\\t? uTerrainReliefStrength : uObjectReliefStrength;\\n"',
            "model-kind relief selection")
    require(shader, "RendererReliefSettings.getTerrainStrength()",
            "terrain relief uniform upload")
    require(shader, "RendererReliefSettings.getObjectStrength()",
            "object relief uniform upload")
    require(shader, "clamp(reliefStrength, 0.0, 9.5)",
            "shader relief ceiling matches the twenty-step diagnostic range")
    require(shader, "pow(reliefFactor, max(boundedStrength - 4.5, 0.0))",
            "extended relief range remains progressive above the preserved endpoint")
    require(shader, "RendererColorDiagnosticSettings.getDimnessMultiplier()",
            "live dimness uniform composition")
    require(shader, '"uniform float uContrast;\\n"',
            "live contrast shader uniform")
    require(shader, "RendererColorDiagnosticSettings.getContrastMultiplier()",
            "live contrast uniform upload")
    require(shader, '"uniform float uGamma;\\n"', "live gamma shader uniform")
    require(shader, '"uniform float uSaturation;\\n"', "live saturation shader uniform")
    require(shader, "RendererColorDiagnosticSettings.getGammaValue()", "live gamma uniform upload")
    require(shader, "RendererColorDiagnosticSettings.getSaturationMultiplier()",
            "live saturation uniform upload")
    require(shader, "color.rgb = applySaturation(color.rgb)", "saturation color-grade pass")
    require(shader, "color.rgb = applyGamma(color.rgb)", "gamma color-grade pass")
    require(shader, '"\\tcolor.rgb *= uBrightness;\\n"',
            "brightness applies after Classic or Remaster shading")
    require(color, "private static volatile int dimnessLevel = CENTER_LEVEL;",
            "centered brightness/dimness default")
    require(color, "private static volatile int contrastLevel = CENTER_LEVEL;",
            "centered contrast default")
    require(color, "private static volatile int gammaLevel = CENTER_LEVEL;",
            "centered gamma default")
    require(color, "private static volatile int saturationLevel = CENTER_LEVEL;",
            "centered saturation default")
    require(color, 'CENTERED_SCALE_VERSION = "centered-20-v2"',
            "versioned centered color persistence")
    require(color, "return 1.0f + progress * 0.5f;",
            "brightness range below the center")
    require(color, "return 1.2f - progress * 0.9f;",
            "inverse contrast range below the center")
    require(color, "return 1.2f + progress * 1.9f;",
            "accepted high-contrast endpoint above the center")
    require(relief, "return 2.0f + (boundedLevel - DEFAULT_LEVEL) * 0.75f;",
            "expanded relief range above the centered default")
    require(relief, 'CENTERED_SCALE_VERSION = "centered-default-20-v1"',
            "versioned centered relief persistence")
    require(relief, 'TERRAIN_LEVEL_PROPERTY_KEY = "opengl_terrain_relief_level"',
            "persisted terrain relief level")
    require(relief, "static void loadFromClientSettings(Properties props)",
            "persisted relief settings load")
    require(color, 'DIMNESS_LEVEL_PROPERTY_KEY = "opengl_dimness_level"',
            "persisted dimness level")
    require(color, "static void saveToClientSettings(Properties props)",
            "persisted color settings save")
    require(applet, "KeyEvent.VK_F7 && var1.isShiftDown()",
            "object relief diagnostic hotkey")
    require(applet, "KeyEvent.VK_F8 && var1.isShiftDown()",
            "contrast diagnostic hotkey")
    require(applet, "KeyEvent.VK_F10 && var1.isShiftDown()",
            "saturation diagnostic hotkey")
    require(mudclient, 'RendererDiagnosticSession.newEventRecord("renderer.tuning.change")',
            "AI-readable tuning event")
    require(settings_panel, 'addTuningRows(rows, "Terrain shading"',
            "player-facing terrain shading slider label")
    require(mudclient, '? "terrain shading"',
            "player-facing terrain shading change message")
    require(settings_panel,
            "state.contrastLevel,\n\t\t\t\tRendererColorDiagnosticSettings.MAX_LEVEL",
            "two-line contrast slider scale")
    require(settings_panel, "private static void handleRendererTuningSliderInput(View view, Input input, int settingIndex",
            "direct slider segment selection")
    require(profile_applier, "case TUNING:",
            "tuning changes persist")
    require(shader, '"\\tif (uRawMaterialMode == 0) {\\n"',
            "relief applies outside the Remaster-only lighting branch")
    require(shadow_mask, 'return "terrain shadow dir " + REMASTER_SHADOW_MASK_BASE_ALPHA',
            "shadow channel debug summary")
    require(shadow_mask,
            'DIRECTIONAL_ALPHA_SCALE_ENV =\n\t\t"SPOILED_MILK_REMASTER_DIRECTIONAL_SHADOW_ALPHA_SCALE"',
            "directional terrain-shadow diagnostic override")
    require(shadow_mask,
            "* REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE;",
            "directional terrain-shadow diagnostic scaling")
    require(shadow_mask,
            "Math.min(4, Runtime.getRuntime().availableProcessors() - 1)",
            "bounded remaster shadow-mask build parallelism")
    require(shadow_mask,
            "forEachMaskRow(height, y -> {",
            "parallel row ownership for shadow raster and blur passes")
    require(shadow_mask,
            "MASK_BUILD_POOL.invoke(new MaskRowAction(action, 0, rowCount));",
            "single bounded shadow-mask worker pool")
    require(applet, "String shadingLine = RendererReliefSettings.debugSummary()",
            "F6 shading summary")
    for key in (
        "shading.terrainReliefMode=",
        "shading.terrainReliefLevel=",
        "shading.terrainReliefStrength=",
        "shading.objectReliefMode=",
        "shading.objectReliefLevel=",
        "shading.objectReliefStrength=",
        "shading.dimnessLevel=",
        "shading.dimnessMultiplier=",
        "shading.contrastLevel=",
        "shading.contrastMultiplier=",
        "shading.gammaLevel=",
        "shading.gammaValue=",
        "shading.saturationLevel=",
        "shading.saturationMultiplier=",
        "shading.diffuseResponse=model-kind-fixed",
        "shading.terrainShadowChannels=directional+contact",
        "shading.objectShadowMask=not-applied",
        "shading.terrainShadowDirectionalBaseAlpha=",
        "shading.terrainShadowDirectionalAlphaScale=",
        "shading.terrainShadowContactAlpha=",
    ):
        require(frame_capture, key, f"AI-readable capture field {key}")

    with tempfile.TemporaryDirectory(prefix="renderer-shading-test-") as temp_dir:
        temp = Path(temp_dir)
        compile_fixture(temp)
        if run_fixture(temp, {}) != [
            "max", "2.0", "10", "max", "2.0", "10", "10", "1.0", "10", "1.2",
            "10", "1.0", "10", "1.0"
        ]:
            raise AssertionError("parity defaults must retain max relief for terrain and objects")
        if run_fixture(temp, {"SPOILED_MILK_OPENGL_RELIEF": "low"}) != [
            "low", "0.5", "3", "low", "0.5", "3", "10", "1.0", "10", "1.2",
            "10", "1.0", "10", "1.0"
        ]:
            raise AssertionError("legacy shared relief override must still drive both scopes")
        if run_fixture(
            temp,
            {
                "SPOILED_MILK_OPENGL_RELIEF": "medium",
                "SPOILED_MILK_OPENGL_TERRAIN_RELIEF": "off",
                "SPOILED_MILK_OPENGL_OBJECT_RELIEF": "high",
            },
        ) != [
            "off", "0.0", "1", "high", "1.5", "8", "10", "1.0", "10", "1.2",
            "10", "1.0", "10", "1.0"
        ]:
            raise AssertionError("scoped terrain/object overrides must be independent")
        if run_fixture(temp, {}, "legacy-relief-default") != ["10", "2.0", "10", "2.0"]:
            raise AssertionError("legacy relief defaults must migrate to level 10 without visual change")
        if run_fixture(temp, {}, "legacy-baseline") != ["10", "1.0", "10", "1.2"]:
            raise AssertionError("legacy baseline color settings must migrate to the centered baseline")
        if run_fixture(temp, {}, "previous-centered-baseline") != ["10", "1.0", "10", "1.2"]:
            raise AssertionError("21-position centered baseline must migrate to the 20-position baseline")
        if run_fixture(temp, {}, "persistence") != [
            "20", "1", "14", "20", "1", "20", "centered-20-v2",
            "centered-default-20-v1"
        ]:
            raise AssertionError("persisted tuning levels must load, clamp, and save")
        centered_endpoints = run_fixture(temp, {}, "centered-endpoints")
        if centered_endpoints[0] != "1" or centered_endpoints[1] != "1.5" \
                or centered_endpoints[2] != "20" or abs(float(centered_endpoints[3]) - 3.1) > 0.0001:
            raise AssertionError("centered color endpoints must expose brightness and inverse contrast")

    print("PASS: terrain and object shading diagnostics are independent with parity defaults")


if __name__ == "__main__":
    main()
