package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.IntConsumer;

final class RemasterShadowMaskBuilder {
	private static final String TEXTURE_SIZE_PROPERTY = "spoiledmilk.remasterShadowMaskTextureSize";
	private static final String TEXTURE_SIZE_ENV = "SPOILED_MILK_REMASTER_SHADOW_MASK_TEXTURE_SIZE";
	private static final String BLUR_RADIUS_PROPERTY = "spoiledmilk.remasterShadowMaskBlurRadius";
	private static final String BLUR_RADIUS_ENV = "SPOILED_MILK_REMASTER_SHADOW_MASK_BLUR_RADIUS";
	private static final String SCENERY_BLUR_RADIUS_PROPERTY = "spoiledmilk.remasterSceneryShadowBlurRadius";
	private static final String SCENERY_BLUR_RADIUS_ENV = "SPOILED_MILK_REMASTER_SCENERY_SHADOW_BLUR_RADIUS";
	private static final String SCENERY_ALPHA_SCALE_PROPERTY = "spoiledmilk.remasterSceneryShadowAlphaScale";
	private static final String SCENERY_ALPHA_SCALE_ENV = "SPOILED_MILK_REMASTER_SCENERY_SHADOW_ALPHA_SCALE";
	private static final String DIRECTIONAL_ALPHA_SCALE_PROPERTY =
		"spoiledmilk.remasterDirectionalShadowAlphaScale";
	private static final String DIRECTIONAL_ALPHA_SCALE_ENV =
		"SPOILED_MILK_REMASTER_DIRECTIONAL_SHADOW_ALPHA_SCALE";
	private static final String LENGTH_SCALE_PROPERTY = "spoiledmilk.remasterShadowLengthScale";
	private static final String LENGTH_SCALE_ENV = "SPOILED_MILK_REMASTER_SHADOW_LENGTH_SCALE";
	private static final String AZIMUTH_BUCKET_PROPERTY = "spoiledmilk.remasterShadowMaskAzimuthBucket";
	private static final String AZIMUTH_BUCKET_ENV = "SPOILED_MILK_REMASTER_SHADOW_MASK_AZIMUTH_BUCKET";
	private static final String ELEVATION_BUCKET_PROPERTY = "spoiledmilk.remasterShadowMaskElevationBucket";
	private static final String ELEVATION_BUCKET_ENV = "SPOILED_MILK_REMASTER_SHADOW_MASK_ELEVATION_BUCKET";
	private static final String CACHE_ENTRIES_PROPERTY = "spoiledmilk.remasterShadowMaskCacheEntries";
	private static final String CACHE_ENTRIES_ENV = "SPOILED_MILK_REMASTER_SHADOW_MASK_CACHE_ENTRIES";
	private static final String CONTACT_ALPHA_PROPERTY = "spoiledmilk.remasterContactShadowAlpha";
	private static final String CONTACT_ALPHA_ENV = "SPOILED_MILK_REMASTER_CONTACT_SHADOW_ALPHA";
	private static final String CONTACT_RADIUS_SCALE_PROPERTY = "spoiledmilk.remasterContactShadowRadiusScale";
	private static final String CONTACT_RADIUS_SCALE_ENV = "SPOILED_MILK_REMASTER_CONTACT_SHADOW_RADIUS_SCALE";
	private static final String CONTACT_BLUR_RADIUS_PROPERTY = "spoiledmilk.remasterContactShadowBlurRadius";
	private static final String CONTACT_BLUR_RADIUS_ENV = "SPOILED_MILK_REMASTER_CONTACT_SHADOW_BLUR_RADIUS";
	private static final int MASK_BUILD_PARALLELISM = Math.max(
		1,
		Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
	private static final int MASK_BUILD_ROW_THRESHOLD = 16;
	private static final ForkJoinPool MASK_BUILD_POOL =
		MASK_BUILD_PARALLELISM > 1
			? new ForkJoinPool(MASK_BUILD_PARALLELISM)
			: null;

	static final int REMASTER_SHADOW_MASK_GRID_SIZE = 512;
	static final float REMASTER_SHADOW_MASK_BASE_ALPHA = 0.42f;
	static final float REMASTER_SHADOW_MASK_MAX_ALPHA = 0.58f;
	static final float REMASTER_SHADOW_MASK_MIN_LENGTH = 96.0f;
	static final float REMASTER_SHADOW_MASK_MAX_LENGTH = 1792.0f;
	static final float REMASTER_SHADOW_MASK_LENGTH_SCALE =
		readFloat(LENGTH_SCALE_PROPERTY, LENGTH_SCALE_ENV, 0.5f, 0.1f, 2.0f);
	static final float REMASTER_SHADOW_MASK_MIN_WIDTH = 24.0f;
	static final float REMASTER_SHADOW_MASK_MIN_DRAW_ALPHA = 0.018f;
	static final int REMASTER_SHADOW_MASK_TEXTURE_SIZE =
		readInt(TEXTURE_SIZE_PROPERTY, TEXTURE_SIZE_ENV, 512, 256, 1024);
	static final float REMASTER_SHADOW_MASK_TEXTURE_PADDING = 384.0f;
	static final int REMASTER_SHADOW_MASK_BLUR_RADIUS =
		readInt(BLUR_RADIUS_PROPERTY, BLUR_RADIUS_ENV, 1, 0, 12);
	static final int REMASTER_SHADOW_MASK_SCENERY_BLUR_RADIUS =
		readInt(SCENERY_BLUR_RADIUS_PROPERTY, SCENERY_BLUR_RADIUS_ENV, 4, 0, 12);
	static final float REMASTER_SHADOW_MASK_SCENERY_ALPHA_SCALE =
		readFloat(SCENERY_ALPHA_SCALE_PROPERTY, SCENERY_ALPHA_SCALE_ENV, 1.3f, 0.25f, 2.0f);
	static final float REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE =
		readFloat(DIRECTIONAL_ALPHA_SCALE_PROPERTY, DIRECTIONAL_ALPHA_SCALE_ENV, 1.0f, 0.0f, 2.0f);
	static final float REMASTER_SHADOW_MASK_AZIMUTH_BUCKET_DEGREES =
		readFloat(AZIMUTH_BUCKET_PROPERTY, AZIMUTH_BUCKET_ENV, 3.0f, 1.0f, 45.0f);
	static final float REMASTER_SHADOW_MASK_ELEVATION_BUCKET_DEGREES =
		readFloat(ELEVATION_BUCKET_PROPERTY, ELEVATION_BUCKET_ENV, 2.0f, 1.0f, 30.0f);
	static final float REMASTER_SHADOW_MASK_CENTER_RETAIN = 0.82f;
	static final float REMASTER_SHADOW_MASK_BLUR_BOOST = 1.18f;
	static final float REMASTER_SHADOW_MASK_CLIP_START_OFFSET = 24.0f;
	static final float REMASTER_SHADOW_MASK_CONTACT_ALPHA =
		readFloat(CONTACT_ALPHA_PROPERTY, CONTACT_ALPHA_ENV, 0.5f, 0.0f, 0.95f);
	static final float REMASTER_SHADOW_MASK_CONTACT_MIN_RADIUS = 4.0f;
	static final float REMASTER_SHADOW_MASK_CONTACT_MAX_RADIUS = 132.0f;
	static final float REMASTER_SHADOW_MASK_CONTACT_RADIUS_SCALE =
		readFloat(CONTACT_RADIUS_SCALE_PROPERTY, CONTACT_RADIUS_SCALE_ENV, 0.05f, 0.02f, 6.0f);
	static final int REMASTER_SHADOW_MASK_CONTACT_BLUR_RADIUS =
		readInt(CONTACT_BLUR_RADIUS_PROPERTY, CONTACT_BLUR_RADIUS_ENV, 2, 0, 6);
	static final float REMASTER_SHADOW_MASK_CONTACT_CENTER_RETAIN = 0.68f;
	static final float REMASTER_SHADOW_MASK_CONTACT_BLUR_BOOST = 1.08f;
	static final int REMASTER_SHADOW_MASK_CACHE_ENTRIES =
		readInt(CACHE_ENTRIES_PROPERTY, CACHE_ENTRIES_ENV, 16, 1, 64);
	static final boolean REMASTER_SHADOW_MASK_DIRECT_WALL_SEGMENT_CLIP = RemasterShadowClassifier.DIRECT_WALL_SEGMENT_CLIP;

	private final LinkedHashMap<Long, RemasterTerrainShadowMask> cacheBySignature =
		new LinkedHashMap<Long, RemasterTerrainShadowMask>(16, 0.75f, true);
	private RemasterShadowMaskBuild lastBuild;
	private long lastInputSignature;
	private boolean lastInputSignatureKnown;
	private long lastWorldSignature;
	private float lastLightAzimuthDegrees;
	private float lastLightElevationDegrees;
	private long lastSettingsSignature;
	private boolean lastInputComponentsKnown;
	private boolean lastCacheHit;
	private boolean lastRebuild;
	private String lastBuildReason = "cold";

	RemasterShadowMaskBuild build(
		Renderer3DWorldChunkFrame chunkFrame,
		RemasterShadowRoofCoverage roofCoverage,
		long worldSignature) {
		RendererRemasterLightSettings.LightAngles lightAngles =
			RendererRemasterLightSettings.getShadowMaskLightAngles();
		float lightAzimuthDegrees = remasterShadowMaskLightAzimuthDegrees(lightAngles);
		float lightElevationDegrees = remasterShadowMaskLightElevationDegrees(lightAngles);
		long settingsSignature = remasterTerrainShadowSettingsSignature();
		long inputSignature = remasterTerrainShadowInputSignature(
			worldSignature,
			lightAzimuthDegrees,
			lightElevationDegrees,
			settingsSignature);
		if (lastInputSignatureKnown && lastInputSignature == inputSignature) {
			if (lastBuild == null || lastBuild.mask == null) {
				return null;
			}
			return new RemasterShadowMaskBuild(
				lastBuild.mask,
				lastBuild.stripCasterCount,
				lastBuild.softSceneryCasterCount,
				lastBuild.contactCasterCount,
				true,
				false,
				"same-input");
		}
		String inputReason = remasterTerrainShadowInputReason(
			worldSignature,
			lightAzimuthDegrees,
			lightElevationDegrees,
			settingsSignature);
		List<RemasterTerrainShadowCaster> casters = buildRemasterTerrainShadowCasters(
			chunkFrame,
			roofCoverage,
			lightAzimuthDegrees,
			lightElevationDegrees);
		if (casters.isEmpty()) {
			rememberInput(
				inputSignature,
				worldSignature,
				lightAzimuthDegrees,
				lightElevationDegrees,
				settingsSignature);
			lastBuild = null;
			lastBuildReason = "no-casters:" + inputReason;
			return null;
		}
		int stripCasterCount = countRemasterShadowMaskCasters(casters, RemasterTerrainShadowCaster.STYLE_STRIP);
		int softSceneryCasterCount =
			countRemasterShadowMaskCasters(casters, RemasterTerrainShadowCaster.STYLE_SOFT_SCENERY);
		int contactCasterCount =
			countRemasterShadowMaskCasters(casters, RemasterTerrainShadowCaster.STYLE_CONTACT);
		RemasterTerrainShadowMask mask = buildMaskTexture(roofCoverage, chunkFrame, casters, inputReason);
		if (mask == null) {
			rememberInput(
				inputSignature,
				worldSignature,
				lightAzimuthDegrees,
				lightElevationDegrees,
				settingsSignature);
			lastBuild = null;
			lastBuildReason = "empty-mask:" + inputReason;
			return null;
		}
		RemasterShadowMaskBuild build =
			new RemasterShadowMaskBuild(
				mask,
				stripCasterCount,
				softSceneryCasterCount,
				contactCasterCount,
				lastCacheHit,
				lastRebuild,
				lastBuildReason);
		rememberInput(
			inputSignature,
			worldSignature,
			lightAzimuthDegrees,
			lightElevationDegrees,
			settingsSignature);
		lastBuild = build;
		return build;
	}

	void clear() {
		cacheBySignature.clear();
		lastBuild = null;
		lastInputSignature = 0L;
		lastInputSignatureKnown = false;
		lastWorldSignature = 0L;
		lastLightAzimuthDegrees = 0.0f;
		lastLightElevationDegrees = 0.0f;
		lastSettingsSignature = 0L;
		lastInputComponentsKnown = false;
		lastCacheHit = false;
		lastRebuild = false;
		lastBuildReason = "cleared";
	}

	static String debugSettingsSummary() {
		return "terrain shadow dir " + REMASTER_SHADOW_MASK_BASE_ALPHA
			+ "/" + REMASTER_SHADOW_MASK_MAX_ALPHA
			+ " x" + REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE
			+ " soft " + REMASTER_SHADOW_MASK_SCENERY_ALPHA_SCALE
			+ " contact " + REMASTER_SHADOW_MASK_CONTACT_ALPHA;
	}

	static long remasterShadowWorldSignature(Renderer3DWorldChunkFrame chunkFrame) {
		long hash = 0xcbf29ce484222325L;
		if (chunkFrame == null) {
			return mix(hash, 0);
		}
		hash = mix(hash, chunkFrame.getChunkCount());
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			boolean objectChunk = chunk.isObjectChunk();
			hash = mix(hash, chunk.getPlane());
			hash = mix(hash, chunk.getCenterSectionX());
			hash = mix(hash, chunk.getCenterSectionY());
			hash = mix(hash, chunk.getOriginWorldX());
			hash = mix(hash, chunk.getOriginWorldZ());
			hash = mix(hash, objectChunk ? 1 : 0);
			hash = mix(hash, chunk.getChunkRole());
			hash = mix(hash, chunk.getShadowCasterCount());
			if (objectChunk) {
				continue;
			}
			hash = mix(hash, chunk.getTriangleCount());
			hash = mix(hash, chunk.getTerrainTriangles());
			hash = mix(hash, chunk.getWallTriangles());
			hash = mix(hash, chunk.getRoofTriangles());
			hash = mix(hash, chunk.hasRoofCoverageData() ? 1 : 0);
			hash = mix(hash, chunk.getRoofCoveredTileCount());
			hash = mix(hash, (int) chunk.getSignature());
			hash = mix(hash, (int) (chunk.getSignature() >>> 32));
			for (int index = 0; index < chunk.getShadowCasterCount(); index++) {
				hash = mixShadowRelevantCaster(hash, chunk.getShadowCaster(index));
			}
		}
		return hash;
	}

