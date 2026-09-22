#!/usr/bin/env python3
"""Reference-library integrity without requiring the ignored original export."""
import json
import runpy
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
module = runpy.run_path(str(ROOT / 'scripts/build-item-reference-library.py'))
library = ROOT / 'dev/myworld/reference-library/items'
module['check'](library)
config, groups = module['classification']()
records = json.loads((library / 'catalog.json').read_text())['sprites']
by_index = {r['item_sprite_index']: r for r in records}
assert set(by_index) == set(range(486))
assert len([r for r in records if r['overflow_padding']]) == 8
assert len([r for r in records if r['declared_canvas'] == [47, 33]]) == 13
assert groups[422] == 'tools/utility' and by_index[422]['label'] == 'Sleeping bag'
assert groups[423] != 'weapons/staves' and groups[424] == 'armor/shields'
assert groups[463] == 'consumables/food'
assert by_index[80]['canvas'] == [48, 32]
assert by_index[80]['visible_bounds'] == [10, 8, 37, 24]
assert groups[80] == 'weapons/daggers' and groups[385] == 'weapons/daggers'
assert 80 in config['cross_references']['ammunition/thrown']
for group in config['groups']:
    count = len(module['expand'](config['groups'][group]))
    pages = list((library / group).glob('contact-*.png'))
    assert len(pages) == (count + 47) // 48, group
    for page in pages:
        with Image.open(page) as im:
            assert im.width == 1044 and im.mode == 'RGB'
print('PASS: complete art reference coverage, native dagger scale, shared forms, label corrections and overflow flags')
