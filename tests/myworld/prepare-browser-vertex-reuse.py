#!/usr/bin/env python3
"""Prepare a loopback A/B of the released browser client with exact terrain vertex reuse."""
import argparse
from array import array
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
PUBLIC_ORIGIN = 'https://play.rscspoiledmilk.com'
PUBLIC_SOCKET = 'wss://play.rscspoiledmilk.com:43505/'
HEADER = struct.Struct('<6I')
STRIDE = 48


def sha(data):
    return hashlib.sha256(data).hexdigest()


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f'Expected one source match for {old}')
    return text.replace(old, new)


def reindex(raw, resource):
    magic, version, stride, count, index_count, batch_count = HEADER.unpack_from(raw)
    expected = (resource['vertexCount'], resource['indexCount'], resource['batchIntCount'])
    start = HEADER.size
    index_offset = start + count * stride
    batch_offset = index_offset + index_count * 4
    if ((magic, version, stride) != (0x575A4332, 1, STRIDE)
            or (count, index_count, batch_count) != expected
            or batch_offset + batch_count * 4 != len(raw)):
        raise ValueError('Unexpected packed geometry shape')
    vertices = [raw[offset:offset + stride] for offset in range(start, index_offset, stride)]
    lookup = {}
    unique = []
    remap = []
    for vertex in vertices:
        if vertex not in lookup:
            lookup[vertex] = len(unique)
            unique.append(vertex)
        remap.append(lookup[vertex])
    old_indices = array('I')
    old_indices.frombytes(raw[index_offset:batch_offset])
    if sys.byteorder != 'little':
        old_indices.byteswap()
    if any(index >= count for index in old_indices):
        raise ValueError('Packed index points outside the vertex plane')
    new_indices = array('I', (remap[index] for index in old_indices))
    if any(unique[new] != vertices[old] for old, new in zip(old_indices, new_indices)):
        raise ValueError('A submitted per-index attribute changed')
    if sys.byteorder != 'little':
        new_indices.byteswap()
    return (HEADER.pack(magic, version, stride, len(unique), index_count, batch_count)
            + b''.join(unique) + new_indices.tobytes() + raw[batch_offset:]), len(unique)


def replace_staged(path, data):
    # The stage hard-links immutable release assets. Only replace directory entries.
    path.unlink()
    path.write_bytes(data)