	private static long mixShadowRelevantCaster(
		long hash,
		Renderer3DWorldChunkFrame.ShadowCaster caster) {
		if (caster == null) {
			return mix(hash, 0);
		}
		hash = mix(hash, caster.getModelKind().ordinal());
		hash = mix(hash, signatureShadowCasterWorld(caster.getBaseX0()));
		hash = mix(hash, signatureShadowCasterWorld(caster.getBaseY()));
		hash = mix(hash, signatureShadowCasterWorld(caster.getBaseZ0()));
		hash = mix(hash, signatureShadowCasterWorld(caster.getBaseX1()));
		hash = mix(hash, signatureShadowCasterWorld(caster.getBaseZ1()));
		hash = mix(hash, signatureShadowCasterSize(caster.getHeight()));
		hash = mix(hash, signatureShadowCasterSize(caster.getWidth()));
		hash = mix(hash, caster.getOpacity());
		hash = mix(hash, caster.isOutdoorOnly() ? 1 : 0);
		hash = mix(hash, signatureShadowCasterWorld(caster.getFootprintMinX()));
		hash = mix(hash, signatureShadowCasterWorld(caster.getFootprintMaxX()));
		hash = mix(hash, signatureShadowCasterWorld(caster.getFootprintMinZ()));
		return mix(hash, signatureShadowCasterWorld(caster.getFootprintMaxZ()));
	}

