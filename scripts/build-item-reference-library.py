#!/usr/bin/env python3
"""Arrange existing exported artwork; never resample or repaint the source icons."""
import argparse
import csv
import hashlib
import json
import re
from collections import defaultdict
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
LIBRARY = ROOT / 'dev/myworld/reference-library/items'


def aliases():
    source = (ROOT / 'Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java').read_text()
    pattern = re.compile(r'addItemDefinition\(new ItemDef\("((?:[^"\\]|\\.)*)",\s*"(?:[^"\\]|\\.)*",\s*"(?:[^"\\]|\\.)*",\s*\d+,\s*\d+,\s*"items:(\d+)"[^\n]*?,\s*(\d+)\)\);')
    result = defaultdict(list)
    for name, sprite, item in pattern.findall(source):
        if int(item) < 1290 and name != 'Retired item' and name not in result[int(sprite)]:
            result[int(sprite)].append(name)
    return result


def read_export(source):
    names = aliases()
    records = []
    with (source / 'sprite-manifest.csv').open(newline='') as stream:
        rows = list(csv.DictReader(stream))
    for row in rows:
        if row['source'] != 'Authentic_Sprites.orsc' or row['category'] != 'items-inventory-ground':
            continue
        sprite_id = int(row['sprite_id'])
        crop_path = source / row['path']
        crop = Image.open(crop_path).convert('RGBA')
        width, height = int(row['bound_width']), int(row['bound_height'])
        x, y = (int(row['x_shift']), int(row['y_shift'])) if row['requires_shift'] == 'True' else (0, 0)
        assert crop.size == (int(row['width']), int(row['height'])), crop_path
        assert x >= 0 and y >= 0, row
        # A few legacy exports extend past their declared bounds. Preserve all
        # pixels and flag the padding; never silently clip art or normalize scale.
        actual_size = (max(width, x + crop.width), max(height, y + crop.height))
        canvas = Image.new('RGBA', actual_size)
        canvas.paste(crop, (x, y))  # No mask: preserve source RGBA exactly, including alpha.
        records.append((dict(sprite_id=sprite_id, item_sprite_index=sprite_id - 2150,
                             source_path=row['path'], source_sha256=hashlib.sha256(crop_path.read_bytes()).hexdigest(),
                             canvas=list(actual_size), declared_canvas=[width, height],
                             overflow_padding=actual_size != (width, height), crop_size=list(crop.size), offset=[x, y],
                             crop_rgba_sha256=hashlib.sha256(crop.tobytes()).hexdigest(),
                             aliases=names[sprite_id - 2150]), canvas))
    ids = [r['sprite_id'] for r, _ in records]
    assert len(ids) == len(set(ids))
    return sorted(records, key=lambda pair: pair[0]['sprite_id'])


