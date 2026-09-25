#!/usr/bin/env python3
"""Mechanical extraction only; never modifies production art or approved gallery."""
import hashlib
import json
from collections import deque
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'dev/myworld/art-candidates/naga-cleave-middle-v1'
SOURCE = ROOT / 'dev/myworld/assets/sprites/npcs/slayer-movement-preview/naga.png'

def main():
    generated = Image.open(OUT / 'generated-source.png').convert('RGBA')
    # Actual generated source has alpha. Connected extraction excludes the next
    # pose's tail, which intrudes into the middle object's rectangular bounds.
    assert generated.size == (2172, 724)
    assert generated.getchannel('A').getextrema() == (0, 255)
    alpha = generated.getchannel('A').load()
    seed = (1200, 500)
    assert alpha[seed] > 0
    visited = {seed}
    queue = deque([seed])
    while queue:
        x, y = queue.popleft()
        for p in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
            if 0 <= p[0] < generated.width and 0 <= p[1] < generated.height and p not in visited and alpha[p] > 0:
                visited.add(p)
                queue.append(p)
    isolated = Image.new('RGBA', generated.size)
    src, dst = generated.load(), isolated.load()
    for p in visited:
        dst[p] = src[p]
    bounds = isolated.getbbox()
    extracted = isolated.crop(bounds)
    # One uniform 0.12 scale chosen from head-to-ground body height, not blades.
    resized = extracted.resize(tuple(round(v * .12) for v in extracted.size), Image.Resampling.NEAREST)
    replacement = Image.new('RGBA', (100, 100))
    offset = (2, 91-resized.height)
    assert offset[0] + resized.width <= 100 and offset[1] >= 0
    replacement.paste(resized, offset)
    replacement.save(OUT / 'middle-native.png')
    sheet = Image.open(SOURCE).convert('RGBA')
    old = [sheet.crop((500,i*100,600,(i+1)*100)) for i in range(3)]
    new = [old[0].copy(), replacement, old[2].copy()]
    assert all(old[i].tobytes() == new[i].tobytes() for i in (0,2))
    for name, frames in [('old',old),('candidate',new)]:
        strip = Image.new('RGBA', (300,100))
        flattened = []
        for i, frame in enumerate(frames):
            strip.paste(frame,(i*100,0))
            bg = Image.new('RGB', (100,100), (18,20,23))
            bg.paste(frame,(0,0),frame)
            flattened.append(bg)
        strip.save(OUT / f'{name}-strip.png')
        atlas = Image.new('RGB',(300,100))
        for i, frame in enumerate(flattened): atlas.paste(frame,(i*100,0))
        palette = atlas.quantize(colors=256,method=Image.Quantize.MEDIANCUT)
        indexed = [f.quantize(palette=palette,dither=Image.Dither.NONE) for f in flattened]
        for scale in (1,3):
            gifs = [f.resize((100*scale,100*scale),Image.Resampling.NEAREST) for f in indexed]
            path = OUT / f'{name}-{scale}x.gif'
            gifs[0].save(path,save_all=True,append_images=gifs[1:],duration=200,loop=0,disposal=2,optimize=False)
            check = Image.open(path)
            assert check.n_frames == 3
            for i in range(3):
                check.seek(i)
                assert check.info['duration'] == 200
                assert check.convert('RGB').tobytes() == gifs[i].convert('RGB').tobytes()
    manifest = dict(approved=False,production_unchanged=True,canvas=[100,100],duration_ms=[200]*3,
        source_sha256=hashlib.sha256((OUT/'generated-source.png').read_bytes()).hexdigest(),
        production_sha256=hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        extraction_bounds=bounds,scale=.12,placement=offset,extracted_size=extracted.size,
        endpoints='Exact production RGBA frame 0 and frame 2; only middle replaced.',
        prompt='Change only middle to both swords sweeping down, hands chest height; preserve olive palette, low-resolution RSC style, body/ground/tail; first and last unchanged.',
        generation_source='/home/justin/.codex/generated_images/01a067bf-8c49-74e0-af7f-b682d8820b2a/exec-c2d9348f-1e07-475c-b15e-c877228962be.png',
        notes='Real source alpha preserved. Connected extraction and nearest scaling only, no repaint. Generated middle remains a candidate; unchanged endpoints are original production, not regenerated endpoints. No native canvas expansion.')
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    (OUT/'index.html').write_text("""<!doctype html><meta charset="utf-8"><title>Naga cleave middle-pose candidate</title>
<style>body{background:#121417;color:#ddd;font:16px system-ui;margin:24px}img{image-rendering:pixelated}.row{display:flex;gap:24px}figure{margin:0}a{color:#8bd}.strip{width:900px;max-width:100%}</style>
<h1>Naga cleave — middle-pose candidate</h1><p>Only middle artwork changes. Exact approved first and last frames retained. Native canvas remains 100×100. Both loops: 0,1,2 at 200 ms per frame.</p>
<div class="row"><figure><h2>Current approved</h2><img src="old-3x.gif"></figure><figure><h2>New middle pose</h2><img src="candidate-3x.gif"></figure></div>
<h2>Candidate frames</h2><img class="strip" src="candidate-strip.png"><h2>Approved frames</h2><img class="strip" src="old-strip.png">
<p>Preview only: no production/game or full-gallery changes. Body-height/ground registration, nearest-neighbor extraction, no hand painting. Blade orientation and pose await approval.</p>
<p><a href="candidate-1x.gif">Native GIF</a> · <a href="candidate-3x.gif">3× GIF</a> · <a href="candidate-strip.png">RGBA strip</a> · <a href="manifest.json">Provenance</a></p>""")
    print(json.dumps(manifest,indent=2))

if __name__ == '__main__': main()
