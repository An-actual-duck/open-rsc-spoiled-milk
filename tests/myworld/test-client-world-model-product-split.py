#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
RSMODEL = ROOT / "Client_Base/src/orsc/graphics/three/RSModel.java"
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
SCENE = ROOT / "Client_Base/src/orsc/graphics/three/Scene.java"
WORLD_CHUNK_FRAME = ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DWorldChunkFrame.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/renderer-v2-plan.md"


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        raise AssertionError(message)


def forbid(text: str, needle: str, message: str) -> None:
    if needle in text:
        raise AssertionError(message)


def main() -> None:
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    rsmodel = RSMODEL.read_text(encoding="utf-8")
    world = WORLD.read_text(encoding="utf-8")
    scene = SCENE.read_text(encoding="utf-8")
    world_chunk_frame = WORLD_CHUNK_FRAME.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require(world, "private void publishTerrainProduct(RSModel worldMod)",
            "Terrain model publication should be isolated behind a product boundary")
    require(world, "private TerrainModelInput buildTerrainModelInput(int plane, Sector[] sourceSectors)",
            "Terrain model input should be built from an explicit sector-window source")
    require(world, "private TerrainModelInput loadTerrainModelInput(int plane, int sectionX, int sectionY)",
            "Terrain model input should be loaded through the active-window cache")
    require(world, "private final Map<String, TerrainModelInput> terrainModelInputCache",
            "Terrain model input should have bounded cache storage")
    require(world, "private final Set<String> terrainModelInputBuildsInFlight",
            "Terrain model input preloads should de-dupe in-flight builds")
    require(world, "private void queueTerrainModelInputPreload(final int height, final int sectionX, final int sectionY)",
            "Predictive preload should be able to queue terrain model-input records")
    require(world, "private String terrainModelInputKey(int height, int sectionX, int sectionY)",
            "Terrain model input cache entries should be keyed by section window and plane")
    require(world, "private void emitTerrainProduct(TerrainModelInput input, RSModel worldMod)",
            "Terrain model input should replay through a single terrain product emitter")
    require(world, "private void emitTerrainVertices(TerrainModelInput input, RSModel worldMod)",
            "Terrain vertex emission should consume terrain model-input records")
    require(world, "private void emitTerrainFaceProduct(TerrainModelInput input, RSModel worldMod)",
            "Terrain face emission should consume terrain model-input records")
    require(world, "private static final class TerrainModelInput",
            "Terrain model-input records should be represented explicitly")
    require(world, "private static final class TerrainModelInputSource",
            "Terrain model-input builders should read from explicit sector-window sources")
    require(world, "private static final class TerrainVertexInput",
            "Terrain vertex input records should be represented explicitly")
    require(world, "private static final class TerrainTileFaceInput",
            "Terrain tile-face input records should be represented explicitly")
    require(world, "private static final class TerrainOverlayFaceInput",
            "Terrain overlay-face input records should be represented explicitly")
    forbid(world, "private void emitTerrainVertices(int plane, RSModel worldMod)",
           "Terrain vertex emission should no longer keep the old plane-based direct path")
    forbid(world, "private void emitTerrainFaceProduct(int plane, RSModel worldMod)",
           "Terrain face emission should no longer keep the old plane-based direct path")
    require(world, "private WallModelInput loadWallModelInput(int plane, int sectionX, int sectionY)",
            "Wall model input should be loaded through the active-window cache")
    require(world, "private WallModelInput buildWallModelInput(Sector[] sourceSectors)",
            "Wall model input should be built from an explicit sector-window source")
    require(world, "private void emitWallProduct(WallModelInput input, boolean showWallOnMinimap)",
            "Wall model input should replay through a wall product emitter")
    require(world, "segment.vertexCoords[offset]",
            "Wall product replay should use cached final wall vertices")
    require(world, "segment.frontTexture, segment.backTexture",
            "Wall product replay should use cached wall material ids")
    forbid(world, "insertWallIntoModel(segment",
           "Wall product replay should not depend on insertWallIntoModel or live tileElevationCache")
    require(world, "private void publishWallProduct(int plane)",
            "Wall model publication should be isolated behind a product boundary")
    require(world, "private final Map<String, WallModelInput> wallModelInputCache",
            "Wall model input should have bounded cache storage")
    require(world, "private final Set<String> wallModelInputBuildsInFlight",
            "Wall model input preloads should de-dupe in-flight builds")
    require(world, "private void queueWallModelInputPreload(final int height, final int sectionX, final int sectionY)",
            "Predictive preload should be able to queue wall model-input records")
    require(world, "private static final class WallModelInput",
            "Wall model-input records should be represented explicitly")
    require(world, "private static final class WallSegmentInput",
            "Wall segment input records should be represented explicitly")
    require(world, "private RoofModelInput loadRoofModelInput(int plane, int sectionX, int sectionY)",
            "Roof model input should be loaded through the active-window cache")
    require(world, "private RoofModelInput buildRoofModelInput(Sector[] sourceSectors)",
            "Roof model input should be built from an explicit sector-window source")
    require(
        world,
        "private RoofElevationWorkspace prepareRoofElevationProduct(\n"
        "\t\tTerrainModelInputSource source,\n"
        "\t\tint[][] structuralBaseElevations)",
        "Roof elevation preparation should accept an explicit structural base",
    )
    require(world, "private List<RoofFaceInput> collectRoofFaceInputs(TerrainModelInputSource source, RoofElevationWorkspace elevations)",
            "Roof face input collection should consume source-window data and the roof elevation workspace")
    require(world, "private void emitRoofFaceProduct(RoofModelInput input)",
            "Roof model input should replay through a roof product emitter")
    require(world, "private void publishRoofProduct(int plane, RoofModelInput input)",
            "Roof model publication should be isolated behind a product boundary")
    require(world, "private final Map<String, RoofModelInput> roofModelInputCache",
            "Roof model input should have bounded cache storage")
    require(world, "private final Set<String> roofModelInputBuildsInFlight",
            "Roof model input preloads should de-dupe in-flight builds")
    require(world, "private void queueRoofModelInputPreload(final int height, final int sectionX, final int sectionY)",
            "Predictive preload should be able to queue roof model-input records")
    require(world, "private static final class RoofElevationWorkspace",
            "Roof elevation mutation should be represented by an explicit workspace")
    require(
        world,
        "private static RoofElevationWorkspace fromSource(\n"
        "\t\t\tTerrainModelInputSource source,\n"
        "\t\t\tint[][] structuralBaseElevations)",
        "Roof elevation workspaces should support floor-local or cumulative terrain data",
    )
    require(world, "private static final class RoofModelInput",
            "Roof model-input records should be represented explicitly")
    require(world, "private static final class RoofFaceInput",
            "Roof face input records should be represented explicitly")

    require(world, "private final Map<String, WorldModelProduct> worldModelProductCache",
            "Presentable world-model products should have bounded cache storage")
    require(world, "private final Set<String> worldModelProductBuildsInFlight",
            "Presentable world-model product preloads should de-dupe in-flight builds")
    require(world, "private WorldModelProduct loadWorldModelProduct(",
            "Landscape generation should load combined world-model products from a cache boundary")
    require(world, "private void queueWorldModelProductPreload(",
            "Predictive preload should warm presentable world-model products")
    require(
        world,
        "boolean stackedUpperPlane) {\n"
        "\t\treturn sectionWindowKey(height, sectionX, sectionY)",
        "World-model products should distinguish floor-local and stacked upper geometry",
    )
    require(
        world,
        'stackedUpperPlane ? "-stacked-upper" : "-floor-local"',
        "World-model cache keys should prevent flat and stacked products from colliding",
    )
    require(
        world,
        "buildWallModelInput(stackedWindow.sectors, structuralBaseElevations)",
        "Stacked wall inputs should continue from the completed lower floor",
    )
    require(
        world,
        "buildRoofModelInput(stackedWindow.sectors, structuralBaseElevations)",
        "Stacked roof inputs should continue from the completed lower floor",
    )
    require(world, "private static final class WorldModelProduct",
            "Terrain, wall, and roof model inputs should be grouped into a presentable product")
    require(world, "private boolean hasTerrainIfNeeded(boolean includeTerrain)",
            "Overlay-only products should be upgradeable when active terrain is later required")
    require(world, "private final WorldGpuChunkMesh gpuChunkMesh;",
            "Presentable world-model products should carry a GPU-ready chunk mesh")
    require(world, "private static WorldGpuChunkMesh buildWorldGpuChunkMesh(",
            "World-model products should build an active-window-local GPU-ready mesh")
    require(world, "private static final class WorldGpuChunkMesh",
            "GPU-ready chunk mesh records should be represented explicitly")
    require(world, "private static final class WorldGpuChunkMeshBuilder",
            "GPU-ready chunk mesh records should be built through an explicit builder")
    require(world, "private final int originWorldX;",
            "GPU-ready chunk meshes should carry their world-space origin")
    require(world, "private final int[] vertexCoords;",
            "GPU-ready chunk meshes should carry vertex coordinates")
    require(world, "private final float[] vertexTextureU;",
            "GPU-ready chunk meshes should carry vertex texture-u coordinates")
    require(world, "private final float[] vertexTextureV;",
            "GPU-ready chunk meshes should carry vertex texture-v coordinates")
    require(world, "private final int[] vertexLights;",
            "GPU-ready chunk meshes should carry raw legacy vertex light values")
    require(world, "private final int[] indices;",
            "GPU-ready chunk meshes should carry index buffers")
    require(world, "private final Renderer3DModelKind[] triangleModelKinds;",
            "GPU-ready chunk meshes should carry per-triangle model kinds")
    require(world, "private final long signature;",
            "GPU-ready chunk meshes should expose deterministic upload signatures")
    require(world, "private volatile Renderer3DWorldChunkFrame renderer3DWorldChunkFrame =",
            "World should cache the active renderer-v2 chunk snapshot")
    require(world, "public Renderer3DWorldChunkFrame getRenderer3DWorldChunkFrame()",
            "World should expose the active renderer-v2 chunk snapshot")
    require(world, "private Renderer3DWorldChunkFrame buildRenderer3DWorldChunkFrame(int plane, int sectionX, int sectionY)",
            "World should build renderer-v2 chunk snapshots from cached products")
    require(world, "product.gpuChunkMesh.toRenderer3DWorldChunkMesh()",
            "World should publish GPU-ready products through immutable renderer-v2 chunk meshes")
    require(world, "private void addFace(\n\t\t\tRenderer3DModelKind kind,",
            "GPU-ready chunk meshes should be built from faces before triangulation")
    require(world, "populateTextureCoordinates(texture, faceVertexCoords, textureU, textureV);",
            "GPU-ready chunk meshes should derive UV coordinates from each original face")
    require(world, "vertexLights.add(Integer.valueOf(vertexLight(faceVertexLights, vertex)));",
            "GPU-ready chunk meshes should preserve per-vertex light inputs")
    require(world, "Float.floatToIntBits(value)",
            "GPU-ready chunk mesh upload signatures should include texture coordinate bits")
    require(world, "this.renderer3DWorldChunkFrame = this.buildRenderer3DWorldChunkFrame(plane, x, z);",
            "Active section loads should refresh the renderer-v2 chunk snapshot")
    require(world, "private Renderer3DWorldChunkFrame.ChunkMesh toRenderer3DWorldChunkMesh()",
            "GPU-ready chunk meshes should convert to a frame-safe snapshot")
    require(rsmodel, "public static Renderer3DWorldChunkFrame.ChunkMesh buildRenderer3DObjectChunkMesh(",
            "Materialized object models should be exportable as resident chunk meshes")
    require(rsmodel, "public int getRenderer3DTransformVersion()",
            "Materialized object models should expose a transform version for resident chunk caching")
    require(rsmodel, "this.renderer3DTransformVersion++;",
            "Materialized object transform changes should invalidate resident object chunks")
    require(rsmodel, "model.resetTransformCache(7972);",
            "Resident object mesh export should consume transformed world-space model vertices")
    require(rsmodel, "if (visibleFront != visibleBack) {",
            "One-sided resident object materials should emit both culling sides for parity")
    require(rsmodel, "int visibleMaterial = visibleFront ? frontMaterial : backMaterial;",
            "Resident object one-sided fallback should preserve the visible legacy material")
    require(rsmodel, "kind != Renderer3DModelKind.GAME_OBJECT && kind != Renderer3DModelKind.WALL_OBJECT",
            "Resident object mesh export should only include scenery and wall-object models")
    require(mudclient, "private Renderer3DWorldChunkFrame appendResidentObjectChunkFrame(Renderer3DWorldChunkFrame baseFrame)",
            "Client frames should append a resident object chunk to the world chunk frame")
    require(mudclient, "Renderer3DSettings.canUseResidentObjectChunks()",
            "Client should only append resident object chunks when the experimental resident-object mode is enabled")
    require(mudclient, "clearResidentObjectChunkCache();\n\t\t\treturn baseFrame;",
            "Client should clear resident object cache when resident-object chunks are disabled")
    require(mudclient, "private List<ResidentObjectChunkInput> buildResidentObjectChunkInputs(Renderer3DWorldChunkFrame baseFrame)",
            "Client should collect spatial resident object chunks from materialized instance arrays")
    require(mudclient, "RESIDENT_OBJECT_CHUNK_TILE_SIZE",
            "Resident object chunks should be spatially subdivided")
    require(mudclient, "private Renderer3DWorldChunkFrame.ChunkMesh buildResidentObjectChunkMesh(ResidentObjectChunkInput input)",
            "Client should build resident object chunks from cached object chunk inputs")
    require(mudclient, "cached != null && cached.cacheKey == input.cacheKey",
            "Client should reuse resident object chunks when object transforms are unchanged")
    require(mudclient, "mixResidentObjectChunkCacheKey(this.cacheKey, tileX)",
            "Resident object cache keys should include tile placement")
    require(mudclient, "mixResidentObjectChunkCacheKey(this.cacheKey, objectId)",
            "Resident object cache keys should include object identity")
    require(mudclient, "mixResidentObjectChunkCacheKey(this.cacheKey, direction)",
            "Resident object cache keys should include object direction")
    require(mudclient, "model.getRenderer3DTransformVersion()",
            "Resident object cache keys should include model transform versions")
    require(mudclient, "this.isGameObjectInstanceMaterialized(i) && this.getGameObjectInstanceModel(i) != null",
            "Resident object chunk should only include materialized game-object instances")
    require(mudclient, "this.isWallObjectInstanceMaterialized(i) && this.getWallObjectInstanceModel(i) != null",
            "Resident object chunk should only include materialized wall-object instances")
    require(mudclient, "applyRenderer3DMaterialMetadata(\n\t\t\tRenderer3DModelKind.GAME_OBJECT,\n\t\t\tobjectId,\n\t\t\tmodel);",
            "Game-object material metadata should be attached once when assigning the live model")
    require(mudclient, "applyRenderer3DMaterialMetadata(\n\t\t\tRenderer3DModelKind.WALL_OBJECT,\n\t\t\ttype,\n\t\t\tmodel);",
            "Wall-object material metadata should be attached once when constructing the live model")
    forbid(mudclient, "applyRenderer3DMaterialMetadata(kind, objectId, model);",
           "Resident object enumeration should not repeat immutable material classification every frame")
    require(mudclient, "this.appendResidentObjectChunkFrame(this.world.getRenderer3DWorldChunkFrame())",
            "Renderer frames should receive resident world chunks plus dynamic object chunks")
    require(world_chunk_frame, "public final class Renderer3DWorldChunkFrame",
            "Renderer-v2 chunk snapshots should have a dedicated public frame type")
    require(world_chunk_frame, "public static final class ChunkMesh",
            "Renderer-v2 chunk snapshots should expose individual chunk meshes")
    require(world_chunk_frame, "public boolean isObjectChunk()",
            "Renderer-v2 chunks should explicitly identify dynamic object chunks")
    require(world_chunk_frame, "public int getVertexCoord(int coordIndex)",
            "Renderer-v2 chunk snapshots should expose immutable indexed vertex reads")
    require(world_chunk_frame, "public int[] copyVertexCoords()",
            "Renderer-v2 chunk snapshots should provide defensive copies for bulk reads")
    require(world_chunk_frame, "public float getVertexTextureU(int vertexIndex)",
            "Renderer-v2 chunk snapshots should expose immutable texture-u reads")
    require(world_chunk_frame, "public float getVertexTextureV(int vertexIndex)",
            "Renderer-v2 chunk snapshots should expose immutable texture-v reads")
    require(world_chunk_frame, "public int getVertexLight(int vertexIndex)",
            "Renderer-v2 chunk snapshots should expose immutable vertex-light reads")
    require(world_chunk_frame, "public int[] copyVertexLights()",
            "Renderer-v2 chunk snapshots should provide defensive light copies")
    require(world, "Renderer3DModelKind.TERRAIN",
            "GPU-ready chunk meshes should classify terrain triangles")
    require(world, "Renderer3DModelKind.WALL",
            "GPU-ready chunk meshes should classify wall triangles")
    require(world, "Renderer3DModelKind.ROOF",
            "GPU-ready chunk meshes should classify roof triangles")
    require(world, "int drawOriginX = 0;",
            "Resident chunk draw vertices should remain in active-window local X")
    require(world, "int drawOriginZ = 0;",
            "Resident chunk draw vertices should remain in active-window local Z")
    require(world, "drawOriginX + va.x",
            "Terrain GPU mesh vertices should use active-window-local X")
    require(world, "drawOriginZ + va.z",
            "Terrain GPU mesh vertices should use active-window-local Z")
    require(world, "triangleTextures.add(Integer.valueOf(texture));",
            "GPU-ready chunk meshes should preserve per-triangle material ids")
    require(world, "triangleFallbackColors.add(Integer.valueOf(resolveFallbackColor(texture, fallbackColor)));",
            "GPU-ready chunk meshes should preserve resolved per-triangle fallback colors")
    require(world, "private int resourceToRgb(int resource)",
            "GPU-ready chunk meshes should resolve synthetic resource colors for flat fallback batches")

    require(world, "CpuSectionWindow window = loadCpuSectionWindow(plane, sectionX, sectionY);",
            "Terrain model input cache should build from the CPU section-window cache")
    require(world, "TerrainModelInput built = buildTerrainModelInput(plane, window.sectors);",
            "Terrain model input should be built from cached CPU window sectors")
    require(
        world,
        "plane, sectionX, sectionY, true, !Config.C_HIDE_ROOFS, false);",
        "Predictive terrain preload should warm the active floor-local product",
    )
    require(
        world,
        "1, sectionX, sectionY, false, !Config.C_HIDE_ROOFS, true);",
        "Predictive terrain preload should warm stacked upper-floor products",
    )
    require(world, "boolean includeRoofGeometry = !Config.C_HIDE_ROOFS;",
            "Landscape generation should make roof geometry inclusion explicit")
    require(world, "WorldModelProduct worldProduct = this.loadWorldModelProduct(",
            "Landscape generation should load the combined world-model product once")
    require(world, "includeRoofGeometry,\n\t\t\t\t\t!showWallOnMinimap && plane > 0);",
            "Landscape generation should key products by roof visibility and story role")
    require(world, "WorldGpuChunkMesh gpuChunkMesh = buildWorldGpuChunkMesh(",
            "World-model product loads should build the GPU-ready chunk mesh from cached inputs")
    require(world, "terrainInput = applyWallEndpointShadows(terrainInput, wallInput);",
            "World-model products should apply wall endpoint terrain lighting before GPU mesh upload")
    require(world, "private static TerrainModelInput applyWallEndpointShadows(",
            "Wall endpoint terrain lighting should be isolated behind an explicit product step")
    require(world, "applyWallSegmentTerrainLight(segment);",
            "Legacy wall product replay should preserve terrain vertex light mutations")
    require(world, "built.gpuChunkMesh.getTriangleCount()",
            "World-model product telemetry should report GPU-ready mesh size")
    require(world, "this.emitTerrainProduct(worldProduct.terrainInput, worldMod);",
            "Landscape generation should replay terrain from the combined product")
    require(world, "this.publishTerrainProduct(worldMod);",
            "Landscape generation should call the terrain product boundary")
    require(world, "private void publishTerrainAuthorityProduct(TerrainModelInput input)",
            "Native terrain authority should publish collision, minimap, and elevation without legacy geometry")
    require(world, "this.shouldUseNativeTerrainAuthorityOnly()",
            "Landscape generation should explicitly gate the native authority-only terrain path")
    require(world, "Renderer3DSettings.canSkipProjectedWorldCapture()",
            "Native authority-only terrain must require trusted resident replacement")
    require(world, "&& !WorldEditorBuildSettings.isEnabled();",
            "World Builder must retain legacy terrain geometry for exact authoring picks")
    require(mudclient, "world.isNativeTerrainAuthorityOnlyActive()",
            "Native authority-only terrain must activate projected terrain picking")
    require(mudclient, "private int[] projectScreenToCurrentTerrainTile()",
            "Native projected terrain picking should converge against current terrain elevation")
    require(mudclient, "scene.projectScreenToTerrainTile(",
            "Native projected terrain picking should use the terrain-height raycast")
    require(scene, "projectScreenToTerrainTileIterative(",
            "Native projected terrain picking should retain a bounded height-aware fallback")
    require(scene, "for (int pass = 0; pass < 16; pass++)",
            "Terrain pick fallback should converge through bounded elevation sampling")
    require(scene, "world.isPresentationTerrainFaceTile(",
            "Native terrain picking should cover the radius-two presentation field")
    require(scene, "crossedTerrain(",
            "Terrain ray marching should accept either coordinate-space crossing direction")
    require(world, "public int getPresentationTerrainElevation(",
            "World should expose a read-only presentation-height sampler")
    require(mudclient, "activeGameplayTargetToward(",
            "Visual-only terrain clicks should route toward the active gameplay window")
    require(mudclient, '"Walk toward"',
            "Visual-only terrain clicks should state their bounded movement semantics")
    require(world_chunk_frame, "public final class Renderer3DWorldChunkFrame",
            "Native terrain raycast coverage should retain the resident world frame contract")
    require(mudclient, "MenuItemAction.LANDSCAPE_CAST_SPELL",
            "Projected native terrain picking should preserve ground-target spells")
    require(world, "this.emitWallProduct(worldProduct.wallInput, showWallOnMinimap);",
            "Landscape generation should replay walls from the combined product")
    require(world, "this.publishWallProduct(plane);",
            "Landscape generation should call the wall product boundary")
    require(world, "if (includeRoofGeometry) {\n\t\t\t\t\tthis.emitRoofFaceProduct(worldProduct.roofInput);",
            "Landscape generation should replay roofs only when roof geometry is enabled")
    require(world, "} else {\n\t\t\t\t\tthis.publishHiddenRoofProduct(plane, worldProduct.roofInput);",
            "Landscape generation should publish a hidden-roof elevation/grid product when roofs are disabled")

    require(world, "this.modelWallGrid[plane] = this.modelAccumulate.divideModelByGrid",
            "Wall product should still publish the legacy wall grid")
    require(world, "this.modelRoofGrid[plane] = this.modelAccumulate.divideModelByGrid",
            "Roof product should still publish the legacy roof grid")
    require(world, "private void publishHiddenRoofProduct(int plane, RoofModelInput input)",
            "Hidden-roof products should avoid publishing legacy roof model geometry")
    require(world, "this.modelLandscapeGrid = this.modelAccumulate.divideModelByGrid",
            "Terrain product should still publish the legacy terrain grid")

    require(plan, "Isolate terrain and roof publication, wall product generation",
            "Renderer plan should record the completed model-product split slice")
    require(plan, "[x] Move terrain face emission and roof face emission",
            "Renderer plan should mark terrain/roof face emission extraction complete")
    require(plan, "Convert the named product boundaries into cacheable model-input",
            "Renderer plan should keep the next model-input cache step visible")
    require(plan, "[x] Start terrain model-input records",
            "Renderer plan should record the terrain model-input record slice")
    require(plan, "[x] Add cache storage keyed by CPU section-window key and plane",
            "Renderer plan should record the terrain model-input cache slice")
    require(plan, "[x] Refactor terrain input builders to read from supplied sector",
            "Renderer plan should record terrain input source decoupling")
    require(plan, "[x] Add cached wall model-input records",
            "Renderer plan should record wall model-input caching")
    require(plan, "[x] Convert wall model-input records from replaying through",
            "Renderer plan should record final wall vertex inputs")
    require(plan, "[x] Isolate roof elevation mutation behind a roof elevation workspace",
            "Renderer plan should record roof elevation workspace isolation")
    require(plan, "[x] Add roof model-input records",
            "Renderer plan should record roof model-input records")
    require(plan, "[x] Make roof model-input records cacheable",
            "Renderer plan should record roof model-input cacheability")
    require(plan, "[x] Publish active resident chunk mesh snapshots on",
            "Renderer plan should record renderer-v2 chunk snapshot publication")
    require(plan, "[x] Promote materialized game-object and wall-object models into",
            "Renderer plan should record resident object-buffer promotion")

    print("PASS: client world model product boundaries are split")


if __name__ == "__main__":
    main()
