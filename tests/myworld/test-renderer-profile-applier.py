#!/usr/bin/env python3
"""Fingerprint renderer profile application, persistence, and callbacks."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"


FIXTURE = r"""
package orsc;

import orsc.remastered.RemasteredSpriteSettings;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class RendererProfileApplierFixture {
	private static final class FileStore implements RendererProfileApplier.SettingsStore {
		private final Path path;
		int loads;
		int saves;

		FileStore(Path path) {
			this.path = path;
		}

		public Properties load() {
			loads++;
			return read(path);
		}

		public void save(Properties properties) {
			saves++;
			write(path, properties);
		}
	}

	private static final class Host implements RendererProfileApplier.Host {
		int resizeCount;
		int appearanceCount;
		int width;
		int height;

		public void applyRenderSurfaceResize() {
			resizeCount++;
			width = RenderSurfaceSettings.getWidth();
			height = RenderSurfaceSettings.getHeight();
		}

		public void refreshAppearancePreview() {
			appearanceCount++;
		}
	}

	private RendererProfileApplierFixture() {
	}

	public static void main(String[] args) {
		Path path = Paths.get(args[0]);
		String scenario = args[1];
		if ("fingerprints".equals(scenario)) {
			fingerprints(path);
		} else if ("malformed".equals(scenario)) {
			malformedAndMissing();
		} else if ("runtime-load".equals(scenario)) {
			runtimeLoadOverrides();
		} else if ("runtime-sprites".equals(scenario)) {
			runtimeSpriteOverride(path);
		} else if ("write-restart".equals(scenario)) {
			writeRestartState(path);
		} else if ("read-restart".equals(scenario)) {
			readRestartState(path);
		} else {
			throw new AssertionError("unknown scenario " + scenario);
		}
	}

	private static void fingerprints(Path path) {
		Properties initial = new Properties();
		initial.setProperty("unrelated.setting", "keep-me");
		write(path, initial);
		FileStore store = new FileStore(path);
		Host host = new Host();
		RendererProfileApplier applier = new RendererProfileApplier(store, host);

		RemasteredSpriteSettings.setEnabled(true);
		RendererReliefSettings.setTerrainLevel(3);
		RendererReliefSettings.setObjectLevel(4);
		RendererColorDiagnosticSettings.setDimnessLevel(5);
		RendererColorDiagnosticSettings.setContrastLevel(6);
		RendererColorDiagnosticSettings.setGammaLevel(15);
		RendererColorDiagnosticSettings.setSaturationLevel(16);
		applier.apply(RendererProfileSettings.Mode.CLASSIC);
		assertProperty(read(path), "shadow_quality", "off");
		assertClassic(host);
		assertEquals(1, store.loads, "Classic load count");
		assertEquals(1, store.saves, "Classic save count");
		assertSavedClassic(read(path));

		applier.apply(RendererProfileSettings.Mode.CLASSIC);
		assertEquals(2, host.resizeCount, "repeated Classic resize count");
		assertEquals(2, host.appearanceCount, "repeated Classic appearance count");
		assertEquals(2, store.saves, "repeated Classic save count");

		applier.apply(RendererProfileSettings.Mode.REMASTER);
		assertProperty(read(path), "shadow_quality", "high");
		assertEquals(RendererProfileSettings.Mode.REMASTER, RendererProfileSettings.getMode(),
			"Remaster profile");
		assertEquals(RenderSurfaceSettings.Mode.WIDE, RenderSurfaceSettings.getMode(),
			"Remaster surface");
		assertEquals(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN, OpenGLWindowSettings.getMode(),
			"Remaster window");
		assertEquals(RendererLightingSettings.Mode.DIRECTIONAL, RendererLightingSettings.getMode(),
			"Remaster lighting");
		assertEquals(RendererGeometrySettings.Mode.SMOOTH, RendererGeometrySettings.getMode(),
			"Remaster geometry");
		assertEquals(RendererTerrainVariationSettings.Mode.ON,
			RendererTerrainVariationSettings.getMode(), "Remaster terrain variation");
		assertEquals(RendererFogSettings.Mode.ON, RendererFogSettings.getMode(), "Remaster fog");
		assertEquals(RendererBrightnessSettings.Mode.HIGH, RendererBrightnessSettings.getMode(),
			"Remaster brightness");
		assertEquals(RendererToneSettings.Mode.CYCLE, RendererToneSettings.getMode(), "Remaster tone");
		assertTuning(10, 10, 10, 10, 10, 10, "Remaster tuning reset");
		assertTrue(!RemasteredSpriteSettings.isEnabled(),
			"Remaster must not automatically enable enhanced sprites");
		assertEquals(3, host.resizeCount, "Remaster resize count");
		assertEquals(2, host.appearanceCount, "Remaster appearance count");

		RenderSurfaceSettings.setMode(RenderSurfaceSettings.Mode.SVGA);
		RendererToneSettings.setMode(RendererToneSettings.Mode.COOL_NIGHT);
		RendererColorDiagnosticSettings.setGammaLevel(17);
		int resizeBeforeCustom = host.resizeCount;
		int appearanceBeforeCustom = host.appearanceCount;
		applier.apply(RendererProfileSettings.Mode.CUSTOM);
		assertEquals(RenderSurfaceSettings.Mode.SVGA, RenderSurfaceSettings.getMode(),
			"Custom preserves surface");
		assertEquals(RendererToneSettings.Mode.COOL_NIGHT, RendererToneSettings.getMode(),
			"Custom preserves tone");
		assertEquals(17, RendererColorDiagnosticSettings.getGammaLevel(),
			"Custom preserves tuning");
		assertEquals(resizeBeforeCustom, host.resizeCount, "Custom resize count");
		assertEquals(appearanceBeforeCustom, host.appearanceCount, "Custom appearance count");

		RendererProfileSettings.setMode(RendererProfileSettings.Mode.CLASSIC);
		assertEquals(RendererProfileSettings.Mode.REMASTER, applier.cycleAndApply(),
			"cycle Classic to Remaster");
		assertEquals(RendererProfileSettings.Mode.CUSTOM, applier.cycleAndApply(),
			"cycle Remaster to Custom");
		assertEquals(RendererProfileSettings.Mode.CLASSIC, applier.cycleAndApply(),
			"cycle Custom to Classic");

		Path manualPath = path.resolveSibling("manual-settings.conf");
		Properties manualInitial = new Properties();
		manualInitial.setProperty("manual.unrelated", "retained");
		write(manualPath, manualInitial);
		FileStore manualStore = new FileStore(manualPath);
		Host manualHost = new Host();
		RendererProfileApplier manual = new RendererProfileApplier(manualStore, manualHost);
		RenderSurfaceSettings.setMode(RenderSurfaceSettings.Mode.SVGA);
		manual.markCustomAndPersist(RendererProfileApplier.Change.RENDER_SURFACE);
		Properties manualSaved = read(manualPath);
		assertProperty(manualSaved, "manual.unrelated", "retained");
		assertProperty(manualSaved, "render_surface_mode", "800x600");
		assertProperty(manualSaved, "opengl_renderer_profile", "custom");
		assertTrue(!manualSaved.containsKey("opengl_lighting"),
			"partial manual save must not invent unrelated renderer keys");
		assertEquals(1, manualHost.resizeCount, "manual surface resize count");
		assertEquals(0, manualHost.appearanceCount, "manual surface appearance count");

		RemasteredSpriteSettings.setEnabled(true);
		manual.markCustomAndPersist(RendererProfileApplier.Change.REMASTERED_SPRITES);
		assertProperty(read(manualPath), "remastered_sprites", "false");
		assertEquals(1, manualHost.appearanceCount, "manual sprite appearance count");
		assertEquals(2, manualStore.loads, "manual transaction load count");
		assertEquals(2, manualStore.saves, "manual transaction save count");
	}

	private static void assertClassic(Host host) {
		assertEquals(RendererProfileSettings.Mode.CLASSIC, RendererProfileSettings.getMode(),
			"Classic profile");
		assertTrue(!RemasteredSpriteSettings.isEnabled(), "Classic sprites");
		assertTuning(18, 18, 14, 7, 10, 10, "Classic tuning");
		assertEquals(RenderSurfaceSettings.Mode.SVGA, RenderSurfaceSettings.getMode(),
			"Classic surface");
		assertEquals(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN, OpenGLWindowSettings.getMode(),
			"Classic window");
		assertEquals(RendererLightingSettings.Mode.CLASSIC, RendererLightingSettings.getMode(),
			"Classic lighting");
		assertEquals(RendererGeometrySettings.Mode.SMOOTH, RendererGeometrySettings.getMode(),
			"Classic geometry");
		assertEquals(RendererTerrainVariationSettings.Mode.OFF,
			RendererTerrainVariationSettings.getMode(), "Classic terrain variation");
		assertEquals(RendererFogSettings.Mode.ON, RendererFogSettings.getMode(), "Classic fog");
		assertEquals(RendererBrightnessSettings.Mode.HIGH, RendererBrightnessSettings.getMode(),
			"Classic brightness");
		assertEquals(RendererToneSettings.Mode.DAY, RendererToneSettings.getMode(), "Classic tone");
		assertEquals(1, host.resizeCount, "Classic resize count");
		assertEquals(800, host.width, "Classic resize width");
		assertEquals(600, host.height, "Classic resize height");
		assertEquals(1, host.appearanceCount, "Classic appearance count");
	}

	private static void assertSavedClassic(Properties saved) {
		assertProperty(saved, "unrelated.setting", "keep-me");
		assertProperty(saved, "render_surface_mode", "800x600");
		assertProperty(saved, "opengl_window_mode", "borderless-fullscreen");
		assertProperty(saved, "opengl_fog_distance", "on");
		assertTrue(!saved.containsKey("opengl_fog_strength"), "legacy fog key removed");
		assertProperty(saved, "opengl_lighting", "classic");
		assertProperty(saved, "opengl_geometry", "smooth");
		assertProperty(saved, "opengl_terrain_variation", "off");
		assertProperty(saved, "opengl_tone_preview", "day");
		assertProperty(saved, "opengl_relief_tuning_scale", "centered-default-20-v1");
		assertProperty(saved, "opengl_terrain_relief_level", "18");
		assertProperty(saved, "opengl_object_relief_level", "18");
		assertProperty(saved, "opengl_color_tuning_scale", "centered-20-v2");
		assertProperty(saved, "opengl_dimness_level", "14");
		assertProperty(saved, "opengl_contrast_level", "7");
		assertProperty(saved, "opengl_gamma_level", "10");
		assertProperty(saved, "opengl_saturation_level", "10");
		assertProperty(saved, "opengl_renderer_profile", "classic");
		assertProperty(saved, "remastered_sprites", "false");
		assertTrue(!saved.containsKey("opengl_brightness"),
			"profile persistence must retain runtime-only brightness behavior");
	}

	private static void malformedAndMissing() {
		loadAll(new Properties());
		assertEquals(RendererProfileSettings.Mode.REMASTER, RendererProfileSettings.getMode(),
			"missing profile");
		assertEquals(RenderSurfaceSettings.Mode.WIDE, RenderSurfaceSettings.getMode(), "missing surface");
		assertEquals(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN, OpenGLWindowSettings.getMode(),
			"missing window");

		Properties malformed = new Properties();
		malformed.setProperty("opengl_renderer_profile", "garbage");
		malformed.setProperty("render_surface_mode", "garbage");
		malformed.setProperty("opengl_window_mode", "garbage");
		malformed.setProperty("opengl_lighting", "garbage");
		malformed.setProperty("opengl_geometry", "garbage");
		malformed.setProperty("opengl_terrain_variation", "garbage");
		malformed.setProperty("opengl_fog_distance", "garbage");
		malformed.setProperty("opengl_tone_preview", "garbage");
		malformed.setProperty("opengl_terrain_relief_level", "garbage");
		malformed.setProperty("opengl_object_relief_level", "garbage");
		malformed.setProperty("opengl_dimness_level", "garbage");
		malformed.setProperty("opengl_contrast_level", "garbage");
		malformed.setProperty("opengl_gamma_level", "garbage");
		malformed.setProperty("opengl_saturation_level", "garbage");
		malformed.setProperty("remastered_sprites", "garbage");
		loadAll(malformed);
		assertEquals(RendererProfileSettings.Mode.REMASTER, RendererProfileSettings.getMode(),
			"malformed profile");
		assertEquals(RenderSurfaceSettings.Mode.WIDE, RenderSurfaceSettings.getMode(), "malformed surface");
		assertEquals(OpenGLWindowSettings.Mode.WINDOWED, OpenGLWindowSettings.getMode(),
			"malformed window");
		assertEquals(RendererLightingSettings.Mode.CLASSIC, RendererLightingSettings.getMode(),
			"malformed lighting");
		assertEquals(RendererGeometrySettings.Mode.SMOOTH, RendererGeometrySettings.getMode(),
			"malformed geometry");
		assertEquals(RendererTerrainVariationSettings.Mode.ON,
			RendererTerrainVariationSettings.getMode(), "malformed terrain variation");
		assertEquals(RendererFogSettings.Mode.ON, RendererFogSettings.getMode(), "malformed fog");
		assertEquals(RendererToneSettings.Mode.CYCLE, RendererToneSettings.getMode(), "malformed tone");
		assertTuning(10, 10, 10, 10, 10, 10, "malformed tuning");
		assertTrue(!RemasteredSpriteSettings.isEnabled(), "malformed sprites");
	}

	private static void runtimeLoadOverrides() {
		Properties saved = new Properties();
		saved.setProperty("opengl_renderer_profile", "classic");
		saved.setProperty("render_surface_mode", "800x600");
		saved.setProperty("opengl_window_mode", "borderless-fullscreen");
		saved.setProperty("opengl_lighting", "classic");
		saved.setProperty("opengl_geometry", "smooth");
		saved.setProperty("opengl_terrain_variation", "on");
		saved.setProperty("opengl_fog_distance", "on");
		saved.setProperty("remastered_sprites", "false");
		loadAll(saved);
		assertEquals(RendererProfileSettings.Mode.REMASTER, RendererProfileSettings.getMode(),
			"runtime profile load precedence");
		assertEquals(RenderSurfaceSettings.Mode.WIDE, RenderSurfaceSettings.getMode(),
			"runtime surface load precedence");
		assertEquals(OpenGLWindowSettings.Mode.WINDOWED, OpenGLWindowSettings.getMode(),
			"runtime window load precedence");
		assertEquals(RendererLightingSettings.Mode.DIRECTIONAL, RendererLightingSettings.getMode(),
			"runtime lighting load precedence");
		assertEquals(RendererGeometrySettings.Mode.WIRE, RendererGeometrySettings.getMode(),
			"runtime geometry load precedence");
		assertEquals(RendererTerrainVariationSettings.Mode.OFF,
			RendererTerrainVariationSettings.getMode(), "runtime terrain load precedence");
		assertEquals(RendererFogSettings.Mode.OFF, RendererFogSettings.getMode(),
			"runtime fog load precedence");
		assertEquals(RendererToneSettings.Mode.COOL_NIGHT, RendererToneSettings.getMode(),
			"runtime tone load precedence");
		assertTrue(!RemasteredSpriteSettings.isEnabled(), "owner pause overrides runtime sprite load");
	}

	private static void runtimeSpriteOverride(Path path) {
		FileStore store = new FileStore(path);
		Host host = new Host();
		RendererProfileApplier applier = new RendererProfileApplier(store, host);
		applier.apply(RendererProfileSettings.Mode.CLASSIC);
		assertTrue(!RemasteredSpriteSettings.isEnabled(),
			"owner pause also blocks enhanced-sprite runtime override");
		assertProperty(read(path), "remastered_sprites", "false");
		assertEquals(1, host.appearanceCount, "runtime sprite Classic refresh count");
	}

	private static void writeRestartState(Path path) {
		Properties initial = new Properties();
		initial.setProperty("restart.unrelated", "retained");
		write(path, initial);
		RemasteredSpriteSettings.setEnabled(true);
		new RendererProfileApplier(new FileStore(path), new Host())
			.apply(RendererProfileSettings.Mode.REMASTER);
	}

	private static void readRestartState(Path path) {
		Properties saved = read(path);
		loadAll(saved);
		assertProperty(saved, "restart.unrelated", "retained");
		assertEquals(RendererProfileSettings.Mode.REMASTER, RendererProfileSettings.getMode(),
			"restart profile");
		assertEquals(RenderSurfaceSettings.Mode.WIDE, RenderSurfaceSettings.getMode(), "restart surface");
		assertEquals(RendererLightingSettings.Mode.DIRECTIONAL, RendererLightingSettings.getMode(),
			"restart lighting");
		assertEquals(RendererToneSettings.Mode.CYCLE, RendererToneSettings.getMode(), "restart tone");
		assertTrue(!RemasteredSpriteSettings.isEnabled(), "paused sprites remain off after restart");
		assertTuning(10, 10, 10, 10, 10, 10, "restart tuning");
	}

	private static void loadAll(Properties properties) {
		RendererProfileSettings.loadFromClientSettings(properties);
		RenderSurfaceSettings.loadFromClientSettings(properties);
		OpenGLWindowSettings.loadFromClientSettings(properties);
		RendererLightingSettings.loadFromClientSettings(properties);
		RendererGeometrySettings.loadFromClientSettings(properties);
		RendererTerrainVariationSettings.loadFromClientSettings(properties);
		RendererFogSettings.loadFromClientSettings(properties);
		RendererToneSettings.loadFromClientSettings(properties);
		RendererReliefSettings.loadFromClientSettings(properties);
		RendererColorDiagnosticSettings.loadFromClientSettings(properties);
		RemasteredSpriteSettings.loadFromClientSettings(properties);
	}

	private static void assertTuning(int terrain, int object, int dimness, int contrast,
									 int gamma, int saturation, String label) {
		assertEquals(terrain, RendererReliefSettings.getTerrainLevel(), label + " terrain");
		assertEquals(object, RendererReliefSettings.getObjectLevel(), label + " object");
		assertEquals(dimness, RendererColorDiagnosticSettings.getDimnessLevel(), label + " dimness");
		assertEquals(contrast, RendererColorDiagnosticSettings.getContrastLevel(), label + " contrast");
		assertEquals(gamma, RendererColorDiagnosticSettings.getGammaLevel(), label + " gamma");
		assertEquals(saturation, RendererColorDiagnosticSettings.getSaturationLevel(),
			label + " saturation");
	}

	private static Properties read(Path path) {
		Properties properties = new Properties();
		if (!path.toFile().isFile()) {
			return properties;
		}
		try (FileInputStream input = new FileInputStream(path.toFile())) {
			properties.load(input);
			return properties;
		} catch (IOException failure) {
			throw new AssertionError("unable to read " + path, failure);
		}
	}

	private static void write(Path path, Properties properties) {
		try (FileOutputStream output = new FileOutputStream(path.toFile())) {
			properties.store(output, "fixture");
		} catch (IOException failure) {
			throw new AssertionError("unable to write " + path, failure);
		}
	}

	private static void assertProperty(Properties properties, String key, String expected) {
		assertEquals(expected, properties.getProperty(key), "property " + key);
	}

	private static void assertTrue(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
	}

	private static void assertEquals(Object expected, Object actual, String label) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
		}
	}
}
"""


def run_fixture(classes: Path, settings: Path, scenario: str, properties: dict[str, str] | None = None) -> None:
    command = ["java"]
    for key, value in (properties or {}).items():
        command.append(f"-D{key}={value}")
    command.extend([
        "-cp", f"{classes}:{CLIENT_JAR}",
        "orsc.RendererProfileApplierFixture",
        str(settings),
        scenario,
    ])
    subprocess.run(command, cwd=ROOT, check=True)


def main() -> None:
    if not CLIENT_JAR.is_file():
        raise SystemExit(f"FAIL: build the client before running this fixture: {CLIENT_JAR}")
    with tempfile.TemporaryDirectory(prefix="renderer-profile-applier-") as directory:
        temp = Path(directory)
        source = temp / "orsc/RendererProfileApplierFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        subprocess.run(
            ["javac", "-source", "1.8", "-target", "1.8", "-cp", str(CLIENT_JAR),
             "-d", str(temp), str(source)],
            cwd=ROOT,
            check=True,
        )

        run_fixture(temp, temp / "fingerprints.conf", "fingerprints")
        run_fixture(temp, temp / "malformed.conf", "malformed")
        run_fixture(
            temp,
            temp / "runtime-load.conf",
            "runtime-load",
            {
                "spoiledmilk.openglRendererProfile": "remaster",
                "spoiledmilk.renderSurfaceMode": "16:9",
                "spoiledmilk.openglWindowMode": "windowed",
                "spoiledmilk.openglLighting": "directional",
                "spoiledmilk.openglGeometry": "wire",
                "spoiledmilk.terrainVariationEnabled": "false",
                "spoiledmilk.openglFogDistance": "off",
                "spoiledmilk.openglTonePreview": "cool-night",
                "spoiledmilk.remasteredSprites": "true",
            },
        )
        run_fixture(
            temp,
            temp / "runtime-sprites.conf",
            "runtime-sprites",
            {"spoiledmilk.remasteredSprites": "true"},
        )
        restart = temp / "restart.conf"
        run_fixture(temp, restart, "write-restart")
        run_fixture(temp, restart, "read-restart")

    print("PASS: renderer profile bundles, persistence, overrides, and callbacks are stable")


if __name__ == "__main__":
    main()
