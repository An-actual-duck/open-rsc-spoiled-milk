package orsc;

import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DMeshFrame;
import orsc.graphics.three.Renderer3DTextureData;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpenGLWorldTextureCache implements AutoCloseable {
	private static final int DEFAULT_ATLAS_SIZE = 2048;

	private final LwjglBindings gl;
	private final Map<Integer, OpenGLTextureRegion> textureRegionsById = new HashMap<>();
	private final Map<Integer, Long> textureSignaturesById = new HashMap<>();
	private final List<OpenGLTextureAtlas> atlases = new ArrayList<>();
	private long lastChunkTextureSignature = Long.MIN_VALUE;
	private WorldTextureUploadStats lastChunkTextureStats;

	OpenGLWorldTextureCache(LwjglBindings gl) {
		this.gl = gl;
	}

	WorldTextureUploadStats uploadReferencedTextures(Renderer3DMeshFrame meshFrame) throws Exception {
		int referencedTextures = 0;
		int cachedTextures = 0;
		int uploadedTextures = 0;
		int missingTextures = 0;
		int triangleCount = Math.min(meshFrame.getTriangleCount(), meshFrame.getTriangleTextures().length);
		int[] triangleTextures = meshFrame.getTriangleTextures();
		Set<Integer> seenTextureIds = new HashSet<>();

		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int textureId = triangleTextures[triangle];
			if (!isTextureBacked(meshFrame, textureId) || !seenTextureIds.add(textureId)) {
				continue;
			}

			referencedTextures++;
			Renderer3DTextureData textureData = meshFrame.getTexture(textureId);
			if (textureData == null) {
				missingTextures++;
			} else if (uploadIfNeeded(textureData)) {
				uploadedTextures++;
			} else {
				cachedTextures++;
			}
		}

		return new WorldTextureUploadStats(
			referencedTextures,
			cachedTextures,
			uploadedTextures,
			missingTextures,
			atlases.size());
	}

	WorldTextureUploadStats uploadReferencedTextures(
		Renderer3DFrame frame,
		Renderer3DWorldChunkFrame chunkFrame) throws Exception {
		long chunkTextureSignature = chunkTextureUploadSignature(frame, chunkFrame);
		if (lastChunkTextureStats != null
			&& lastChunkTextureStats.missingTextures == 0
			&& lastChunkTextureSignature == chunkTextureSignature) {
			return new WorldTextureUploadStats(
				lastChunkTextureStats.referencedTextures,
				lastChunkTextureStats.referencedTextures,
				0,
				0,
				atlases.size());
		}

		int referencedTextures = 0;
		int cachedTextures = 0;
		int uploadedTextures = 0;
		int missingTextures = 0;

		for (int index = 0; index < chunkFrame.getReferencedTextureCount(); index++) {
			int textureId = chunkFrame.getReferencedTextureId(index);
			if (!isTextureBacked(frame, textureId)) {
				continue;
			}
			referencedTextures++;
			Renderer3DTextureData textureData = frame.getTexture(textureId);
			if (textureData == null) {
				missingTextures++;
			} else if (uploadIfNeeded(textureData)) {
				uploadedTextures++;
			} else {
				cachedTextures++;
			}
		}

		WorldTextureUploadStats stats = new WorldTextureUploadStats(
			referencedTextures,
			cachedTextures,
			uploadedTextures,
			missingTextures,
			atlases.size());
		lastChunkTextureSignature = chunkTextureSignature;
		lastChunkTextureStats = stats;
		return stats;
	}

	private long chunkTextureUploadSignature(Renderer3DFrame frame, Renderer3DWorldChunkFrame chunkFrame) {
		long signature = 1469598103934665603L;
		signature = mixSignature(
			signature,
			frame == null ? 0L : frame.getTextureCatalogSignature());
		return mixSignature(
			signature,
			chunkFrame == null ? 0L : chunkFrame.getTextureReferenceSignature());
	}

	private long mixSignature(long signature, long value) {
		signature ^= value;
		return signature * 1099511628211L;
	}

	OpenGLTextureRegion getRegion(Renderer3DMeshFrame meshFrame, int textureId) {
		if (!isTextureBacked(meshFrame, textureId)) {
			return null;
		}
		Renderer3DTextureData textureData = meshFrame.getTexture(textureId);
		return textureData == null ? null : textureRegionsById.get(textureData.getTextureId());
	}

	OpenGLTextureRegion getRegion(Renderer3DFrame frame, int textureId) {
		if (!isTextureBacked(frame, textureId)) {
			return null;
		}
		Renderer3DTextureData textureData = frame.getTexture(textureId);
		return textureData == null ? null : textureRegionsById.get(textureData.getTextureId());
	}

	private boolean isTextureBacked(Renderer3DMeshFrame meshFrame, int textureId) {
		return textureId >= 0 && textureId < meshFrame.getTextureCount();
	}

	private boolean isTextureBacked(Renderer3DFrame frame, int textureId) {
		return frame != null && textureId >= 0 && textureId < frame.getTextureCount();
	}

	private boolean uploadIfNeeded(Renderer3DTextureData textureData) throws Exception {
		OpenGLTextureRegion existingRegion = textureRegionsById.get(textureData.getTextureId());
		Long existingSignature = textureSignaturesById.get(textureData.getTextureId());
		if (existingRegion != null
			&& existingRegion.getWidth() == textureData.getWidth()
			&& existingRegion.getHeight() == textureData.getHeight()
			&& existingSignature != null
			&& existingSignature.longValue() == textureData.getSignature()) {
			return false;
		}
		if (existingRegion != null
			&& existingRegion.getWidth() == textureData.getWidth()
			&& existingRegion.getHeight() == textureData.getHeight()) {
			OpenGLTextureRegion updatedRegion = updateTextureRegion(textureData, existingRegion);
			textureRegionsById.put(textureData.getTextureId(), updatedRegion);
			textureSignaturesById.put(textureData.getTextureId(), textureData.getSignature());
			return true;
		}

		OpenGLTextureRegion region = null;
		for (OpenGLTextureAtlas atlas : atlases) {
			region = atlas.upload(textureData);
			if (region != null) {
				break;
			}
		}

		if (region == null) {
			OpenGLTextureAtlas atlas = OpenGLTextureAtlas.create(gl, DEFAULT_ATLAS_SIZE, DEFAULT_ATLAS_SIZE);
			atlases.add(atlas);
			region = atlas.upload(textureData);
			if (region == null) {
				throw new IllegalArgumentException(
					"world texture is too large for OpenGL atlas: "
					+ textureData.getWidth()
					+ "x"
					+ textureData.getHeight());
			}
			OpenGLRendererLog.log("OpenGL world texture atlas allocated: "
				+ DEFAULT_ATLAS_SIZE
				+ "x"
				+ DEFAULT_ATLAS_SIZE
				+ " texture="
				+ atlas.getTextureId());
		}

		textureRegionsById.put(textureData.getTextureId(), region);
		textureSignaturesById.put(textureData.getTextureId(), textureData.getSignature());
		return true;
	}

	private OpenGLTextureRegion updateTextureRegion(
		Renderer3DTextureData textureData,
		OpenGLTextureRegion region) throws Exception {
		gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());
		gl.glTexSubImage2D(
			gl.GL_TEXTURE_2D,
			0,
			region.getX() - OpenGLTextureAtlas.PADDING,
			region.getY() - OpenGLTextureAtlas.PADDING,
			textureData.getWidth() + OpenGLTextureAtlas.PADDING * 2,
			textureData.getHeight() + OpenGLTextureAtlas.PADDING * 2,
			gl.GL_RGBA,
			gl.GL_UNSIGNED_BYTE,
			OpenGLTexturePixels.buildPaddedRgbaPixels(
				textureData.copyToDirectRgbaBuffer(),
				textureData.getWidth(),
				textureData.getHeight(),
				OpenGLTextureAtlas.PADDING));
		return new OpenGLTextureRegion(
			region.getTextureId(),
			region.getX(),
			region.getY(),
			region.getWidth(),
			region.getHeight(),
			region.getU0(),
			region.getV0(),
			region.getU1(),
			region.getV1(),
			textureData.hasTransparency());
	}

	@Override
	public void close() throws Exception {
		for (OpenGLTextureAtlas atlas : atlases) {
			atlas.close();
		}
		atlases.clear();
		textureRegionsById.clear();
		textureSignaturesById.clear();
	}
}
