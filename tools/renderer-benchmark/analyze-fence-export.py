#!/usr/bin/env python3
"""Validate and summarize a native cutout fence export; writes JSON to stdout."""
import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path


def runs(bits):
    result=[]; length=0
    for bit in [*bits,False]:
        if bit: length+=1
        elif length: result.append(length); length=0
    return result


def analyze(capture):
    paths=[capture/name for name in ('fence-triangles.tsv','fence-textures.tsv')]
    pixels={}; sizes={}; signatures={}
    for row in csv.DictReader(paths[1].read_text().splitlines(),delimiter='\t'):
        texture,x,y,width,height,rgba=[int(row[k]) for k in ('texture','x','y','width','height','rgba')]
        if texture not in (10,12) or not 0<=x<width or not 0<=y<height or not 0<=rgba<=0xffffffff:
            raise ValueError('Invalid native fence texture coordinate or value')
        size=(width,height)
        if texture in sizes and sizes[texture]!=size: raise ValueError('Inconsistent texture dimensions')
        sizes[texture]=size; signatures[texture]=row['signature']
        if (x,y) in pixels.setdefault(texture,{}): raise ValueError('Duplicate texture pixel')
        pixels[texture][x,y]=rgba
    triangles=collections.Counter(); samples={}; vertical_spans=collections.defaultdict(set)
    for row in csv.DictReader(paths[0].read_text().splitlines(),delimiter='\t'):
        texture=int(row['effectiveTexture'])
        if texture not in pixels: raise ValueError('Triangle references missing exported texture')
        triangles[texture]+=1
        vertical_spans[texture].add(max(int(row[f'y{i}']) for i in range(3))-min(int(row[f'y{i}']) for i in range(3)))
        samples.setdefault(texture,row)
    if not triangles: raise ValueError('No actual native fence triangles')
    result={}
    for texture,(width,height) in sizes.items():
        data=pixels[texture]
        if len(data)!=width*height: raise ValueError('Incomplete texture pixel grid')
        mask=lambda x,y: (data[x,y]&255)>127 # Exact binary depth threshold on uint8 alpha.
        horizontal=[n for y in range(height) for n in runs([mask(x,y) for x in range(width)])]
        vertical=[n for x in range(width) for n in runs([mask(x,y) for y in range(height)])]
        opaque=sum(mask(x,y) for x,y in data)
        result[str(texture)]=dict(width=width,height=height,signature=signatures[texture],triangles=triangles[texture],opaquePixels=opaque,totalPixels=width*height,opaqueFraction=opaque/(width*height),horizontalOpaqueRunHistogram=dict(sorted(collections.Counter(horizontal).items())),verticalOpaqueRunHistogram=dict(sorted(collections.Counter(vertical).items())),triangleYSpans=sorted(vertical_spans[texture]),sampleTriangle=samples.get(texture))
    return dict(schema=1,source='Immutable native Renderer3DFrame chunks and Renderer3DTextureData, not reconstructed art',files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in paths},textures=result,limits='Texture runs are source alpha features; shadow-space width depends on actual UV axes, quad span and sun projection. Not a temporal or FPS result.')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('capture',type=Path)
    print(json.dumps(analyze(parser.parse_args().capture),indent=2))
