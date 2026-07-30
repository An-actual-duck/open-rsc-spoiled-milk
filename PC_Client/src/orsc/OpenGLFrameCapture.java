package orsc;

import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.three.Renderer3DDepthFrame;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DMaterialFamily;
import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

import static orsc.OpenGLFramePresenter.*;

/*
 * RENDERER-V2 OWNER: frame-capture diagnostics. Capture data should be added
 * here after the relevant renderer owner exposes it, not by expanding presenter
 * internals first.
 */
final class OpenGLFrameCapture {
	final File directory;
	final int sequence;
	final List<String> layers = new ArrayList<String>();
	boolean failed;
	String failureMessage;

	OpenGLFrameCapture(File directory, int sequence) {
		this.directory = directory;
		this.sequence = sequence;
	}

	static OpenGLFrameCapture create(
		int sequence,
		Frame frame,
		boolean worldReplacementComposite,
		OpenGLFramePresenter presenter) throws Exception {
		File baseDirectory = frameCaptureBaseDirectory();
		String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
		File directory = new File(
			baseDirectory,
			"capture-" + timestamp + "-" + String.format("%03d", sequence));
		if (!directory.mkdirs() && !directory.isDirectory()) {
			throw new IllegalStateException("could not create " + directory.getAbsolutePath());
		}
		OpenGLFrameCapture capture = new OpenGLFrameCapture(directory, sequence);
		capture.writeMetadata(frame, worldReplacementComposite, presenter);
		return capture;
	}

	String getDirectoryPath() {
		return directory.getAbsolutePath();
	}

	boolean hasFailed() {
		return failed;
	}

	String getFailureMessage() {
		return failureMessage;
	}

	String[] getArtifactNames() {
		File[] files = directory.listFiles();
		if (files == null || files.length == 0) {
			return new String[0];
		}
		List<String> names = new ArrayList<String>();
		for (File file : files) {
			if (file.isFile()) {
				names.add(file.getName());
			}
		}
		Collections.sort(names);
		return names.toArray(new String[names.size()]);
	}

	void markFailed(Throwable throwable) {
		failed = true;
		failureMessage = throwable == null ? "unknown" : throwable.getMessage();
	}

	void writeFrameInputs(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		writeImage("00-legacy-source.png", imageFromFrame(frame));
		writeDepthDiagnostics(frame);
		writeWorldFaces(frame);
		writeSpriteCommands(frame);
		writeWorldSpriteSnapshots(frame);
		writeRenderer2DCommandLimits(frame);
		writeWorldSpriteCommands(frame, presenter);
		writeCompositeSceneCommands(frame, presenter);
		writeStaticWorldCommands(frame, presenter);
		writeStaticWorldMaterialTriangles(frame, presenter);
		writeResidentMaterialFamilies(frame);
		writeStaticRangeCandidates(frame, presenter);
		writeFrontOccluderCandidates(frame, presenter);
		writeSpriteSubmissions(frame);
		writeCharacterSprites(frame, null);
		writeSpriteAnchors(frame);
	}

	private void writeRenderer2DCommandLimits(Frame frame) throws Exception {
		Renderer2DFrame.CaptureStats stats = frame == null || frame.renderer2DFrame == null
			? Renderer2DFrame.CaptureStats.EMPTY
			: frame.renderer2DFrame.getCaptureStats();
		PrintWriter writer = new PrintWriter(new File(directory, "renderer-2d-command-limits.tsv"));
		try {
			writer.println("stream\tlimit\tattempted\taccepted\tdropped");
			writeRenderer2DCommandLimit(
				writer,
				"sprite",
				stats.getSpriteCommandLimit(),
				stats.getSpriteCommandAttempts(),
				stats.getSpriteCommandsCaptured(),
				stats.getSpriteCommandsSkippedOverflow());
			writeRenderer2DCommandLimit(
				writer,
				"text",
				stats.getTextCommandLimit(),
				stats.getTextCommandAttempts(),
				stats.getTextCommandsCaptured(),
				stats.getTextCommandsSkippedOverflow());
			writeRenderer2DCommandLimit(
				writer,
				"primitive",
				stats.getPrimitiveCommandLimit(),
				stats.getPrimitiveCommandAttempts(),
				stats.getPrimitiveCommandsCaptured(),
				stats.getPrimitiveCommandsSkippedOverflow());
			writeRenderer2DCommandLimit(
				writer,
				"rotated-sprite",
				stats.getRotatedSpriteCommandLimit(),
				stats.getRotatedSpriteCommandAttempts(),
				stats.getRotatedSpriteCommandsCaptured(),
				stats.getRotatedSpriteCommandsSkippedOverflow());
			writeRenderer2DCommandLimit(
				writer,
				"circle",
				stats.getCircleCommandLimit(),
				stats.getCircleCommandAttempts(),
				stats.getCircleCommandsCaptured(),
				stats.getCircleCommandsSkippedOverflow());
		} finally {
			writer.close();
		}
	}

	private void writeRenderer2DCommandLimit(
		PrintWriter writer,
		String stream,
		int limit,
		int attempted,
		int accepted,
		int dropped) {
		writer.println(stream + "\t" + limit + "\t" + attempted + "\t" + accepted + "\t" + dropped);
	}

	void writeLayer(String name, BufferedImage image) throws Exception {
		if (image == null) {
			return;
		}
		String fileName = name + ".png";
		writeImage(fileName, image);
		layers.add(fileName);
	}

