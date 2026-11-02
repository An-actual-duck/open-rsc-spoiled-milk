#!/usr/bin/env python3
"""Exercise output relocation preservation and canonical asset/build boundaries."""
import importlib.util
import json
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('workspace_layout', ROOT / 'scripts/workspace-layout.py')
layout = importlib.util.module_from_spec(spec)
spec.loader.exec_module(layout)


def fixture(root):
    (root / 'config').mkdir()
    (root / 'config/workspace-layout.json').write_bytes((ROOT / 'config/workspace-layout.json').read_bytes())
    for directory in ['runtime', 'authoring', 'references']:
        (root / 'dev/myworld/assets' / directory).mkdir(parents=True)
    (root / 'output').mkdir()


def rejects(action):
    try:
        action()
    except ValueError:
        return
    raise AssertionError('Unsafe operation was accepted')


with tempfile.TemporaryDirectory(prefix='workspace-layout-') as directory:
    root = Path(directory)
    fixture(root)
    assert layout.output_path('tests', ['combat/run-1'], root) == root / 'output/tests/combat/run-1'
    rejects(lambda: layout.output_path('unknown', ['test'], root))
    rejects(lambda: layout.output_path('art', ['../tests'], root))
    rejects(lambda: layout.output_path('art', ['/tmp/escape'], root))
    original = root / 'output/combat-test'
    original.mkdir()
    payload = b'Fixture bytes and metadata must survive a directory relocation.\n'
    (original / 'result.bin').write_bytes(payload)
    image = root / 'output/Medallion.png'
    image.write_bytes(b'Unchanged draft artwork fixture')
    plan = layout.migrate_outputs(False, root)
    assert len(plan) == 2 and (original / 'result.bin').read_bytes() == payload
    before = (original / 'result.bin').stat()
    layout.migrate_outputs(True, root)
    moved = root / 'output/tests/combat/result.bin'
    assert moved.read_bytes() == payload and moved.stat().st_ino == before.st_ino
    assert (root / 'output/art/items/medallion/Medallion.png').read_bytes() == b'Unchanged draft artwork fixture'
    assert not original.exists() and not image.exists()
    assert len(list((root / 'output/archive/layout-migrations').glob('*/completed.jsonl'))) == 1
    assert not layout.check(root)
    bad = root / 'dev/myworld/assets/runtime/editable.aseprite'
    bad.write_bytes(b'Working master belongs in authoring')
    assert any('Authoring/work file' in message for message in layout.check(root))
    bad.unlink()
    # A target collision must reject the entire plan before moving any source.
    original.mkdir()
    (original / 'new.bin').write_bytes(b'Preserve a conflicting old output')
    rejects(lambda: layout.migrate_outputs(True, root))
    assert (original / 'new.bin').read_bytes() == b'Preserve a conflicting old output'
    assert moved.read_bytes() == payload

with tempfile.TemporaryDirectory(prefix='workspace-layout-symlink-') as directory:
    root = Path(directory)
    fixture(root)
    outside = root / 'outside'
    outside.mkdir()
    (root / 'output/art').symlink_to(outside, target_is_directory=True)
    rejects(lambda: layout.output_path('art', ['escape'], root))
    assert not list(outside.iterdir())

with tempfile.TemporaryDirectory(prefix='workspace-layout-active-') as directory:
    root = Path(directory)
    fixture(root)
    source = root / 'output/combat-test'
    source.mkdir()
    path = source / 'active.bin'
    path.write_bytes(b'An open output must not be moved')
    with path.open('rb'):
        rejects(lambda: layout.migrate_outputs(True, root))
    assert path.read_bytes() == b'An open output must not be moved'

build = ET.parse(ROOT / 'Client_Base/build.xml').getroot()
asset_sets = build.findall(".//copy[@todir='${build}/myworld-assets']/fileset")
assert len(asset_sets) == 1 and asset_sets[0].get('dir') == '../dev/myworld/assets/runtime'
assert not layout.check(ROOT), '\n'.join(layout.check(ROOT))
print('PASS output paths, traversal/symlink rejection, dry-run, exact relocation, collision/active-file preservation and runtime-only packaging')