def prepare_variant(source, output, prefix, port, ws_port):
    shutil.copytree(source, output, copy_function=os.link)
    assets = output / prefix
    file = assets / 'browser-public-alpha.js'
    js = file.read_text().replace(PUBLIC_ORIGIN, f'http://127.0.0.1:{port}')
    js = js.replace(PUBLIC_SOCKET, f'ws://127.0.0.1:{ws_port}/')
    js = replace_once(js, "'spoiledmilk.browserPublicOrigin='+PUBLIC_ORIGIN",
                      f"'spoiledmilk.browserPublicOrigin={PUBLIC_ORIGIN}'")
    replace_staged(file, js.encode())
    file = assets / 'browser-deployment-config.js'
    js = file.read_text().replace(PUBLIC_ORIGIN, f'http://127.0.0.1:{port}')
    js = js.replace(PUBLIC_SOCKET, f'ws://127.0.0.1:{ws_port}/')
    replace_staged(file, js.encode())
    file = assets / 'browser-bulk-staging.js'
    js = replace_once(file.read_text(), PUBLIC_ORIGIN, f'http://127.0.0.1:{port}')
    replace_staged(file, js.encode())
    return assets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('web', type=Path, help='Verified browser release web directory')
    parser.add_argument('--name', required=True)
    parser.add_argument('--center', nargs=2, default=[3, 13], type=int, metavar=('X', 'Y'))
    parser.add_argument('--radius', type=int, choices=range(1, 4), default=2)
    args = parser.parse_args()
    if not args.name or not all(char.isalnum() or char in '-_' for char in args.name):
        parser.error('Use a simple experiment name')
    source = args.web.resolve()
    release = json.loads((source / 'public-release.json').read_text())
    build = release['buildId']
    if release['assetPrefix'] != f'/assets/{build}/' or not all(c.isalnum() or c == '-' for c in build):
        raise ValueError('Expected a versioned browser source')
    prefix = f'assets/{build}'
    chunks = source / prefix / 'whole-zone'
    canonical_raw = (chunks / 'manifest.json').read_bytes()
    compact_raw = (chunks / 'compact-manifest.json').read_bytes()
    reference = json.loads(compact_raw)
    if (sha(canonical_raw) != release['canonicalGpuManifestSha256']
            or sha(compact_raw) != release['compactManifestSha256']
            or reference['canonicalCatalogSha256'] != sha(canonical_raw)):
        raise ValueError('Release geometry manifests failed provenance checks')
    source_cells = {row['id']: row for row in json.loads(canonical_raw)['chunks']}
    stage_root = Path(subprocess.check_output(
        ['python3', str(ROOT/'scripts/workspace-layout.py'), 'path', 'diagnostics',
         'browser-static-vertex-reuse', '--mkdir'], cwd=ROOT, text=True).strip())
    stage = stage_root / args.name
    stage.mkdir()
    control = prepare_variant(source, stage/'control', prefix, 41727, 43571)
    candidate = prepare_variant(source, stage/'reuse', prefix, 41728, 43571)
    candidate_chunks = candidate/'whole-zone'
    changed = []
    for row in reference['resources']:
        cell = source_cells[row['id']]
        if (cell['sourceKind'] != 'world' or cell['plane'] != 0
                or abs(cell['cellX'] - args.center[0]) > args.radius
                or abs(cell['cellY'] - args.center[1]) > args.radius):
            continue
        path = candidate_chunks / row['path']
        raw = path.read_bytes()
        if sha(raw) != row['sha256']:
            raise ValueError(f'Compact bytes failed verification for {row["id"]}')
        packed, vertices = reindex(raw, row)
        if len(packed) >= len(raw):
            continue
        replace_staged(path, packed)
        changed.append({'id': row['id'], 'cell': [cell['cellX'], cell['cellY']],
                        'beforeBytes': len(raw), 'afterBytes': len(packed),
                        'beforeVertices': row['vertexCount'], 'afterVertices': vertices})
        row.update(bytes=len(packed), sha256=sha(packed), vertexCount=vertices)
    if not changed:
        raise ValueError('No geometry could be reduced in the selected neighborhood')
    reference['vertexCount'] = sum(row['vertexCount'] for row in reference['resources'])
    reference['resourceFileBytes'] = sum(row['bytes'] for row in reference['resources'])
    reference['geometryGpuBytes'] = sum(row['vertexCount'] * STRIDE + row['indexCount'] * 4
                                       for row in reference['resources'])
    new_manifest = (json.dumps(reference, indent=2) + '\n').encode()
    replace_staged(candidate_chunks/'compact-manifest.json', new_manifest)

    file = candidate/'browser-whole-zone-config.js'
    js = file.read_text()
    body = json.loads(js.removeprefix('export default Object.freeze(').removesuffix(');\n'))
    body['compact'].update(manifestBytes=len(new_manifest), manifestSha256=sha(new_manifest))
    js = 'export default Object.freeze(' + json.dumps(body, separators=(',', ':')) + ');\n'
    replace_staged(file, js.encode())

    file = candidate/'browser-compact-catalog.js'
    js = file.read_text()
    selected = ','.join(str(row['id']) for row in changed)
    guard = f"const PRIVATE_REINDEXED=new Set([{selected}]);"
    js = replace_once(js, 'const HASH=', guard+'\nconst HASH=')
    js = replace_once(js, 'r.vertexCount!==original.vertexCount',
        '(PRIVATE_REINDEXED.has(r.id) ? !Number.isSafeInteger(r.vertexCount) || r.vertexCount<=0 || r.vertexCount>=original.vertexCount : r.vertexCount!==original.vertexCount)')
    js = replace_once(js, '{...original,path:r.path', '{...original,vertexCount:r.vertexCount,path:r.path')
    replace_staged(file, js.encode())

    assert (control/'whole-zone/compact-manifest.json').read_bytes() == compact_raw
    receipt = {'sourceBuild': build, 'changedChunks': changed,
               'savedBytes': sum(row['beforeBytes']-row['afterBytes'] for row in changed),
               'controlCompactSha256': sha(compact_raw), 'reuseCompactSha256': sha(new_manifest),
               'control': 'http://127.0.0.1:41727/', 'reuse': 'http://127.0.0.1:41728/',
               'scope': 'Loopback only. Per-index attributes, index order, batch bytes and canonical map identity preserved.'}
    (stage/'stage.json').write_text(json.dumps(receipt, indent=2)+'\n')
    print(stage)


if __name__ == '__main__':
    main()
