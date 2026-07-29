package orsc;

import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RemasterGlowMaskBuilder {
	private static final String TEXTURE_SIZE_PROPERTY = "spoiledmilk.remasterGlowMaskTextureSize";
	private static final String TEXTURE_SIZE_ENV = "SPOILED_MILK_REMASTER_GLOW_MASK_TEXTURE_SIZE";
	private static final String CACHE_ENTRIES_PROPERTY = "spoiledmilk.remasterGlowMaskCacheEntries";
	private static final String CACHE_ENTRIES_ENV = "SPOILED_MILK_REMASTER_GLOW_MASK_CACHE_ENTRIES";
	private static final int TEXTURE_SIZE =
		readInt(TEXTURE_SIZE_PROPERTY, TEXTURE_SIZE_ENV, 256, 128, 512);
	private static final int CACHE_ENTRIES =
		readInt(CACHE_ENTRIES_PROPERTY, CACHE_ENTRIES_ENV, 16, 1, 64);
	private static final float TEXTURE_PADDING = 384.0f;
	private static final float GLOW_SCALE = 0.34f;
	private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
	private static final long FNV_PRIME = 0x100000001b3L;

	private final LinkedHashMap<Long, RemasterGlowMask> cacheBySignature =
		new LinkedHashMap<Long, RemasterGlowMask>(16, 0.75f, true);
	/*
	 * This builder is owned by the OpenGL presenter thread. These fixed-size
	 * accumulation arrays are synchronous scratch and never escape buildMask.
	 */
	private final float[] scratchRed = new float[TEXTURE_SIZE * TEXTURE_SIZE];
	private final float[] scratchGreen = new float[TEXTURE_SIZE * TEXTURE_SIZE];
	private final float[] scratchBlue = new float[TEXTURE_SIZE * TEXTURE_SIZE];

	RemasterGlowMaskBuild build(Renderer3DWorldChunkFrame chunkFrame) {
		if (chunkFrame == null || chunkFrame.getChunkCount() <= 0) {
			return null;
		}
		List<Renderer3DWorldChunkFrame.GlowEmitter> emitters = collectEmitters(chunkFrame);
		if (emitters.isEmpty()) {
			return null;
		}
		RemasterGlowMaskBounds bounds = RemasterGlowMaskBounds.from(chunkFrame, emitters);
		if (bounds == null) {
			return null;
		}
		long signature = signature(chunkFrame, emitters, bounds);
		RemasterGlowMask cached = cacheBySignature.get(Long.valueOf(signature));
		if (cached != null) {
			return new RemasterGlowMaskBuild(cached, true, false, "cache");
		}

		RemasterGlowMask mask = buildMask(signature, bounds, emitters);
		if (mask == null || mask.visiblePixels <= 0) {
			return null;
		}
		cacheBySignature.put(Long.valueOf(signature), mask);
		trimCache();
		return new RemasterGlowMaskBuild(mask, false, true, "rebuild");
	}

	void clear() {
		cacheBySignature.clear();
	}

	private List<Renderer3DWorldChunkFrame.GlowEmitter> collectEmitters(
		Renderer3DWorldChunkFrame chunkFrame) {
		List<Renderer3DWorldChunkFrame.GlowEmitter> emitters =
			new ArrayList<Renderer3DWorldChunkFrame.GlowEmitter>();
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			for (int index = 0; index < chunk.getGlowEmitterCount(); index++) {
				Renderer3DWorldChunkFrame.GlowEmitter emitter = chunk.getGlowEmitter(index);
				if (emitter != null && emitter.getIntensity() > 0 && emitter.getRadius() > 0) {
					emitters.add(emitter);
				}
			}
		}
		return emitters;
	}

	private RemasterGlowMask buildMask(
		long signature,
		RemasterGlowMaskBounds bounds,
		List<Renderer3DWorldChunkFrame.GlowEmitter> emitters) {
		int pixelCount = TEXTURE_SIZE * TEXTURE_SIZE;
		Arrays.fill(scratchRed, 0.0f);
		Arrays.fill(scratchGreen, 0.0f);
		Arrays.fill(scratchBlue, 0.0f);
		float pixelWidth = bounds.spanX() / TEXTURE_SIZE;
		float pixelHeight = bounds.spanZ() / TEXTURE_SIZE;
		for (Renderer3DWorldChunkFrame.GlowEmitter emitter : emitters) {
			accumulateEmitter(
				bounds,
				pixelWidth,
				pixelHeight,
				emitter,
				scratchRed,
				scratchGreen,
				scratchBlue);
		}

		ByteBuffer pixels = ByteBuffer.allocateDirect(pixelCount * 4);
		int visiblePixels = 0;
		for (int pixel = 0; pixel < pixelCount; pixel++) {
			int r = toByte(scratchRed[pixel]);
			int g = toByte(scratchGreen[pixel]);
			int b = toByte(scratchBlue[pixel]);
			int a = Math.max(r, Math.max(g, b));
			if (a > 0) {
				visiblePixels++;
			}
			pixels.put((byte) r);
			pixels.put((byte) g);
			pixels.put((byte) b);
			pixels.put((byte) a);
		}
		pixels.flip();
		return new RemasterGlowMask(
			signature,
			TEXTURE_SIZE,
			TEXTURE_SIZE,
			visiblePixels,
			bounds.minX,
			bounds.minZ,
			bounds.invSpanX(),
			bounds.invSpanZ(),
			pixels);
	}

	private void accumulateEmitter(
		RemasterGlowMaskBounds bounds,
		float pixelWidth,
		float pixelHeight,
		Renderer3DWorldChunkFrame.GlowEmitter emitter,
		float[] red,
		float[] green,
		float[] blue) {
		float radius = Math.max(1.0f, emitter.getRadius());
		int minPixelX = clampPixel((int) Math.floor((emitter.getCenterX() - radius - bounds.minX) * bounds.invSpanX() * TEXTURE_SIZE));
		int maxPixelX = clampPixel((int) Math.ceil((emitter.getCenterX() + radius - bounds.minX) * bounds.invSpanX() * TEXTURE_SIZE));
		int minPixelZ = clampPixel((int) Math.floor((emitter.getCenterZ() - radius - bounds.minZ) * bounds.invSpanZ() * TEXTURE_SIZE));
		int maxPixelZ = clampPixel((int) Math.ceil((emitter.getCenterZ() + radius - bounds.minZ) * bounds.invSpanZ() * TEXTURE_SIZE));
		float sourceRed = ((emitter.getColor() >> 16) & 0xff) / 255.0f;
		float sourceGreen = ((emitter.getColor() >> 8) & 0xff) / 255.0f;
		float sourceBlue = (emitter.getColor() & 0xff) / 255.0f;
		float intensity = (emitter.getIntensity() / 255.0f) * GLOW_SCALE;
		for (int z = minPixelZ; z <= maxPixelZ; z++) {
			float worldZ = bounds.minZ + (z + 0.5f) * pixelHeight;
			for (int x = minPixelX; x <= maxPixelX; x++) {
				float worldX = bounds.minX + (x + 0.5f) * pixelWidth;
				float dx = (worldX - emitter.getCenterX()) / radius;
				float dz = (worldZ - emitter.getCenterZ()) / radius;
				float distance = (float) Math.sqrt(dx * dx + dz * dz);
				if (distance >= 1.0f) {
					continue;
				}
				float falloff = smoothStep(1.0f - distance);
				float strength = falloff * intensity;
				int pixel = z * TEXTURE_SIZE + x;
				red[pixel] += sourceRed * strength;
				green[pixel] += sourceGreen * strength;
				blue[pixel] += sourceBlue * strength;
			}
		}
	}

	private int clampPixel(int value) {
		return Math.max(0, Math.min(TEXTURE_SIZE - 1, value));
	}

	private int toByte(float value) {
		return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
	}

	private float smoothStep(float value) {
		float t = Math.max(0.0f, Math.min(1.0f, value));
		return t * t * (3.0f - 2.0f * t);
	}

	private long signature(
		Renderer3DWorldChunkFrame chunkFrame,
		List<Renderer3DWorldChunkFrame.GlowEmitter> emitters,
		RemasterGlowMaskBounds bounds) {
		long hash = FNV_OFFSET_BASIS;
		hash = mix(hash, chunkFrame.getChunkCount());
		hash = mix(hash, chunkFrame.getTotalTriangleCount());
		hash = mix(hash, TEXTURE_SIZE);
		hash = mix(hash, Math.round(bounds.minX / 4.0f));
		hash = mix(hash, Math.round(bounds.maxX / 4.0f));
		hash = mix(hash, Math.round(bounds.minZ / 4.0f));
		hash = mix(hash, Math.round(bounds.maxZ / 4.0f));
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
			hash = mix(hash, chunk.getPlane());
			hash = mix(hash, chunk.getCenterSectionX());
			hash = mix(hash, chunk.getCenterSectionY());
			hash = mix(hash, chunk.getOriginWorldX());
			hash = mix(hash, chunk.getOriginWorldZ());
			hash = mix(hash, (int) chunk.getSignature());
			hash = mix(hash, (int) (chunk.getSignature() >>> 32));
		}
		hash = mix(hash, emitters.size());
		for (Renderer3DWorldChunkFrame.GlowEmitter emitter : emitters) {
			hash = mix(hash, emitter.getModelKind().ordinal());
			hash = mix(hash, Math.round(emitter.getCenterX() / 4.0f));
			hash = mix(hash, Math.round(emitter.getCenterY() / 4.0f));
			hash = mix(hash, Math.round(emitter.getCenterZ() / 4.0f));
			hash = mix(hash, Math.round(emitter.getRadius() / 4.0f));
			hash = mix(hash, emitter.getColor());
			hash = mix(hash, emitter.getIntensity());
		}
		return hash;
	}

	private void trimCache() {
		while (cacheBySignature.size() > CACHE_ENTRIES) {
			Map.Entry<Long, RemasterGlowMask> eldest = cacheBySignature.entrySet().iterator().next();
			cacheBySignature.remove(eldest.getKey());
		}
	}

	private static long mix(long hash, int value) {
		hash ^= value & 0xffffffffL;
		return hash * FNV_PRIME;
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
			System.out.println("[renderer-v2 opengl] Invalid remaster glow integer '" + value
				+ "' for " + propertyName + "; using " + defaultValue + ".");
			return defaultValue;
		}
	}

	private static final class RemasterGlowMaskBounds {
		private final float minX;
		private final float maxX;
		private final float minZ;
		private final float maxZ;

		private RemasterGlowMaskBounds(float minX, float maxX, float minZ, float maxZ) {
			this.minX = minX;
			this.maxX = maxX;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}

		private static RemasterGlowMaskBounds from(
			Renderer3DWorldChunkFrame chunkFrame,
			List<Renderer3DWorldChunkFrame.GlowEmitter> emitters) {
			float minX = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float minZ = Float.MAX_VALUE;
			float maxZ = -Float.MAX_VALUE;
			for (Renderer3DWorldChunkFrame.ChunkMesh chunk : chunkFrame.getChunks()) {
				for (int vertex = 0; vertex < chunk.getVertexCount(); vertex++) {
					int coord = vertex * OpenGLWorldChunkRenderer.POSITION_COMPONENT_COUNT;
					float x = chunk.getVertexCoord(coord);
					float z = chunk.getVertexCoord(coord + 2);
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
				}
			}
			for (Renderer3DWorldChunkFrame.GlowEmitter emitter : emitters) {
				float radius = emitter.getRadius() + TEXTURE_PADDING;
				minX = Math.min(minX, emitter.getCenterX() - radius);
				maxX = Math.max(maxX, emitter.getCenterX() + radius);
				minZ = Math.min(minZ, emitter.getCenterZ() - radius);
				maxZ = Math.max(maxZ, emitter.getCenterZ() + radius);
			}
			if (minX == Float.MAX_VALUE || minZ == Float.MAX_VALUE) {
				return null;
			}
			return new RemasterGlowMaskBounds(minX - TEXTURE_PADDING, maxX + TEXTURE_PADDING, minZ - TEXTURE_PADDING, maxZ + TEXTURE_PADDING);
		}

		private float spanX() {
			return Math.max(1.0f, maxX - minX);
		}

		private float spanZ() {
			return Math.max(1.0f, maxZ - minZ);
		}

		private float invSpanX() {
			return 1.0f / spanX();
		}

		private float invSpanZ() {
			return 1.0f / spanZ();
		}
	}
}
