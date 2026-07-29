#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
WORLD_STREAM_MANAGER = ROOT / "Client_Base/src/orsc/graphics/three/WorldStreamManager.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/renderer-v2-plan.md"


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        raise AssertionError(message)


def main() -> None:
    world = WORLD.read_text(encoding="utf-8")
    manager = WORLD_STREAM_MANAGER.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require(world, "private static final int CPU_SECTION_WINDOW_CACHE_LIMIT = 24;",
            "World should bound persistent CPU section-window memory")
    require(world, "private final Map<String, CpuSectionWindow> cpuSectionWindowCache",
            "World should keep a persistent CPU section-window cache")
    require(world, "private final Set<String> cpuSectionWindowBuildsInFlight",
            "CPU section-window preloads should de-dupe in-flight builds")
    require(world, "queueCpuSectionWindowPreload(plane, sectionX, sectionY);",
            "Predictive terrain preload should warm the likely active CPU window")
    require(world, "queueCpuSectionWindowPreload(1, sectionX, sectionY);",
            "Ground-plane prediction should also warm upper-level CPU windows")
    require(world, "private CpuSectionWindow loadCpuSectionWindow(",
            "Foreground active loads should consume the CPU section-window cache")
    require(world, "private CpuSectionWindow buildCpuSectionWindow(",
            "World should build reusable CPU section windows from decoded sectors")
    require(world, "applyBridgeDecorations(window);",
            "CPU section windows should be bridge-normalized before caching")
    require(world, "window.copyInto(target);",
            "Live active sectors should still get isolated mutable copies")
    require(world, "if (!bridgeDecorationsApplied) {",
            "Active generation should skip duplicate bridge normalization for prepared windows")
    require(world, "this.worldStreamManager.markCpuBuilt(",
            "CPU section-window builds should be reflected in chunk telemetry")
    require(world, "this.worldStreamManager.markCpuCacheHit(",
            "CPU section-window cache hits should be reflected in chunk telemetry")
    require(world, "if (nativeLayeredTerrainSnapshot != null) {\n\t\t\treturn;",
            "Server-authoritative native windows must not launch stale speculative builds")
    require(world, "if (!key.equals(sectionWindowKey(height, sectionX, sectionY)))",
            "CPU window cache must refuse a build whose terrain scope changed")
    for key_guard in (
        "terrainModelInputKey(plane, sectionX, sectionY)",
        "wallModelInputKey(plane, sectionX, sectionY)",
        "roofModelInputKey(plane, sectionX, sectionY)",
        "worldModelProductKey(",
    ):
        require(world, f"if (!key.equals({key_guard}",
                f"model-product cache must retain its {key_guard} scope fence")

    require(manager, "private long cpuWindowBuilds;",
            "Telemetry should count CPU section-window builds")
    require(manager, "private long cpuWindowCacheHits;",
            "Telemetry should count CPU section-window cache hits")
    require(manager, "cpuWindows=",
            "Telemetry output should include CPU section-window build counts")
    require(manager, "cpuCacheHits=",
            "Telemetry output should include CPU section-window cache hits")

    require(plan, "bounded CPU section-window cache warmed by prediction",
            "Renderer plan should record the completed CPU section-window cache slice")
    require(plan, "Split terrain, wall, roof, collision, and minimap products",
            "Renderer plan should keep the deeper model-product cache step visible")

    print("PASS: client CPU section-window cache is wired")


if __name__ == "__main__":
    main()
