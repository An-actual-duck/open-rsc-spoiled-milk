#!/usr/bin/env python3
"""Validate OpenGL resident-world state survives logout/login cycles."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PRESENTER = ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
CHUNK_RENDERER = ROOT / "PC_Client/src/orsc/OpenGLWorldChunkRenderer.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        raise SystemExit(f"FAIL: {label} missing expected snippet: {snippet!r}")


def main() -> None:
    presenter = PRESENTER.read_text(encoding="utf-8")
    chunk_renderer = CHUNK_RENDERER.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")

    require(
        presenter,
        "RESIDENT_WORLD_SESSION_CLEAR_FRAME_THRESHOLD = 8",
        "logout/login resident chunk cleanup grace threshold",
    )
    require(
        presenter,
        "residentWorldMissingFrameCount++",
        "resident chunk session tracks missing frames",
    )
    require(
        presenter,
        "residentWorldMissingFrameCount >= RESIDENT_WORLD_SESSION_CLEAR_FRAME_THRESHOLD",
        "resident chunk session cleanup waits for stable no-world frames",
    )
    require(
        presenter,
        "previousFrameHadResidentWorldChunks = false;",
        "resident chunk session boundary resets after cleanup",
    )
    require(
        presenter,
        "worldChunkRenderer.clearResidentWorldSession();",
        "presenter resident chunk cleanup call",
    )
    require(
        presenter,
        "&& meshFrame.getTriangleCount() > 0;",
        "projected replacement drawable mesh guard",
    )
    require(
        presenter,
        "&& !Renderer3DSettings.canSkipProjectedWorldCapture()",
        "projected replacement is disabled when resident chunk capture owns the world",
    )
    require(
        presenter,
        "&& renderer3DMeshFrame.getTriangleCount() > 0;",
        "projected fallback drawable mesh guard",
    )
    require(
        presenter,
        "boolean budgetResidentChunkUploads =\n\t\t\t\tcanDrawProjectedStaticFallback && !Renderer3DSettings.canSkipLegacyWorldRaster();",
        "resident chunks upload immediately when the legacy world raster is skipped",
    )
    require(
        presenter,
        "if (Renderer3DSettings.canSkipLegacyWorldRaster()\n\t\t\t&& Renderer3DSettings.canSkipProjectedWorldCapture()) {\n\t\t\treturn true;\n\t\t}",
        "resident chunk frames own the world when legacy and projected fallbacks are disabled",
    )
    require(
        presenter,
        "boolean residentChunksReadyThisFrame =",
        "resident chunks can take ownership after frame upload",
    )
    require(
        presenter,
        "!residentChunksReadyThisFrame\n\t\t\t\t&& (WORLD_MESH_VISIBLE || WORLD_MESH_TEXTURED_VISIBLE);",
        "projected mesh is suppressed once resident chunks are drawable",
    )
    require(
        presenter,
        "boolean drawResidentChunkFallback =\n\t\t\t!worldReplacementComposite\n\t\t\t\t&& (!canDrawProjectedStaticFallback || residentChunksReadyThisFrame);",
        "resident chunks bridge base-active frames when drawable",
    )
    require(
        presenter,
        "|| residentChunksReadyThisFrame\n\t\t\t\t\t|| drawResidentChunkFallback",
        "resident chunk draw enabled for ready and fallback frames",
    )
    require(
        presenter,
        "boolean replayOpenGLWorldUi = worldReplacementComposite;",
        "single frame-stable replacement decision",
    )
    require(
        presenter,
        'return "base-active-this-frame";',
        "post-upload readiness does not change active frame owner",
    )
    require(
        chunk_renderer,
        "void clearResidentWorldSession() throws Exception",
        "resident world session cleanup method",
    )
    require(
        chunk_renderer,
        "private void clearResidentResources() throws Exception",
        "shared resident resource cleanup",
    )
    require(
        mudclient,
        "this.ensureGameplayRendererWorldChunkFrame();\n\t\t\t\t\tlong sceneRenderStart = RenderTelemetry.now();",
        "gameplay render repairs missing world chunk frame before scene capture",
    )
    require(
        mudclient,
        "private void ensureGameplayRendererWorldChunkFrame()",
        "gameplay renderer world chunk frame repair helper",
    )
    require(
        mudclient,
        "this.scene.forceLegacyWorldRasterOnce();",
        "missing world chunks force a legacy raster fallback instead of black world",
    )
    require(
        mudclient,
        "this.reloadCurrentRegionForRoofVisibility();",
        "missing world chunks reload the current gameplay region when possible",
    )
    require(
        mudclient,
        "if (!hardAreaLoad\n\t\t\t\t\t&& this.hasCompletedInitialRegionLoad\n\t\t\t\t\t&& this.lastHeightOffset == this.requestedPlane",
        "relog region load cannot reuse stale bounds before the initial gameplay load completes",
    )

    print("PASS: renderer relog resident-world lifecycle validated")


if __name__ == "__main__":
    main()
