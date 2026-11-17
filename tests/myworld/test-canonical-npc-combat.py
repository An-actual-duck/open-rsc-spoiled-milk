#!/usr/bin/env python3
"""Run real modern NPC combat on a disposable map/config with no account database.

Build server/core.jar first with scripts/build-server.sh. Evidence is retained
under the declared tests output category, including on failure.
"""
from pathlib import Path
import subprocess
import tempfile
import sys
import json
import hashlib
import re

ROOT = Path(__file__).resolve().parents[2]


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check-authoring-migration', action='store_true',
        help='Also compare the initial migration with the frozen owner-approved authoring records')
    args = parser.parse_args()
    if args.check_authoring_migration:
        subprocess.run([sys.executable, str(ROOT / 'tools/generators/generate-npc-combat-catalog.py'), '--check'], check=True)
    output = Path(subprocess.check_output([sys.executable, str(ROOT / 'scripts/workspace-layout.py'),
        'path', 'tests', 'canonical-npc-combat', '--mkdir'], text=True).strip())
    run = Path(tempfile.mkdtemp(prefix='runtime-', dir=output))
    fixture = run / 'fixture'
    subprocess.run([sys.executable, str(ROOT / 'tests/myworld/prepare-cleric-well-fixture.py'), str(fixture)], check=True)
    # Reuse its isolated config/schema setup, then replace the well-specific
    # liquid/wall geometry with a flat combat test arena. Native collision stays
    # authoritative; opening rollback tiles cannot erase native water or walls.
    package = fixture / 'package'
    manifest = json.loads((package / 'manifest.json').read_text())
    for sector in manifest['terrainSectors']:
        path = package / sector['path']
        terrain = json.loads(path.read_text())
        tile = dict(terrain['runs'][0]['tile'])
        tile.update(overlay=3, verticalWall=0, horizontalWall=0, diagonalWall=0)
        terrain['runs'] = [dict(count=48*48, tile=tile)]
        path.write_text(json.dumps(terrain))
        sector['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    for placement in manifest['placementSets']:
        path = package / placement['path']
        data = json.loads(path.read_text())
        for family in ['scenery', 'npcs', 'groundItems', 'boundaries']:
            data[family] = []
        path.write_text(json.dumps(data))
        placement['sha256'] = hashlib.sha256(path.read_bytes()).hexdigest()
    (package / 'manifest.json').write_text(json.dumps(manifest))
    config = fixture / 'myworld.conf'
    config.write_text(re.sub(r'(?m)^layered_native_terrain_manifest_sha256:.*$',
        'layered_native_terrain_manifest_sha256: ' + hashlib.sha256((package / 'manifest.json').read_bytes()).hexdigest(), config.read_text()))
    classes = run / 'classes'
    classes.mkdir()
    sources = ['CurrentCombatHarness', 'MutableGameClock', 'SeededGameRandom', 'CanonicalNpcCombatFixture']
    cp = f'{ROOT}/server/core.jar:{ROOT}/server/lib/*'
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', cp, '-d', str(classes)] +
        [str(ROOT / f'server/test/com/openrsc/server/combat/{name}.java') for name in sources], check=True)
    log = run / 'runtime.log'
    with log.open('w') as stream:
        result = subprocess.run(['java', '-Xmx768m', '-cp', f'{classes}:{cp}',
            'com.openrsc.server.combat.CanonicalNpcCombatFixture'], cwd=fixture, stdout=stream, stderr=subprocess.STDOUT)
    print(log)
    if result.returncode:
        print('\n'.join(log.read_text().splitlines()[-24:]))
        raise SystemExit(result.returncode)
    lines = [line for line in log.read_text().splitlines() if 'PASS canonical NPC combat:' in line]
    assert len(lines) == 1, 'Runtime must report completion, not just exit successfully'
    print(lines[0])


if __name__ == '__main__':
    main()
