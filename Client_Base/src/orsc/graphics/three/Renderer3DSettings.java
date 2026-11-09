package orsc.graphics.three;

import orsc.WorldEditorBuildSettings;

public final class Renderer3DSettings {
	private static final String GEOMETRY_CAPTURE_PROPERTY = "spoiledmilk.renderer3DGeometryCapture";
	private static final String GEOMETRY_CAPTURE_ENV = "SPOILED_MILK_RENDERER3D_GEOMETRY_CAPTURE";
	private static final String VISIBLE_WORLD_PROPERTY = "spoiledmilk.renderer3DVisibleWorld";
	private static final String VISIBLE_WORLD_ENV = "SPOILED_MILK_RENDERER3D_VISIBLE_WORLD";
	private static final String OPENGL_WORLD_MESH_PROPERTY = "spoiledmilk.openglWorldMesh";
	private static final String OPENGL_WORLD_MESH_ENV = "SPOILED_MILK_OPENGL_WORLD_MESH";
	private static final String OPENGL_WORLD_TEXTURED_VISIBLE_PROPERTY = "spoiledmilk.openglWorldMeshTexturedVisible";
	private static final String OPENGL_WORLD_TEXTURED_VISIBLE_ENV = "SPOILED_MILK_OPENGL_WORLD_TEXTURED_VISIBLE";
	private static final String OPENGL_WORLD_TEXTURED_STATIC_VISIBLE_PROPERTY =
		"spoiledmilk.openglWorldMeshTexturedStaticVisible";
	private static final String OPENGL_WORLD_TEXTURED_STATIC_VISIBLE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_TEXTURED_STATIC_VISIBLE";
	private static final String OPENGL_WORLD_REPLACEMENT_COMPOSITE_PROPERTY =
		"spoiledmilk.openglWorldReplacementComposite";
	private static final String OPENGL_WORLD_REPLACEMENT_COMPOSITE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_REPLACEMENT_COMPOSITE";
	private static final String SKIP_LEGACY_WORLD_RASTER_PROPERTY = "spoiledmilk.skipLegacyWorldRaster";
	private static final String SKIP_LEGACY_WORLD_RASTER_ENV = "SPOILED_MILK_SKIP_LEGACY_WORLD_RASTER";
	private static final String OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE_PROPERTY =
		"spoiledmilk.openglWorldChunksTexturedVisible";
	private static final String OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE";
	private static final String OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE_PROPERTY =
		"spoiledmilk.openglWorldChunksReplacementComposite";
	private static final String OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE";
	private static final String OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT_PROPERTY =
		"spoiledmilk.openglWorldChunksTrustedReplacement";
	private static final String OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT";
	private static final String OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS_PROPERTY =
		"spoiledmilk.openglWorldChunksResidentObjects";
	private static final String OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS_ENV =
		"SPOILED_MILK_OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS";
	private static final boolean VISIBLE_WORLD_ENABLED =
		readBoolean(VISIBLE_WORLD_PROPERTY, VISIBLE_WORLD_ENV);
	private static final boolean GEOMETRY_CAPTURE_ENABLED =
		VISIBLE_WORLD_ENABLED || readBoolean(GEOMETRY_CAPTURE_PROPERTY, GEOMETRY_CAPTURE_ENV);
	private static final boolean OPENGL_WORLD_MESH_ENABLED =
		readBoolean(OPENGL_WORLD_MESH_PROPERTY, OPENGL_WORLD_MESH_ENV);
	private static final boolean OPENGL_WORLD_TEXTURED_VISIBLE_ENABLED =
		OPENGL_WORLD_MESH_ENABLED && readBoolean(OPENGL_WORLD_TEXTURED_VISIBLE_PROPERTY, OPENGL_WORLD_TEXTURED_VISIBLE_ENV);
	private static final boolean OPENGL_WORLD_TEXTURED_STATIC_VISIBLE_ENABLED =
		OPENGL_WORLD_TEXTURED_VISIBLE_ENABLED
			&& readBoolean(OPENGL_WORLD_TEXTURED_STATIC_VISIBLE_PROPERTY, OPENGL_WORLD_TEXTURED_STATIC_VISIBLE_ENV);
	private static final boolean OPENGL_WORLD_REPLACEMENT_COMPOSITE_ENABLED =
		OPENGL_WORLD_TEXTURED_STATIC_VISIBLE_ENABLED
			&& readBoolean(OPENGL_WORLD_REPLACEMENT_COMPOSITE_PROPERTY, OPENGL_WORLD_REPLACEMENT_COMPOSITE_ENV, true);
	private static final boolean SKIP_LEGACY_WORLD_RASTER_ENABLED =
		OPENGL_WORLD_REPLACEMENT_COMPOSITE_ENABLED
			&& readBoolean(SKIP_LEGACY_WORLD_RASTER_PROPERTY, SKIP_LEGACY_WORLD_RASTER_ENV, false);
	private static final boolean RESIDENT_CHUNK_REPLACEMENT_ENABLED =
		OPENGL_WORLD_MESH_ENABLED
			&& readBoolean(
				OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE_PROPERTY,
				OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE_ENV)
			&& readBoolean(
				OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE_PROPERTY,
				OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE_ENV);
	private static final boolean RESIDENT_CHUNK_TRUSTED_REPLACEMENT_ENABLED =
		RESIDENT_CHUNK_REPLACEMENT_ENABLED
			&& readBoolean(
				OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT_PROPERTY,
				OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT_ENV,
				false);
	private static final boolean RESIDENT_CHUNK_OBJECTS_ENABLED =
		RESIDENT_CHUNK_REPLACEMENT_ENABLED
			&& readBoolean(
				OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS_PROPERTY,
				OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS_ENV,
				false);
	private static volatile boolean openGLPresentationAvailable;
    private static volatile boolean browserPresentationAvailable;

	private Renderer3DSettings() {
	}

	static boolean isGeometryCaptureEnabled() {
		return GEOMETRY_CAPTURE_ENABLED || WorldEditorBuildSettings.isEnabled();
	}

	static boolean isVisibleWorldEnabled() {
		return VISIBLE_WORLD_ENABLED;
	}

	public static boolean canSkipLegacyWorldRaster() {
		return (openGLPresentationAvailable || browserPresentationAvailable) && SKIP_LEGACY_WORLD_RASTER_ENABLED;
	}

	public static boolean canSkipProjectedWorldCapture() {
		return (openGLPresentationAvailable || browserPresentationAvailable) && RESIDENT_CHUNK_TRUSTED_REPLACEMENT_ENABLED;
	}

	public static boolean canUseResidentObjectChunks() {
		return (openGLPresentationAvailable || browserPresentationAvailable) && RESIDENT_CHUNK_OBJECTS_ENABLED;
	}

    /** Explicit optional backend capability; false for every ordinary desktop launch. */
    public static void setBrowserPresentationAvailable(boolean available) {
        browserPresentationAvailable = available;
    }

	public static void setOpenGLPresentationAvailable(boolean available) {
		openGLPresentationAvailable = available;
	}

	static boolean canSkipProjectedObjectMeshCapture() {
		return canUseResidentObjectChunks();
	}

	private static boolean readBoolean(String propertyName, String environmentName) {
		return readBoolean(propertyName, environmentName, false);
	}

	private static boolean readBoolean(String propertyName, String environmentName, boolean defaultValue) {
		String property = System.getProperty(propertyName);
		if (property != null) {
			return Boolean.parseBoolean(property);
		}
		String environment = System.getenv(environmentName);
		return environment == null ? defaultValue : Boolean.parseBoolean(environment);
	}
}