	private void rememberInput(
		long inputSignature,
		long worldSignature,
		float lightAzimuthDegrees,
		float lightElevationDegrees,
		long settingsSignature) {
		lastInputSignature = inputSignature;
		lastInputSignatureKnown = true;
		lastWorldSignature = worldSignature;
		lastLightAzimuthDegrees = lightAzimuthDegrees;
		lastLightElevationDegrees = lightElevationDegrees;
		lastSettingsSignature = settingsSignature;
		lastInputComponentsKnown = true;
	}

	private String remasterTerrainShadowInputReason(
		long worldSignature,
		float lightAzimuthDegrees,
		float lightElevationDegrees,
		long settingsSignature) {
		if (!lastInputComponentsKnown) {
			return "cold";
		}
		StringBuilder reason = new StringBuilder();
		if (lastWorldSignature != worldSignature) {
			appendReason(reason, "world");
		}
		if (Float.compare(lastLightAzimuthDegrees, lightAzimuthDegrees) != 0
			|| Float.compare(lastLightElevationDegrees, lightElevationDegrees) != 0) {
			appendReason(reason, "sun");
		}
		if (lastSettingsSignature != settingsSignature) {
			appendReason(reason, "settings");
		}
		return reason.length() == 0 ? "input" : reason.toString();
	}

	private static void appendReason(StringBuilder reason, String value) {
		if (reason.length() > 0) {
			reason.append('+');
		}
		reason.append(value);
	}

	private long remasterTerrainShadowInputSignature(
		long worldSignature,
		float lightAzimuthDegrees,
		float lightElevationDegrees,
		long settingsSignature) {
		long hash = 0xcbf29ce484222325L;
		hash = mix(hash, (int) worldSignature);
		hash = mix(hash, (int) (worldSignature >>> 32));
		hash = mix(hash, Float.floatToIntBits(lightAzimuthDegrees));
		hash = mix(hash, Float.floatToIntBits(lightElevationDegrees));
		hash = mix(hash, (int) settingsSignature);
		return mix(hash, (int) (settingsSignature >>> 32));
	}

