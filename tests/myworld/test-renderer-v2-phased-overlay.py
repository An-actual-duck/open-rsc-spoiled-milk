#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GRAPHICS = ROOT / "Client_Base/src/orsc/graphics/two/GraphicsController.java"
MUDCLIENT_GRAPHICS = ROOT / "Client_Base/src/orsc/graphics/two/MudClientGraphics.java"
RENDERER_FRAME = ROOT / "Client_Base/src/orsc/graphics/Renderer2DFrame.java"
RENDERER_TRANSFORM = ROOT / "Client_Base/src/orsc/graphics/RendererSpriteTransform.java"
SETTINGS = ROOT / "Client_Base/src/orsc/graphics/Renderer2DSettings.java"
TELEMETRY = ROOT / "Client_Base/src/orsc/RenderTelemetry.java"
APPLET = ROOT / "PC_Client/src/orsc/ORSCApplet.java"
PRESENTER = ROOT / "PC_Client/src/orsc/OpenGLFramePresenter.java"
COMPOSITE_SCENE_BUILDER = ROOT / "PC_Client/src/orsc/OpenGLCompositeSceneBuilder.java"
GLYPH_TEXTURE_CACHE = ROOT / "PC_Client/src/orsc/OpenGLGlyphTextureCache.java"
SPRITE_TEXTURE_CACHE = ROOT / "PC_Client/src/orsc/OpenGLSpriteTextureCache.java"
WORLD_SPRITE_DRAW_CONTROLLER = ROOT / "PC_Client/src/orsc/OpenGLWorldSpriteDrawController.java"
WORLD_SPRITE_RENDERER = ROOT / "PC_Client/src/orsc/OpenGLWorldSpriteRenderer.java"
SPRITE_TEXTURE_BUILDER = ROOT / "PC_Client/src/orsc/OpenGLSpriteTextureBuilder.java"
SCALED_WINDOW = ROOT / "PC_Client/src/orsc/ScaledWindow.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/renderer-v2-plan.md"


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"{label} missing expected snippet: {needle!r}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise AssertionError(f"{label} still contains forbidden snippet: {needle!r}")


