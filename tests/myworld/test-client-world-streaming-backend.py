#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
WORLD_STREAM_MANAGER = ROOT / "Client_Base/src/orsc/graphics/three/WorldStreamManager.java"
RENDERER_3D_FRAME = ROOT / "Client_Base/src/orsc/graphics/three/Renderer3DFrame.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/renderer-v2-plan.md"


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        raise AssertionError(message)


def main() -> None:
    world = WORLD.read_text(encoding="utf-8")
    manager = WORLD_STREAM_MANAGER.read_text(encoding="utf-8")
    renderer_3d_frame = RENDERER_3D_FRAME.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require(plan, "### Phase 5: Chunked World Streaming Backend",
            "Phase 5 should be framed as the chunked world-streaming backend")
    require(plan, "requested, sector decoded, CPU chunk built, GPU uploaded, presentable",
            "Plan should define the long-term chunk lifecycle")
    require(plan, "persistent GPU chunks uploaded",
            "Plan should include persistent OpenGL chunk rendering")
    require(plan, "predictive preloading from the local walk queue/camera direction",
            "Plan should include predictive preloading")

    require(manager, 'SPOILED_MILK_WORLD_STREAM_TELEMETRY',
            "World streaming telemetry should be opt-in by env var")
    require(manager, "private enum ChunkState",
            "World streaming should track chunk lifecycle state")
    for state in [
        "REQUESTED",
        "DECODING",
        "DECODED",
        "CPU_BUILT",
        "GPU_UPLOADED",
        "PRESENTABLE",
        "ACTIVE",
        "STALE",
    ]:
        require(manager, state, f"Chunk lifecycle should include {state}")
    require(manager, "void markWindowRequested(",
            "World stream manager should track preload-window requests")
    require(manager, "void markDecoding(",
            "World stream manager should track sector decode starts")
    require(manager, "void markDecoded(",
            "World stream manager should track sector decode completion")
    require(manager, "void markCpuBuilt(",
            "World stream manager should track CPU terrain-window builds")
    require(manager, "void markCpuCacheHit(",
            "World stream manager should track CPU terrain-window cache hits")
    require(manager, "void markTerrainInputBuilt(",
            "World stream manager should track terrain model-input builds")
    require(manager, "void markTerrainInputCacheHit(",
            "World stream manager should track terrain model-input cache hits")
    require(manager, "void markWallInputBuilt(",
            "World stream manager should track wall model-input builds")
    require(manager, "void markWallInputCacheHit(",
            "World stream manager should track wall model-input cache hits")
    require(manager, "void markRoofInputBuilt(",
            "World stream manager should track roof model-input builds")
    require(manager, "void markRoofInputCacheHit(",
            "World stream manager should track roof model-input cache hits")
    require(manager, "void markWorldProductBuilt(",
            "World stream manager should track presentable world-model product builds")
    require(manager, "void markWorldProductCacheHit(",
            "World stream manager should track presentable world-model product cache hits")
    require(manager, "void markGpuMeshProductBuilt(",
            "World stream manager should track CPU-side GPU mesh products")
    require(manager, "ChunkState.PRESENTABLE",
            "World-model products should advance chunks to the presentable lifecycle state")
    require(manager, "record.state = ChunkState.STALE;",
            "World stream manager should retire old active chunks as stale")
    require(manager, "void recordActiveWindowLoad(",
            "World stream manager should time active terrain window rebuilds")
    require(manager, "[world-stream telemetry] activeWindows=",
            "World stream telemetry should report active-window stalls")
    for metric in [
        "terrainInputs=",
        "terrainInputHits=",
        "wallInputs=",
        "wallInputHits=",
        "roofInputs=",
        "roofInputHits=",
        "worldProducts=",
        "worldProductHits=",
        "gpuMeshProducts=",
        "gpuMeshTriangles=",
    ]:
        require(manager, metric, f"World stream telemetry should report {metric}")

    require(world, "private final WorldStreamManager worldStreamManager = new WorldStreamManager();",
            "World should own the chunk-state streaming manager")
    require(world, "this.worldStreamManager.markWindowRequested(",
            "World should record predictive preload windows")
    require(world, "this.worldStreamManager.markRequested(height, sectionX, sectionY);",
            "World should record each sector request")
    require(world, "this.worldStreamManager.markDecoding(height, sectionX, sectionY);",
            "World should record foreground/background sector decodes")
    require(world, "this.worldStreamManager.markCacheHit(height, sectionX, sectionY);",
            "World should record sector cache hits")
    require(world, "this.worldStreamManager.markCpuBuilt(",
            "World should record CPU terrain-window builds")
    require(world, "this.worldStreamManager.markCpuCacheHit(",
            "World should record CPU terrain-window cache hits")
    require(world, "this.worldStreamManager.markTerrainInputBuilt(",
            "World should record terrain model-input builds")
    require(world, "this.worldStreamManager.markTerrainInputCacheHit(",
            "World should record terrain model-input cache hits")
    require(world, "this.worldStreamManager.markWallInputBuilt(",
            "World should record wall model-input builds")
    require(world, "this.worldStreamManager.markWallInputCacheHit(",
            "World should record wall model-input cache hits")
    require(world, "this.worldStreamManager.markRoofInputBuilt(",
            "World should record roof model-input builds")
    require(world, "this.worldStreamManager.markRoofInputCacheHit(",
            "World should record roof model-input cache hits")
    require(world, "this.worldStreamManager.markWorldProductBuilt(",
            "World should record presentable world-model product builds")
    require(world, "this.worldStreamManager.markWorldProductCacheHit(",
            "World should record presentable world-model product cache hits")
    require(world, "this.worldStreamManager.markGpuMeshProductBuilt(",
            "World should record CPU-side GPU mesh products")
    require(world, "private WorldModelProduct loadWorldModelProduct(",
            "World should load presentable world-model products from a cache boundary")
    require(world, "private void queueWorldModelProductPreload(",
            "World should preload presentable world-model products predictively")
    require(world, "this.worldStreamManager.markActiveWindow(",
            "World should mark the completed active terrain window")
    require(world, "this.worldStreamManager.recordActiveWindowLoad(",
            "World should time completed active terrain-window rebuilds")
    require(world, "public Renderer3DWorldChunkFrame getRenderer3DWorldChunkFrame()",
            "World should publish the active world-space chunk frame")
    require(renderer_3d_frame, "private Renderer3DWorldChunkFrame worldChunkFrame = Renderer3DWorldChunkFrame.EMPTY;",
            "Renderer3DFrame should carry the active world-space chunk frame")
    require(renderer_3d_frame, "public Renderer3DWorldChunkFrame getWorldChunkFrame()",
            "Renderer3DFrame should expose the active world-space chunk frame")
    require(
        mudclient,
        "this.appendResidentObjectChunkFrame(\n"
        "\t\t\t\t\t\t\t\tthis.world.getRenderer3DWorldChunkFrame())",
            "Client draw should attach world-space chunk snapshots plus resident object chunks to captured frames")

    print("PASS: client world streaming backend scaffold is documented and instrumented")


if __name__ == "__main__":
    main()