def sheets(records, directory, title, prefix='contact'):
    directory.mkdir(parents=True, exist_ok=True)
    # Integer 3x nearest-neighbour previews; exact-size transparent files are separate.
    font = ImageFont.load_default(size=12)
    pages = []
    for page in range((len(records) + 47) // 48):
        batch = records[page * 48:(page + 1) * 48]
        art_height = max(canvas.height for _, canvas in batch) * 3
        row_height = art_height + 46
        sheet = Image.new('RGB', (6 * 174, 44 + ((len(batch) + 5) // 6) * row_height), '#202329')
        draw = ImageDraw.Draw(sheet)
        draw.text((10, 8), title + ' | 3x NEAREST | canvas border shown', fill='white', font=font)
        for pos, (record, canvas) in enumerate(batch):
            x, y = (pos % 6) * 174 + 12, 42 + (pos // 6) * row_height
            w, h = canvas.width * 3, canvas.height * 3
            for cy in range(0, h, 12):
                for cx in range(0, w, 12):
                    color = '#3d424a' if (cx // 12 + cy // 12) % 2 else '#30343b'
                    draw.rectangle((x+cx, y+cy, x+min(cx+11,w-1), y+min(cy+11,h-1)), fill=color)
            enlarged = canvas.resize((w, h), Image.Resampling.NEAREST)
            sheet.paste(enlarged, (x, y), enlarged)
            draw.rectangle((x-1, y-1, x+w, y+h), outline='#7c8593')
            if record.get('overflow_padding'):
                dw, dh = record['declared_canvas']
                draw.rectangle((x-1, y-1, x+dw*3, y+dh*3), outline='#ffa74d')
            label = record.get('label') or (record['aliases'][0] if record['aliases'] else 'UNLABELED')
            draw.text((x, y+art_height+5), f"{record['sprite_id']} / items:{record['item_sprite_index']}", fill='#b7ccdf', font=font)
            draw.text((x, y+art_height+22), label[:24], fill='white', font=font)
        filename = f'{prefix}-{page+1:02}.png'
        sheet.save(directory / filename)
        pages.append(filename)
    return pages


def expand(spec):
    result = []
    for token in spec.split():
        ends = [int(n) for n in token.split('-')]
        result.extend(range(ends[0], ends[-1]+1))
    return result


def classification():
    config = json.loads((LIBRARY / 'classification.json').read_text())
    groups = {}
    for group, spec in config['groups'].items():
        assert re.fullmatch(r'[a-z-]+/[a-z-]+', group), group
        for index in expand(spec):
            assert index not in groups, f'Duplicate classification: {index}'
            groups[index] = group
    return config, groups


def build(records, source, output):
    config, groups = classification()
    assert set(groups) == {r['item_sprite_index'] for r, _ in records}, 'Missing or extra classification'
    grouped = defaultdict(list)
    catalog = []
    for record, canvas in records:
        index = record['item_sprite_index']
        group = groups[index]
        record['group'] = group
        record['label'] = config['labels'].get(str(index), record['aliases'][0] if record['aliases'] else f'Unidentified shape {index}')
        record['label_kind'] = 'visual-description' if str(index) in config['labels'] else 'definition-name-hint'
        record['image'] = f'{group}/id-{record["sprite_id"]:05}.png'
        record['visible_bounds'] = list(canvas.getbbox()) if canvas.getbbox() else None
        record['canvas_rgba_sha256'] = hashlib.sha256(canvas.tobytes()).hexdigest()
        (output / group).mkdir(parents=True, exist_ok=True)
        canvas.save(output / record['image'])
        grouped[group].append((record, canvas))
        catalog.append(record)
    by_index = {r['item_sprite_index']: r for r in catalog}
    index_lines = []
    for group, entries in sorted(grouped.items()):
        pages = sheets(entries, output / group, group)
        notes = config['notes'].get(group, 'Compare silhouettes within this subgroup, then choose one matching the requested object. Match its native canvas occupancy and interior shading rather than averaging unrelated shapes.')
        lines = [f'# {group}', '', 'AI visual references only. Read [the library rules](../../README.md) first.', '', notes, '',
                 '## Contact sheets', '', *[f'- [{page}]({page})' for page in pages], '',
                 'The sheets are 3x nearest-neighbour previews on a checkerboard. Individual PNGs below are native-size RGBA canvases. The checkerboard, borders and labels are NOT sprite art.', '',
                 '## Native references', '', '| Sprite / index | Visual label | Canvas | Visible bounds (x,y,w,h) | File |', '| --- | --- | --- | --- | --- |']
        for r, canvas in entries:
            bounds = r['visible_bounds']
            bbox = f'{bounds[0]},{bounds[1]},{bounds[2]-bounds[0]},{bounds[3]-bounds[1]}' if bounds else 'empty'
            warning = ' **export overflow; not a canvas standard**' if r['overflow_padding'] else ''
            lines.append(f'| {r["sprite_id"]} / items:{r["item_sprite_index"]} | {r["label"]} | {canvas.width}x{canvas.height}{warning} | {bbox} | [{Path(r["image"]).name}]({Path(r["image"]).name}) |')
        if config['cross_references'].get(group):
            lines += ['', '## Also inspect', '']
            for index in config['cross_references'][group]:
                r = by_index[index]
                lines.append(f'- [{r["label"]} — items:{index}](../../{r["image"]})')
        (output / group / 'README.md').write_text('\n'.join(lines) + '\n')
        index_lines.append(f'- [{group}]({group}/README.md) — {len(entries)} sprites')
    overflow = [r['sprite_id'] for r in catalog if r['overflow_padding']]
    (output / 'catalog.json').write_text(json.dumps(dict(schema=1, source_export=source.name,
        source_manifest_sha256=hashlib.sha256((source/'sprite-manifest.csv').read_bytes()).hexdigest(),
        note='Aliases are historical client hints, not authoritative item identities or in-game recolors. Visual grouping was reviewed against the actual exported PNGs.',
        sprites=catalog), indent=2) + '\n')
    (output / 'README.md').write_text('''# Inventory / ground sprite reference library

For AI creating new RSC-style icons. This is an art-reference collection, not
a gameplay database, runtime asset pack, or browsable application.

## Before creating an icon

1. Find the requested object family below, read its short notes, and **open its
   contact sheet visually**. Do not rely on labels alone.
2. Open the closest individual PNG. This is the full reference canvas, not a
   tightly cropped object. Read its canvas and visible-bounds measurements.
3. Use that reference as the scale, orientation, placement and shading anchor.
   Describe the user's desired changes while keeping the unchanged qualities
   explicit. For example: use the ordinary dagger as the scale/pose anchor,
   change its blade into a Terror Fang, and retain native-size readability.
4. Compare the result on equal-size canvases at 1x and integer nearest-neighbour
   zoom. Do not auto-fit each object to fill a thumbnail or enlarge the source
   canvas to accommodate a new design without asking the user first.

## Reference rules

- Original exported artwork is unchanged: no painting, smoothing, recoloring,
  upscaling, sharpening or alpha cleanup was applied to individual sprites.
  The export's cropped pixels were placed at their recorded offsets on a
  transparent canvas. Offsets are part of the reference, not padding to remove.
- Most declared canvases are **48x32**; 13 are **47x33**. Do not normalize those
  exceptions. Match the selected reference and the destination's real contract.
- Eight exports have crop/offset data extending past their declared bounds.
  Their reference PNGs include explicitly flagged extra transparent extent to
  preserve every source pixel. Orange sheet borders show declared bounds. These
  are archival anomalies, NOT permission to enlarge a new item canvas.
- Contact sheets use uniform **3x nearest-neighbour** zoom and a neutral
  checkerboard. Never copy the checkerboard, frame border, labels or sheet
  spacing into sprite artwork. Only the individual transparent PNG is an input
  art reference.
- Favor readable stepped silhouettes and compact interior shading. Preserve
  the selected example's highlights and texture; avoid smooth resampling or
  inventing bright edge halos. Family notes refine this general guidance.
- Many sprites are neutral recolor bases. White/grey regions may be runtime
  color masks, not the item's intended final metal/material color. No speculative
  recolors are included here. Choose the new item's palette explicitly.
- Labels describe what the image looks like where historical client names
  conflict. `catalog.json` keeps source identity, hashes and geometry for audit;
  its name aliases are search hints only. Sprite IDs and `items:N` indices are
  not gameplay item IDs. Shared forms are cross-linked instead of duplicated.
- This covers the supplied Authentic_Sprites export only. It does not claim to
  include every later custom item or approved newly authored sprite.

## Object families

''' + '\n'.join(index_lines) + '''

## Rebuild / verify

The editable grouping and family-specific notes live in `classification.json`.
The exported originals remain untouched in their original output directory.
From the repository root (Pillow 12.2 used for the initial build):

```sh
python3 scripts/build-item-reference-library.py --source output/sprite-png-export-20260702-154351
python3 scripts/build-item-reference-library.py --check
```

Generated PNGs, contact sheets, catalog and subgroup notes are checked in so
future AI sessions do not need the original ignored export to use the library.
Edit the classification source and rebuild, not the generated reference images.
''' + f'\nCoverage: {len(catalog)} sprites, {len(grouped)} subgroups. Export-overflow sprite IDs: {overflow}.\n')
    check(output)


def check(output, source=None):
    config, groups = classification()
    catalog = json.loads((output/'catalog.json').read_text())['sprites']
    assert len(catalog) == len(groups) == 486
    assert {r['item_sprite_index'] for r in catalog} == set(groups)
    expected_pngs = set()
    for r in catalog:
        assert r['group'] == groups[r['item_sprite_index']]
        path = output / r['image']
        expected_pngs.add(path)
        im = Image.open(path).convert('RGBA')
        assert list(im.size) == r['canvas'], path
        assert hashlib.sha256(im.tobytes()).hexdigest() == r['canvas_rgba_sha256'], path
        x, y = r['offset']; w, h = r['crop_size']
        assert hashlib.sha256(im.crop((x,y,x+w,y+h)).tobytes()).hexdigest() == r['crop_rgba_sha256'], path
        blank = im.copy(); blank.paste((0,0,0,0), (x,y,x+w,y+h))
        assert blank.getbbox() is None, f'Unexpected pixels outside source crop: {path}'
    assert set(output.glob('*/*/id-*.png')) == expected_pngs, 'Stale or missing individual references'
    if source:
        original = {r['sprite_id']: (r, canvas) for r, canvas in read_export(source)}
        assert set(original) == {r['sprite_id'] for r in catalog}
        for r in catalog:
            original_record, canvas = original[r['sprite_id']]
            assert r['source_sha256'] == original_record['source_sha256'], r['image']
            assert r['offset'] == original_record['offset'] and r['canvas'] == list(canvas.size), r['image']
            assert hashlib.sha256(canvas.tobytes()).hexdigest() == r['canvas_rgba_sha256'], r['image']
    for group in config['groups']:
        assert (output/group/'README.md').is_file()
        assert (output/group/'contact-01.png').is_file()
    print(f'PASS: {len(catalog)} unique references, exact pixels/offsets/canvases, complete grouping')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, help='Export root containing sprite-manifest.csv')
    parser.add_argument('--output', type=Path, default=LIBRARY)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--overview', type=Path, help='Only make temporary visual-review sheets')
    args = parser.parse_args()
    if args.check:
        check(args.output, args.source)
        return
    if not args.source:
        parser.error('--source is required unless --check is used')
    records = read_export(args.source)
    if args.overview:
        sheets(records, args.overview, 'Authentic inventory/ground sprite review')
        print(f'{len(records)} full-canvas references; overview at {args.overview}')
        return
    build(records, args.source, args.output)


if __name__ == '__main__':
    main()
