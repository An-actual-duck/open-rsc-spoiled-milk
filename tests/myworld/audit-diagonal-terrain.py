#!/usr/bin/env python3
"""Inventory diagonal terrain and compare roof triangles with receiver coverage."""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import struct


SIZE = 48
ENCODINGS = {
    'raw-layered-sector-v1': struct.Struct('>6BI'),
    'raw-layered-sector-v2-u16': struct.Struct('>H5BI'),
}


def audit(package, manifest_sha):
    manifest = (package / 'manifest.json').read_bytes()
    if hashlib.sha256(manifest).hexdigest() != manifest_sha:
        raise ValueError('Terrain manifest does not match the requested snapshot')
    source = json.loads(manifest)
    sectors = {}
    for row in source['terrainSectors']:
        decoder = ENCODINGS[row['encoding']]
        path = (package / row['path']).resolve()
        if not path.is_relative_to(package):
            raise ValueError(f'Terrain path escapes package: {row["path"]}')
        data = path.read_bytes()
        if len(data) != SIZE * SIZE * decoder.size or hashlib.sha256(data).hexdigest() != row['sha256']:
            raise ValueError(f'Terrain sector failed size or hash check: {row["path"]}')
        sectors[(row['worldSpace'], row['level'], row['sectorX'], row['sectorY'])] = decoder, data

    def tile(space, level, x, z):
        sx, tx = divmod(x, SIZE)
        sz, tz = divmod(z, SIZE)
        sector = sectors.get((space, level, sx, sz))
        if sector is None:
            return None
        decoder, data = sector
        return decoder.unpack_from(data, (tx * SIZE + tz) * decoder.size)

    levels = {}
    roof_mismatches = []
    example_tiles = {}
    for (space, level, sx, sz), (decoder, data) in sectors.items():
        totals = levels.setdefault(f'{space}:{level}', Counter())
        totals['sectors'] += 1
        for i, (elevation, color, overlay, roof, _, _, diagonal) in enumerate(decoder.iter_unpack(data)):
            if 0 < diagonal < 12000:
                spelling = 'northwest-southeast'
                neighbors = ((1, -1), (-1, 1))
                first_half = 'x<=z'
            elif 12000 < diagonal < 24000:
                spelling = 'northeast-southwest'
                neighbors = ((-1, -1), (1, 1))
                first_half = 'x+z>=128'
            else:
                continue
            x, z = sx * SIZE + i // SIZE, sz * SIZE + i % SIZE
            totals[spelling] += 1
            if overlay:
                totals['withOverlay'] += 1
            edge = [tile(space, level, x+dx, z+dz) for dx, dz in ((1, 0), (0, 1), (1, 1))]
            sloped = all(edge) and any(row[0] != elevation for row in edge)
            if sloped:
                totals['withSlope'] += 1
            identity = dict(worldSpace=space, level=level, x=x, z=z, diagonal=diagonal, wall=spelling,
                            tileElevation=elevation, texture=color, overlay=overlay, roof=roof)
            if overlay:
                example_tiles.setdefault((space, level, spelling, bool(sloped)), identity)
            if not roof:
                continue
            totals['withRoof'] += 1
            adjacent = [tile(space, level, x+dx, z+dz) for dx, dz in neighbors]
            if any(row is None for row in adjacent):
                totals['missingRoofNeighborSector'] += 1
                continue
            if not any(row[3] for row in adjacent):
                totals['singleHalfMeshFullMask'] += 1
                roof_mismatches.append(dict(identity, meshHalf=first_half, oldReceiverCoverage='full',
                                            roofNeighbors=[row[3] for row in adjacent]))

    return {'packageId': source['packageId'], 'manifestSha256': manifest_sha,
            'sectors': len(sectors), 'levels': levels,
            'roofMeshReceiverMismatches': roof_mismatches,
            'diagonalOverlayReviewTiles': list(example_tiles.values())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package', type=Path)
    parser.add_argument('manifest_sha256')
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    result = audit(args.package.resolve(), args.manifest_sha256)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(f'{result["sectors"]} sectors. '
          f'{len(result["roofMeshReceiverMismatches"])} single-half roofs with full-tile masks.')


if __name__ == '__main__':
    main()
