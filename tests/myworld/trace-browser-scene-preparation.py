#!/usr/bin/env python3
"""Stage a loopback-only Java observer for region scene preparation."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]


def instrument(path, edits):
    java = (ROOT / 'Client_Base/src/orsc' / path).read_text()
    for anchor, replacement in edits:
        if java.count(anchor) != 1:
            raise ValueError(f'Unexpected {path} implementation near {anchor!r}')
        java = java.replace(anchor, replacement)
    return java


def client_source(trace_models=False):
    edits = [
        ('\tprivate void drawGame(int var1) {', '''    private int sceneTraceRemaining, sceneTraceSequence, sceneTraceFrame;
    private long sceneTraceOrigin, sceneTraceBeforeTerrain, sceneTraceBeforeScene;
    private long sceneTraceAfterScene, sceneTraceAfterObjects, sceneTraceAfterLatch;
    private long sceneTraceObjectInputs, sceneTraceObjectMeshes;
    private int sceneTraceHits, sceneTraceMisses;
    private boolean sceneTraceDrawing;

    private long sceneTraceMs(long value) {
        return (value - sceneTraceOrigin) / 1000000;
    }

    private void writeSceneTrace(String phase) {
        if (sceneTraceRemaining > 0) {
            System.out.println("BROWSER_SCENE_STAGE sequence=" + sceneTraceSequence
                + " phase=" + phase + " elapsedMs=" + sceneTraceMs(System.nanoTime()));
        }
    }

\tprivate void drawGame(int var1) {'''),
        ('\t\tif (clientPort != null) clientPort.beginWorldTransition(ordinaryWalking);', '''        if (ordinaryWalking) {
            sceneTraceRemaining = 8;
            sceneTraceSequence++;
            sceneTraceFrame = 0;
            sceneTraceOrigin = System.nanoTime();
            writeSceneTrace("transition");
        }
\t\tif (clientPort != null) clientPort.beginWorldTransition(ordinaryWalking);'''),
        ('\t\tthis.layeredScenePresentationLatch.updatePending(pending);', '''\t\tthis.layeredScenePresentationLatch.updatePending(pending);
        if (!pending) writeSceneTrace("scene-ready");'''),
        ('\t\t\t\t\t\t\t&& this.shouldRetainLastPresentedFrame()) {\n\t\t\t\t\t\tclientPort.draw();',
         '''\t\t\t\t\t\t\t&& this.shouldRetainLastPresentedFrame()) {
                        writeSceneTrace("held-before-preparation");
\t\t\t\t\t\tclientPort.draw();'''),
        ('long drawPreparationStart = RenderTelemetry.now();', '''long drawPreparationStart = RenderTelemetry.now();
                        sceneTraceDrawing = sceneTraceRemaining > 0;
                        long sceneTraceDrawStart = sceneTraceDrawing ? System.nanoTime() : 0L;
                        if (sceneTraceDrawing) {
                            sceneTraceObjectInputs = sceneTraceObjectMeshes = 0L;
                            sceneTraceHits = sceneTraceMisses = 0;
                            sceneTraceAfterObjects = sceneTraceAfterLatch = 0L;
                        }'''),
        ('\t\t\t\t\tthis.ensureGameplayRendererWorldChunkFrame();\n\t\t\t\t\tlong beforeSceneNanos', '''                    if (sceneTraceDrawing) sceneTraceBeforeTerrain = System.nanoTime();
\t\t\t\t\tthis.ensureGameplayRendererWorldChunkFrame();
                    if (sceneTraceDrawing) sceneTraceBeforeScene = System.nanoTime();
\t\t\t\t\tlong beforeSceneNanos'''),
        ('\t\t\t\t\tthis.scene.endScene(-113);', '''                    if (sceneTraceDrawing) this.scene.traceBrowserScenePreparation(sceneTraceSequence, sceneTraceFrame+1);
\t\t\t\t\tthis.scene.endScene(-113);
                    if (sceneTraceDrawing) sceneTraceAfterScene = System.nanoTime();'''),
        ('\t\t\t\t\t\tthis.completeLayeredSceneActivationFreshFrame(', '''                        if (sceneTraceDrawing) sceneTraceAfterObjects = System.nanoTime();
\t\t\t\t\t\tthis.completeLayeredSceneActivationFreshFrame('''),
        ('\t\t\t\t\t\t\tpresentedWorldChunkFrame);\n\t\t\t\t\t\tRenderer3DDepthFrame', '''\t\t\t\t\t\t\tpresentedWorldChunkFrame);
                        if (sceneTraceDrawing) sceneTraceAfterLatch = System.nanoTime();
\t\t\t\t\t\tRenderer3DDepthFrame'''),
        ('\t\tif (staticPresentationRebuildPending) {\n\t\t\tBoundaryLoadingDiagnostics.recordStaticPresentationBuild(', '''        if (sceneTraceDrawing) {
            sceneTraceObjectInputs = inputNanos;
            sceneTraceObjectMeshes = meshBuildNanos;
            sceneTraceHits = cacheHits;
            sceneTraceMisses = cacheMisses;
        }
\t\tif (staticPresentationRebuildPending) {
\t\t\tBoundaryLoadingDiagnostics.recordStaticPresentationBuild('''),
        ('\t\t\t\t\tRenderTelemetry.recordClientDrawPreparation(beforeSceneNanos, residentObjectsNanos,', '''                    if (sceneTraceDrawing) {
                        sceneTraceDrawing = false;
                        sceneTraceRemaining--;
                        long beforeDraw = System.nanoTime();
                        boolean retained = this.shouldRetainLastPresentedFrame();
                        System.out.println("BROWSER_SCENE_FRAME sequence=" + sceneTraceSequence
                            + " sample=" + (++sceneTraceFrame)
                            + " startMs=" + sceneTraceMs(sceneTraceDrawStart)
                            + " endMs=" + sceneTraceMs(beforeDraw)
                            + " retained=" + retained
                            + " prepMs=" + ((sceneTraceBeforeTerrain-sceneTraceDrawStart)/1000000)
                            + " terrainMs=" + ((sceneTraceBeforeScene-sceneTraceBeforeTerrain)/1000000)
                            + " sceneMs=" + ((sceneTraceAfterScene-sceneTraceBeforeScene)/1000000)
                            + " objectsMs=" + (sceneTraceAfterObjects==0?0:(sceneTraceAfterObjects-sceneTraceAfterScene)/1000000)
                            + " latchMs=" + (sceneTraceAfterLatch==0?0:(sceneTraceAfterLatch-sceneTraceAfterObjects)/1000000)
                            + " uiMs=" + ((beforeDraw-Math.max(sceneTraceAfterScene,sceneTraceAfterLatch))/1000000)
                            + " objectInputsMs=" + (sceneTraceObjectInputs/1000000)
                            + " meshMs=" + (sceneTraceObjectMeshes/1000000)
                            + " hits=" + sceneTraceHits + " misses=" + sceneTraceMisses);
                    }
\t\t\t\t\tRenderTelemetry.recordClientDrawPreparation(beforeSceneNanos, residentObjectsNanos,'''),
    ]
    if trace_models:
        edits.extend([
            ('    private boolean sceneTraceDrawing;', """    private boolean sceneTraceDrawing;
    private StringBuilder traceSceneryRows;

    private void traceSceneryBuild(ResidentObjectChunkInput input, ResidentObjectChunkBuildResult result) {
        if (traceSceneryRows == null || traceSceneryRows.length() > 4000) return;
        int cellSize = residentObjectChunkTileSize(input.chunkRole);
        int cellX = Math.floorDiv(this.midRegionBaseX + input.localOriginTileX, cellSize);
        int cellZ = Math.floorDiv(this.midRegionBaseZ + input.localOriginTileZ, cellSize);
        traceSceneryRows.append(input.chunkRole).append(':').append(input.anchor.getPlane())
            .append(':').append(cellX).append(':').append(cellZ).append(':')
            .append(Long.toHexString(input.canonicalContentKey)).append(':')
            .append(Long.toHexString(input.cacheKey)).append(':')
            .append(input.liveModelCount).append(':').append(input.presentationModelCount).append(':')
            .append(result.durationNanos/1000000d).append(';');
    }"""),
            ('                            sceneTraceHits = sceneTraceMisses = 0;', """                            sceneTraceHits = sceneTraceMisses = 0;
                            traceSceneryRows = sceneTraceFrame < 2 ? new StringBuilder() : null;"""),
            ('				meshBuildNanos += result.durationNanos;', """                traceSceneryBuild(input, result);
				meshBuildNanos += result.durationNanos;"""),
            ('			meshCpuNanos += result.durationNanos;\n			objectChunks.set(', """            traceSceneryBuild(pending.input, result);
			meshCpuNanos += result.durationNanos;
			objectChunks.set("""),
            ('+ " hits=" + sceneTraceHits + " misses=" + sceneTraceMisses);', """+ " hits=" + sceneTraceHits + " misses=" + sceneTraceMisses);
                        if (traceSceneryRows != null) {
                            System.out.println("BROWSER_SCENERY_BUILDS sequence=" + sceneTraceSequence
                                + " sample=" + sceneTraceFrame + " rows=" + traceSceneryRows);
                            traceSceneryRows = null;
                        }"""),
        ])
    return instrument('mudclient.java', edits)


def scene_source(skip_owned_faces, trace_models=False):
    edits = [
        ('\tpublic final void endScene(int var1) {', """    private int browserTraceSequence, browserTraceFrame;

    public void traceBrowserScenePreparation(int sequence, int frame) {
        browserTraceSequence = sequence;
        browserTraceFrame = frame;
    }