	private long remasterTerrainShadowSettingsSignature() {
		long hash = 0xcbf29ce484222325L;
		hash = mix(hash, REMASTER_SHADOW_MASK_TEXTURE_SIZE);
		hash = mix(hash, REMASTER_SHADOW_MASK_BLUR_RADIUS);
		hash = mix(hash, REMASTER_SHADOW_MASK_SCENERY_BLUR_RADIUS);
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_SCENERY_ALPHA_SCALE));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_TEXTURE_PADDING));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_LENGTH_SCALE));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CENTER_RETAIN));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_BLUR_BOOST));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CLIP_START_OFFSET));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_ALPHA));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_MIN_RADIUS));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_MAX_RADIUS));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_RADIUS_SCALE));
		hash = mix(hash, REMASTER_SHADOW_MASK_CONTACT_BLUR_RADIUS);
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_CENTER_RETAIN));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_BLUR_BOOST));
		hash = mix(hash, REMASTER_SHADOW_MASK_DIRECT_WALL_SEGMENT_CLIP ? 1 : 0);
		return mix(hash, REMASTER_SHADOW_MASK_CACHE_ENTRIES);
	}

	private RemasterTerrainShadowMask buildMaskTexture(
		RemasterShadowRoofCoverage roofCoverage,
		Renderer3DWorldChunkFrame chunkFrame,
		List<RemasterTerrainShadowCaster> casters,
		String inputReason) {
		RemasterShadowMaskBounds bounds = RemasterShadowMaskBounds.from(roofCoverage, chunkFrame);
		if (bounds == null) {
			return null;
		}
		bounds = bounds.withPadding(REMASTER_SHADOW_MASK_TEXTURE_PADDING);
		final RemasterShadowMaskBounds rasterBounds = bounds;
		long signature = remasterTerrainShadowMaskSignature(casters, bounds);
		RemasterTerrainShadowMask cachedMask = cacheBySignature.get(signature);
		if (cachedMask != null) {
			lastCacheHit = true;
			lastRebuild = false;
			lastBuildReason = "mask-cache:" + inputReason;
			return cachedMask;
		}
		lastCacheHit = false;
		lastRebuild = true;
		lastBuildReason = "rebuilt:" + inputReason;
		int width = REMASTER_SHADOW_MASK_TEXTURE_SIZE;
		int height = REMASTER_SHADOW_MASK_TEXTURE_SIZE;
		float[] stripAlpha = new float[width * height];
		float[] sceneryAlpha = new float[width * height];
		float[] contactAlpha = new float[width * height];
		float[] horizontalAlpha = new float[width * height];
		float[] stripBlurredAlpha = new float[width * height];
		float[] sceneryBlurredAlpha = new float[width * height];
		float[] contactBlurredAlpha = new float[width * height];
		Map<Long, List<RemasterTerrainShadowCaster>> casterGrid =
			buildRemasterTerrainShadowCasterGrid(casters);
		forEachMaskRow(height, y -> {
			float z = rasterBounds.zAt(y, height);
			int row = y * width;
			for (int x = 0; x < width; x++) {
				remasterTerrainShadowMaskAlphas(
					roofCoverage,
					casterGrid,
					rasterBounds.xAt(x, width),
					z,
					stripAlpha,
					sceneryAlpha,
					contactAlpha,
					row + x);
			}
		});
		blurHorizontal(stripAlpha, horizontalAlpha, width, height, REMASTER_SHADOW_MASK_BLUR_RADIUS);
		blurVertical(horizontalAlpha, stripBlurredAlpha, width, height, REMASTER_SHADOW_MASK_BLUR_RADIUS);
		blurHorizontal(sceneryAlpha, horizontalAlpha, width, height, REMASTER_SHADOW_MASK_SCENERY_BLUR_RADIUS);
		blurVertical(horizontalAlpha, sceneryBlurredAlpha, width, height, REMASTER_SHADOW_MASK_SCENERY_BLUR_RADIUS);
		blurHorizontal(contactAlpha, horizontalAlpha, width, height, REMASTER_SHADOW_MASK_CONTACT_BLUR_RADIUS);
		blurVertical(horizontalAlpha, contactBlurredAlpha, width, height, REMASTER_SHADOW_MASK_CONTACT_BLUR_RADIUS);
		ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
		int visiblePixels = 0;
		for (int index = 0; index < stripAlpha.length; index++) {
			float stripDirectionalAlpha = Math.max(
				stripAlpha[index] * REMASTER_SHADOW_MASK_CENTER_RETAIN,
				stripBlurredAlpha[index] * REMASTER_SHADOW_MASK_BLUR_BOOST);
			float sceneryDirectionalAlpha = Math.max(
				sceneryAlpha[index] * REMASTER_SHADOW_MASK_CENTER_RETAIN,
				sceneryBlurredAlpha[index] * REMASTER_SHADOW_MASK_BLUR_BOOST)
				* REMASTER_SHADOW_MASK_SCENERY_ALPHA_SCALE;
			float directionalAlpha = Math.max(stripDirectionalAlpha, sceneryDirectionalAlpha)
				* REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE;
			float diffusedContactAlpha = Math.max(
				contactAlpha[index] * REMASTER_SHADOW_MASK_CONTACT_CENTER_RETAIN,
				contactBlurredAlpha[index] * REMASTER_SHADOW_MASK_CONTACT_BLUR_BOOST);
			float alpha = Math.max(directionalAlpha, diffusedContactAlpha);
			alpha = clamp(alpha, 0.0f, REMASTER_SHADOW_MASK_MAX_ALPHA);
			if (alpha > REMASTER_SHADOW_MASK_MIN_DRAW_ALPHA) {
				visiblePixels++;
			}
			pixels.put((byte) 0);
			pixels.put((byte) 0);
			pixels.put((byte) 0);
			pixels.put((byte) Math.round(alpha * 255.0f));
		}
		if (visiblePixels <= 0) {
			return null;
		}
		pixels.flip();
		RemasterTerrainShadowMask mask = new RemasterTerrainShadowMask(
			signature,
			width,
			height,
			visiblePixels,
			bounds.minX,
			bounds.minZ,
			bounds.invSpanX(),
			bounds.invSpanZ(),
			pixels);
		cacheBySignature.put(Long.valueOf(signature), mask);
		trimRemasterShadowMaskCache();
		return mask;
	}

	private void trimRemasterShadowMaskCache() {
		Iterator<Map.Entry<Long, RemasterTerrainShadowMask>> iterator = cacheBySignature.entrySet().iterator();
		while (cacheBySignature.size() > REMASTER_SHADOW_MASK_CACHE_ENTRIES && iterator.hasNext()) {
			iterator.next();
			iterator.remove();
		}
	}

	private long remasterTerrainShadowMaskSignature(
		List<RemasterTerrainShadowCaster> casters,
		RemasterShadowMaskBounds bounds) {
		long hash = 0xcbf29ce484222325L;
		hash = mix(hash, REMASTER_SHADOW_MASK_TEXTURE_SIZE);
		hash = mix(hash, REMASTER_SHADOW_MASK_BLUR_RADIUS);
		hash = mix(hash, REMASTER_SHADOW_MASK_SCENERY_BLUR_RADIUS);
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_SCENERY_ALPHA_SCALE));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_TEXTURE_PADDING));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_LENGTH_SCALE));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CENTER_RETAIN));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_BLUR_BOOST));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CLIP_START_OFFSET));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_ALPHA));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_MIN_RADIUS));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_MAX_RADIUS));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_RADIUS_SCALE));
		hash = mix(hash, REMASTER_SHADOW_MASK_CONTACT_BLUR_RADIUS);
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_CENTER_RETAIN));
		hash = mix(hash, Float.floatToIntBits(REMASTER_SHADOW_MASK_CONTACT_BLUR_BOOST));
		hash = mix(hash, REMASTER_SHADOW_MASK_DIRECT_WALL_SEGMENT_CLIP ? 1 : 0);
		hash = mix(hash, signatureWorldFloat(bounds.minX));
		hash = mix(hash, signatureWorldFloat(bounds.maxX));
		hash = mix(hash, signatureWorldFloat(bounds.minZ));
		hash = mix(hash, signatureWorldFloat(bounds.maxZ));
		hash = mix(hash, casters == null ? 0 : casters.size());
		if (casters != null) {
			long[] casterSignatures = new long[casters.size()];
			for (int index = 0; index < casters.size(); index++) {
				casterSignatures[index] = casters.get(index).stableSignature();
			}
			Arrays.sort(casterSignatures);
			for (long casterSignature : casterSignatures) {
				hash = mix(hash, (int) casterSignature);
				hash = mix(hash, (int) (casterSignature >>> 32));
			}
		}
		return hash;
	}

	private void remasterTerrainShadowMaskAlphas(
		RemasterShadowRoofCoverage roofCoverage,
		Map<Long, List<RemasterTerrainShadowCaster>> casterGrid,
		float x,
		float z,
		float[] stripAlpha,
		float[] sceneryAlpha,
		float[] contactAlpha,
		int index) {
		List<RemasterTerrainShadowCaster> casters =
			casterGrid.get(remasterShadowMaskCellKey(remasterShadowMaskCell(x), remasterShadowMaskCell(z)));
		if (casters == null || casters.isEmpty()) {
			return;
		}
		float strip = 0.0f;
		float scenery = 0.0f;
		float contact = 0.0f;
		for (RemasterTerrainShadowCaster caster : casters) {
			float casterAlpha = caster.alphaAt(x, z);
			if (casterAlpha <= 0.0f || caster.isBlockedBy(roofCoverage, x, z)) {
				continue;
			}
			if (caster.style == RemasterTerrainShadowCaster.STYLE_STRIP) {
				strip = Math.max(strip, casterAlpha);
			} else if (caster.style == RemasterTerrainShadowCaster.STYLE_SOFT_SCENERY) {
				scenery = Math.max(scenery, casterAlpha);
			} else if (caster.style == RemasterTerrainShadowCaster.STYLE_CONTACT) {
				contact = Math.max(contact, casterAlpha);
			}
		}
		stripAlpha[index] = clamp(strip, 0.0f, REMASTER_SHADOW_MASK_MAX_ALPHA);
		sceneryAlpha[index] = clamp(scenery, 0.0f, REMASTER_SHADOW_MASK_MAX_ALPHA);
		contactAlpha[index] = clamp(contact, 0.0f, REMASTER_SHADOW_MASK_MAX_ALPHA);
	}

	private static void blurHorizontal(float[] source, float[] target, int width, int height, int radius) {
		if (radius <= 0) {
			System.arraycopy(source, 0, target, 0, source.length);
			return;
		}
		forEachMaskRow(height, y -> {
			int row = y * width;
			for (int x = 0; x < width; x++) {
				float total = 0.0f;
				int count = 0;
				for (int offset = -radius; offset <= radius; offset++) {
					int sampleX = x + offset;
					if (sampleX < 0 || sampleX >= width) {
						continue;
					}
					total += source[row + sampleX];
					count++;
				}
				target[row + x] = count <= 0 ? 0.0f : total / count;
			}
		});
	}

	private static void blurVertical(float[] source, float[] target, int width, int height, int radius) {
		if (radius <= 0) {
			System.arraycopy(source, 0, target, 0, source.length);
			return;
		}
		forEachMaskRow(height, y -> {
			for (int x = 0; x < width; x++) {
				float total = 0.0f;
				int count = 0;
				for (int offset = -radius; offset <= radius; offset++) {
					int sampleY = y + offset;
					if (sampleY < 0 || sampleY >= height) {
						continue;
					}
					total += source[sampleY * width + x];
					count++;
				}
				target[y * width + x] = count <= 0 ? 0.0f : total / count;
			}
		});
	}

	private static void forEachMaskRow(int rowCount, IntConsumer action) {
		if (rowCount <= 0 || action == null) {
			return;
		}
		if (MASK_BUILD_POOL == null || rowCount <= MASK_BUILD_ROW_THRESHOLD) {
			for (int row = 0; row < rowCount; row++) {
				action.accept(row);
			}
			return;
		}
		MASK_BUILD_POOL.invoke(new MaskRowAction(action, 0, rowCount));
	}

	private static final class MaskRowAction extends RecursiveAction {
		private final IntConsumer action;
		private final int startInclusive;
		private final int endExclusive;

		private MaskRowAction(
			IntConsumer action,
			int startInclusive,
			int endExclusive) {
			this.action = action;
			this.startInclusive = startInclusive;
			this.endExclusive = endExclusive;
		}

		@Override
		protected void compute() {
			int count = endExclusive - startInclusive;
			if (count <= MASK_BUILD_ROW_THRESHOLD) {
				for (int row = startInclusive; row < endExclusive; row++) {
					action.accept(row);
				}
				return;
			}
			int middle = startInclusive + count / 2;
			invokeAll(
				new MaskRowAction(action, startInclusive, middle),
				new MaskRowAction(action, middle, endExclusive));
		}
	}

	private List<RemasterTerrainShadowCaster> buildRemasterTerrainShadowCasters(
		Renderer3DWorldChunkFrame chunkFrame,
		RemasterShadowRoofCoverage roofCoverage,
		float lightAzimuthDegrees,
		float lightElevationDegrees) {
		List<RemasterTerrainShadowCaster> casters = new ArrayList<RemasterTerrainShadowCaster>();
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int casterCount = chunk.getShadowCasterCount();
			for (int index = 0; index < casterCount; index++) {
				Renderer3DWorldChunkFrame.ShadowCaster caster = chunk.getShadowCaster(index);
				if (!RemasterShadowClassifier.isEligibleCaster(caster)) {
					continue;
				}
				if (RemasterShadowClassifier.roofClassificationForCaster(roofCoverage, chunk, caster) != 0) {
					continue;
				}
				RemasterTerrainShadowCaster projected = RemasterTerrainShadowCaster.from(
					caster,
					chunk.getPlane(),
					lightAzimuthDegrees,
					lightElevationDegrees);
				if (projected != null) {
					casters.add(projected);
				}
				RemasterTerrainShadowCaster contact =
					RemasterTerrainShadowCaster.contactFrom(caster, chunk.getPlane());
				if (contact != null) {
					casters.add(contact);
				}
			}
		}
		return casters;
	}

	private int countRemasterShadowMaskCasters(List<RemasterTerrainShadowCaster> casters, int style) {
		int count = 0;
		for (RemasterTerrainShadowCaster caster : casters) {
			if (caster.style == style) {
				count++;
			}
		}
		return count;
	}

	private Map<Long, List<RemasterTerrainShadowCaster>> buildRemasterTerrainShadowCasterGrid(
		List<RemasterTerrainShadowCaster> casters) {
		Map<Long, List<RemasterTerrainShadowCaster>> grid =
			new HashMap<Long, List<RemasterTerrainShadowCaster>>();
		for (RemasterTerrainShadowCaster caster : casters) {
			int minCellX = remasterShadowMaskCell(caster.minX);
			int maxCellX = remasterShadowMaskCell(caster.maxX);
			int minCellZ = remasterShadowMaskCell(caster.minZ);
			int maxCellZ = remasterShadowMaskCell(caster.maxZ);
			for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
				for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
					long key = remasterShadowMaskCellKey(cellX, cellZ);
					List<RemasterTerrainShadowCaster> cellCasters = grid.get(key);
					if (cellCasters == null) {
						cellCasters = new ArrayList<RemasterTerrainShadowCaster>();
						grid.put(key, cellCasters);
					}
					cellCasters.add(caster);
				}
			}
		}
		return grid;
	}

	private static int remasterShadowMaskCell(float value) {
		return (int) Math.floor(value / REMASTER_SHADOW_MASK_GRID_SIZE);
	}

	private static long remasterShadowMaskCellKey(int cellX, int cellZ) {
		return ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
	}

	static float remasterShadowMaskLightAzimuthDegrees() {
		return remasterShadowMaskLightAzimuthDegrees(RendererRemasterLightSettings.getShadowMaskLightAngles());
	}

	static float remasterShadowMaskLightElevationDegrees() {
		return remasterShadowMaskLightElevationDegrees(RendererRemasterLightSettings.getShadowMaskLightAngles());
	}

	private static float remasterShadowMaskLightAzimuthDegrees(
		RendererRemasterLightSettings.LightAngles lightAngles) {
		return quantize(lightAngles.azimuthDegrees, REMASTER_SHADOW_MASK_AZIMUTH_BUCKET_DEGREES);
	}

	private static float remasterShadowMaskLightElevationDegrees(
		RendererRemasterLightSettings.LightAngles lightAngles) {
		return clamp(
			quantize(
				lightAngles.elevationDegrees,
				REMASTER_SHADOW_MASK_ELEVATION_BUCKET_DEGREES),
			5.0f,
			85.0f);
	}

	static float quantize(float value, float bucketSize) {
		if (bucketSize <= 0.0f) {
			return value;
		}
		return Math.round(value / bucketSize) * bucketSize;
	}

	static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	static long mix(long hash, int value) {
		hash ^= value;
		return hash * 0x100000001b3L;
	}

	static int signatureWorldFloat(float value) {
		return Math.round(value / 4.0f);
	}

	static int signatureUnitFloat(float value) {
		return Math.round(value * 1000.0f);
	}

	private static int signatureShadowCasterWorld(int value) {
		return Math.round(value / 32.0f);
	}

	private static int signatureShadowCasterSize(int value) {
		return Math.round(value / 32.0f);
	}

	private static int readInt(
		String propertyName,
		String envName,
		int defaultValue,
		int minValue,
		int maxValue) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}

		try {
			int parsed = Integer.parseInt(value.trim());
			return Math.max(minValue, Math.min(maxValue, parsed));
		} catch (NumberFormatException ex) {
			System.out.println("[renderer-v2 opengl] Invalid remaster shadow integer '" + value
				+ "' for " + propertyName + "; using " + defaultValue + ".");
			return defaultValue;
		}
	}

	private static float readFloat(
		String propertyName,
		String envName,
		float defaultValue,
		float minValue,
		float maxValue) {
		String value = System.getProperty(propertyName);
		if (value == null || value.trim().isEmpty()) {
			value = System.getenv(envName);
		}
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}

		try {
			float parsed = Float.parseFloat(value.trim());
			return clamp(parsed, minValue, maxValue);
		} catch (NumberFormatException ex) {
			System.out.println("[renderer-v2 opengl] Invalid remaster shadow float '" + value
				+ "' for " + propertyName + "; using " + defaultValue + ".");
			return defaultValue;
		}
	}
}



