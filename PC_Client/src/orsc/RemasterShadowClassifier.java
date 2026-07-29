package orsc;

import orsc.graphics.three.Renderer3DModelKind;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

final class RemasterShadowClassifier {
	static final boolean DIRECT_WALL_SEGMENT_CLIP = false;
	private static final int MAX_WORLD_WALL_SHADOW_SPAN = 512;
	private static final int MAX_WALL_OBJECT_SHADOW_SPAN = 512;
	private static final int MAX_GAME_OBJECT_SHADOW_SPAN = 1024;

	static RemasterShadowInventory inspectInventory(
		Renderer3DWorldChunkFrame worldChunkFrame) {
		return inspectInventory(
			worldChunkFrame,
			RemasterShadowRoofCoverage.from(worldChunkFrame));
	}

	static RemasterShadowInventory inspectInventory(
		Renderer3DWorldChunkFrame worldChunkFrame,
		RemasterShadowRoofCoverage roofCoverage) {
		if (worldChunkFrame == null || worldChunkFrame.getChunkCount() <= 0) {
			return RemasterShadowInventory.EMPTY;
		}

		int receiverChunks = 0;
		int receiverTriangles = 0;
		int totalCasters = 0;
		int wallCasters = 0;
		int gameObjectCasters = 0;
		int wallObjectCasters = 0;
		int outdoorOnlyCasters = 0;
		int clippingCandidates = 0;
		int roofedReceivers = 0;
		int outdoorReceivers = 0;
		int unknownReceivers = 0;
		int roofedCasters = 0;
		int outdoorCasters = 0;
		int unknownCasters = 0;
		int sunlightEligibleCasters = 0;
		int sunlightSuppressedRoofedCasters = 0;
		int sunlightSuppressedUnknownCasters = 0;
		for (Renderer3DWorldChunkFrame.ChunkMesh chunk : worldChunkFrame.getChunks()) {
			int terrainTriangles = Math.max(0, chunk.getTerrainTriangles());
			if (terrainTriangles > 0) {
				receiverChunks++;
				receiverTriangles += terrainTriangles;
			}
			int triangleLimit = Math.min(chunk.getTriangleCount(), chunk.getIndexCount() / 3);
			for (int triangle = 0; triangle < triangleLimit; triangle++) {
				if (chunk.getTriangleModelKind(triangle) != Renderer3DModelKind.TERRAIN) {
					continue;
				}
				int classification = roofClassificationForTriangle(roofCoverage, chunk, triangle);
				if (classification > 0) {
					roofedReceivers++;
				} else if (classification == 0) {
					outdoorReceivers++;
				} else {
					unknownReceivers++;
				}
			}
			int casterCount = chunk.getShadowCasterCount();
			for (int index = 0; index < casterCount; index++) {
				Renderer3DWorldChunkFrame.ShadowCaster caster = chunk.getShadowCaster(index);
				if (!isEligibleCaster(caster)) {
					continue;
				}
				totalCasters++;
				Renderer3DModelKind kind = caster.getModelKind();
				if (kind == Renderer3DModelKind.WALL) {
					wallCasters++;
				} else if (kind == Renderer3DModelKind.GAME_OBJECT) {
					gameObjectCasters++;
				} else if (kind == Renderer3DModelKind.WALL_OBJECT) {
					wallObjectCasters++;
				}
				if (caster.isOutdoorOnly()) {
					outdoorOnlyCasters++;
				}
				if (isClippingCandidate(caster)) {
					clippingCandidates++;
				}
				int classification = roofClassificationForCaster(roofCoverage, chunk, caster);
				if (classification > 0) {
					roofedCasters++;
					sunlightSuppressedRoofedCasters++;
				} else if (classification == 0) {
					outdoorCasters++;
					sunlightEligibleCasters++;
				} else {
					unknownCasters++;
					sunlightSuppressedUnknownCasters++;
				}
			}
		}
		return new RemasterShadowInventory(
			receiverChunks,
			receiverTriangles,
			totalCasters,
			wallCasters,
			gameObjectCasters,
			wallObjectCasters,
			outdoorOnlyCasters,
			clippingCandidates,
			roofedReceivers,
			outdoorReceivers,
			unknownReceivers,
			roofedCasters,
			outdoorCasters,
			unknownCasters,
			sunlightEligibleCasters,
			sunlightSuppressedRoofedCasters,
			sunlightSuppressedUnknownCasters);
	}

