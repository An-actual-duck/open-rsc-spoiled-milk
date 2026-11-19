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


def client_source():
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
    return instrument('mudclient.java', edits)


def scene_source(skip_owned_faces):
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
    return instrument('graphics/three/Scene.java', edits)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('loopback_web', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--skip-owned-faces', action='store_true')
    args = parser.parse_args()
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
        java.write_text(client_source())
        scene = temporary / 'Scene.java'
        scene.write_text(scene_source(args.skip_owned_faces))
        subprocess.run([args.jdk/'bin/javac', '-encoding', 'UTF-8', '-cp', client, '-d', classes, java, scene], check=True)
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