final class RemasterShadowMaskBounds {
	final float minX;
	final float maxX;
	final float minZ;
	final float maxZ;

	RemasterShadowMaskBounds(float minX, float maxX, float minZ, float maxZ) {
		this.minX = minX;
		this.maxX = maxX;
		this.minZ = minZ;
		this.maxZ = maxZ;
	}

	static RemasterShadowMaskBounds from(
		RemasterShadowRoofCoverage roofCoverage,
		Renderer3DWorldChunkFrame chunkFrame) {
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			int triangleCount = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
			for (int triangle = 0; triangle < triangleCount; triangle++) {
				if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
					continue;
				}
				if (RemasterShadowClassifier.roofClassificationForTriangle(roofCoverage, chunk, triangle) != 0) {
					continue;
				}
				int sourceIndex = triangle * 3;
				for (int corner = 0; corner < 3; corner++) {
					int vertex = chunk.getIndex(sourceIndex + corner);
					int coord = vertex * 3;
					float x = chunk.getVertexCoord(coord);
					float z = chunk.getVertexCoord(coord + 2);
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
		}
		if (!Float.isFinite(minX)
			|| !Float.isFinite(maxX)
			|| !Float.isFinite(minZ)
			|| !Float.isFinite(maxZ)
			|| maxX <= minX
			|| maxZ <= minZ) {
			return null;
		}
		return new RemasterShadowMaskBounds(minX, maxX, minZ, maxZ);
	}

	RemasterShadowMaskBounds withPadding(float padding) {
		return new RemasterShadowMaskBounds(
			minX - padding,
			maxX + padding,
			minZ - padding,
			maxZ + padding);
	}

	float xAt(int pixelX, int width) {
		return minX + ((pixelX + 0.5f) / Math.max(1.0f, (float) width)) * spanX();
	}

	float zAt(int pixelY, int height) {
		return minZ + ((pixelY + 0.5f) / Math.max(1.0f, (float) height)) * spanZ();
	}

	float invSpanX() {
		return 1.0f / Math.max(1.0f, spanX());
	}

	float invSpanZ() {
		return 1.0f / Math.max(1.0f, spanZ());
	}

	private float spanX() {
		return maxX - minX;
	}

	private float spanZ() {
		return maxZ - minZ;
	}
}

final class RemasterTerrainShadowCaster {
	static final int STYLE_STRIP = 0;
	static final int STYLE_SOFT_SCENERY = 1;
	static final int STYLE_CONTACT = 2;

	final int style;
	final int plane;
	final float baseX0;
	final float baseZ0;
	final float baseX1;
	final float baseZ1;
	final float centerX;
	final float centerZ;
	final float length;
	final float halfWidth;
	private final float edgeX;
	private final float edgeZ;
	private final float edgeLength;
	final float directionX;
	final float directionZ;
	final float opacity;
	final float sceneryFootprintHalfAlong;
	final float sceneryFootprintHalfAcross;
	final float contactCoreRadius;
	final float contactBleedRadius;
	final boolean contactUsesBounds;
	final float contactMinX;
	final float contactMaxX;
	final float contactMinZ;
	final float contactMaxZ;
	final float minX;
	final float maxX;
	final float minZ;
	final float maxZ;