def main() -> None:
    graphics = GRAPHICS.read_text(encoding="utf-8")
    mudclient_graphics = MUDCLIENT_GRAPHICS.read_text(encoding="utf-8")
    frame = RENDERER_FRAME.read_text(encoding="utf-8")
    renderer_transform = RENDERER_TRANSFORM.read_text(encoding="utf-8")
    settings = SETTINGS.read_text(encoding="utf-8")
    telemetry = TELEMETRY.read_text(encoding="utf-8")
    applet = APPLET.read_text(encoding="utf-8")
    presenter = PRESENTER.read_text(encoding="utf-8")
    composite_scene_builder = COMPOSITE_SCENE_BUILDER.read_text(encoding="utf-8")
    glyph_texture_cache = GLYPH_TEXTURE_CACHE.read_text(encoding="utf-8")
    sprite_texture_cache = SPRITE_TEXTURE_CACHE.read_text(encoding="utf-8")
    world_sprite_draw_controller = WORLD_SPRITE_DRAW_CONTROLLER.read_text(encoding="utf-8")
    world_sprite_renderer = WORLD_SPRITE_RENDERER.read_text(encoding="utf-8")
    sprite_texture_builder = SPRITE_TEXTURE_BUILDER.read_text(encoding="utf-8")
    presenter = (
        presenter
        + "\n" + composite_scene_builder
        + "\n" + glyph_texture_cache
        + "\n" + sprite_texture_cache
        + "\n" + world_sprite_draw_controller
        + "\n" + world_sprite_renderer
        + "\n" + sprite_texture_builder
    )
    scaled_window = SCALED_WINDOW.read_text(encoding="utf-8")
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    require(
        settings,
        'PHASE_AWARE_AFTER_FRAME("phased")',
        "phase-aware overlay mode",
    )
    require(
        settings,
        'NATIVE_UI_AFTER_FRAME("native-ui")',
        "native UI overlay mode",
    )
    require(
        settings,
        "public static boolean canReplayPhaseAwareSpritesAfterFrame()",
        "phase-aware overlay accessor",
    )
    require(
        settings,
        "public static boolean canReplaceUiSpritesWithOpenGL()",
        "native UI replacement accessor",
    )
    require(
        settings,
        "SPOILED_MILK_OPENGL_NATIVE_UI_REPLACE",
        "native UI destructive replacement flag",
    )
    require(
        settings,
        "&& OPENGL_NATIVE_UI_REPLACE_ENABLED",
        "native UI replacement requires explicit destructive opt-in",
    )
    require(
        settings,
        "public static boolean canPresentUiBaseFrame()",
        "pre-UI base frame accessor",
    )
    require(
        settings,
        "OPENGL_WORLD_UI_REPLAY_ENABLED",
        "OpenGL world UI replay flag",
    )
    require(
        settings,
        "public static boolean canReplayUiOverOpenGLWorld()",
        "OpenGL world UI replay accessor",
    )
    require(
        presenter,
        "boolean phaseAwareReplay = Renderer2DSettings.canReplayPhaseAwareSpritesAfterFrame();",
        "phase-aware replay branch",
    )
    require(
        presenter,
        "private void drawOpenGLWorldCompositeOverlay(",
        "OpenGL world composite replay branch",
    )
    require(
        presenter,
        "boolean replayOpenGLWorldUi = worldReplacementComposite;",
        "OpenGL world UI replay requires active replacement world frame",
    )
    require(
        presenter,
        "if (!worldReplacementComposite) {\n\t\t\trunOpenGLPass(() -> drawWorldSprites(frame));\n\t\t}",
        "OpenGL replacement composite owns scene sprite restore without duplicate diagnostic world sprites",
    )
    require(
        applet,
        "String[] rendererDebugOverlayLines = rendererDebugOverlayLines(frameImage);",
        "renderer debug overlay should be captured as data before presentation",
    )
    require(
        applet,
        "if (!ScaledWindow.isOpenGLPrimaryWindowEnabled()) {\n\t\t\tdrawRendererDebugOverlay(frameImage, rendererDebugOverlayLines);\n\t\t}",
        "OpenGL-primary debug overlay should not be painted into the software base frame",
    )
    require(
        scaled_window,
        "String[] rendererDebugOverlayLines",
        "ScaledWindow should pass renderer debug overlay lines to OpenGL",
    )
    require(
        presenter,
        "runOpenGLPass(() -> drawRendererDebugOverlay(frame));",
        "OpenGL presenter should draw renderer debug overlay as a final pass",
    )
    require(
        presenter,
        "private void drawRendererDebugOverlay(Frame frame) throws Exception",
        "OpenGL presenter should own native renderer debug overlay drawing",
    )
    require(
        presenter,
        "DEBUG_OVERLAY_TEXTURE_UPDATE_NANOS",
        "OpenGL renderer debug overlay should throttle texture rebuilds",
    )
    require(
        presenter,
        "if (debugOverlayTextureRegion == null || now >= debugOverlayNextTextureUpdateNanos)",
        "OpenGL renderer debug overlay should reuse cached texture regions between updates",
    )
    require(
        presenter,
        "debugOverlayTextureRegion = uploadedRegion;",
        "OpenGL renderer debug overlay should cache uploaded texture regions",
    )
    require(
        presenter,
        "private static DynamicTextureData buildRendererDebugOverlayTexture(String[] lines)",
        "OpenGL renderer debug overlay should be converted directly to texture data",
    )
    require(
        telemetry,
        "return RUNTIME_ENABLED || RendererDebugSettings.isOverlayEnabled();",
        "F6 performance HUD should collect telemetry without a launch-time telemetry flag",
    )
    require(
        telemetry,
        "if (RUNTIME_ENABLED\n\t\t\t\t&& (frameStats.count % REPORT_INTERVAL == 0",
        "F6 performance HUD should not enable periodic console telemetry reports",
    )
    require(
        presenter,
        "commands[spriteIndex].getPhase() != Renderer2DFrame.Phase.UI_OVERLAY",
        "OpenGL world UI replay filters sprite phase",
    )
    require(
        presenter,
        "&& (!isOpenGLCompositeDirectSpriteCommand(commands[spriteIndex])\n"
        "\t\t\t\t\t|| isOpenGLCompositeWorldSpriteCommand(commands[spriteIndex]))",
        "OpenGL world composite direct-replays overlay sprite commands without duplicating world sprites",
    )
    require(
        presenter,
        "private boolean isOpenGLCompositeDirectSpriteCommand(Renderer2DFrame.SpriteCommand command)",
        "OpenGL world composite direct-replays translucent scene sprites",
    )
    require(
        presenter,
        "command.getPhase() == Renderer2DFrame.Phase.SCENE\n\t\t\t\t&& command.getAlpha() < Renderer2DFrame.SpriteCommand.FULL_ALPHA",
        "OpenGL world composite treats translucent scene sprites as overlays",
    )
    require(
        presenter,
        "buildOpenGLCompositeDirectOverlayCoverageMask(\n\t\t\t\tcommands,\n\t\t\t\ttextCommands,\n\t\t\t\tprimitiveCommands,",
        "OpenGL world composite masks scene restore under direct sprite overlays",
    )
    require(
        presenter,
        "int commandVisiblePixels = drawVisibleSpriteCommand(frame, command, directSpriteMask);",
        "OpenGL world composite clips scene restore using direct sprite mask",
    )
    require(
        presenter,
        "private boolean[] directOverlayCoverageMask;",
        "OpenGL direct-overlay coverage owns reusable presenter scratch storage",
    )
    require(
        presenter,
        "private boolean[] acquireDirectOverlayCoverageMask(int sourceWidth, int sourceHeight)",
        "OpenGL direct-overlay coverage has an explicit scratch acquisition boundary",
    )
    require(
        presenter,
        "Arrays.fill(directOverlayCoverageMask, 0, requiredPixels, false);",
        "OpenGL direct-overlay coverage clears the active scratch range before reuse",
    )
    coverage_builder = presenter.split(
        "private boolean[] buildOpenGLCompositeDirectOverlayCoverageMask(",
        1,
    )[1].split("\n\tprivate boolean[] markOverlayRectangle(", 1)[0]
    require(
        coverage_builder,
        "acquireDirectOverlayCoverageMask(sourceWidth, sourceHeight)",
        "OpenGL direct-overlay coverage reuses presenter scratch storage",
    )
    forbid(
        coverage_builder,
        "new boolean[sourceWidth * sourceHeight]",
        "OpenGL direct-overlay coverage should not allocate a full-frame mask per frame",
    )
    require(
        presenter,
        "|| isOpenGLCompositeWorldSpriteCommand(command)\n\t\t\t\t|| isOpenGLCompositeDirectSpriteCommand(command))",
        "OpenGL replacement composite keeps world sprites out of software-visible scene restore",
    )
    require(
        presenter,
        "drawOpenGLCompositeWorldSpriteCommands(frame, compositeSceneCommands);",
        "OpenGL replacement composite submits world sprites through the typed world sprite path",
    )
    require(
        world_sprite_draw_controller,
        "depthDiagnosticTexture = delegate.buildDepthVisibleEntitySpriteTexture(\n"
        "\t\t\t\tframe,\n"
        "\t\t\t\tcommand,\n"
        "\t\t\t\tworldSpriteCommand.anchor,\n"
        "\t\t\t\tnull,\n"
        "\t\t\t\tworldSpriteCommand.anchorMatch);",
        "OpenGL replacement composite retains software depth diagnostics during GPU migration",
    )
    require(
        world_sprite_draw_controller,
        "worldSpriteCommand.anchor != null && delegate.hasActiveFrameCapture()",
        "software sprite depth masking runs only for capture",
    )
    require(
        world_sprite_draw_controller,
        "DynamicTextureData textureData = OpenGLSpriteTextureBuilder.buildDirectSpriteTexture(command);",
        "OpenGL depth-owned sprites use exact command-sized source sampling by default",
    )
    require(
        presenter,
        "drawCharacterSpriteLayers(frame, worldSpriteCommand.anchor, characterLayerCommands)",
        "OpenGL character sprites composite same-anchor clothing layers before depth draw",
    )
    require(
        presenter,
        "compositeSpriteCommandInto(command, minX, minY, width, compositePixels)",
        "OpenGL character layer composition uses legacy fixed-point sprite sampling",
    )
    require(
        presenter,
        "drawDepthOwnedWorldSpriteTextureData(\n\t\t\t\tframe,\n\t\t\t\tworldSpriteCommand,\n\t\t\t\ttextureData,",
        "OpenGL replacement composite submits anchored sprites to GPU depth",
    )
    require(
        presenter,
        "gl.glDepthMask(false);",
        "OpenGL depth-owned multipart sprites do not reject their own layers",
    )
    require(
        presenter,
        "private void putVertex(\n\t\tRenderer3DFrame frame",
        "OpenGL depth-owned sprites back-project exact command rectangles",
    )
    require(
        presenter,
        "private void drawQuad(",
        "OpenGL depth-owned sprites submit stable command-sized quads through a reusable helper",
    )
    require(
        presenter,
        "gl.glBufferData(gl.GL_ARRAY_BUFFER, uploadBuffer, gl.GL_STREAM_DRAW);",
        "OpenGL depth-owned sprite quads use a streamed VBO instead of immediate-mode submission",
    )
    require(
        presenter,
        "QUAD_INDEX_COUNT",
        "OpenGL depth-owned sprite quads use indexed triangle submission",
    )
    require(
        presenter,
        "region.getU0(),\n\t\t\tregion.getU1(),\n\t\t\tregion.getV0(),\n\t\t\tregion.getV1(),",
        "OpenGL command-sized world sprite textures are not mirrored a second time",
    )
    forbid(
        presenter,
        "WORLD_SPRITE_ATLAS_BATCHING",
        "OpenGL cached world sprite batching should remain retired after command-sized parity validation",
    )
    require(
        presenter,
        "command.getSourceStartX16()",
        "OpenGL cached world sprites use fixed-point source x sampling",
    )
    require(
        presenter,
        "command.getSourceScaleY16()",
        "OpenGL cached world sprites use fixed-point source y scale",
    )
    require(
        presenter,
        "worldSpriteDepthTextureBatches = drawn;",
        "OpenGL depth-owned sprite command telemetry",
    )
    forbid(
        presenter,
        "drawOpenGLCompositeStaticWorldRange(frame, currentOrder, nextOrder);",
        "OpenGL replacement composite must not overpaint combat sprites with broad static redraw ranges",
    )
    require(
        presenter,
        "List<OpenGLCompositeSceneCommand> sceneCommands = new ArrayList<OpenGLCompositeSceneCommand>();",
        "OpenGL replacement composite builds an ordered scene command stream",
    )
    require(
        presenter,
        "private boolean isLegacyGroundItemSpriteCommand(Renderer2DFrame.SpriteCommand command)",
        "OpenGL replacement composite recognizes ground item scene sprites",
    )
    require(
        presenter,
        "return legacySpriteId >= 40000 && legacySpriteId < 50000;",
        "OpenGL replacement composite includes ground item scene sprite range",
    )
    require(
        mudclient,
        "this.scene.drawSprite(40000 + this.groundItemID[centerX]",
        "ground items should keep their legacy scene sprite id",
    )
    require(
        mudclient_graphics,
        "withRenderer2DLegacySpriteId(index, () ->",
        "ground item draw commands should be tagged with a legacy sprite id",
    )
    require(
        mudclient_graphics,
        "this.mudClientRef.drawItemAt(index - 40000, x, y, width, height, topPixelSkew,\n\t\t\t\t\t\t\tthis.mudClientRef.getGroundItemIndexFromScenePickIndex(scenePickIndex))",
        "ground item draw commands should be tagged for OpenGL world-sprite replay with source index",
    )
    require(
        presenter,
        "captureLayer(activeFrameCapture, \"04c-ordered-static-overlays\");",
        "OpenGL frame capture isolates ordered static world overlays",
    )
    forbid(
        presenter,
        "drawTexturedOverlayRange(",
        "retired post-sprite static geometry replay",
    )
    require(
        presenter,
        "worldSpriteMatchScore(anchor, command, false)",
        "entity ordering can recover anchors by loose bounds when sprite ids differ",
    )
    require(
        presenter,
        "Renderer3DFrame.SpriteAnchor exactIdAnchor = null;",
        "entity ordering should keep exact sprite-id anchors before loose fallback",
    )
    require(
        presenter,
        "if (exactIdAnchor != null) {\n\t\t\treturn exactIdAnchor;\n\t\t}",
        "entity ordering should prefer exact sprite-id anchors before loose fallback",
    )
    require(
        presenter,
        "captureLayer(activeFrameCapture, \"04b-entity-sprites\");",
        "OpenGL frame capture isolates direct entity sprite submission",
    )
    require(
        presenter,
        "&& legacySceneSpriteCommand) {\n\t\t\ttextureData = buildVisibleSpriteTexture(frame, command, null);",
        "OpenGL world composite retries legacy scene sprite restore if overlay mask hides it completely",
    )
    require(
        presenter,
        "&& textureData.visiblePixelCount * 10 < directTextureData.visiblePixelCount) {\n\t\t\t\t\ttextureData = directTextureData;",
        "OpenGL world composite falls back to direct legacy scene sprite replay if software visibility mostly drops it",
    )
    require(
        presenter,
        "private boolean isLegacySceneSpriteCommand(Renderer2DFrame.SpriteCommand command)",
        "OpenGL world composite legacy scene sprite detector",
    )
    require(
        presenter,
        "&& !isSceneRestoreClipped(clippedSceneRestoreMask, frame.sourceWidth, screenX, screenY))",
        "OpenGL scene sprite restore leaves direct overlay pixels uncontested",
    )
    require(
        presenter,
        "private boolean[] markOverlayRectangle(",
        "OpenGL world composite masks primitive and text overlay rectangles",
    )
    require(
        mudclient,
        "private void drawHitSplatBox(int x, int y, int amount, int color) {\n\t\tthis.getSurface().setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);",
        "OpenGL world overlay phase for hit splat primitive/text replay",
    )
    require(
        mudclient,
        "if (S_SHOW_FLOATING_NAMETAGS) {\n\t\t\t\t\t\tthis.getSurface().setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);",
        "OpenGL world overlay phase for floating nametag text replay",
    )
    require(
        mudclient,
        "this.getSurface().setRenderer2DPhase(Renderer2DFrame.Phase.WORLD_OVERLAY);\n\t\ttry {\n\t\t\tif (isDragonBreathCombatEffect(character.combatEffectType))",
        "OpenGL world overlay phase for attached combat effect replay",
    )
    require(
        presenter,
        "drawVisibleSpriteCommand(frame, command)",
        "OpenGL world restores software-visible scene sprites",
    )
    require(
        presenter,
        "VISIBLE_SPRITE_RESTORE_COLOR_TOLERANCE",
        "OpenGL world scene sprite restore tolerates dark pixel drift",
    )
    require(
        presenter,
        "private boolean matchesRestoredSpriteColor(int finalRgb, int replayRgb)",
        "OpenGL world scene sprite restore color matcher",
    )
    require(
        presenter,
        "private boolean isOpenGLWorldOverlayPhase(Renderer2DFrame.Phase phase)",
        "OpenGL world overlay phase helper",
    )
    require(
        presenter,
        "private void prepareOverlayTexturedReplayState() throws Exception",
        "OpenGL overlay textured replay state reset",
    )
    require(
        presenter,
        "private void prepareOverlaySolidReplayState() throws Exception",
        "OpenGL overlay solid replay state reset",
    )
    require(
        presenter,
        "gl.glDisable(gl.GL_ALPHA_TEST);\n\t\tgl.glEnable(gl.GL_BLEND);\n\t\tgl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);",
        "OpenGL overlay replay clears inherited alpha-test state",
    )
    require(
        presenter,
        "prepareOverlayTexturedReplayState();\n\t\tOpenGLTextureRegion region = uploadSpriteTexture(command.getSprite(), command.getTransform());",
        "OpenGL direct sprite replay owns transparent overlay state",
    )
    require(
        presenter,
        "prepareOverlayTexturedReplayState();\n\t\tfor (Renderer2DFrame.TextCommand.GlyphCommand glyph : command.getGlyphs())",
        "OpenGL text replay owns transparent overlay state",
    )
    require(
        presenter,
        "private void applyPixelTextureFilter() throws Exception",
        "OpenGL pixel texture filter helper",
    )
    require(
        presenter,
        "private void applyTextTextureFilter() throws Exception",
        "OpenGL text texture filter helper",
    )
    require(
        presenter,
        "viewportPresenter.textSmoothingAlpha() > 0.0f ? gl.GL_LINEAR : gl.GL_NEAREST",
        "fractional scale smoothing is scoped to text filtering",
    )
    require(
        presenter,
        "gl.glBindTexture(gl.GL_TEXTURE_2D, region.getTextureId());\n\t\t\tapplyTextTextureFilter();",
        "OpenGL glyph replay is the only fractional-scale filtering path",
    )
    forbid(
        presenter,
        "currentPresentationSmoothingAlpha",
        "presentation-scoped smoothing state",
    )
    forbid(
        presenter,
        "gl.glColor4f(1.0f, 1.0f, 1.0f, currentTextSmoothingAlpha);",
        "whole-frame smoothing overlay",
    )
    require(
        scaled_window,
        "Frame scaling must stay pixel-crisp.",
        "Swing fallback keeps frame scaling nearest-neighbor",
    )
    require(
        presenter,
        "private static final int UNDERGROUND_WORLD_TILE_Z_THRESHOLD = 3000;",
        "OpenGL skybox underground coordinate threshold",
    )
    require(
        presenter,
        "if (shouldDrawSkyBackdrop(frame)) {\n\t\t\tdrawSkyBackdrop(frame, presentation);\n\t\t}",
        "OpenGL skybox skips underground frames",
    )
    require(
        presenter,
        "chunk.getChunkRole() != Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD",
        "OpenGL skybox underground check ignores object-only chunks",
    )
    require(
        presenter,
        "Renderer3DWorldChunkFrame.ChunkMesh primaryWorldChunk = findPrimaryWorldChunk(worldChunkFrame);",
        "OpenGL skybox underground check uses one primary world chunk",
    )
    require(
        presenter,
        "chunk.getTerrainTriangles() > 0",
        "OpenGL skybox primary chunk prefers active terrain chunk",
    )
    require(
        presenter,
        "worldUnitToTile(chunk.getOriginWorldZ()) >= UNDERGROUND_WORLD_TILE_Z_THRESHOLD",
        "OpenGL skybox underground check uses world-Z dungeon band",
    )
    require(
        presenter,
        "isUndergroundPrimaryWorldChunk(Renderer3DWorldChunkFrame.ChunkMesh chunk)",
        "OpenGL skybox underground check only treats primary non-surface chunks as underground",
    )
    require(
        presenter,
        "return Math.floorDiv(worldUnit, TILE_SIZE);",
        "OpenGL skybox underground check converts scene units to tiles",
    )
    require(
        presenter,
        "command.getPhase() == Renderer2DFrame.Phase.UI_OVERLAY && !command.requiresOrderedReplay()",
        "direct UI-phase replay guard",
    )
    require(
        presenter,
        "int commandVisiblePixels = drawVisibleSpriteCommand(frame, command);",
        "scene/world visibility fallback",
    )
    require(
        presenter,
        "logCompositeSpriteCommand(\"direct-overlay\", command, command.getWidth() * command.getHeight());\n\t\t\t\tdrawOpenGLCompositeDirectSpriteCommand(command);",
        "OpenGL replacement composite replays direct world sprite overlays",
    )
    require(
        presenter,
        "private DynamicTextureData buildDirectSpriteTexture(Renderer2DFrame.SpriteCommand command)",
        "OpenGL replacement composite direct sprite texture builder",
    )
    require(
        presenter,
        "Scene restoration has already been issued. Reuse its proven atlas",
        "overlay atlas capacity is recycled after scene restoration",
    )
    require(
        presenter,
        "OpenGLTextureRegion region = visibleSpriteTextureAtlas.upload(textureData);",
        "direct overlays use the proven dynamic sprite atlas",
    )
    require(
        presenter,
        "rgba = (replayRgb << 8) | Renderer2DFrame.SpriteCommand.FULL_ALPHA;",
        "OpenGL replacement composite keeps direct sprite texture pixels opaque",
    )
    require(
        presenter,
        "int[] directReplayedByPhase = new int[Renderer2DFrame.Phase.values().length];",
        "direct replay phase counters",
    )
    require(
        presenter,
        "RenderTelemetry.recordSpriteCaptureStats(renderer2DCaptureStats);",
        "sprite capture telemetry hook",
    )
    require(
        presenter,
        "Renderer2DSettings.isNativeUiOverlayMode()\n\t\t\t&& !Renderer2DSettings.canReplaceUiSpritesWithOpenGL()",
        "native UI capture-only safety gate",
    )
    require(
        graphics,
        "renderer2DCaptureSkippedAlpha++;",
        "alpha capture rejection counter",
    )
    require(
        graphics,
        "private boolean canCaptureAlphaRenderer2DSprite(int alpha)",
        "native UI alpha capture gate",
    )
    require(
        graphics,
        "Renderer2DSettings.canReplayUiOverOpenGLWorld()",
        "OpenGL world UI replay capture gate",
    )
    require(
        graphics,
        "private boolean canCaptureRenderer2DNativeUiCommand()",
        "native UI command capture-or-replay helper",
    )
    require(
        graphics,
        "private boolean canCaptureRenderer2DOpenGLWorldOverlayReplay()",
        "OpenGL world overlay command capture helper",
    )
    require(
        graphics,
        "private void addRenderer2DTextGlyphCommand(",
        "OpenGL text capture keeps valid glyphs when later glyphs are clipped",
    )
    require(
        graphics,
        "boolean renderer2DNativeTextReplayable = canCaptureRenderer2DNativeUiCommand();",
        "OpenGL world UI text replay capture",
    )
    require(
        graphics,
        "&& shouldReplaceRenderer2DText(recordRenderer2DTextCommand(renderer2DNativeGlyphs)))",
        "OpenGL text replacement accepts partial clipped glyph commands",
    )
    require(
        graphics,
        "createRenderer2DTextGlyphCommand(antiAliased, fontData, x, color, indexAddr, y);",
        "OpenGL text replay captures glyphs individually",
    )
    require(
        graphics,
        "boolean replacedByRenderer2D = shouldReplaceRenderer2DUiSprite(recordRenderer2DSprite(\n"
        "\t\t\t\t\tsprite,\n"
        "\t\t\t\t\tvar3,\n"
        "\t\t\t\t\tvar5,\n"
        "\t\t\t\t\tvar9,\n"
        "\t\t\t\t\tvar8,",
        "legacy alpha sprite capture",
    )
    require(
        graphics,
        "shouldReplaceRenderer2DUiSprite(recordRenderer2DScaledSprite(",
        "native UI replacement guard",
    )
    require(
        graphics,
        "private boolean shouldReplaceRenderer2DUiSprite(boolean captured, boolean requiresOrderedReplay)",
        "native UI ordered-replay replacement guard",
    )
    require(
        graphics,
        "boolean requiresOrderedReplay = mirrorX || destColumnSkewPerRow != 0;",
        "masked native UI ordered-replay detection",
    )
    require(
        graphics,
        "(!transform.canReplayOverSoftwareFrame()\n\t\t\t\t&& !canCaptureRenderer2DTransformedSpriteReplay())",
        "replacement composite captures translucent transformed sprites",
    )
    if graphics.count("&& !canCaptureRenderer2DTransformedSpriteReplay())") < 2:
        raise AssertionError("both generic and masked translucent sprite capture paths must support replacement replay")
    require(
        graphics,
        "transform.getOpacity(),\n\t\t\t\ttransform,",
        "masked UI sprite opacity is carried by the OpenGL command",
    )
    require(
        renderer_transform,
        "public int getOpacity()",
        "sprite transform exposes opacity for OpenGL replay",
    )
    forbid(
        renderer_transform,
        "((red * transformRed) >> 8) * opacity",
        "OpenGL texture conversion must not bake opacity into sprite RGB",
    )
    require(
        graphics,
        "boolean replacedByRenderer2D = shouldReplaceRenderer2DUiSprite(\n"
        "\t\t\t\t\trecordRenderer2DMaskedScaledSprite(",
        "masked native UI replacement path",
    )
    require(
        graphics,
        "maxLeft + destWidth <= this.clipLeft\n"
        "\t\t\t|| minLeft >= this.clipRight\n"
        "\t\t\t|| destY + destHeight <= this.clipTop\n"
        "\t\t\t|| destY >= this.clipBottom",
        "masked sprite capture keeps partially clipped scene sprites",
    )
    forbid(
        graphics,
        "minLeft < this.clipLeft\n"
        "\t\t\t|| destY < this.clipTop\n"
        "\t\t\t|| maxLeft + destWidth > this.clipRight\n"
        "\t\t\t|| destY + destHeight > this.clipBottom",
        "masked sprite capture must not require full destination containment",
    )
    require(
        graphics,
        "public final void captureRenderer2DUiBaseFrame()",
        "pre-UI base frame surface capture",
    )
    require(
        mudclient,
        "this.getSurface().captureRenderer2DUiBaseFrame();",
        "pre-UI base frame phase boundary",
    )
    require(
        mudclient,
        "this.getSurface().blackScreen(true);\n"
        "\t\t\tthis.getSurface().captureRenderer2DUiBaseFrame();\n"
        "\t\t\tthis.getSurface().setRenderer2DPhase(Renderer2DFrame.Phase.UI_OVERLAY);\n"
        "\t\t\tthis.panelAppearance.drawPanel();",
        "character creator native UI base capture",
    )
    require(
        applet,
        "BufferedImage renderer2DUiBaseImage = getRenderer2DUiBaseImage();",
        "pre-UI base frame applet wrapper",
    )
    require(
        scaled_window,
        "shouldUseRenderer2DUiBaseImage(renderer2DUiBaseImage, renderer2DFrame)",
        "pre-UI base frame OpenGL route",
    )
    require(
        scaled_window,
        "renderer2DFrame.getCaptureStats().isNativeUiBaseEligible()",
        "pre-UI base frame safety gate",
    )
    require(
        telemetry,
        "replacedUi=",
        "native UI replacement telemetry",
    )
    require(
        telemetry,
        "uiBase=",
        "pre-UI base frame telemetry",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] sprite capture avg:",
        "capture rejection telemetry report",
    )
    require(
        graphics,
        "recordRenderer2DTextDraw(renderer2DGlyphs);",
        "renderer-v2 text telemetry capture",
    )
    require(
        graphics,
        "shouldReplaceRenderer2DText(recordRenderer2DTextCommand(renderer2DNativeGlyphs))",
        "native UI text replacement guard",
    )
    require(
        graphics,
        "new Renderer2DFrame.TextCommand.GlyphCommand(",
        "native UI text glyph command capture",
    )
    require(
        presenter,
        "Renderer2DFrame.TextCommand[] textCommands = renderer2DFrame.getTextCommands();",
        "OpenGL text command frame handoff",
    )
    require(
        presenter,
        "Renderer2DFrame.PrimitiveCommand[] primitiveCommands = renderer2DFrame.getPrimitiveCommands();",
        "OpenGL primitive command frame handoff",
    )
    require(
        presenter,
        "private void drawTextCommand(Renderer2DFrame.TextCommand command)",
        "OpenGL text command renderer",
    )
    require(
        presenter,
        "private void drawPrimitiveCommand(Renderer2DFrame.PrimitiveCommand command)",
        "OpenGL primitive command renderer",
    )
    require(
        presenter,
        "private boolean shouldReplayNativeBaseCommands(Renderer2DFrame renderer2DFrame)",
        "OpenGL native base command replay safety gate",
    )
    require(
        presenter,
        "Renderer2DSettings.canPresentUiBaseFrame()",
        "OpenGL native base replay requires pre-UI base mode",
    )
    require(
        presenter,
        "Renderer2DFrame.RotatedSpriteCommand[] rotatedSpriteCommands = renderer2DFrame.getRotatedSpriteCommands();",
        "OpenGL rotated sprite command frame handoff",
    )
    require(
        presenter,
        "Renderer2DFrame.CircleCommand[] circleCommands = renderer2DFrame.getCircleCommands();",
        "OpenGL circle command frame handoff",
    )
    require(
        presenter,
        "private void drawRotatedSpriteCommand(Renderer2DFrame.RotatedSpriteCommand command)",
        "OpenGL rotated sprite command renderer",
    )
    require(
        presenter,
        "private void enableSourceClip(int clipLeft, int clipTop, int clipRight, int clipBottom)",
        "OpenGL source clip helper",
    )
    require(
        presenter,
        "gl.glScissor(",
        "OpenGL scissor clipping for native minimap replay",
    )
    require(
        presenter,
        "GL_SCISSOR_TEST",
        "OpenGL scissor state binding",
    )
    require(
        presenter,
        "private void drawCircleCommand(Renderer2DFrame.CircleCommand command)",
        "OpenGL circle command renderer",
    )
    require(
        presenter,
        "RendererTextureData.fromOpaqueSprite(sprite)",
        "OpenGL opaque minimap texture upload",
    )
    require(
        presenter,
        "final class OpenGLGlyphTextureCache",
        "OpenGL glyph atlas cache",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] text capture avg:",
        "text capture telemetry report",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] primitive capture avg:",
        "primitive capture telemetry report",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] native UI base avg:",
        "native UI base blocker telemetry report",
    )
    require(
        telemetry,
        "circle=\" + formatCount(circleDrawStats.average())",
        "native UI circle telemetry report",
    )
    require(
        telemetry,
        "replacedUi=\" + formatCount(textCaptureReplacedUiStats.average())",
        "native text replacement telemetry report",
    )
    require(
        applet,
        "sprite cap/static/vis ",
        "performance overlay sprite capture/replay line",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] sprite phases avg: scene=",
        "renderer telemetry sprite phase summary line",
    )
    require(
        telemetry,
        "legacySceneSpriteRestoreCommandAverage",
        "telemetry legacy scene sprite restore summary",
    )
    forbid(
        applet,
        "recent gl phases b/w/ws/o/db/s ",
        "bounded performance overlay verbose OpenGL phase timing line",
    )
    require(
        applet,
        "String tuningKeysLine = ClientHotkeySettings.showDeveloperFunctionKeyHints()",
        "bounded performance overlay release-aware renderer tuning line",
    )
    require(
        telemetry,
        "static void recordOpenGLFramePhases(",
        "OpenGL phase timing telemetry recorder",
    )
    require(
        presenter,
        "RenderTelemetry.recordOpenGLFramePhases(",
        "OpenGL presenter should record phase timings",
    )
    require(
        applet,
        "world split chunk/proj/chdraw ",
        "performance overlay scene phase timing line",
    )
    require(
        presenter,
        "VISIBLE_SPRITE_ATLAS_FULL",
        "visible replay atlas-full sentinel",
    )
    require(
        telemetry,
        "skipAtlasFull=",
        "atlas-full telemetry report",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] sprite direct phases avg:",
        "direct phase telemetry report",
    )
    require(
        telemetry,
        "[renderer-v2 telemetry] sprite visible phases avg:",
        "visible phase telemetry report",
    )
    require(
        graphics,
        "boolean replacedByRenderer2D = shouldReplaceRenderer2DUiSprite(recordRenderer2DScaledSprite(\n"
        "\t\t\t\t\tsprite,\n"
        "\t\t\t\t\tvar14,\n"
        "\t\t\t\t\twidth,\n"
        "\t\t\t\t\theight,\n"
        "\t\t\t\t\tvar10,\n"
        "\t\t\t\t\tvar11,\n"
        "\t\t\t\t\tscaleX,\n"
        "\t\t\t\t\tscaleY,\n"
        "\t\t\t\t\tvar7));",
        "legacy spriteClipping scaled capture",
    )
    require(
        graphics,
        "private boolean recordRenderer2DPrimitive(int x, int y, int width, int height, int color, int alpha)",
        "native UI primitive capture helper",
    )
    require(
        graphics,
        "shouldReplaceRenderer2DPrimitive(recordRenderer2DPrimitive(",
        "native UI primitive replacement guard",
    )
    require(
        graphics,
        "recordRenderer2DPrimitive(x, y, 1, 1, val, 256)",
        "native UI setPixel primitive capture",
    )
    require(
        graphics,
        "markRenderer2DNativeUiSoftwareDirty(Renderer2DNativeUiBlocker.MINIMAP);",
        "native UI base minimap blocker guard",
    )
    require(
        graphics,
        "new Renderer2DFrame.PrimitiveCommand(",
        "native UI primitive command capture",
    )
    require(
        graphics,
        "private boolean recordRenderer2DRotatedSprite(",
        "native UI rotated sprite capture helper",
    )
    require(
        graphics,
        "new Renderer2DFrame.RotatedSpriteCommand(",
        "native UI rotated sprite command capture",
    )
    require(
        graphics,
        "this.clipLeft,\n\t\t\t\tthis.clipTop,\n\t\t\t\tthis.clipRight,\n\t\t\t\tthis.clipBottom,",
        "native UI rotated sprite clip capture",
    )
    require(
        frame,
        "public int getClipLeft()",
        "rotated sprite clip left accessor",
    )
    require(
        graphics,
        "private boolean recordRenderer2DCircle(int x, int y, int radius, int color, int alpha)",
        "native UI circle capture helper",
    )
    require(
        graphics,
        "new Renderer2DFrame.CircleCommand(",
        "native UI circle command capture",
    )
    require(
        graphics,
        "markRenderer2DNativeUiSoftwareDirty(Renderer2DNativeUiBlocker.CIRCLE);",
        "native UI base circle blocker fallback",
    )
    require(
        graphics,
        "Renderer2DNativeUiBlocker",
        "native UI blocker reason enum",
    )
    require(
        plan,
        "safe|visible|phased|native-ui|geometry",
        "documented native UI overlay mode",
    )
    require(
        plan,
        "SPOILED_MILK_OPENGL_UI_BASE_FRAME=true",
        "documented pre-UI base frame diagnostic mode",
    )
    require(
        plan,
        "SPOILED_MILK_OPENGL_NATIVE_UI_REPLACE=true",
        "documented native UI destructive replacement flag",
    )

    print("PASS: renderer-v2 phased sprite overlay mode is wired")


if __name__ == "__main__":
    main()
