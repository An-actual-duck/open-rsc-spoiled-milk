#!/usr/bin/env python3
"""Movement fixtures only: no application startup, server, or account mutation."""
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'dev/myworld/assets/runtime/sprites/npcs/slayer-monsters'
JAR = ROOT / 'Client_Base/Open_RSC_Client.jar'
manifest = json.loads((ASSETS / 'provenance.json').read_text())['entries']
defs = json.loads((ROOT / 'server/conf/server/defs/SlayerMovementPreviewNpcDefs.json').read_text())['npcs']
assert [d['id'] for d in defs] == list(range(863, 871))
assert [e['id'] for e in manifest] == list(range(863, 871))
for entry, npc in zip(manifest, defs):
    data = (ASSETS / (entry['key'] + '.png')).read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry['sha256']
    assert struct.unpack('>II', data[16:24]) == (sum(entry['columns']), entry['height'])
    assert data[25] == 6, 'RGBA required'
    assert npc['aggressive'] == 0
    assert npc['attackable'] == 1
    assert npc['command'] == npc['command2'] == ''
    assert npc['roundMode'] == 1 and npc['walkModel'] == 10
    assert npc['camera1'] == entry['columns'][0] * 12 // 5
    assert npc['camera2'] == (entry['height'] // 3) * 12 // 5
    assert all(npc[k] == 1 for k in ('attack', 'strength', 'defense'))
    assert (npc['hits'], npc['combatlvl']) == {863: (30, 20), 864: (45, 35), 865: (60, 50), 866: (75, 60), 867: (90, 75), 868: (150, 85), 869: (200, 105), 870: (250, 125)}[npc['id']]
    assert [npc['sprites' + str(i)] for i in range(1, 13)] == [0] + [-1] * 11
    with zipfile.ZipFile(JAR) as jar:
        assert jar.read('myworld-assets/sprites/npcs/slayer-movement-preview/' + entry['key'] + '.png') == data

# These stable sprite identities are now production encounters. Only the explicit
# Banshee undead-classification overlay is approved outside their base definitions.
# Do not broadly exempt other files or silently accept duplicate gameplay records.
allowed_overrides = {865: {'id': 865, 'undead': True}}
seen_overrides = set()
for path in (ROOT / 'server/conf/server/defs').rglob('*.json'):
    if path.name == 'SlayerMovementPreviewNpcDefs.json':
        continue
    content = json.loads(path.read_text())
    if isinstance(content, dict):
        for npc in content.get('npcs', []):
            npc_id = npc.get('id')
            if npc_id in range(863, 871):
                assert path.name == 'NpcDefsMyWorld.json', f'Unapproved production definition overlap: {path}'
                assert npc_id in allowed_overrides and npc == allowed_overrides[npc_id], f'Unreviewed Slayer override: {npc}'
                assert npc_id not in seen_overrides, f'Duplicate Slayer override: {npc_id}'
                seen_overrides.add(npc_id)
assert seen_overrides == set(allowed_overrides), 'Approved Banshee undead metadata is missing'
handler = (ROOT / 'server/src/com/openrsc/server/external/EntityHandler.java').read_text()
assert handler.index('/defs/MyWorldNpcDefs.json') < handler.index('/defs/SlayerMovementPreviewNpcDefs.json')
assert handler.index('/defs/SlayerMovementPreviewNpcDefs.json') < handler.index('applyOptionalNpcOverrides(getServer().getConfig().CONFIG_DIR + "/defs/NpcDefsMyWorld.json")')
client = (ROOT / 'Client_Base/src/orsc/mudclient.java').read_text()
assert client.count('loadSlayerMovementPreviewSprites();') == 2
assert 'preview.movementFrame(npc.stepFrame, def.getWalkModel(), moving)' in client
with tempfile.TemporaryDirectory(prefix='slayer-movement-test-') as scratch:
    subprocess.run(['javac', '-J-Xmx128m', '-cp', str(JAR), '-d', scratch,
                    str(ROOT / 'tests/myworld/SlayerMovementPreviewFixture.java')], check=True)
    for cwd in (str(ROOT), scratch):
        subprocess.run(['java', '-Xmx256m', '-XX:+UseSerialGC', '-Djava.awt.headless=true', '-cp', scratch + ':' + str(JAR),
                        'orsc.SlayerMovementPreviewFixture', cwd], cwd=cwd, check=True)
print('PASS manifest hashes, packaged assets, stable production sprite definitions, exact undead overlay, native poses and disk/JAR loading')
