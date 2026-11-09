package orsc;

import orsc.remastered.RemasteredSpriteSettings;

import java.util.Properties;

/** Applies renderer profile bundles and persists renderer-profile changes. */
final class RendererProfileApplier {

	enum Change {
		RENDER_SURFACE,
		WINDOW,
		FOG,
		LIGHTING,
		SHADOWS,
		GEOMETRY,
		TERRAIN_VARIATION,
		TONE,
		TUNING,
		PROFILE,
		REMASTERED_SPRITES
	}

	interface Host {
		void applyRenderSurfaceResize();

		void refreshAppearancePreview();
	}

	interface SettingsStore {
		Properties load();

		void save(Properties properties);
	}

	private static final Change[] COMPLETE_PROFILE = {
		Change.RENDER_SURFACE,
		Change.WINDOW,
		Change.FOG,
		Change.LIGHTING,
		Change.SHADOWS,
		Change.GEOMETRY,
		Change.TERRAIN_VARIATION,
		Change.TONE,
		Change.TUNING,
		Change.PROFILE,
		Change.REMASTERED_SPRITES
	};
	// Brightness remains an in-memory compatibility value: the pre-extraction
	// profile path applied HIGH but did not write opengl_brightness.

	private final SettingsStore settingsStore;
	private final Host host;

	RendererProfileApplier(SettingsStore settingsStore, Host host) {
		if (settingsStore == null) {
			throw new IllegalArgumentException("settingsStore");
		}
		if (host == null) {
			throw new IllegalArgumentException("host");
		}
		this.settingsStore = settingsStore;
		this.host = host;
	}

	RendererProfileSettings.Mode cycleAndApply() {
		return apply(RendererProfileSettings.cycleMode());
	}

	RendererProfileSettings.Mode apply(RendererProfileSettings.Mode requestedMode) {
		RendererProfileSettings.Mode mode = RendererProfileSettings.setMode(requestedMode);
		if (mode != RendererProfileSettings.Mode.CUSTOM) {
			RendererReliefSettings.resetDefaults();
			RendererColorDiagnosticSettings.resetDefaults();
		}
		if (mode == RendererProfileSettings.Mode.CLASSIC) {
			applyClassic();
		} else if (mode == RendererProfileSettings.Mode.REMASTER) {
			applyRemaster();
		}
		persist(settingsStore, COMPLETE_PROFILE);
		return mode;
	}

	void markCustomAndPersist(Change change) {
		if (change == null) {
			throw new IllegalArgumentException("change");
		}
		if (change == Change.RENDER_SURFACE) {
			host.applyRenderSurfaceResize();
		}
		RendererProfileSettings.markCustom();
		if (change == Change.REMASTERED_SPRITES) {
			host.refreshAppearancePreview();
		}
		persist(settingsStore, change, Change.PROFILE);
	}

	static void persist(SettingsStore settingsStore, Change... changes) {
		if (settingsStore == null) {
			throw new IllegalArgumentException("settingsStore");
		}
		Properties properties = settingsStore.load();
		if (properties == null) {
			properties = new Properties();
		}
		if (changes != null) {
			for (Change change : changes) {
				if (change != null) {
					saveChange(properties, change);
				}
			}
		}
		settingsStore.save(properties);
	}

	private void applyClassic() {
		RemasteredSpriteSettings.applyClassicProfile();
		host.refreshAppearancePreview();
		RendererReliefSettings.setTerrainLevel(18);
		RendererReliefSettings.setObjectLevel(18);
		RendererColorDiagnosticSettings.setDimnessLevel(14);
		RendererColorDiagnosticSettings.setContrastLevel(7);
		RenderSurfaceSettings.setMode(RenderSurfaceSettings.Mode.SVGA);
		OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN);
		RendererLightingSettings.setMode(RendererLightingSettings.Mode.CLASSIC);
		RendererShadowSettings.applyProfileDefault(RendererShadowSettings.Quality.OFF);
		RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
		RendererTerrainVariationSettings.setMode(RendererTerrainVariationSettings.Mode.OFF);
		RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
		RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.HIGH);
		RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);
		host.applyRenderSurfaceResize();
	}

	private void applyRemaster() {
		RenderSurfaceSettings.setMode(RenderSurfaceSettings.Mode.WIDE);
		OpenGLWindowSettings.setMode(OpenGLWindowSettings.Mode.BORDERLESS_FULLSCREEN);
		RendererLightingSettings.setMode(RendererLightingSettings.Mode.DIRECTIONAL);
		RendererShadowSettings.applyProfileDefault(RendererShadowSettings.Quality.HIGH);
		RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.SMOOTH);
		RendererTerrainVariationSettings.setMode(RendererTerrainVariationSettings.Mode.ON);
		RendererFogSettings.setMode(RendererFogSettings.Mode.ON);
		RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.HIGH);
		RendererToneSettings.setMode(RendererToneSettings.Mode.CYCLE);
		host.applyRenderSurfaceResize();
	}

	private static void saveChange(Properties properties, Change change) {
		switch (change) {
			case RENDER_SURFACE:
				RenderSurfaceSettings.saveToClientSettings(properties);
				break;
			case WINDOW:
				OpenGLWindowSettings.saveToClientSettings(properties);
				break;
			case FOG:
				RendererFogSettings.saveToClientSettings(properties);
				break;
			case LIGHTING:
				RendererLightingSettings.saveToClientSettings(properties);
				break;
			case SHADOWS:
				RendererShadowSettings.saveToClientSettings(properties);
				break;
			case GEOMETRY:
				RendererGeometrySettings.saveToClientSettings(properties);
				break;
			case TERRAIN_VARIATION:
				RendererTerrainVariationSettings.saveToClientSettings(properties);
				break;
			case TONE:
				RendererToneSettings.saveToClientSettings(properties);
				break;
			case TUNING:
				RendererReliefSettings.saveToClientSettings(properties);
				RendererColorDiagnosticSettings.saveToClientSettings(properties);
				break;
			case PROFILE:
				RendererProfileSettings.saveToClientSettings(properties);
				break;
			case REMASTERED_SPRITES:
				RemasteredSpriteSettings.saveToClientSettings(properties);
				break;
		}
	}
}