\tpublic final void endScene(int var1) {
        final boolean traceScene = browserTraceFrame > 0;
        long traceStart = traceScene ? System.nanoTime() : 0L;
        long traceAfterModels = 0L, traceAfterSprites = 0L, traceAfterCull = 0L;
        int traceFaces = 0;"""),
        ('\t\t\tmodelRotateNanos = RenderTelemetry.elapsedSince(modelRotateStart);',
         '            if (traceScene) traceAfterModels = System.nanoTime();\n\t\t\tmodelRotateNanos = RenderTelemetry.elapsedSince(modelRotateStart);'),
        ('\t\t\t\trecordSpriteSubmissions(geometryFrame);\n\t\t\t}\n\t\t\tthis.m_zb = 0;',
         '\t\t\t\trecordSpriteSubmissions(geometryFrame);\n\t\t\t}\n            if (traceScene) traceAfterSprites = System.nanoTime();\n\t\t\tthis.m_zb = 0;'),
        ('\t\t\tworldCullNanos = RenderTelemetry.elapsedSince(worldCullStart);',
         '            if (traceScene) traceAfterCull = System.nanoTime();\n\t\t\tworldCullNanos = RenderTelemetry.elapsedSince(worldCullStart);'),
        ('                    boolean modelMouseOutside = residentPickModel && skipProjectedWorldCapture',
         """                    // The existing per-face predicate is always true for this model
                    // when the renderer owns it and it cannot have a projected pick.
                    if (earlyResidentFaceCull && residentPickModel && skipProjectedWorldCapture
                        && !forceLegacyWorldRaster && (!this.m_K || !this.interactionOnlyPickActive
                            && (var2.m_db || var2.m_zb == null || var2.facePickIndex == null))) {
                        if (traceScene) traceFaces += var2.faceHead;
                        """ + ('continue;' if skip_owned_faces else '') + """
                    }
                    boolean modelMouseOutside = residentPickModel && skipProjectedWorldCapture"""),
        ("""\t\t\tthrow GenUtil.makeThrowable(var22, "lb.P(" + var1 + ')');\n\t\t}\n\t}""",
         """\t\t\tthrow GenUtil.makeThrowable(var22, "lb.P(" + var1 + ')');