	private RemasterTerrainShadowCaster(
		int style,
		int plane,
		float baseX0,
		float baseZ0,
		float baseX1,
		float baseZ1,
		float length,
		float halfWidth,
		float directionX,
		float directionZ,
		float opacity) {
		this(
				style,
				plane,
				baseX0,
				baseZ0,
				baseX1,
				baseZ1,
				length,
				halfWidth,
				directionX,
				directionZ,
				opacity,
				0.0f,
				0.0f,
				0.0f,
				0.0f,
				false,
				0.0f,
				0.0f,
				0.0f,
				0.0f);
	}

	private RemasterTerrainShadowCaster(
		int style,
		int plane,
		float baseX0,
		float baseZ0,
		float baseX1,
		float baseZ1,
		float length,
		float halfWidth,
		float directionX,
		float directionZ,
		float opacity,
		float sceneryFootprintHalfAlong,
		float sceneryFootprintHalfAcross,
		float contactCoreRadius,
		float contactBleedRadius,
		boolean contactUsesBounds,
		float contactMinX,
		float contactMaxX,
		float contactMinZ,
		float contactMaxZ) {
		float edgeDx = baseX1 - baseX0;
		float edgeDz = baseZ1 - baseZ0;
		float edgeLength = (float) Math.sqrt(edgeDx * edgeDx + edgeDz * edgeDz);
		this.style = style;
		this.plane = plane;
		this.baseX0 = baseX0;
		this.baseZ0 = baseZ0;
		this.baseX1 = baseX1;
		this.baseZ1 = baseZ1;
		this.centerX = (baseX0 + baseX1) * 0.5f;
		this.centerZ = (baseZ0 + baseZ1) * 0.5f;
		this.length = length;
		this.halfWidth = halfWidth;
		this.edgeLength = edgeLength;
		this.edgeX = edgeDx / Math.max(0.0001f, edgeLength);
		this.edgeZ = edgeDz / Math.max(0.0001f, edgeLength);
		this.directionX = directionX;
		this.directionZ = directionZ;
		this.opacity = RemasterShadowMaskBuilder.clamp(opacity, 0.0f, 1.0f);
		this.sceneryFootprintHalfAlong = Math.max(0.0f, sceneryFootprintHalfAlong);
		this.sceneryFootprintHalfAcross = Math.max(0.0f, sceneryFootprintHalfAcross);
		this.contactCoreRadius =
			RemasterShadowMaskBuilder.clamp(contactCoreRadius, 0.0f, Math.max(0.0f, halfWidth));
		this.contactBleedRadius = Math.max(0.0f, contactBleedRadius);
		this.contactUsesBounds = contactUsesBounds;
		this.contactMinX = Math.min(contactMinX, contactMaxX);
		this.contactMaxX = Math.max(contactMinX, contactMaxX);
		this.contactMinZ = Math.min(contactMinZ, contactMaxZ);
		this.contactMaxZ = Math.max(contactMinZ, contactMaxZ);
		float projectedX0 = baseX0 + directionX * length;
		float projectedZ0 = baseZ0 + directionZ * length;
		float projectedX1 = baseX1 + directionX * length;
		float projectedZ1 = baseZ1 + directionZ * length;
		if (style == STYLE_CONTACT && contactUsesBounds) {
			this.minX = this.contactMinX - this.contactBleedRadius;
			this.maxX = this.contactMaxX + this.contactBleedRadius;
			this.minZ = this.contactMinZ - this.contactBleedRadius;
			this.maxZ = this.contactMaxZ + this.contactBleedRadius;
		} else if (style == STYLE_CONTACT) {
			float contactReach = this.contactCoreRadius + this.contactBleedRadius;
			this.minX = Math.min(baseX0, baseX1) - contactReach;
			this.maxX = Math.max(baseX0, baseX1) + contactReach;
			this.minZ = Math.min(baseZ0, baseZ1) - contactReach;
			this.maxZ = Math.max(baseZ0, baseZ1) + contactReach;
		} else {
			this.minX = Math.min(Math.min(baseX0, baseX1), Math.min(projectedX0, projectedX1)) - halfWidth;
			this.maxX = Math.max(Math.max(baseX0, baseX1), Math.max(projectedX0, projectedX1)) + halfWidth;
			this.minZ = Math.min(Math.min(baseZ0, baseZ1), Math.min(projectedZ0, projectedZ1)) - halfWidth;
			this.maxZ = Math.max(Math.max(baseZ0, baseZ1), Math.max(projectedZ0, projectedZ1)) + halfWidth;
		}
	}

	static RemasterTerrainShadowCaster from(
		Renderer3DWorldChunkFrame.ShadowCaster source,
		int plane,
		float lightAzimuthDegrees,
		float lightElevationDegrees) {
		if (source == null || source.getHeight() <= 0) {
			return null;
		}
		double azimuth = Math.toRadians(lightAzimuthDegrees);
		double elevation = Math.toRadians(lightElevationDegrees);
		float lightX = (float) (Math.cos(elevation) * Math.cos(azimuth));
		float lightY = Math.max(0.12f, Math.abs((float) Math.sin(elevation)));
		float lightZ = (float) (Math.cos(elevation) * Math.sin(azimuth));
		float horizontalLength = (float) Math.sqrt(lightX * lightX + lightZ * lightZ);
		if (horizontalLength <= 0.0001f) {
			return null;
		}
		float shadowDirectionX = -lightX / horizontalLength;
		float shadowDirectionZ = -lightZ / horizontalLength;
		float height = Math.max(0.0f, source.getHeight());
		float rawLength = RemasterShadowMaskBuilder.clamp(
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MIN_LENGTH + height * (horizontalLength / lightY) * 2.0f,
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MIN_LENGTH,
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MAX_LENGTH);
		float length = RemasterShadowMaskBuilder.clamp(
			rawLength * RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_LENGTH_SCALE,
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MIN_LENGTH
				* RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_LENGTH_SCALE,
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MAX_LENGTH
				* RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_LENGTH_SCALE);
		float width = Math.max(
			source.getWidth(),
			Math.max(
				Math.abs(source.getBaseX1() - source.getBaseX0()),
				Math.abs(source.getBaseZ1() - source.getBaseZ0())));
		Renderer3DModelKind kind = source.getModelKind();
		int style = kind == Renderer3DModelKind.GAME_OBJECT ? STYLE_SOFT_SCENERY : STYLE_STRIP;
		float halfWidth = remasterShadowHalfWidth(kind, width);
			float baseX0 = source.getBaseX0();
			float baseZ0 = source.getBaseZ0();
			float baseX1 = source.getBaseX1();
			float baseZ1 = source.getBaseZ1();
			float sceneryFootprintHalfAlong = 0.0f;
			float sceneryFootprintHalfAcross = 0.0f;
			if (style == STYLE_SOFT_SCENERY) {
				float centerX = (source.getFootprintMinX() + source.getFootprintMaxX()) * 0.5f;
				float centerZ = (source.getFootprintMinZ() + source.getFootprintMaxZ()) * 0.5f;
				float footprintHalfX =
					Math.max(0.0f, (source.getFootprintMaxX() - source.getFootprintMinX()) * 0.5f);
				float footprintHalfZ =
					Math.max(0.0f, (source.getFootprintMaxZ() - source.getFootprintMinZ()) * 0.5f);
				if (footprintHalfX <= 0.0f || footprintHalfZ <= 0.0f) {
					centerX = (source.getBaseX0() + source.getBaseX1()) * 0.5f;
					centerZ = (source.getBaseZ0() + source.getBaseZ1()) * 0.5f;
				}
				float normalX = -shadowDirectionZ;
				float normalZ = shadowDirectionX;
				if (footprintHalfX > 0.0f && footprintHalfZ > 0.0f) {
					sceneryFootprintHalfAlong =
						Math.abs(footprintHalfX * shadowDirectionX) + Math.abs(footprintHalfZ * shadowDirectionZ);
					sceneryFootprintHalfAcross =
						Math.abs(footprintHalfX * normalX) + Math.abs(footprintHalfZ * normalZ);
				}
				float edgeSoftness = Math.max(24.0f, Math.min(96.0f, width * 0.12f));
				halfWidth = Math.max(
					36.0f,
					Math.min(180.0f, Math.max(width * 0.30f, sceneryFootprintHalfAcross + edgeSoftness)));
				float sourceHalfAcross = sceneryFootprintHalfAcross > 0.0f
					? sceneryFootprintHalfAcross
					: Math.max(36.0f, Math.min(132.0f, width * 0.30f));
				baseX0 = centerX - normalX * sourceHalfAcross;
				baseZ0 = centerZ - normalZ * sourceHalfAcross;
				baseX1 = centerX + normalX * sourceHalfAcross;
				baseZ1 = centerZ + normalZ * sourceHalfAcross;
			}
			return new RemasterTerrainShadowCaster(
				style,
				plane,
				baseX0,
				baseZ0,
				baseX1,
				baseZ1,
				length,
				halfWidth,
				shadowDirectionX,
				shadowDirectionZ,
				source.getOpacity() / 255.0f,
				sceneryFootprintHalfAlong,
				sceneryFootprintHalfAcross,
				0.0f,
				0.0f,
				false,
				0.0f,
				0.0f,
				0.0f,
				0.0f);
		}

