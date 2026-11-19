#!/usr/bin/env python3
"""Build a loopback-only lighting timing overlay alongside the verified client archive."""
import argparse
import hashlib
from pathlib import Path
import os
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]


def instrument(name, edits):
    source = (ROOT / 'PC_Client/src/orsc' / (name + '.java')).read_text()
    for anchor, replacement in edits:
        if source.count(anchor) != 1:
            raise ValueError(f'Unexpected {name} source near {anchor!r}')
        source = source.replace(anchor, replacement)
    return source


def terrain_source(hoist_distances=False):
    edits = [
        ('    boolean update(Renderer3DFrame frame) {', '''    final long[] traceNanos = new long[3];

    boolean update(Renderer3DFrame frame) {
        Arrays.fill(traceNanos, 0);
        long traceStart = System.nanoTime();'''),
        ('        if (signature == sourceSignature)', '''        traceNanos[0] = System.nanoTime() - traceStart;
        if (signature == sourceSignature)'''),
        ('        int[] heights = ', '''        long sampleStart = System.nanoTime();
        int[] heights = '''),
        ('        rgba = build(heights, width, height);', '''        traceNanos[1] = System.nanoTime() - sampleStart;
        long shadeStart = System.nanoTime();
        rgba = build(heights, width, height);
        traceNanos[2] = System.nanoTime() - shadeStart;'''),
    ]
    if hoist_distances:
        edits.extend([
            ('        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {', '''        double[] lengths = new double[directions.length];
        for (int direction = 0; direction < directions.length; direction++) {
            int[] d = directions[direction];
            lengths[direction] = Math.hypot(d[0], d[1]) * TILE;
        }
        for (int z = 0; z < height; z++) for (int x = 0; x < width; x++) {'''),
            ('            if (y != UNKNOWN) for (int[] d : directions) {', '''            if (y != UNKNOWN) for (int direction = 0; direction < directions.length; direction++) {
                int[] d = directions[direction];'''),
            ('                double length = Math.hypot(d[0],d[1]) * TILE;', '                double length = lengths[direction];'),
        ])
    return instrument('TerrainDefinitionField', edits)


def lighting_source():
    return instrument('BrowserLightingData', [
        ('        List<Mask> changes=new ArrayList<Mask>(3);', '''        final long traceStart=System.nanoTime();
        long terrainWork=0,glowWork=0,roofWork=0,copyWork=0,copyStarted;
        long terrainSignatureWork=0,terrainSamplesWork=0,terrainShadingWork=0;
        List<Mask> changes=new ArrayList<Mask>(3);'''),
        ('''        if(directional&&RendererTerrainDefinitionSettings.getMode().shaderId>=2&&terrain.update(frame)) {''',
         '''        long terrainStarted=System.nanoTime();
        boolean terrainAvailable=directional&&RendererTerrainDefinitionSettings.getMode().shaderId>=2&&terrain.update(frame);
        terrainWork=System.nanoTime()-terrainStarted;
        if(terrainAvailable) {
            terrainSignatureWork=terrain.traceNanos[0];terrainSamplesWork=terrain.traceNanos[1];terrainShadingWork=terrain.traceNanos[2];'''),
        ('if(terrainRevision!=terrain.revision){changes.add', 'if(terrainRevision!=terrain.revision){copyStarted=System.nanoTime();changes.add'),
        ('terrainRevision=terrain.revision;}', 'terrainRevision=terrain.revision;copyWork+=System.nanoTime()-copyStarted;}'),
        ('        RemasterGlowMaskBuild built=directional?glow.build(world):null;', '''        long glowStarted=System.nanoTime();
        RemasterGlowMaskBuild built=directional?glow.build(world):null;
        glowWork=System.nanoTime()-glowStarted;'''),
        ('if(glowSignature!=mask.signature){changes.add', 'if(glowSignature!=mask.signature){copyStarted=System.nanoTime();changes.add'),
        ('glowSignature=mask.signature;}', 'glowSignature=mask.signature;copyWork+=System.nanoTime()-copyStarted;}'),
        ('        GeometryShadowRoofMask roof=roofs.get(world,plane);', '''        long roofStarted=System.nanoTime();
        GeometryShadowRoofMask roof=roofs.get(world,plane);
        roofWork=System.nanoTime()-roofStarted;'''),
        ('if(roofSignature!=roof.signature){changes.add', 'if(roofSignature!=roof.signature){copyStarted=System.nanoTime();changes.add'),
        ('roofSignature=roof.signature;}', 'roofSignature=roof.signature;copyWork+=System.nanoTime()-copyStarted;}'),
        ('        return new Frame(values,changes);\n    }', '''        long traceTotal=System.nanoTime()-traceStart;
        if(traceTotal>=12_000_000L||!changes.isEmpty()){
            int bytes=0,units=0;for(Mask mask:changes){bytes+=mask.rgba.length;units|=1<<mask.unit;}
            System.out.println("BROWSER_LIGHTING_TIMING epoch="+context+" level="+frame.getSignedWorldLevel()
                +" chunks="+world.getChunkCount()+" units="+units+" bytes="+bytes
                +" totalMs="+ms(traceTotal)+" terrainMs="+ms(terrainWork)
                +" signatureMs="+ms(terrainSignatureWork)+" sampleMs="+ms(terrainSamplesWork)
                +" shadeMs="+ms(terrainShadingWork)+" glowMs="+ms(glowWork)+" roofMs="+ms(roofWork)+" copyMs="+ms(copyWork));
        }
        return new Frame(values,changes);
    }
    private static double ms(long elapsed){return elapsed/1000000d;}'''),
    ])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('loopback_web', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--hoist-distances', action='store_true')
    args = parser.parse_args()
    source = args.loopback_web.resolve()
    assets, = (source / 'assets').iterdir()
    bootstrap = (assets / 'browser-public-alpha.js').read_text()
    if "PUBLIC_ORIGIN='http://127.0.0.1:41727'" not in bootstrap or "PUBLIC_WEBSOCKET='ws://127.0.0.1:43571/'" not in bootstrap:
        raise ValueError('The lighting overlay is limited to the loopback fixture')
    jar = assets / 'client.jar'
    original = (assets / 'browser.js').read_text()
    anchor = "deployment.assetRoot+'client.jar', window.browserProof.mode"
    if original.count(anchor) != 1:
        raise ValueError('Unknown staged classpath')
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    with tempfile.TemporaryDirectory() as temp:
        temp = Path(temp)
        classes = temp / 'classes'
        classes.mkdir()
        sources = []
        for name, text in [('TerrainDefinitionField', terrain_source(args.hoist_distances)), ('BrowserLightingData', lighting_source())]:
            path = temp / (name + '.java')
            path.write_text(text)
            sources.append(path)
        subprocess.run([args.jdk / 'bin/javac', '-cp', jar, '-d', classes, *sources], check=True)
        overlay = temp / 'lighting-observer.jar'
        subprocess.run([args.jdk / 'bin/jar', 'cf', overlay, '-C', classes, 'orsc'], check=True)
        shutil.copytree(source, args.output, copy_function=os.link)
        stage = args.output / 'assets' / assets.name
        shutil.copy2(overlay, stage / overlay.name)
        browser = stage / 'browser.js'
        browser.unlink()
        browser.write_text(original.replace(anchor, "deployment.assetRoot+'lighting-observer.jar:'+ " + anchor))
        assert hashlib.sha256((stage / 'client.jar').read_bytes()).hexdigest() == digest
    print(digest)


if __name__ == '__main__':
    main()