\t\t} finally {
            if (traceScene) {
                long now = System.nanoTime();
                System.out.println("BROWSER_SCENE_WORK sequence=" + browserTraceSequence + " sample=" + browserTraceFrame
                    + " modelPhaseMs=" + ((traceAfterModels-traceStart)/1000000)
                    + " spriteSubmitMs=" + ((traceAfterSprites-traceAfterModels)/1000000)
                    + " cullMs=" + ((traceAfterCull-traceAfterSprites)/1000000)
                    + " restMs=" + ((now-traceAfterCull)/1000000)
                    + " eligibleFaces=" + traceFaces);
                browserTraceFrame = 0;
            }
\t\t}
\t}"""),
    ]
    if trace_models:
        edits.extend([
            ('			long modelRotateStart = RenderTelemetry.now();', """            if (traceScene) {
                RSModel.beginFinalizationTrace();
                BrowserBatchedModelKernel.beginProjectionTrace();
            }
			long modelRotateStart = RenderTelemetry.now();"""),
            ('            if (traceScene) traceAfterModels = System.nanoTime();', """            if (traceScene) {
                traceAfterModels = System.nanoTime();
                System.out.println("BROWSER_MODEL_WORK sequence=" + browserTraceSequence
                    + " sample=" + browserTraceFrame + RSModel.finishFinalizationTrace()
                    + BrowserBatchedModelKernel.finishProjectionTrace());
            }"""),
        ])
    return instrument('graphics/three/Scene.java', edits)


def model_source(skip_diffuse_zero_fill):
    edits = [
        ('\tprivate void resetTransformCache(int var1) {', '''    static final class FinalizationTrace {
        final Thread owner = Thread.currentThread();
        int dirty, normals, diffuse, projections, rotatedVertices;
        long dirtyNanos, normalNanos, diffuseNanos, projectionNanos;
    }

    private static FinalizationTrace finalizationTrace;

    static void beginFinalizationTrace() {
        finalizationTrace = new FinalizationTrace();
    }

    static String finishFinalizationTrace() {
        FinalizationTrace trace = finalizationTrace;
        finalizationTrace = null;
        return " dirty=" + trace.dirty + " dirtyMs=" + (trace.dirtyNanos/1000000d)
            + " normalCalls=" + trace.normals + " normalsMs=" + (trace.normalNanos/1000000d)
            + " diffuseCalls=" + trace.diffuse + " diffuseMs=" + (trace.diffuseNanos/1000000d)
            + " literalProjectionCalls=" + trace.projections + " literalProjectionMs=" + (trace.projectionNanos/1000000d)
            + " literalProjectionVertices=" + trace.rotatedVertices;
    }

    private static FinalizationTrace currentFinalizationTrace() {
        FinalizationTrace trace = finalizationTrace;
        return trace != null && trace.owner == Thread.currentThread() ? trace : null;
    }

\tprivate void resetTransformCache(int var1) {'''),
        ('\t\t\t} else if (this.m_Yb == 1) {\n\t\t\t\tthis.m_Yb = 0;', '''\t\t\t} else if (this.m_Yb == 1) {
                FinalizationTrace trace = currentFinalizationTrace();
                long dirtyStart = trace == null ? 0L : System.nanoTime();
\t\t\t\tthis.m_Yb = 0;'''),
        ('\t\t\t\tthis.calculateBoundingBoxes(999999);\n\t\t\t\tthis.computeNormals((byte) 14);', '''\t\t\t\tthis.calculateBoundingBoxes(999999);
                long normalsStart = trace == null ? 0L : System.nanoTime();
\t\t\t\tthis.computeNormals((byte) 14);
                if (trace != null) {
                    long end = System.nanoTime();
                    trace.dirty++;
                    trace.normals++;
                    trace.dirtyNanos += end - dirtyStart;
                    trace.normalNanos += end - normalsStart;
                }'''),
        ('\t\t\t\t\tthis.computeDiffuse(var1 ^ -85);', '''                    FinalizationTrace trace = currentFinalizationTrace();
                    long diffuseStart = trace == null ? 0L : System.nanoTime();
\t\t\t\t\tthis.computeDiffuse(var1 ^ -85);
                    if (trace != null) {
                        trace.diffuseNanos += System.nanoTime() - diffuseStart;
                        trace.diffuse++;
                    }'''),
        ('\t\t\t\tthis.m_dc = true;\n\t\t\t\tint xy_xy = 0;', '''\t\t\t\tthis.m_dc = true;
                FinalizationTrace trace = currentFinalizationTrace();
                long projectStart = trace == null ? 0L : System.nanoTime();
\t\t\t\tint xy_xy = 0;'''),
        ('\t\t\t\t\tthis.vertZRot[var17] = zO;\n\t\t\t\t}\n\n\t\t\t} else {', '''\t\t\t\t\tthis.vertZRot[var17] = zO;
\t\t\t\t}
                if (trace != null) {
                    trace.projections++;
                    trace.rotatedVertices += this.vertHead;
                    trace.projectionNanos += System.nanoTime() - projectStart;
                }

\t\t\t} else {'''),
    ]
    if skip_diffuse_zero_fill:
        edits.append((
            """				int var7;
				for (var7 = 0; this.vertHead > var7; ++var7) {
					tmpXNorm[var7] = 0;
					tmpYNorm[var7] = 0;
					tmpZNorm[var7] = 0;
					faceCount[var7] = 0;
				}

				var7 = -16""",
            "\t\t\t\tint var7 = -16",
        ))
    return instrument('graphics/three/RSModel.java', edits)


def kernel_source():
    return instrument('graphics/three/BrowserBatchedModelKernel.java', [
        ('    void flush() {', '''    private static Thread traceOwner;
    private static long traceNativeNanos;
    private static int traceNativeCalls, traceNativeVertices;

    static void beginProjectionTrace() {
        traceOwner = Thread.currentThread();
        traceNativeNanos = 0L;
        traceNativeCalls = traceNativeVertices = 0;
    }

    static String finishProjectionTrace() {
        traceOwner = null;
        return " batchedCalls=" + traceNativeCalls + " batchedVertices=" + traceNativeVertices
            + " batchedCallMs=" + (traceNativeNanos/1000000d);
    }

    void flush() {'''),
        ('                used = backend.transform(size, rows, counts, camera, trig, trigSource);', '''                boolean tracing = traceOwner != null && traceOwner == Thread.currentThread();
                long begin = tracing ? System.nanoTime() : 0L;
                used = backend.transform(size, rows, counts, camera, trig, trigSource);
                if (tracing) {
                    traceNativeNanos += System.nanoTime() - begin;
                    traceNativeCalls++;
                    traceNativeVertices += packetVertices;
                }'''),
    ])



def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('loopback_web', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--skip-owned-faces', action='store_true')
    parser.add_argument('--trace-models', action='store_true')
    parser.add_argument('--skip-diffuse-zero-fill', action='store_true')
    args = parser.parse_args()
    if args.skip_diffuse_zero_fill and not args.trace_models:
        parser.error('--skip-diffuse-zero-fill requires --trace-models')
    web = args.loopback_web.resolve()
    assets, = (web / 'assets').iterdir()
    policy = (assets / 'browser-public-alpha.js').read_text()
    if "PUBLIC_ORIGIN='http://127.0.0.1:41727'" not in policy or "PUBLIC_WEBSOCKET='ws://127.0.0.1:43571/'" not in policy:
        raise ValueError('The scene observer requires the loopback profile')
    client = assets / 'client.jar'
    browser = (assets / 'browser.js').read_text()
    anchor = "deployment.assetRoot+'client.jar', window.browserProof.mode"
    if browser.count(anchor) != 1:
        raise ValueError('Unexpected Java classpath')
    digest = hashlib.sha256(client.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory() as temporary:
        temporary = Path(temporary)
        classes = temporary / 'classes'
        classes.mkdir()
        java = temporary / 'mudclient.java'
        java.write_text(client_source(args.trace_models))
        scene = temporary / 'Scene.java'
        scene.write_text(scene_source(args.skip_owned_faces, args.trace_models))
        sources = [java, scene]
        if args.trace_models:
            for name, content in [('RSModel', model_source(args.skip_diffuse_zero_fill)), ('BrowserBatchedModelKernel', kernel_source())]:
                path = temporary / (name + '.java')
                path.write_text(content)
                sources.append(path)
        subprocess.run([args.jdk/'bin/javac', '-encoding', 'UTF-8', '-cp', client, '-d', classes, *sources], check=True)
        overlay = temporary / 'scene-observer.jar'
        subprocess.run([args.jdk/'bin/jar', 'cf', overlay, '-C', classes, 'orsc'], check=True)
        shutil.copytree(web, args.output, copy_function=os.link)
        target = args.output / 'assets' / assets.name
        shutil.copy2(overlay, target / overlay.name)
        target_browser = target / 'browser.js'
        target_browser.unlink()
        target_browser.write_text(browser.replace(anchor, "deployment.assetRoot+'scene-observer.jar:'+ " + anchor))
        assert hashlib.sha256((target/'client.jar').read_bytes()).hexdigest() == digest
    print(digest)


if __name__ == '__main__':
    main()