	static RemasterTerrainShadowCaster contactFrom(
		Renderer3DWorldChunkFrame.ShadowCaster source,
		int plane) {
		if (source == null || source.getHeight() <= 0) {
			return null;
		}
		Renderer3DModelKind kind = source.getModelKind();
		if (kind != Renderer3DModelKind.GAME_OBJECT
			&& kind != Renderer3DModelKind.WALL
			&& kind != Renderer3DModelKind.WALL_OBJECT) {
			return null;
		}
		boolean wallKind = kind == Renderer3DModelKind.WALL || kind == Renderer3DModelKind.WALL_OBJECT;
		float width = Math.max(
			source.getWidth(),
			Math.max(
				Math.abs(source.getBaseX1() - source.getBaseX0()),
				Math.abs(source.getBaseZ1() - source.getBaseZ0())));
		float footprintRadius = RemasterShadowMaskBuilder.clamp(
			width * (wallKind ? 0.06f : 0.18f),
			wallKind ? 6.0f : 16.0f,
			wallKind ? 28.0f : 72.0f);
		float visibleBleedRadius = Math.max(width * 0.34f, source.getHeight() * 0.12f)
			* RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CONTACT_RADIUS_SCALE;
		visibleBleedRadius = RemasterShadowMaskBuilder.clamp(
			visibleBleedRadius,
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CONTACT_MIN_RADIUS,
			RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CONTACT_MAX_RADIUS
				* Math.max(1.0f, RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CONTACT_RADIUS_SCALE));
		float opacity = wallKind
			? source.getOpacity() / 255.0f * 0.72f
			: source.getOpacity() / 255.0f;
		if (kind == Renderer3DModelKind.GAME_OBJECT) {
			float footprintMinX = source.getFootprintMinX();
			float footprintMaxX = source.getFootprintMaxX();
			float footprintMinZ = source.getFootprintMinZ();
			float footprintMaxZ = source.getFootprintMaxZ();
			if (footprintMaxX <= footprintMinX || footprintMaxZ <= footprintMinZ) {
				return null;
			}
			float centerX = (footprintMinX + footprintMaxX) * 0.5f;
			float centerZ = (footprintMinZ + footprintMaxZ) * 0.5f;
			return new RemasterTerrainShadowCaster(
				STYLE_CONTACT,
				plane,
					centerX,
					centerZ,
					centerX,
					centerZ,
					0.0f,
					visibleBleedRadius,
					1.0f,
					0.0f,
					opacity,
					0.0f,
					0.0f,
					0.0f,
					visibleBleedRadius,
					true,
					footprintMinX,
					footprintMaxX,
					footprintMinZ,
					footprintMaxZ);
		}
		return new RemasterTerrainShadowCaster(
			STYLE_CONTACT,
			plane,
				source.getBaseX0(),
				source.getBaseZ0(),
				source.getBaseX1(),
				source.getBaseZ1(),
				0.0f,
				footprintRadius + visibleBleedRadius,
				1.0f,
				0.0f,
				opacity,
				0.0f,
				0.0f,
				footprintRadius,
				visibleBleedRadius,
				false,
				0.0f,
				0.0f,
				0.0f,
				0.0f);
	}

	private static float remasterShadowHalfWidth(Renderer3DModelKind kind, float width) {
		if (kind == Renderer3DModelKind.GAME_OBJECT) {
			return Math.max(36.0f, Math.min(132.0f, width * 0.30f));
		}
		if (kind == Renderer3DModelKind.WALL_OBJECT) {
			return Math.max(32.0f, width * 0.28f);
		}
		return Math.max(RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MIN_WIDTH, Math.min(96.0f, width * 0.18f));
	}

	float alphaAt(float x, float z) {
		if (style == STYLE_CONTACT) {
			return alphaAtContact(x, z);
		}
		if (style == STYLE_SOFT_SCENERY) {
			return alphaAtSoftScenery(x, z);
		}
		float determinant = edgeX * directionZ - edgeZ * directionX;
		if (edgeLength > 0.0001f && Math.abs(determinant) > 0.08f) {
			float px = x - baseX0;
			float pz = z - baseZ0;
			float edgeAlong = (px * directionZ - pz * directionX) / determinant;
			float shadowAlong = (edgeX * pz - edgeZ * px) / determinant;
			if (shadowAlong < 0.0f || shadowAlong > length) {
				return 0.0f;
			}
			if (edgeAlong < -halfWidth || edgeAlong > edgeLength + halfWidth) {
				return 0.0f;
			}
			float sideFade = Math.min(
				(edgeAlong + halfWidth) / Math.max(16.0f, halfWidth),
				(edgeLength + halfWidth - edgeAlong) / Math.max(16.0f, halfWidth));
			float endFade = (length - shadowAlong) / Math.max(64.0f, length * 0.22f);
			return RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_BASE_ALPHA
				* opacity
				* RemasterShadowMaskBuilder.clamp(Math.min(sideFade, endFade), 0.0f, 1.0f);
		}
		return alphaAtCenterFallback(x, z);
	}

	private float alphaAtSoftScenery(float x, float z) {
		if (sceneryFootprintHalfAlong > 0.0f && sceneryFootprintHalfAcross > 0.0f) {
			return alphaAtFootprintScenery(x, z);
		}
		float dx = x - centerX;
		float dz = z - centerZ;
		float along = dx * directionX + dz * directionZ;
		if (along < -halfWidth * 0.45f || along > length) {
			return 0.0f;
		}
		float across = Math.abs(dx * -directionZ + dz * directionX);
		float farFade = smoothStep(0.0f, Math.max(96.0f, length * 0.28f), length - along);
		float startFade = smoothStep(-halfWidth * 0.35f, Math.max(16.0f, halfWidth * 0.45f), along);
		float trunkWidth = Math.max(9.0f, Math.min(26.0f, halfWidth * 0.24f));
		float trunk = 1.0f - smoothStep(trunkWidth, trunkWidth * 3.2f, across);
		float trunkFade = smoothStep(0.0f, Math.max(36.0f, halfWidth * 0.75f), along)
			* smoothStep(0.0f, Math.max(112.0f, length * 0.36f), length - along);
		float canopyCenter = Math.min(length * 0.36f, Math.max(72.0f, halfWidth * 1.45f));
		float canopyRadiusAlong = Math.max(112.0f, halfWidth * 2.35f);
		float canopyRadiusAcross = Math.max(48.0f, halfWidth * 1.25f);
		float canopyAlong = (along - canopyCenter) / canopyRadiusAlong;
		float canopyAcross = across / canopyRadiusAcross;
		float canopyDistance = canopyAlong * canopyAlong + canopyAcross * canopyAcross;
		float canopy = 1.0f - smoothStep(0.16f, 1.0f, canopyDistance);
		float shapedAlpha = Math.max(trunk * trunkFade * 0.85f, canopy * startFade * farFade * 0.55f);
		return RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_BASE_ALPHA
			* opacity
			* RemasterShadowMaskBuilder.clamp(shapedAlpha, 0.0f, 1.0f);
	}