	static boolean isEligibleCaster(Renderer3DWorldChunkFrame.ShadowCaster caster) {
		return caster != null
			&& caster.getHeight() > 0
			&& caster.getOpacity() > 0
			&& hasPlausibleShadowSpan(caster);
	}

	private static boolean hasPlausibleShadowSpan(Renderer3DWorldChunkFrame.ShadowCaster caster) {
		Renderer3DModelKind kind = caster.getModelKind();
		int maxSpan;
		if (kind == Renderer3DModelKind.GAME_OBJECT) {
			maxSpan = MAX_GAME_OBJECT_SHADOW_SPAN;
		} else if (kind == Renderer3DModelKind.WALL_OBJECT) {
			maxSpan = MAX_WALL_OBJECT_SHADOW_SPAN;
		} else if (kind == Renderer3DModelKind.WALL) {
			maxSpan = MAX_WORLD_WALL_SHADOW_SPAN;
		} else {
			return false;
		}
		int footprintSpanX = Math.max(0, caster.getFootprintMaxX() - caster.getFootprintMinX());
		int footprintSpanZ = Math.max(0, caster.getFootprintMaxZ() - caster.getFootprintMinZ());
		int baseSpanX = Math.abs(caster.getBaseX1() - caster.getBaseX0());
		int baseSpanZ = Math.abs(caster.getBaseZ1() - caster.getBaseZ0());
		return caster.getWidth() <= maxSpan
			&& footprintSpanX <= maxSpan
			&& footprintSpanZ <= maxSpan
			&& baseSpanX <= maxSpan
			&& baseSpanZ <= maxSpan;
	}

	static boolean isClippingCandidate(Renderer3DWorldChunkFrame.ShadowCaster caster) {
		if (!isEligibleCaster(caster)) {
			return false;
		}
		Renderer3DModelKind kind = caster.getModelKind();
		return kind == Renderer3DModelKind.WALL || kind == Renderer3DModelKind.WALL_OBJECT;
	}

	static int roofClassificationForTriangle(
		RemasterShadowRoofCoverage roofCoverage,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		int triangle) {
		if (chunk == null || roofCoverage == null) {
			return -1;
		}
		int sourceIndex = triangle * 3;
		int x = 0;
		int z = 0;
		for (int corner = 0; corner < 3; corner++) {
			int vertex = chunk.getIndex(sourceIndex + corner);
			int coord = vertex * 3;
			x += chunk.getVertexCoord(coord);
			z += chunk.getVertexCoord(coord + 2);
		}
		return roofCoverage.classify(chunk.getPlane(), x / 3, z / 3);
	}

	static int roofClassificationForCaster(
		RemasterShadowRoofCoverage roofCoverage,
		Renderer3DWorldChunkFrame.ChunkMesh chunk,
		Renderer3DWorldChunkFrame.ShadowCaster caster) {
		if (chunk == null || caster == null || roofCoverage == null) {
			return -1;
		}
		if (caster.getModelKind() == Renderer3DModelKind.WALL
			|| caster.getModelKind() == Renderer3DModelKind.WALL_OBJECT) {
			return roofCoverage.classifyBoundaryCaster(
				chunk.getPlane(),
				caster.getBaseX0(),
				caster.getBaseZ0(),
				caster.getBaseX1(),
				caster.getBaseZ1());
		}
		int x = (caster.getBaseX0() + caster.getBaseX1()) / 2;
		int z = (caster.getBaseZ0() + caster.getBaseZ1()) / 2;
		return roofCoverage.classify(chunk.getPlane(), x, z);
	}
}
