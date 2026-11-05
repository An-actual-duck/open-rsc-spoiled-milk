#!/usr/bin/env python3
"""Render offline showcase GIFs from the shipped actor and VFX implementation."""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import html
import json
import os
from pathlib import Path
import shutil
import subprocess

from PIL import Image, ImageDraw, ImageFont, ImageSequence

ROOT = Path(__file__).resolve().parents[1]
BACKGROUND = (17, 25, 35)
COMPONENTS = {
    'hallowed-strikes': 'Hallowed Strikes', 'quickened-pulse': 'Quickened Pulse',
    'mending-flesh': 'Mending Flesh', 'sap-blood': 'Sap Blood', 'barkskin': 'Barkskin',
    'thorns': 'Thorns', 'unnatural-might': 'Unnatural Might', 'bloodrage': 'Bloodrage',
    'blood-hunger': 'Blood Hunger',
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def plan(catalog: Path) -> dict:
    items = {item['id']: item for item in json.loads(catalog.read_text())['items']}

    def gear(ids):
        return [items[item_id] for item_id in ids]

    return {'presentation': 'Offline composition using the real client renderer; not gameplay footage.', 'scenes': [
        {'key': 'ice-crystal', 'title': 'Ice Crystal', 'subtitle': 'Tier 3 Ice', 'kind': 'spell',
         'color': '#87d9ff', 'effectId': 35, 'gear': gear([2073, 2086, 2099, 2816, 2956, 102])},
        {'key': 'thunder-strike', 'title': 'Thunder Strike', 'subtitle': 'Tier 3 Thunder', 'kind': 'spell',
         'color': '#f6d66e', 'effectId': 34, 'gear': gear([2076, 2089, 2102, 2806, 2946, 2238])},
        {'key': 'battering-ram', 'title': 'Battering Ram', 'subtitle': 'Tier 3 Wood', 'kind': 'spell',
         'color': '#9cc675', 'effectId': 37, 'gear': gear([2080, 2093, 2106, 2876, 3016, 2278])},
        {'key': 'sacred-unicorn', 'title': 'Sacred Unicorn', 'subtitle': 'Summoning', 'kind': 'summon',
         'color': '#dcc8ff', 'effectId': 26, 'gear': gear([3142, 3143, 3144, 3145, 3146])},
        {'key': 'saradomins-resplendence', 'title': "Saradomin’s Resplendence", 'subtitle': 'Tier 3 blessing',
         'kind': 'buff', 'color': '#b4ddff', 'effectId': 96, 'gear': []},
        {'key': 'guthixs-vengeance', 'title': "Guthix’s Vengeance", 'subtitle': 'Tier 3 blessing',
         'kind': 'buff', 'color': '#abd58c', 'effectId': 98, 'gear': []},
        {'key': 'zamoraks-wrath', 'title': "Zamorak’s Wrath", 'subtitle': 'Tier 3 blessing',
         'kind': 'buff', 'color': '#ee9696', 'effectId': 100, 'gear': []},
    ]}


def palette(images: list[Image.Image]) -> Image.Image:
    colors = Counter()
    for image in images:
        colors.update(dict((rgb, count) for count, rgb in image.getcolors(image.width * image.height)))
    colors[BACKGROUND] = min(colors[BACKGROUND], 200)
    pixels = [rgb for rgb, count in colors.items() for _ in range(min(count, 200))]
    sample = Image.new('RGB', (len(pixels), 1))
    sample.putdata(pixels)
    return sample.quantize(colors=256, method=Image.Quantize.MEDIANCUT)


def save_gif(path: Path, images: list[Image.Image], durations: list[int], labels=None) -> dict:
    if labels:
        title_font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 24)
        effect_font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 18)
        enlarged = []
        for image, (title, effect) in zip(images, labels):
            image = image.resize((960, 540), Image.Resampling.NEAREST)
            draw = ImageDraw.Draw(image)
            draw.text((480, 463), title, font=title_font, fill='#e9eff5', anchor='mt')
            if effect:
                draw.text((480, 501), effect, font=effect_font, fill='#a9b5c4', anchor='mt')
            enlarged.append(image)
        images = enlarged
    shared = palette(images)
    quantized = [frame.quantize(palette=shared, dither=Image.Dither.NONE)
                 .resize((960, 540), Image.Resampling.NEAREST) for frame in images]
    background = Image.new('RGB', (1, 1), BACKGROUND).quantize(palette=shared).getpixel((0, 0))
    quantized[0].save(path, save_all=True, append_images=quantized[1:], duration=durations,
                      loop=0, disposal=2, background=background, optimize=True)
    with Image.open(path) as gif:
        actual = sum(frame.info.get('duration', 0) for frame in ImageSequence.Iterator(gif))
        assert gif.size == (960, 540) and actual == sum(durations), path
        count = gif.n_frames
    return {'file': path.name, 'sha256': digest(path), 'bytes': path.stat().st_size,
            'dimensions': [960, 540], 'durationMs': actual, 'frames': count}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, default=ROOT)
    parser.add_argument('--appearance-catalog', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--reuse-renders', action='store_true')
    parser.add_argument('--render-only', action='store_true')
    args = parser.parse_args()
    root = args.source_root.resolve()
    output = args.output.resolve()
    source = output / 'source'
    source.mkdir(parents=True, exist_ok=True)
    export_plan = plan(args.appearance_catalog)
    plan_file = source / 'export-plan.json'
    plan_file.write_text(json.dumps(export_plan, indent=2) + '\n')
    shutil.copy2(args.appearance_catalog, source / 'appearance-catalog.json')
    java = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
    jar = root / 'Client_Base/Open_RSC_Client.jar'
    renderer = ROOT / 'tools/showcase/SpellSummonShowcaseRenderer.java'
    shutil.copy2(renderer, source / renderer.name)
    if not args.reuse_renders:
        video = source / 'runtime-cache/video'
        video.mkdir(parents=True, exist_ok=True)
        for name in ['Custom_Sprites.osar', 'Authentic_Sprites.orsc', 'library.orsc']:
            shutil.copy2(root / 'Client_Base/Cache/video' / name, video / name)
        classes = source / 'classes'
        classes.mkdir(exist_ok=True)
        with (source / 'render.log').open('w') as log:
            subprocess.run([str(java / 'javac'), '-cp', str(jar), '-d', str(classes), str(renderer)],
                           check=True, stdout=log, stderr=subprocess.STDOUT)
            subprocess.run([str(java / 'java'), '-Xmx768m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
                            '-cp', str(classes) + ':' + str(jar), 'orsc.SpellSummonShowcaseRenderer',
                            str(root), str(source), str(plan_file)],
                           check=True, stdout=log, stderr=subprocess.STDOUT)
    if args.render_only:
        print(source / 'render-timeline.json')
        return
    timeline = json.loads((source / 'render-timeline.json').read_text())
    definitions = {scene['key']: scene for scene in export_plan['scenes']}
    rendered = {}
    labels = {}
    exports = []
    # Titles stay in the review page; GIFs contain only the composited game visuals.
    for scene in timeline['scenes']:
        key = scene['key']
        frames = [Image.open(source / frame['file']).convert('RGB') for frame in scene['timeline']]
        durations = [frame['durationMs'] for frame in scene['timeline']]
        rendered[key] = (frames, durations)
        labels[key] = [(definitions[key]['title'], COMPONENTS.get(frame['component'], ''))
                       for frame in scene['timeline']]
        proof = save_gif(output / (key + '.gif'), frames, durations)
        proof.update({k: definitions[key][k] for k in ['title', 'subtitle', 'kind', 'effectId']})
        proof['nativeEffectTicks'] = scene['nativeEffectTicks']
        exports.append(proof)
    for filename, title, keys in [
        ('tier-3-elemental-spells.gif', 'Tier 3 Ice, Thunder and Wood', ['ice-crystal', 'thunder-strike', 'battering-ram']),
        ('tier-3-cleric-blessings.gif', 'All three tier 3 blessings',
         ['saradomins-resplendence', 'guthixs-vengeance', 'zamoraks-wrath']),
    ]:
        frames = [image for key in keys for image in rendered[key][0]]
        durations = [duration for key in keys for duration in rendered[key][1]]
        captions = [caption for key in keys for caption in labels[key]]
        proof = save_gif(output / filename, frames, durations, captions)
        proof.update({'title': title, 'kind': 'combined', 'sequence': keys})
        exports.append(proof)
    manifest = {'presentation': export_plan['presentation'], 'sourceCommit': subprocess.check_output(
        ['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip(),
        'clientJarSha256': digest(jar), 'rendererSha256': digest(renderer),
        'exporterSha256': digest(Path(__file__)), 'appearanceCatalogSha256': digest(args.appearance_catalog),
        'nativeTickMs': 20, 'scale': 3, 'background': '#111923',
        'scope': 'No server, account, map, runtime source or asset changes.',
        'timing': 'Actual client effect timing. Pauses between scenes are editorial holds.',
        'exports': exports}
    (output / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    cards = []
    primary = ['tier-3-elemental-spells.gif', 'sacred-unicorn.gif', 'tier-3-cleric-blessings.gif']
    for entry in sorted(exports, key=lambda item: primary.index(item['file']) if item['file'] in primary else 3):
        cards.append('<article><h2>' + html.escape(entry['title']) + '</h2><img loading="lazy" src="' + entry['file']
                     + '"><a href="' + entry['file'] + '" download>Download GIF</a></article>')
    page = '''<!doctype html><meta charset="utf-8"><title>Spell and summon showcase</title>
<style>body{margin:0;background:#0b1119;color:#e9eff5;font:16px system-ui}main{max-width:1280px;margin:40px auto;padding:0 24px}h1{font-size:30px}p{color:#a9b5c4;max-width:900px;line-height:1.6}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(440px,1fr));gap:22px}article{background:#111923;border:1px solid #263445;border-radius:10px;padding:18px}h2{font-size:18px;margin:0}img{display:block;width:100%;image-rendering:pixelated}a{color:#a9d9ff;text-decoration:none}</style>
<main><h1>Spells, summons &amp; blessings</h1><p>Offline mockups using the released game’s own character, creature and effect rendering. Each tier 3 blessing plays its three effect animations in sequence. These are composed sprite previews, not gameplay footage.</p><div class="grid">'''
    (output / 'index.html').write_text(page + ''.join(cards) + '</div></main>\n')
    print('Exported ' + str(len(exports)) + ' GIFs: ' + str(output / 'index.html'))


if __name__ == '__main__':
    main()