	private float alphaAtFootprintScenery(float x, float z) {
		float dx = x - centerX;
		float dz = z - centerZ;
		float along = dx * directionX + dz * directionZ;
		float castDistance = along - sceneryFootprintHalfAlong;
		float edgeSoftness = Math.max(
			24.0f,
			Math.min(104.0f, sceneryFootprintHalfAcross * 0.55f + sceneryFootprintHalfAlong * 0.18f));
		if (along < -sceneryFootprintHalfAlong - edgeSoftness || castDistance > length) {
			return 0.0f;
		}
		float across = Math.abs(dx * -directionZ + dz * directionX);
		float positiveCastDistance = Math.max(0.0f, castDistance);
		float spread = sceneryFootprintHalfAcross + Math.min(112.0f, positiveCastDistance * 0.08f);
		float acrossDistance = Math.max(0.0f, across - spread);
		float sideFade = 1.0f - smoothStep(0.0f, edgeSoftness, acrossDistance);
		float frontFade = smoothStep(
			-sceneryFootprintHalfAlong - edgeSoftness,
			-sceneryFootprintHalfAlong + edgeSoftness,
			along);
		float farFade = smoothStep(0.0f, Math.max(96.0f, length * 0.34f), length - positiveCastDistance);
		float centerWeight = 0.62f + 0.38f * (1.0f - smoothStep(0.0f, Math.max(1.0f, spread), across));
		float shapedAlpha = frontFade * sideFade * farFade * centerWeight;
		return RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_BASE_ALPHA
			* opacity
			* RemasterShadowMaskBuilder.clamp(shapedAlpha, 0.0f, 1.0f);
	}

	private float alphaAtContact(float x, float z) {
		float visibleBleedRadius = Math.max(1.0f, contactBleedRadius);
		float distanceFromFootprint = contactUsesBounds
			? Math.max(0.0f, distanceToFootprintBounds(x, z))
			: Math.max(0.0f, distanceToBaseSegment(x, z) - contactCoreRadius);
		float core = 1.0f - smoothStep(0.0f, 1.0f, distanceFromFootprint / visibleBleedRadius);
		return RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CONTACT_ALPHA
			* opacity
			* RemasterShadowMaskBuilder.clamp(core, 0.0f, 1.0f);
	}

	private float distanceToFootprintBounds(float x, float z) {
		float halfX = Math.max(0.0f, (contactMaxX - contactMinX) * 0.5f);
		float halfZ = Math.max(0.0f, (contactMaxZ - contactMinZ) * 0.5f);
		if (halfX <= 0.0001f || halfZ <= 0.0001f) {
			float dx = x - ((contactMinX + contactMaxX) * 0.5f);
			float dz = z - ((contactMinZ + contactMaxZ) * 0.5f);
			return (float) Math.sqrt(dx * dx + dz * dz);
		}
		float radius = RemasterShadowMaskBuilder.clamp(
			Math.min(halfX, halfZ) * 0.85f,
			Math.min(halfX, halfZ) * 0.35f,
			Math.min(halfX, halfZ));
		float centerX = (contactMinX + contactMaxX) * 0.5f;
		float centerZ = (contactMinZ + contactMaxZ) * 0.5f;
		float qx = Math.abs(x - centerX) - Math.max(0.0f, halfX - radius);
		float qz = Math.abs(z - centerZ) - Math.max(0.0f, halfZ - radius);
		float outsideX = Math.max(qx, 0.0f);
		float outsideZ = Math.max(qz, 0.0f);
		float outsideDistance = (float) Math.sqrt(outsideX * outsideX + outsideZ * outsideZ);
		float insideDistance = Math.min(Math.max(qx, qz), 0.0f);
		return outsideDistance + insideDistance - radius;
	}

	private float distanceToBaseSegment(float x, float z) {
		float segmentX = baseX1 - baseX0;
		float segmentZ = baseZ1 - baseZ0;
		float lengthSquared = segmentX * segmentX + segmentZ * segmentZ;
		if (lengthSquared <= 0.0001f) {
			float dx = x - centerX;
			float dz = z - centerZ;
			return (float) Math.sqrt(dx * dx + dz * dz);
		}
		float t = ((x - baseX0) * segmentX + (z - baseZ0) * segmentZ) / lengthSquared;
		t = RemasterShadowMaskBuilder.clamp(t, 0.0f, 1.0f);
		float closestX = baseX0 + segmentX * t;
		float closestZ = baseZ0 + segmentZ * t;
		float dx = x - closestX;
		float dz = z - closestZ;
		return (float) Math.sqrt(dx * dx + dz * dz);
	}

	private float alphaAtCenterFallback(float x, float z) {
		float dx = x - centerX;
		float dz = z - centerZ;
		float along = dx * directionX + dz * directionZ;
		if (along < 0.0f || along > length) {
			return 0.0f;
		}
		float across = Math.abs(dx * -directionZ + dz * directionX);
		if (across > halfWidth) {
			return 0.0f;
		}
		float sideFade = (halfWidth - across) / Math.max(16.0f, halfWidth * 0.25f);
		float endFade = (length - along) / Math.max(64.0f, length * 0.22f);
		return RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_BASE_ALPHA
			* opacity
			* RemasterShadowMaskBuilder.clamp(Math.min(sideFade, endFade), 0.0f, 1.0f);
	}

	boolean isBlockedBy(RemasterShadowRoofCoverage roofCoverage, float x, float z) {
		if (roofCoverage == null) {
			return false;
		}
		if (roofCoverage.classify(plane, Math.round(x), Math.round(z)) > 0) {
			return true;
		}
		if (style == STYLE_CONTACT) {
			return false;
		}
		float along = shadowAlongAt(x, z);
		if (along <= RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CLIP_START_OFFSET) {
			return false;
		}
		float sourceX = x - directionX * along;
		float sourceZ = z - directionZ * along;
		float startX = sourceX + directionX * RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CLIP_START_OFFSET;
		float startZ = sourceZ + directionZ * RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CLIP_START_OFFSET;
		return roofCoverage.crossesShadowBlocker(plane, startX, startZ, x, z);
	}

	private float shadowAlongAt(float x, float z) {
		if (style == STYLE_SOFT_SCENERY) {
			float dx = x - centerX;
			float dz = z - centerZ;
			return dx * directionX + dz * directionZ;
		}
		float determinant = edgeX * directionZ - edgeZ * directionX;
		if (edgeLength > 0.0001f && Math.abs(determinant) > 0.08f) {
			float px = x - baseX0;
			float pz = z - baseZ0;
			return (edgeX * pz - edgeZ * px) / determinant;
		}
		float dx = x - centerX;
		float dz = z - centerZ;
		return dx * directionX + dz * directionZ;
	}

	long mixSignature(long hash) {
		long signature = stableSignature();
		hash = RemasterShadowMaskBuilder.mix(hash, (int) signature);
		return RemasterShadowMaskBuilder.mix(hash, (int) (signature >>> 32));
	}

	long stableSignature() {
		long hash = 0xcbf29ce484222325L;
		hash = RemasterShadowMaskBuilder.mix(hash, style);
		hash = RemasterShadowMaskBuilder.mix(hash, plane);
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(baseX0));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(baseZ0));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(baseX1));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(baseZ1));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(centerX));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(centerZ));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(length));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(halfWidth));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureUnitFloat(directionX));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureUnitFloat(directionZ));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureUnitFloat(opacity));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(sceneryFootprintHalfAlong));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(sceneryFootprintHalfAcross));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(contactCoreRadius));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(contactBleedRadius));
		hash = RemasterShadowMaskBuilder.mix(hash, contactUsesBounds ? 1 : 0);
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(contactMinX));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(contactMaxX));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(contactMinZ));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(contactMaxZ));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(minX));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(maxX));
		hash = RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(minZ));
		return RemasterShadowMaskBuilder.mix(hash, RemasterShadowMaskBuilder.signatureWorldFloat(maxZ));
	}

	private static float smoothStep(float edge0, float edge1, float value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0f : 0.0f;
		}
		float t = RemasterShadowMaskBuilder.clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}
}