	void writeSummary(
		Frame frame,
		boolean worldReplacementComposite,
		boolean failed) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "summary.txt"));
		try {
			writer.println("sequence=" + sequence);
			writer.println("failed=" + failed);
			if (failureMessage != null) {
				writer.println("failure=" + failureMessage);
			}
			writer.println("source=" + frame.sourceWidth + "x" + frame.sourceHeight);
			writer.println("target=" + frame.targetWidth + "x" + frame.targetHeight);
			writer.println("worldReplacementComposite=" + worldReplacementComposite);
			writeRendererMode(writer, worldReplacementComposite);
			writer.println("layers=");
			for (String layer : layers) {
				writer.println(layer);
			}
		} finally {
			writer.close();
		}
	}

	void writeEntityRestoreStats(OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "entity-restore-stats.tsv"));
		try {
			writer.println(
				"legacySpriteId\tcommands\tvisiblePixels\tdirectPixels\tfallbacks\tskipped\tatlasFull"
					+ "\tdepthEvaluations\tdepthVisiblePixels\tdepthMisses\tdepthSourcePixels"
					+ "\tdepthOccludedPixels\tdepthClippedPixels\tdepthOutOfBoundsPixels"
					+ "\tminAnchorDepth\tmaxAnchorDepth\tbounds\tdescription");
			List<LegacyEntitySpriteDebugStats> stats =
				new ArrayList<LegacyEntitySpriteDebugStats>(presenter.legacyEntitySpriteDebugById.values());
			Collections.sort(stats, new Comparator<LegacyEntitySpriteDebugStats>() {
				@Override
				public int compare(LegacyEntitySpriteDebugStats a, LegacyEntitySpriteDebugStats b) {
					return a.legacySpriteId - b.legacySpriteId;
				}
			});
			for (LegacyEntitySpriteDebugStats stat : stats) {
				writer.println(stat.legacySpriteId
					+ "\t" + stat.commands
					+ "\t" + stat.visiblePixels
					+ "\t" + stat.directPixels
					+ "\t" + stat.fallbacks
					+ "\t" + stat.skipped
					+ "\t" + stat.atlasFull
					+ "\t" + stat.depthEvaluations
					+ "\t" + stat.depthVisiblePixels
					+ "\t" + stat.depthMisses
					+ "\t" + stat.depthSourcePixels
					+ "\t" + stat.depthOccludedPixels
					+ "\t" + stat.depthClippedPixels
					+ "\t" + stat.depthOutOfBoundsPixels
					+ "\t" + emptyIfUnset(stat.minAnchorDepth, Integer.MAX_VALUE)
					+ "\t" + emptyIfUnset(stat.maxAnchorDepth, Integer.MIN_VALUE)
					+ "\t" + bounds(stat.minX, stat.minY, stat.maxX, stat.maxY)
					+ "\t" + stat.describe());
			}
		} finally {
			writer.close();
		}
	}

	void writeEntityDepthEvaluations(OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "entity-depth-evaluations.tsv"));
		try {
			writer.println(
				"index\tsequence\tphase\tlegacySpriteId\tx\ty\twidth\theight\ttopX16\tbottomX16"
					+ "\tanchorFaceId\tanchorLegacyDrawOrder\tanchorAverageDepth\tanchorCameraZ"
					+ "\tanchorMatchMode\tanchorMatchScore"
					+ "\tanchorDrawX\tanchorDrawY\tanchorDrawWidth\tanchorDrawHeight"
					+ "\tanchorDeltaX\tanchorDeltaY\tanchorDeltaWidth\tanchorDeltaHeight"
					+ "\tsourcePixels\tvisiblePixels\toccludedPixels\tclippedPixels"
					+ "\toutOfBoundsPixels\tterrainOccludedPixels\twallOccludedPixels"
					+ "\troofOccludedPixels\tgameObjectOccludedPixels\twallObjectOccludedPixels"
					+ "\tminOccluderLegacyDrawOrder\tmaxOccluderLegacyDrawOrder"
					+ "\tminOccluderDepth\tmaxOccluderDepth"
					+ "\tdominantOccluderKind\tdominantOccluderFaceId\tdominantOccluderModelIndex"
					+ "\tdominantOccluderPixels\tdominantOccluderLegacyDrawOrder"
					+ "\tdominantOccluderDepth"
					+ "\tfullyOccluded\tvisiblePct\toccludedPct\tbounds");
			for (LegacyEntitySpriteDepthEvaluation evaluation : presenter.legacyEntitySpriteDepthEvaluations) {
				writer.println(evaluation.index
					+ "\t" + evaluation.sequence
					+ "\t" + evaluation.phase
					+ "\t" + evaluation.legacySpriteId
					+ "\t" + evaluation.x
					+ "\t" + evaluation.y
					+ "\t" + evaluation.width
					+ "\t" + evaluation.height
					+ "\t" + evaluation.topX16
					+ "\t" + evaluation.bottomX16
					+ "\t" + emptyIfUnset(evaluation.anchorFaceId, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorLegacyDrawOrder, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorAverageDepth, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorCameraZ, Integer.MIN_VALUE)
					+ "\t" + safeTsv(evaluation.anchorMatchMode)
					+ "\t" + emptyIfUnset(evaluation.anchorMatchScore, Integer.MAX_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDrawX, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDrawY, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDrawWidth, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDrawHeight, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDeltaX, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDeltaY, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDeltaWidth, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.anchorDeltaHeight, Integer.MIN_VALUE)
					+ "\t" + evaluation.sourcePixels
					+ "\t" + evaluation.visiblePixels
					+ "\t" + evaluation.occludedPixels
					+ "\t" + evaluation.clippedPixels
					+ "\t" + evaluation.outOfBoundsPixels
					+ "\t" + evaluation.terrainOccludedPixels
					+ "\t" + evaluation.wallOccludedPixels
					+ "\t" + evaluation.roofOccludedPixels
					+ "\t" + evaluation.gameObjectOccludedPixels
					+ "\t" + evaluation.wallObjectOccludedPixels
					+ "\t" + emptyIfUnset(evaluation.minOccluderLegacyDrawOrder, Integer.MAX_VALUE)
					+ "\t" + emptyIfUnset(evaluation.maxOccluderLegacyDrawOrder, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.minOccluderDepth, Integer.MAX_VALUE)
					+ "\t" + emptyIfUnset(evaluation.maxOccluderDepth, Integer.MIN_VALUE)
					+ "\t" + safeTsv(evaluation.dominantOccluderKind)
					+ "\t" + emptyIfUnset(evaluation.dominantOccluderFaceId, -1)
					+ "\t" + emptyIfUnset(evaluation.dominantOccluderModelIndex, -1)
					+ "\t" + evaluation.dominantOccluderPixels
					+ "\t" + emptyIfUnset(evaluation.dominantOccluderLegacyDrawOrder, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(evaluation.dominantOccluderDepth, Integer.MIN_VALUE)
					+ "\t" + evaluation.fullyOccluded
					+ "\t" + percent(evaluation.visiblePixels, evaluation.sourcePixels)
					+ "\t" + percent(evaluation.occludedPixels, evaluation.sourcePixels)
					+ "\t" + bounds(evaluation.x, evaluation.y, evaluation.x + evaluation.width, evaluation.y + evaluation.height));
			}
		} finally {
			writer.close();
		}
	}

	void writeWorldSpriteBatchStats(OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "world-sprite-batch-stats.tsv"));
		try {
			writer.println("commands\ttextureBatches");
			writer.println(presenter.worldSpriteDepthDrawCommands
				+ "\t" + presenter.worldSpriteDepthTextureBatches);
		} finally {
			writer.close();
		}
	}

	void writeShaderVertexParityStats(OpenGLFramePresenter presenter) throws Exception {
		ShaderVertexParityStats stats = presenter == null || presenter.worldMeshRenderer == null
			? ShaderVertexParityStats.EMPTY
			: presenter.worldMeshRenderer.getLastShaderVertexParityStats();
		PrintWriter writer = new PrintWriter(new File(directory, "shader-vertex-parity.txt"));
		try {
			writer.println("vertexCount=" + stats.vertexCount);
			writer.println("positionMismatches=" + stats.positionMismatches);
			writer.println("textureCoordMismatches=" + stats.textureCoordMismatches);
			writer.println("colorMismatches=" + stats.colorMismatches);
			writer.println("maxAbsoluteDelta=" + stats.maxAbsoluteDelta);
		} finally {
			writer.close();
		}
	}

	void writeMetadata(
		Frame frame,
		boolean worldReplacementComposite,
		OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "metadata.txt"));
		try {
			writer.println("sequence=" + sequence);
			writer.println("created=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()));
			writer.println("source=" + frame.sourceWidth + "x" + frame.sourceHeight);
			writer.println("target=" + frame.targetWidth + "x" + frame.targetHeight);
			writer.println("linearFiltering=" + frame.linearFiltering);
			writer.println("worldReplacementComposite=" + worldReplacementComposite);
			writeRendererMode(writer, worldReplacementComposite);
			if (frame.renderer2DFrame != null) {
				Renderer2DFrame renderer2DFrame = frame.renderer2DFrame;
				writer.println("renderer2D=" + renderer2DFrame.getWidth() + "x" + renderer2DFrame.getHeight());
				writer.println("spriteCommands=" + renderer2DFrame.getSpriteCommands().length);
				writer.println("textCommands=" + renderer2DFrame.getTextCommands().length);
				writer.println("primitiveCommands=" + renderer2DFrame.getPrimitiveCommands().length);
				writer.println("rotatedSpriteCommands=" + renderer2DFrame.getRotatedSpriteCommands().length);
				writer.println("circleCommands=" + renderer2DFrame.getCircleCommands().length);
			}
			if (frame.renderer3DFrame != null) {
				Renderer3DFrame renderer3DFrame = frame.renderer3DFrame;
				writer.println("renderer3DViewport="
					+ renderer3DFrame.getViewportWidth()
					+ "x"
					+ renderer3DFrame.getViewportHeight());
				writer.println("cameraOffset="
					+ renderer3DFrame.getCameraOffsetX()
					+ ","
					+ renderer3DFrame.getCameraOffsetY()
					+ ","
					+ renderer3DFrame.getCameraOffsetZ());
				writer.println("cameraRotation="
					+ renderer3DFrame.getCameraRotationX()
					+ ","
					+ renderer3DFrame.getCameraRotationY()
					+ ","
					+ renderer3DFrame.getCameraRotationZ());
				writer.println("activePlane=" + renderer3DFrame.getActivePlane());
				writer.println("roofVisibility=" + renderer3DFrame.getRoofVisibility().name());
				writer.println(
					"roofsVisible=" + renderer3DFrame.getRoofVisibility().areRoofsVisible());
				writer.println("shading.terrainReliefMode=" + RendererReliefSettings.getTerrainMode().id);
				writer.println("shading.terrainReliefLevel=" + RendererReliefSettings.getTerrainLevel());
				writer.println("shading.terrainReliefStrength=" + RendererReliefSettings.getTerrainStrength());
				writer.println("shading.objectReliefMode=" + RendererReliefSettings.getObjectMode().id);
				writer.println("shading.objectReliefLevel=" + RendererReliefSettings.getObjectLevel());
				writer.println("shading.objectReliefStrength=" + RendererReliefSettings.getObjectStrength());
				writer.println("shading.dimnessLevel=" + RendererColorDiagnosticSettings.getDimnessLevel());
				writer.println("shading.dimnessMultiplier=" + RendererColorDiagnosticSettings.getDimnessMultiplier());
				writer.println("shading.contrastLevel=" + RendererColorDiagnosticSettings.getContrastLevel());
				writer.println("shading.contrastMultiplier=" + RendererColorDiagnosticSettings.getContrastMultiplier());
				writer.println("shading.gammaLevel=" + RendererColorDiagnosticSettings.getGammaLevel());
				writer.println("shading.gammaValue=" + RendererColorDiagnosticSettings.getGammaValue());
				writer.println("shading.saturationLevel=" + RendererColorDiagnosticSettings.getSaturationLevel());
				writer.println("shading.saturationMultiplier=" + RendererColorDiagnosticSettings.getSaturationMultiplier());
				writer.println("shading.diffuseResponse=model-kind-fixed");
				writer.println("shading.terrainShadowChannels=directional+contact");
				writer.println("shading.objectShadowMask=not-applied");
				writer.println(
					"shading.terrainShadowDirectionalBaseAlpha="
						+ RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_BASE_ALPHA);
				writer.println(
					"shading.terrainShadowDirectionalMaxAlpha="
						+ RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_MAX_ALPHA);
				writer.println(
					"shading.terrainShadowDirectionalAlphaScale="
						+ RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_DIRECTIONAL_ALPHA_SCALE);
				writer.println(
					"shading.terrainShadowSceneryAlphaScale="
						+ RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_SCENERY_ALPHA_SCALE);
				writer.println(
					"shading.terrainShadowContactAlpha="
						+ RemasterShadowMaskBuilder.REMASTER_SHADOW_MASK_CONTACT_ALPHA);
				writer.println("worldFaces=" + renderer3DFrame.getWorldFaceCount());
				for (Renderer3DModelKind kind : Renderer3DModelKind.values()) {
					writer.println("worldFaces." + kind.name() + "=" + renderer3DFrame.getWorldFaceCount(kind));
				}
				Renderer3DWorldChunkFrame worldChunkFrame = renderer3DFrame.getWorldChunkFrame();
				if (worldChunkFrame != null) {
					writer.println("residentChunks=" + worldChunkFrame.getChunkCount());
					writer.println("residentTriangles=" + worldChunkFrame.getTotalTriangleCount());
					for (Renderer3DMaterialFamily family : Renderer3DMaterialFamily.values()) {
						writer.println(
							"residentMaterialFamily." + family.name() + "="
								+ worldChunkFrame.getMaterialFamilyTriangleCount(family));
					}
				}
				writer.println("spriteSubmissions=" + renderer3DFrame.getSpriteSubmissionCount());
				writer.println("characterSprites=" + renderer3DFrame.getCharacterSpriteCount());
				writer.println("spriteAnchors=" + renderer3DFrame.getSpriteAnchorCount());
				Renderer3DDepthFrame depthFrame = renderer3DFrame.getDepthFrame();
				if (depthFrame != null) {
					writer.println("depthFrame="
						+ depthFrame.getWidth()
						+ "x"
						+ depthFrame.getHeight()
						+ " acceptedFaces="
						+ depthFrame.getAcceptedFaceCount()
						+ " triangles="
						+ depthFrame.getTriangleCount()
						+ " pixels="
						+ depthFrame.getPixelWriteCount());
				}
				if (presenter != null && presenter.worldMeshRenderer != null) {
					writer.println("worldMeshDraw=triangles="
						+ presenter.worldMeshRenderer.getLastDrawTriangles()
						+ " occluderTriangles="
						+ presenter.worldMeshRenderer.getLastDrawOccluderTriangles()
						+ " batches="
						+ presenter.worldMeshRenderer.getLastDrawBatches()
						+ " calls="
						+ presenter.worldMeshRenderer.getLastDrawCalls());
				}
			}
		} finally {
			writer.close();
		}
	}

	static void writeRendererMode(PrintWriter writer, boolean worldReplacementComposite) {
		writer.println("rendererMode.worldMeshEnabled=" + WORLD_MESH_ENABLED);
		writer.println("rendererMode.worldMeshVisible=" + WORLD_MESH_VISIBLE);
		writer.println("rendererMode.worldMeshTexturedVisible=" + WORLD_MESH_TEXTURED_VISIBLE);
		writer.println("rendererMode.worldMeshTexturedStaticVisible=" + WORLD_MESH_TEXTURED_STATIC_VISIBLE);
		writer.println("rendererMode.worldMeshTexturedShader=" + WORLD_MESH_TEXTURED_SHADER);
		writer.println("rendererMode.worldMeshShaderNativeVbo=" + WORLD_MESH_TEXTURED_SHADER);
		writer.println("rendererMode.worldMeshTexturedAlpha=" + TEXTURED_DIAGNOSTIC_ALPHA);
		writer.println("rendererMode.worldStaticTextures=" + WORLD_STATIC_TEXTURES);
		writer.println("rendererMode.worldChunksVisible=" + WORLD_CHUNKS_VISIBLE);
		writer.println("rendererMode.worldChunksFilledVisible=" + WORLD_CHUNKS_FILLED_VISIBLE);
		writer.println("rendererMode.worldChunksTexturedVisible=" + WORLD_CHUNKS_TEXTURED_VISIBLE);
		writer.println("rendererMode.worldReplacementComposite=" + worldReplacementComposite);
		writer.println("rendererMode.worldReplacementCompositeConfigured=" + WORLD_REPLACEMENT_COMPOSITE);
		writer.println("rendererMode.worldChunksReplacementCompositeConfigured=" + WORLD_CHUNKS_REPLACEMENT_COMPOSITE);
		writer.println("rendererMode.worldChunksTrustedReplacement=" + WORLD_CHUNKS_TRUSTED_REPLACEMENT);
		writer.println("rendererMode.worldChunksResidentObjects=" + WORLD_CHUNKS_RESIDENT_OBJECTS);
		writer.println("rendererMode.worldChunksSpatialCull=" + WORLD_CHUNKS_SPATIAL_CULL);
		writer.println("rendererMode.worldChunksSpatialTileSize=" + OpenGLWorldChunkRenderer.spatialBatchTileSize());
		writer.println("rendererMode.worldSpritesVisible=" + WORLD_SPRITES_VISIBLE);
	}

	void writeDepthDiagnostics(Frame frame) throws Exception {
		if (frame.renderer3DFrame == null || frame.renderer3DFrame.getDepthFrame() == null) {
			return;
		}
		Renderer3DDepthFrame depthFrame = frame.renderer3DFrame.getDepthFrame();
		int width = depthFrame.getWidth();
		int height = depthFrame.getHeight();
		int[] pixels = new int[width * height];
		depthFrame.copyColorTo(pixels);
		writeImage("00-depth-color.png", opaqueRgbImage(width, height, pixels));
		depthFrame.copyKindColorTo(pixels);
		writeImage("00-depth-kind.png", argbImage(width, height, pixels));
		depthFrame.copyEntityOccluderMaskTo(pixels);
		writeImage("00-entity-occluder-mask.png", argbImage(width, height, pixels));
	}

	void writeWorldFaces(Frame frame) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "world-faces.tsv"));
		try {
			writer.println(
				"index\tmodelKind\tmodelIndex\tfaceId\ttexture\tcolor\torientation"
					+ "\tlegacyDrawOrder\taverageDepth\tvertexCount\trenderVertexCount"
					+ "\tcameraX\tcameraY\tcameraZ\tscreenX\tscreenY\tlight\ttextureU\ttextureV");
			if (frame.renderer3DFrame == null) {
				return;
			}
			List<Renderer3DFrame.FaceCommand> faces = frame.renderer3DFrame.getWorldFaces();
			for (int i = 0; i < faces.size(); i++) {
				Renderer3DFrame.FaceCommand face = faces.get(i);
				writer.println(i
					+ "\t" + face.getModelKind()
					+ "\t" + face.getModelIndex()
					+ "\t" + face.getFaceId()
					+ "\t" + face.getTexture()
					+ "\t" + face.getColor()
					+ "\t" + face.getOrientation()
					+ "\t" + face.getLegacyDrawOrder()
					+ "\t" + face.getAverageDepth()
					+ "\t" + face.getVertexCount()
					+ "\t" + face.getRenderVertexCount()
					+ "\t" + joinInts(face.getRenderCameraX())
					+ "\t" + joinInts(face.getRenderCameraY())
					+ "\t" + joinInts(face.getRenderCameraZ())
					+ "\t" + joinInts(face.getRenderScreenX())
					+ "\t" + joinInts(face.getRenderScreenY())
					+ "\t" + joinInts(face.getRenderLight())
					+ "\t" + joinFloats(face.getRenderTextureU())
					+ "\t" + joinFloats(face.getRenderTextureV()));
			}
		} finally {
			writer.close();
		}
	}

	void writeSpriteCommands(Frame frame) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "sprite-commands.tsv"));
		try {
			writer.println(
				"index\tsequence\tphase\tlegacySpriteId\tsceneSpriteAnchorIndex\tsceneSpriteDrawOrder"
					+ "\trendererSnapshotOwned\tlegacyEntity\trequiresOrderedReplay"
					+ "\tx\ty\twidth\theight\ttopX16\tbottomX16\talpha"
					+ "\tsourceX\tsourceY\tsourceWidth\tsourceHeight"
					+ "\tspriteWidth\tspriteHeight\tmirrorX");
			if (frame.renderer2DFrame == null) {
				return;
			}
			Renderer2DFrame.SpriteCommand[] commands = frame.renderer2DFrame.getSpriteCommands();
			for (int i = 0; i < commands.length; i++) {
				Renderer2DFrame.SpriteCommand command = commands[i];
				Sprite sprite = command.getSprite();
				writer.println(i
					+ "\t" + command.getSequence()
					+ "\t" + command.getPhase()
					+ "\t" + command.getLegacySpriteId()
					+ "\t" + command.getSceneSpriteAnchorIndex()
					+ "\t" + command.getSceneSpriteDrawOrder()
					+ "\t" + isRendererSnapshotOwned(frame, command)
					+ "\t" + isLegacyEntitySpriteId(command.getLegacySpriteId())
					+ "\t" + command.requiresOrderedReplay()
					+ "\t" + command.getX()
					+ "\t" + command.getY()
					+ "\t" + command.getWidth()
					+ "\t" + command.getHeight()
					+ "\t" + command.getTopX16()
					+ "\t" + command.getBottomX16()
					+ "\t" + command.getAlpha()
					+ "\t" + command.getSourceX()
					+ "\t" + command.getSourceY()
					+ "\t" + command.getSourceWidth()
					+ "\t" + command.getSourceHeight()
					+ "\t" + (sprite == null ? 0 : sprite.getWidth())
					+ "\t" + (sprite == null ? 0 : sprite.getHeight())
					+ "\t" + command.isMirrorX());
			}
		} finally {
			writer.close();
		}
	}

	void writeWorldSpriteSnapshots(Frame frame) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "world-sprite-snapshots.tsv"));
		try {
			writer.println(
				"snapshotIndex\tanchorIndex\tanchorFaceId\tlegacySpriteId\tlegacyDrawOrder"
					+ "\tpickIndex\tpickable\tlayerIndex\tsequence\talpha\tmirrorX\tskewed"
					+ "\tsourceX\tsourceY\tsourceWidth\tsourceHeight"
					+ "\tworldX\tworldY\tworldZ\tcharacterKind\tcharacterArrayIndex"
					+ "\tcharacterServerIndex\tcharacterEntityId\tdirection\tcombatDirection"
					+ "\tcombatTimeout\tcombatEffectType\tcombatEffectTime\tactiveHitSplats");
			if (frame == null || frame.renderer3DFrame == null) {
				return;
			}
			List<Renderer3DFrame.WorldSpriteSnapshot> snapshots =
				frame.renderer3DFrame.getWorldSpriteSnapshots();
			for (int snapshotIndex = 0; snapshotIndex < snapshots.size(); snapshotIndex++) {
				Renderer3DFrame.WorldSpriteSnapshot snapshot = snapshots.get(snapshotIndex);
				Renderer3DFrame.SpriteAnchor anchor = snapshot.getAnchor();
				Renderer3DFrame.SpriteSubmission submission = snapshot.getSubmission();
				Renderer3DFrame.CharacterSprite character = snapshot.getCharacter();
				List<Renderer2DFrame.SpriteCommand> layers = snapshot.getLayers();
				if (layers.isEmpty()) {
					writeWorldSpriteSnapshotRow(
						writer,
						snapshotIndex,
						snapshot,
						anchor,
						submission,
						character,
						-1,
						null);
					continue;
				}
				for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
					writeWorldSpriteSnapshotRow(
						writer,
						snapshotIndex,
						snapshot,
						anchor,
						submission,
						character,
						layerIndex,
						layers.get(layerIndex));
				}
			}
		} finally {
			writer.close();
		}
	}

	private void writeWorldSpriteSnapshotRow(
		PrintWriter writer,
		int snapshotIndex,
		Renderer3DFrame.WorldSpriteSnapshot snapshot,
		Renderer3DFrame.SpriteAnchor anchor,
		Renderer3DFrame.SpriteSubmission submission,
		Renderer3DFrame.CharacterSprite character,
		int layerIndex,
		Renderer2DFrame.SpriteCommand layer) {
		writer.println(snapshotIndex
			+ "\t" + snapshot.getAnchorIndex()
			+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getFaceId()))
			+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getSpriteId()))
			+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getLegacyDrawOrder()))
			+ "\t" + snapshot.getPickIndex()
			+ "\t" + snapshot.isPickable()
			+ "\t" + emptyIfUnset(layerIndex, -1)
			+ "\t" + (layer == null ? "" : String.valueOf(layer.getSequence()))
			+ "\t" + (layer == null ? "" : String.valueOf(layer.getAlpha()))
			+ "\t" + (layer != null && layer.isMirrorX())
			+ "\t" + (layer != null && layer.getTopX16() != layer.getBottomX16())
			+ "\t" + (layer == null ? "" : String.valueOf(layer.getSourceX()))
			+ "\t" + (layer == null ? "" : String.valueOf(layer.getSourceY()))
			+ "\t" + (layer == null ? "" : String.valueOf(layer.getSourceWidth()))
			+ "\t" + (layer == null ? "" : String.valueOf(layer.getSourceHeight()))
			+ "\t" + (submission == null ? "" : String.valueOf(submission.getWorldX()))
			+ "\t" + (submission == null ? "" : String.valueOf(submission.getWorldY()))
			+ "\t" + (submission == null ? "" : String.valueOf(submission.getWorldZ()))
			+ "\t" + (character == null ? "" : character.getKind())
			+ "\t" + (character == null ? "" : String.valueOf(character.getArrayIndex()))
			+ "\t" + (character == null ? "" : String.valueOf(character.getServerIndex()))
			+ "\t" + (character == null ? "" : String.valueOf(character.getEntityId()))
			+ "\t" + (character == null ? "" : character.getDirection())
			+ "\t" + (character != null && character.isCombatDirection())
			+ "\t" + (character == null ? "" : String.valueOf(character.getCombatTimeout()))
			+ "\t" + (character == null ? "" : String.valueOf(character.getCombatEffectType()))
			+ "\t" + (character == null ? "" : String.valueOf(character.getCombatEffectTime()))
			+ "\t" + (character != null && character.hasActiveHitSplats()));
	}

	void writeWorldSpriteCommands(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "world-sprite-commands.tsv"));
		try {
			writer.println(
				"index\tsequence\tphase\tlegacySpriteId\tsceneSpriteAnchorIndex\tsceneSpriteDrawOrder"
					+ "\trendererSnapshotOwned\tworldSpriteKind\tanchorMatchMode\tanchorMatchScore"
					+ "\tanchorFaceId\tanchorLegacyDrawOrder\tanchorAverageDepth\tanchorCameraZ\tdepthOwned"
					+ "\tx\ty\twidth\theight\ttopX16\tbottomX16\talpha"
					+ "\tsourceX\tsourceY\tsourceWidth\tsourceHeight\tspriteWidth\tspriteHeight"
					+ "\tsourceCropped\tmirrorX\tskewed");
			if (frame.renderer2DFrame == null || presenter == null) {
				return;
			}
			List<WorldSpriteCommand> commands = presenter.buildOpenGLCompositeWorldSpriteCommands(
				frame,
				frame.renderer2DFrame.getSpriteCommands());
			for (int i = 0; i < commands.size(); i++) {
				WorldSpriteCommand worldCommand = commands.get(i);
				Renderer2DFrame.SpriteCommand command = worldCommand.command;
				Sprite sprite = command.getSprite();
				Renderer3DFrame.SpriteAnchor anchor = worldCommand.anchor;
				writer.println(i
					+ "\t" + command.getSequence()
					+ "\t" + command.getPhase()
					+ "\t" + command.getLegacySpriteId()
					+ "\t" + command.getSceneSpriteAnchorIndex()
					+ "\t" + command.getSceneSpriteDrawOrder()
					+ "\t" + isRendererSnapshotOwned(frame, command)
					+ "\t" + worldSpriteKind(command)
					+ "\t" + worldCommand.anchorMatch.mode
					+ "\t" + worldCommand.anchorMatch.score
					+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getFaceId()))
					+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getLegacyDrawOrder()))
					+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getAverageDepth()))
					+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getCameraZ()))
					+ "\t" + (anchor != null)
					+ "\t" + command.getX()
					+ "\t" + command.getY()
					+ "\t" + command.getWidth()
					+ "\t" + command.getHeight()
					+ "\t" + command.getTopX16()
					+ "\t" + command.getBottomX16()
					+ "\t" + command.getAlpha()
					+ "\t" + command.getSourceX()
					+ "\t" + command.getSourceY()
					+ "\t" + command.getSourceWidth()
					+ "\t" + command.getSourceHeight()
					+ "\t" + (sprite == null ? 0 : sprite.getWidth())
					+ "\t" + (sprite == null ? 0 : sprite.getHeight())
					+ "\t" + worldCommand.sourceCropped
					+ "\t" + command.isMirrorX()
					+ "\t" + worldCommand.skewed);
			}
		} finally {
			writer.close();
		}
	}

	void writeCompositeSceneCommands(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "scene-commands.tsv"));
		try {
			writer.println(
				"index\tkind\tlegacyDrawOrder\tsequence\tminExclusiveOrder\tmaxExclusiveOrder"
					+ "\tfrontOccluderFaces\tlegacySpriteId\tsceneSpriteAnchorIndex\tsceneSpriteDrawOrder"
					+ "\trendererSnapshotOwned\tworldSpriteKind\tanchorMatchMode\tanchorFaceId"
					+ "\tsourceCropped\tmirrorX\tskewed");
			if (frame.renderer2DFrame == null || presenter == null) {
				return;
			}
			List<OpenGLCompositeSceneCommand> commands = presenter.buildOpenGLCompositeSceneCommands(
				frame,
				frame.renderer2DFrame.getSpriteCommands());
			for (int i = 0; i < commands.size(); i++) {
				OpenGLCompositeSceneCommand sceneCommand = commands.get(i);
				WorldSpriteCommand worldCommand = sceneCommand.worldSpriteCommand;
				Renderer2DFrame.SpriteCommand spriteCommand =
					worldCommand == null ? null : worldCommand.command;
				Renderer3DFrame.SpriteAnchor anchor = worldCommand == null ? null : worldCommand.anchor;
				writer.println(i
					+ "\t" + sceneCommand.kind
					+ "\t" + sceneCommand.legacyDrawOrder
					+ "\t" + sceneCommand.sequence
					+ "\t" + emptyIfUnset(sceneCommand.minExclusiveOrder, Integer.MIN_VALUE)
					+ "\t" + emptyIfUnset(sceneCommand.maxExclusiveOrder, Integer.MAX_VALUE)
					+ "\t" + sceneCommand.frontOccluderFaceKeys.size()
					+ "\t" + (spriteCommand == null ? "" : String.valueOf(spriteCommand.getLegacySpriteId()))
					+ "\t" + (spriteCommand == null ? "" : String.valueOf(spriteCommand.getSceneSpriteAnchorIndex()))
					+ "\t" + (spriteCommand == null ? "" : String.valueOf(spriteCommand.getSceneSpriteDrawOrder()))
					+ "\t" + (spriteCommand != null && isRendererSnapshotOwned(frame, spriteCommand))
					+ "\t" + (spriteCommand == null ? "" : worldSpriteKind(spriteCommand))
					+ "\t" + (worldCommand == null ? "" : worldCommand.anchorMatch.mode)
					+ "\t" + (anchor == null ? "" : String.valueOf(anchor.getFaceId()))
					+ "\t" + (worldCommand != null && worldCommand.sourceCropped)
					+ "\t" + (spriteCommand != null && spriteCommand.isMirrorX())
					+ "\t" + (worldCommand != null && worldCommand.skewed));
			}
		} finally {
			writer.close();
		}
	}

	void writeStaticWorldCommands(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		List<StaticWorldCommand> commands = presenter == null
			? Collections.<StaticWorldCommand>emptyList()
			: presenter.buildOpenGLCompositeStaticWorldCommands(frame);
		PrintWriter commandWriter = new PrintWriter(new File(directory, "static-world-commands.tsv"));
		PrintWriter ownershipWriter =
			new PrintWriter(new File(directory, "static-world-face-ownership.tsv"));
		try {
			commandWriter.println(
				"index\tkind\tmodelKind\tfaceCount\ttriangleCount\tminLegacyDrawOrder\tmaxLegacyDrawOrder");
			ownershipWriter.println("commandIndex\tkind\tmodelKind\tmodelIndex\tfaceId");
			for (int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
				StaticWorldCommand command = commands.get(commandIndex);
				commandWriter.println(commandIndex
					+ "\tBASE_WORLD"
					+ "\t" + command.modelKind
					+ "\t" + command.faceKeys.size()
					+ "\t" + command.triangleCount
					+ "\t" + emptyIfUnset(command.minLegacyDrawOrder, Integer.MAX_VALUE)
					+ "\t" + emptyIfUnset(command.maxLegacyDrawOrder, Integer.MIN_VALUE));
				List<Long> faceKeys = new ArrayList<Long>(command.faceKeys);
				Collections.sort(faceKeys);
				for (Long faceKey : faceKeys) {
					ownershipWriter.println(commandIndex
						+ "\tBASE_WORLD"
						+ "\t" + command.modelKind
						+ "\t" + openGLCompositeModelIndex(faceKey.longValue())
						+ "\t" + openGLCompositeFaceId(faceKey.longValue()));
				}
			}
		} finally {
			commandWriter.close();
			ownershipWriter.close();
		}
	}

	void writeStaticWorldMaterialTriangles(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		List<StaticWorldMaterialTriangle> triangles = presenter == null
			? Collections.<StaticWorldMaterialTriangle>emptyList()
			: presenter.buildOpenGLCompositeStaticWorldMaterialTriangles(frame);
		PrintWriter writer = new PrintWriter(new File(directory, "static-world-material-triangles.tsv"));
		try {
			writer.println(
				"triangleIndex\tmaterialPass\tmodelKind\tmodelIndex\tfaceId\ttextureId\ttextureHasTransparency");
			for (StaticWorldMaterialTriangle triangle : triangles) {
				writer.println(triangle.triangleIndex
					+ "\t" + triangle.materialPass
					+ "\t" + triangle.modelKind
					+ "\t" + triangle.modelIndex
					+ "\t" + triangle.faceId
					+ "\t" + triangle.textureId
					+ "\t" + triangle.textureHasTransparency);
			}
		} finally {
			writer.close();
		}
	}

	void writeResidentMaterialFamilies(Frame frame) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "resident-material-families.tsv"));
		try {
			writer.println(
				"chunkIndex\tplane\tcenterSectionX\tcenterSectionY\tchunkRole"
					+ "\tmodelKind\tmaterialFamily\tshaderId\ttextureId\tfallbackColor"
					+ "\tglowEmitterCount\ttriangleCount");
			if (frame == null || frame.renderer3DFrame == null) {
				return;
			}
			Renderer3DWorldChunkFrame chunkFrame = frame.renderer3DFrame.getWorldChunkFrame();
			if (chunkFrame == null) {
				return;
			}
			List<Renderer3DWorldChunkFrame.ChunkMesh> chunks = chunkFrame.getChunks();
			Map<String, Integer> counts = new java.util.LinkedHashMap<String, Integer>();
			for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
				Renderer3DWorldChunkFrame.ChunkMesh chunk = chunks.get(chunkIndex);
				for (int triangle = 0; triangle < chunk.getTriangleCount(); triangle++) {
					Renderer3DMaterialFamily family = chunk.getTriangleMaterialFamily(triangle);
					String key = chunkIndex
						+ "\t" + chunk.getPlane()
						+ "\t" + chunk.getCenterSectionX()
						+ "\t" + chunk.getCenterSectionY()
						+ "\t" + chunk.getChunkRole()
						+ "\t" + chunk.getTriangleModelKind(triangle)
						+ "\t" + family
						+ "\t" + family.getShaderId()
						+ "\t" + chunk.getTriangleTexture(triangle)
						+ "\t" + chunk.getTriangleFallbackColor(triangle)
						+ "\t" + chunk.getGlowEmitterCount();
					Integer count = counts.get(key);
					counts.put(key, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
				}
			}
			for (Map.Entry<String, Integer> entry : counts.entrySet()) {
				writer.println(entry.getKey() + "\t" + entry.getValue());
			}
		} finally {
			writer.close();
		}
	}

	void writeStaticRangeCandidates(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "static-range-candidates.tsv"));
		try {
			writer.println(
				"index\tminExclusiveOrder\tmaxExclusiveOrder\tworldSpriteCommandsAtOrder\tfinalRange"
					+ "\tstaticFaces\tterrainFaces\twallFaces\troofFaces\tgameObjectFaces\twallObjectFaces"
					+ "\toverlapFaces\toverlapTerrainFaces\toverlapWallFaces\toverlapRoofFaces"
					+ "\toverlapGameObjectFaces\toverlapWallObjectFaces\toverlapWorldSpriteCommands");
			if (frame.renderer2DFrame == null || frame.renderer3DFrame == null || presenter == null) {
				return;
			}
			List<WorldSpriteCommand> worldSpriteCommands = presenter.buildOpenGLCompositeWorldSpriteCommands(
				frame,
				frame.renderer2DFrame.getSpriteCommands());
			List<Renderer3DFrame.FaceCommand> faces = frame.renderer3DFrame.getWorldFaces();
			int index = 0;
			for (int commandIndex = 0; commandIndex < worldSpriteCommands.size();) {
				int currentOrder = worldSpriteCommands.get(commandIndex).legacyDrawOrder;
				int nextCommandIndex = commandIndex + 1;
				while (nextCommandIndex < worldSpriteCommands.size()
					&& worldSpriteCommands.get(nextCommandIndex).legacyDrawOrder == currentOrder) {
					nextCommandIndex++;
				}
				int maxExclusiveOrder = nextCommandIndex < worldSpriteCommands.size()
					? worldSpriteCommands.get(nextCommandIndex).legacyDrawOrder
					: Integer.MAX_VALUE;
				StaticRangeCandidateStats stats = staticRangeCandidateStats(
					faces,
					worldSpriteCommands,
					currentOrder,
					maxExclusiveOrder);
				writer.println(index
					+ "\t" + currentOrder
					+ "\t" + emptyIfUnset(maxExclusiveOrder, Integer.MAX_VALUE)
					+ "\t" + (nextCommandIndex - commandIndex)
					+ "\t" + (maxExclusiveOrder == Integer.MAX_VALUE)
					+ "\t" + stats.staticFaces
					+ "\t" + stats.terrainFaces
					+ "\t" + stats.wallFaces
					+ "\t" + stats.roofFaces
					+ "\t" + stats.gameObjectFaces
					+ "\t" + stats.wallObjectFaces
					+ "\t" + stats.overlapFaces
					+ "\t" + stats.overlapTerrainFaces
					+ "\t" + stats.overlapWallFaces
					+ "\t" + stats.overlapRoofFaces
					+ "\t" + stats.overlapGameObjectFaces
					+ "\t" + stats.overlapWallObjectFaces
					+ "\t" + stats.overlapWorldSpriteCommands);
				index++;
				commandIndex = nextCommandIndex;
			}
		} finally {
			writer.close();
		}
	}

	void writeFrontOccluderCandidates(Frame frame, OpenGLFramePresenter presenter) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "front-occluder-candidates.tsv"));
		try {
			writer.println(
				"index\trangeIndex\tminExclusiveOrder\tmaxExclusiveOrder\tmodelKind\tmodelIndex\tfaceId"
					+ "\tlegacyDrawOrder\taverageDepth\toverlapWorldSpriteCommands\tbounds");
			if (frame.renderer2DFrame == null || frame.renderer3DFrame == null || presenter == null) {
				return;
			}
			List<WorldSpriteCommand> worldSpriteCommands = presenter.buildOpenGLCompositeWorldSpriteCommands(
				frame,
				frame.renderer2DFrame.getSpriteCommands());
			List<Renderer3DFrame.FaceCommand> faces = frame.renderer3DFrame.getWorldFaces();
			int index = 0;
			int rangeIndex = 0;
			for (int commandIndex = 0; commandIndex < worldSpriteCommands.size();) {
				int currentOrder = worldSpriteCommands.get(commandIndex).legacyDrawOrder;
				int nextCommandIndex = commandIndex + 1;
				while (nextCommandIndex < worldSpriteCommands.size()
					&& worldSpriteCommands.get(nextCommandIndex).legacyDrawOrder == currentOrder) {
					nextCommandIndex++;
				}
				int maxExclusiveOrder = nextCommandIndex < worldSpriteCommands.size()
					? worldSpriteCommands.get(nextCommandIndex).legacyDrawOrder
					: Integer.MAX_VALUE;
				for (Renderer3DFrame.FaceCommand face : faces) {
					int order = face.getLegacyDrawOrder();
					if (order <= currentOrder || order < 0) {
						continue;
					}
					if (maxExclusiveOrder != Integer.MAX_VALUE && order >= maxExclusiveOrder) {
						continue;
					}
					if (!isFrontOccluderCandidateKind(face.getModelKind())) {
						continue;
					}
					int[] faceBounds = boundsFor(face.getRenderScreenX(), face.getRenderScreenY());
					int overlappingSpriteCommands =
						countOverlappingWorldSpriteCommands(faceBounds, worldSpriteCommands);
					if (overlappingSpriteCommands <= 0) {
						continue;
					}
					writer.println(index
						+ "\t" + rangeIndex
						+ "\t" + currentOrder
						+ "\t" + emptyIfUnset(maxExclusiveOrder, Integer.MAX_VALUE)
						+ "\t" + face.getModelKind()
						+ "\t" + face.getModelIndex()
						+ "\t" + face.getFaceId()
						+ "\t" + order
						+ "\t" + face.getAverageDepth()
						+ "\t" + overlappingSpriteCommands
						+ "\t" + bounds(faceBounds));
					index++;
				}
				rangeIndex++;
				commandIndex = nextCommandIndex;
			}
		} finally {
			writer.close();
		}
	}

	static StaticRangeCandidateStats staticRangeCandidateStats(
		List<Renderer3DFrame.FaceCommand> faces,
		List<WorldSpriteCommand> worldSpriteCommands,
		int minExclusiveOrder,
		int maxExclusiveOrder) {
		StaticRangeCandidateStats stats = new StaticRangeCandidateStats();
		for (Renderer3DFrame.FaceCommand face : faces) {
			int order = face.getLegacyDrawOrder();
			if (order <= minExclusiveOrder || order < 0) {
				continue;
			}
			if (maxExclusiveOrder != Integer.MAX_VALUE && order >= maxExclusiveOrder) {
				continue;
			}
			stats.recordFace(face.getModelKind(), false);
			int[] faceBounds = boundsFor(face.getRenderScreenX(), face.getRenderScreenY());
			int overlappingSpriteCommands =
				countOverlappingWorldSpriteCommands(faceBounds, worldSpriteCommands);
			if (overlappingSpriteCommands > 0) {
				stats.recordFace(face.getModelKind(), true);
				stats.overlapWorldSpriteCommands += overlappingSpriteCommands;
			}
		}
		return stats;
	}

	static int countOverlappingWorldSpriteCommands(
		int[] faceBounds,
		List<WorldSpriteCommand> worldSpriteCommands) {
		if (faceBounds == null) {
			return 0;
		}
		int overlappingSpriteCommands = 0;
		for (WorldSpriteCommand worldSpriteCommand : worldSpriteCommands) {
			if (worldSpriteCommand == null || worldSpriteCommand.command == null) {
				continue;
			}
			Renderer2DFrame.SpriteCommand command = worldSpriteCommand.command;
			int[] spriteBounds = new int[] {
				command.getX(),
				command.getY(),
				command.getX() + command.getWidth(),
				command.getY() + command.getHeight()
			};
			if (overlaps(faceBounds, spriteBounds)) {
				overlappingSpriteCommands++;
			}
		}
		return overlappingSpriteCommands;
	}

	static boolean isFrontOccluderCandidateKind(Renderer3DModelKind kind) {
		return kind == Renderer3DModelKind.WALL
			|| kind == Renderer3DModelKind.GAME_OBJECT
			|| kind == Renderer3DModelKind.WALL_OBJECT;
	}

	static int[] boundsFor(int[] xs, int[] ys) {
		if (xs == null || ys == null || xs.length == 0 || xs.length != ys.length) {
			return null;
		}
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (int i = 0; i < xs.length; i++) {
			minX = Math.min(minX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxX = Math.max(maxX, xs[i]);
			maxY = Math.max(maxY, ys[i]);
		}
		return new int[] { minX, minY, maxX, maxY };
	}

	static boolean overlaps(int[] left, int[] right) {
		return left[0] < right[2]
			&& left[2] > right[0]
			&& left[1] < right[3]
			&& left[3] > right[1];
	}

	void writeSpriteSubmissions(Frame frame) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "sprite-submissions.tsv"));
		try {
			writer.println(
				"index\tfaceId\tspriteId\tlegacyEntity\tpickIndex\tprojected\tcullReason"
					+ "\tworldX\tworldY\tworldZ\tsourceWidth\tsourceHeight"
					+ "\tcameraX\tcameraY\tcameraZ\tscreenX\tscreenY"
					+ "\tdrawX\tdrawY\tdrawWidth\tdrawHeight\tscale\thorizontalSkew");
			if (frame.renderer3DFrame == null) {
				return;
			}
			List<Renderer3DFrame.SpriteSubmission> submissions =
				frame.renderer3DFrame.getSpriteSubmissions();
			for (int i = 0; i < submissions.size(); i++) {
				Renderer3DFrame.SpriteSubmission submission = submissions.get(i);
				writer.println(i
					+ "\t" + submission.getFaceId()
					+ "\t" + submission.getSpriteId()
					+ "\t" + isLegacyEntitySpriteId(submission.getSpriteId())
					+ "\t" + submission.getPickIndex()
					+ "\t" + submission.isProjected()
					+ "\t" + submission.getCullReason()
					+ "\t" + submission.getWorldX()
					+ "\t" + submission.getWorldY()
					+ "\t" + submission.getWorldZ()
					+ "\t" + submission.getSourceWidth()
					+ "\t" + submission.getSourceHeight()
					+ "\t" + submission.getCameraX()
					+ "\t" + submission.getCameraY()
					+ "\t" + submission.getCameraZ()
					+ "\t" + submission.getScreenX()
					+ "\t" + submission.getScreenY()
					+ "\t" + submission.getDrawX()
					+ "\t" + submission.getDrawY()
					+ "\t" + submission.getDrawWidth()
					+ "\t" + submission.getDrawHeight()
					+ "\t" + submission.getScale()
					+ "\t" + submission.getHorizontalSkew());
			}
		} finally {
			writer.close();
		}
	}

	void writeCharacterSprites(
		Frame frame,
		Map<Integer, LegacyEntitySpriteDebugStats> restoreStatsById) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "character-sprites.tsv"));
		try {
			writer.println(
				"index\tkind\tfaceId\tspriteId\tarrayIndex\tserverIndex\tentityId\tdisplayName"
					+ "\tprojected\tcullReason\tdirection\tcombatDirection\tcombatTimeout"
					+ "\thealthCurrent\thealthMax\tdamageTaken\tactiveHitSplats"
					+ "\tattackingNpcServerIndex\tattackingPlayerServerIndex"
					+ "\tcombatEffectType\tcombatEffectTime"
					+ "\tworldX\tworldY\tworldZ\tvisualOffsetX\tvisualOffsetZ"
					+ "\tsourceWidth\tsourceHeight\tdrawX\tdrawY\tdrawWidth\tdrawHeight"
					+ "\tbodyCommands\tbodySceneCommands\tbodyWorldCommands\tbodyUiCommands"
					+ "\tbodyUnknownCommands\tbodyOrderedCommands\tbodyAlphaZeroCommands"
					+ "\tbodyDestPixels\tbodySourceVisiblePixels\tbodyFirstSequence\tbodyLastSequence"
					+ "\tbodyBounds"
					+ "\trestoreCommands\trestoreVisiblePixels\trestoreDirectPixels"
					+ "\trestoreFallbacks\trestoreSkipped\trestoreAtlasFull"
					+ "\trestoreDepthEvaluations\trestoreDepthVisiblePixels"
					+ "\trestoreDepthMisses\trestoreDepthSourcePixels\trestoreDepthOccludedPixels"
					+ "\trestoreDepthClippedPixels\trestoreDepthOutOfBoundsPixels\trestoreBounds"
					+ "\tdiagnosis");
			if (frame.renderer3DFrame == null) {
				return;
			}
			Map<Integer, SpriteCommandStats> bodyStatsById = buildSpriteCommandStats(frame);
			List<Renderer3DFrame.CharacterSprite> characters =
				frame.renderer3DFrame.getCharacterSprites();
			for (int i = 0; i < characters.size(); i++) {
				Renderer3DFrame.CharacterSprite character = characters.get(i);
				SpriteCommandStats bodyStats = bodyStatsById.get(character.getSpriteId());
				LegacyEntitySpriteDebugStats restoreStats =
					restoreStatsById == null ? null : restoreStatsById.get(character.getSpriteId());
				writer.println(i
					+ "\t" + safeTsv(character.getKind())
					+ "\t" + character.getFaceId()
					+ "\t" + character.getSpriteId()
					+ "\t" + character.getArrayIndex()
					+ "\t" + character.getServerIndex()
					+ "\t" + character.getEntityId()
					+ "\t" + safeTsv(character.getDisplayName())
					+ "\t" + character.isProjected()
					+ "\t" + safeTsv(character.getCullReason())
					+ "\t" + safeTsv(character.getDirection())
					+ "\t" + character.isCombatDirection()
					+ "\t" + character.getCombatTimeout()
					+ "\t" + character.getHealthCurrent()
					+ "\t" + character.getHealthMax()
					+ "\t" + character.getDamageTaken()
					+ "\t" + character.hasActiveHitSplats()
					+ "\t" + character.getAttackingNpcServerIndex()
					+ "\t" + character.getAttackingPlayerServerIndex()
					+ "\t" + character.getCombatEffectType()
					+ "\t" + character.getCombatEffectTime()
					+ "\t" + character.getWorldX()
					+ "\t" + character.getWorldY()
					+ "\t" + character.getWorldZ()
					+ "\t" + character.getVisualOffsetX()
					+ "\t" + character.getVisualOffsetZ()
					+ "\t" + character.getSourceWidth()
					+ "\t" + character.getSourceHeight()
					+ "\t" + character.getDrawX()
					+ "\t" + character.getDrawY()
					+ "\t" + character.getDrawWidth()
					+ "\t" + character.getDrawHeight()
					+ "\t" + intStat(bodyStats, "commands")
					+ "\t" + intStat(bodyStats, "sceneCommands")
					+ "\t" + intStat(bodyStats, "worldCommands")
					+ "\t" + intStat(bodyStats, "uiCommands")
					+ "\t" + intStat(bodyStats, "unknownCommands")
					+ "\t" + intStat(bodyStats, "orderedCommands")
					+ "\t" + intStat(bodyStats, "alphaZeroCommands")
					+ "\t" + intStat(bodyStats, "destPixels")
					+ "\t" + intStat(bodyStats, "sourceVisiblePixels")
					+ "\t" + firstSequence(bodyStats)
					+ "\t" + lastSequence(bodyStats)
					+ "\t" + safeTsv(spriteCommandBounds(bodyStats))
					+ "\t" + restoreIntStat(restoreStats, "commands")
					+ "\t" + restoreIntStat(restoreStats, "visiblePixels")
					+ "\t" + restoreIntStat(restoreStats, "directPixels")
					+ "\t" + restoreIntStat(restoreStats, "fallbacks")
					+ "\t" + restoreIntStat(restoreStats, "skipped")
					+ "\t" + restoreIntStat(restoreStats, "atlasFull")
					+ "\t" + restoreIntStat(restoreStats, "depthEvaluations")
					+ "\t" + restoreIntStat(restoreStats, "depthVisiblePixels")
					+ "\t" + restoreIntStat(restoreStats, "depthMisses")
					+ "\t" + restoreIntStat(restoreStats, "depthSourcePixels")
					+ "\t" + restoreIntStat(restoreStats, "depthOccludedPixels")
					+ "\t" + restoreIntStat(restoreStats, "depthClippedPixels")
					+ "\t" + restoreIntStat(restoreStats, "depthOutOfBoundsPixels")
					+ "\t" + safeTsv(restoreBounds(restoreStats))
					+ "\t" + safeTsv(characterDiagnosis(character, bodyStats, restoreStats)));
			}
		} finally {
			writer.close();
		}
	}

	static Map<Integer, SpriteCommandStats> buildSpriteCommandStats(Frame frame) {
		Map<Integer, SpriteCommandStats> statsById = new HashMap<Integer, SpriteCommandStats>();
		if (frame.renderer2DFrame == null) {
			return statsById;
		}
		Renderer2DFrame.SpriteCommand[] commands = frame.renderer2DFrame.getSpriteCommands();
		for (Renderer2DFrame.SpriteCommand command : commands) {
			int legacySpriteId = command.getLegacySpriteId();
			if (!isLegacyEntitySpriteId(legacySpriteId)) {
				continue;
			}
			SpriteCommandStats stats = statsById.get(legacySpriteId);
			if (stats == null) {
				stats = new SpriteCommandStats(legacySpriteId);
				statsById.put(legacySpriteId, stats);
			}
			stats.record(command);
		}
		return statsById;
	}

	static String characterDiagnosis(
		Renderer3DFrame.CharacterSprite character,
		SpriteCommandStats bodyStats,
		LegacyEntitySpriteDebugStats restoreStats) {
		if (!character.isProjected()) {
			return "not-projected:" + character.getCullReason();
		}
		if (bodyStats == null || bodyStats.commands == 0) {
			return "projected-no-body-command";
		}
		if (bodyStats.sourceVisiblePixels == 0) {
			return "body-command-has-no-visible-source-pixels";
		}
		if (restoreStats == null) {
			return "body-command-before-restore-stats";
		}
		if (restoreStats.depthEvaluations > 0 && restoreStats.depthVisiblePixels > 0) {
			return "restore-depth-visible";
		}
		if (restoreStats.depthMisses > 0 && restoreStats.depthOccludedPixels >= restoreStats.depthSourcePixels) {
			return "restore-depth-fully-occluded";
		}
		if (restoreStats.depthEvaluations > 0
			&& restoreStats.depthSourcePixels > 0
			&& restoreStats.depthVisiblePixels == 0
			&& restoreStats.depthOutOfBoundsPixels > 0
			&& restoreStats.depthOutOfBoundsPixels
				+ restoreStats.depthClippedPixels
				+ restoreStats.depthOccludedPixels >= restoreStats.depthSourcePixels) {
			return "restore-depth-out-of-bounds";
		}
		if (restoreStats.depthEvaluations > 0
			&& restoreStats.depthSourcePixels > 0
			&& restoreStats.depthVisiblePixels == 0
			&& restoreStats.depthClippedPixels > 0
			&& restoreStats.depthOutOfBoundsPixels
				+ restoreStats.depthClippedPixels
				+ restoreStats.depthOccludedPixels >= restoreStats.depthSourcePixels) {
			return "restore-depth-clipped";
		}
		if (restoreStats.commands == 0) {
			return "body-command-not-restored";
		}
		if (restoreStats.visiblePixels > 0) {
			return "restore-visible";
		}
		if (restoreStats.atlasFull > 0) {
			return "restore-atlas-full";
		}
		if (restoreStats.skipped > 0) {
			return "restore-zero-visible-pixels";
		}
		if (restoreStats.depthMisses > 0) {
			return "restore-depth-miss";
		}
		return "restore-no-visible-output";
	}

	static int intStat(SpriteCommandStats stats, String field) {
		if (stats == null) {
			return 0;
		}
		if ("commands".equals(field)) {
			return stats.commands;
		}
		if ("sceneCommands".equals(field)) {
			return stats.sceneCommands;
		}
		if ("worldCommands".equals(field)) {
			return stats.worldCommands;
		}
		if ("uiCommands".equals(field)) {
			return stats.uiCommands;
		}
		if ("unknownCommands".equals(field)) {
			return stats.unknownCommands;
		}
		if ("orderedCommands".equals(field)) {
			return stats.orderedCommands;
		}
		if ("alphaZeroCommands".equals(field)) {
			return stats.alphaZeroCommands;
		}
		if ("destPixels".equals(field)) {
			return stats.destPixels;
		}
		if ("sourceVisiblePixels".equals(field)) {
			return stats.sourceVisiblePixels;
		}
		return 0;
	}

	static int restoreIntStat(LegacyEntitySpriteDebugStats stats, String field) {
		if (stats == null) {
			return 0;
		}
		if ("commands".equals(field)) {
			return stats.commands;
		}
		if ("visiblePixels".equals(field)) {
			return stats.visiblePixels;
		}
		if ("directPixels".equals(field)) {
			return stats.directPixels;
		}
		if ("fallbacks".equals(field)) {
			return stats.fallbacks;
		}
		if ("skipped".equals(field)) {
			return stats.skipped;
		}
		if ("atlasFull".equals(field)) {
			return stats.atlasFull;
		}
		if ("depthEvaluations".equals(field)) {
			return stats.depthEvaluations;
		}
		if ("depthVisiblePixels".equals(field)) {
			return stats.depthVisiblePixels;
		}
		if ("depthMisses".equals(field)) {
			return stats.depthMisses;
		}
		if ("depthSourcePixels".equals(field)) {
			return stats.depthSourcePixels;
		}
		if ("depthOccludedPixels".equals(field)) {
			return stats.depthOccludedPixels;
		}
		if ("depthClippedPixels".equals(field)) {
			return stats.depthClippedPixels;
		}
		if ("depthOutOfBoundsPixels".equals(field)) {
			return stats.depthOutOfBoundsPixels;
		}
		return 0;
	}

	static String firstSequence(SpriteCommandStats stats) {
		if (stats == null || stats.firstSequence == Integer.MAX_VALUE) {
			return "";
		}
		return String.valueOf(stats.firstSequence);
	}

	static String lastSequence(SpriteCommandStats stats) {
		if (stats == null || stats.lastSequence == Integer.MIN_VALUE) {
			return "";
		}
		return String.valueOf(stats.lastSequence);
	}

	static String spriteCommandBounds(SpriteCommandStats stats) {
		if (stats == null) {
			return "";
		}
		return bounds(stats.minX, stats.minY, stats.maxX, stats.maxY);
	}

	static String restoreBounds(LegacyEntitySpriteDebugStats stats) {
		if (stats == null) {
			return "";
		}
		return bounds(stats.minX, stats.minY, stats.maxX, stats.maxY);
	}

	static String joinInts(int[] values) {
		if (values == null || values.length == 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(values[i]);
		}
		return builder.toString();
	}

	static String joinFloats(float[] values) {
		if (values == null || values.length == 0) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append(',');
			}
			builder.append(Float.toString(values[i]));
		}
		return builder.toString();
	}

	static final class SpriteCommandStats {
		private final int legacySpriteId;
		private int commands;
		private int sceneCommands;
		private int worldCommands;
		private int uiCommands;
		private int unknownCommands;
		private int orderedCommands;
		private int alphaZeroCommands;
		private int destPixels;
		private int sourceVisiblePixels;
		private int firstSequence = Integer.MAX_VALUE;
		private int lastSequence = Integer.MIN_VALUE;
		private int minX = Integer.MAX_VALUE;
		private int minY = Integer.MAX_VALUE;
		private int maxX = Integer.MIN_VALUE;
		private int maxY = Integer.MIN_VALUE;

		private SpriteCommandStats(int legacySpriteId) {
			this.legacySpriteId = legacySpriteId;
		}

		private void record(Renderer2DFrame.SpriteCommand command) {
			commands++;
			switch (command.getPhase()) {
				case SCENE:
					sceneCommands++;
					break;
				case WORLD_OVERLAY:
					worldCommands++;
					break;
				case UI_OVERLAY:
					uiCommands++;
					break;
				default:
					unknownCommands++;
					break;
			}
			if (command.requiresOrderedReplay()) {
				orderedCommands++;
			}
			if (command.getAlpha() <= 0) {
				alphaZeroCommands++;
			}
			destPixels += command.getWidth() * command.getHeight();
			sourceVisiblePixels += countVisibleOutputPixels(command);
			firstSequence = Math.min(firstSequence, command.getSequence());
			lastSequence = Math.max(lastSequence, command.getSequence());
			minX = Math.min(minX, command.getX());
			minY = Math.min(minY, command.getY());
			maxX = Math.max(maxX, command.getX() + command.getWidth());
			maxY = Math.max(maxY, command.getY() + command.getHeight());
		}
	}

	static int countVisibleOutputPixels(Renderer2DFrame.SpriteCommand command) {
		if (command.getAlpha() <= 0) {
			return 0;
		}
		Sprite sprite = command.getSprite();
		int[] sourcePixels = sprite.getPixels();
		int spriteWidth = sprite.getWidth();
		int spriteHeight = sprite.getHeight();
		if (sourcePixels == null || sourcePixels.length < spriteWidth * spriteHeight) {
			return 0;
		}
		int visiblePixels = 0;
		for (int row = 0; row < command.getHeight(); row++) {
			int sourceY =
				(int) ((command.getSourceStartY16() + (long) row * command.getSourceScaleY16()) >> 16);
			if (sourceY < 0 || sourceY >= spriteHeight) {
				continue;
			}
			for (int column = 0; column < command.getWidth(); column++) {
				int sourceX =
					(int) ((command.getSourceStartX16() + (long) column * command.getSourceScaleX16()) >> 16);
				if (sourceX < 0 || sourceX >= spriteWidth) {
					continue;
				}
				if (orsc.graphics.RendererTransparency.isVisibleSpritePixel(
					sourcePixels[sourceY * spriteWidth + sourceX])) {
					visiblePixels++;
				}
			}
		}
		return visiblePixels;
	}

	void writeSpriteAnchors(Frame frame) throws Exception {
		PrintWriter writer = new PrintWriter(new File(directory, "sprite-anchors.tsv"));
		try {
			writer.println(
				"index\tfaceId\tspriteId\tlegacyEntity\tpickIndex\tlegacyDrawOrder"
					+ "\taverageDepth\tcameraX\tcameraY\tcameraZ\tscreenX\tscreenY"
					+ "\tdrawX\tdrawY\tdrawWidth\tdrawHeight\tscale\thorizontalSkew\tpickable");
			if (frame.renderer3DFrame == null) {
				return;
			}
			List<Renderer3DFrame.SpriteAnchor> anchors = frame.renderer3DFrame.getSpriteAnchors();
			for (int i = 0; i < anchors.size(); i++) {
				Renderer3DFrame.SpriteAnchor anchor = anchors.get(i);
				writer.println(i
					+ "\t" + anchor.getFaceId()
					+ "\t" + anchor.getSpriteId()
					+ "\t" + isLegacyEntitySpriteId(anchor.getSpriteId())
					+ "\t" + anchor.getPickIndex()
					+ "\t" + anchor.getLegacyDrawOrder()
					+ "\t" + anchor.getAverageDepth()
					+ "\t" + anchor.getCameraX()
					+ "\t" + anchor.getCameraY()
					+ "\t" + anchor.getCameraZ()
					+ "\t" + anchor.getScreenX()
					+ "\t" + anchor.getScreenY()
					+ "\t" + anchor.getDrawX()
					+ "\t" + anchor.getDrawY()
					+ "\t" + anchor.getDrawWidth()
					+ "\t" + anchor.getDrawHeight()
					+ "\t" + anchor.getScale()
					+ "\t" + anchor.getHorizontalSkew()
					+ "\t" + anchor.isPickable());
			}
		} finally {
			writer.close();
		}
	}

	static File frameCaptureBaseDirectory() {
		String path = System.getProperty(FRAME_CAPTURE_DIR_PROPERTY);
		if (path == null || path.trim().isEmpty()) {
			path = System.getenv(FRAME_CAPTURE_DIR_ENV);
		}
		if (path == null || path.trim().isEmpty()) {
			path = "renderer-v2-captures";
		}
		File directory = new File(path.trim());
		if (!directory.isAbsolute()) {
			directory = new File(System.getProperty("user.dir"), path.trim());
		}
		return directory;
	}

	static BufferedImage imageFromFrame(Frame frame) {
		BufferedImage image =
			new BufferedImage(frame.sourceWidth, frame.sourceHeight, BufferedImage.TYPE_INT_ARGB);
		ByteBuffer pixels = frame.pixels();
		for (int y = 0; y < frame.sourceHeight; y++) {
			for (int x = 0; x < frame.sourceWidth; x++) {
				int offset = (y * frame.sourceWidth + x) * 4;
				int red = pixels.get(offset) & 0xFF;
				int green = pixels.get(offset + 1) & 0xFF;
				int blue = pixels.get(offset + 2) & 0xFF;
				int alpha = pixels.get(offset + 3) & 0xFF;
				image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
			}
		}
		return image;
	}

	static BufferedImage opaqueRgbImage(int width, int height, int[] rgbPixels) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, 0xFF000000 | (rgbPixels[y * width + x] & 0xFFFFFF));
			}
		}
		return image;
	}

	static BufferedImage argbImage(int width, int height, int[] argbPixels) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setRGB(x, y, argbPixels[y * width + x]);
			}
		}
		return image;
	}

	void writeImage(String fileName, BufferedImage image) throws Exception {
		if (!ImageIO.write(image, "png", new File(directory, fileName))) {
			throw new IllegalStateException("ImageIO did not find a PNG writer");
		}
	}

	static final class StaticRangeCandidateStats {
		private int staticFaces;
		private int terrainFaces;
		private int wallFaces;
		private int roofFaces;
		private int gameObjectFaces;
		private int wallObjectFaces;
		private int overlapFaces;
		private int overlapTerrainFaces;
		private int overlapWallFaces;
		private int overlapRoofFaces;
		private int overlapGameObjectFaces;
		private int overlapWallObjectFaces;
		private int overlapWorldSpriteCommands;

		private void recordFace(Renderer3DModelKind kind, boolean overlap) {
			staticFaces++;
			if (overlap) {
				overlapFaces++;
			}
			if (kind == Renderer3DModelKind.TERRAIN) {
				terrainFaces++;
				if (overlap) {
					overlapTerrainFaces++;
				}
			} else if (kind == Renderer3DModelKind.WALL) {
				wallFaces++;
				if (overlap) {
					overlapWallFaces++;
				}
			} else if (kind == Renderer3DModelKind.ROOF) {
				roofFaces++;
				if (overlap) {
					overlapRoofFaces++;
				}
			} else if (kind == Renderer3DModelKind.GAME_OBJECT) {
				gameObjectFaces++;
				if (overlap) {
					overlapGameObjectFaces++;
				}
			} else if (kind == Renderer3DModelKind.WALL_OBJECT) {
				wallObjectFaces++;
				if (overlap) {
					overlapWallObjectFaces++;
				}
			}
		}
	}

	static boolean isRendererSnapshotOwned(
		Frame frame,
		Renderer2DFrame.SpriteCommand command) {
		if (frame == null
			|| frame.renderer3DFrame == null
			|| command == null) {
			return false;
		}
		Renderer3DFrame.WorldSpriteSnapshot snapshot =
			frame.renderer3DFrame.getWorldSpriteSnapshot(
				command.getSceneSpriteAnchorIndex());
		return snapshot != null && snapshot.ownsLayer(command);
	}

	static boolean isLegacyEntitySpriteId(int legacySpriteId) {
		return (legacySpriteId >= 5000 && legacySpriteId < 20000)
			|| (legacySpriteId >= 20000 && legacySpriteId < 40000);
	}

	static String worldSpriteKind(Renderer2DFrame.SpriteCommand command) {
		if (command == null) {
			return "unknown";
		}
		int legacySpriteId = command.getLegacySpriteId();
		if (isLegacyEntitySpriteId(legacySpriteId)) {
			return "entity";
		}
		if (legacySpriteId >= 40000 && legacySpriteId < 50000) {
			return "ground-item";
		}
		return "unknown";
	}

	static String emptyIfUnset(int value, int unsetValue) {
		return value == unsetValue ? "" : String.valueOf(value);
	}

	static String bounds(int minX, int minY, int maxX, int maxY) {
		if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE) {
			return "";
		}
		return minX + "," + minY + "-" + maxX + "," + maxY;
	}

	static String bounds(int[] bounds) {
		if (bounds == null || bounds.length < 4) {
			return "";
		}
		return bounds(bounds[0], bounds[1], bounds[2], bounds[3]);
	}

	static String percent(int numerator, int denominator) {
		if (denominator <= 0) {
			return "";
		}
		return String.format(Locale.US, "%.1f", numerator * 100.0 / denominator);
	}

	static String safeTsv(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}
}
