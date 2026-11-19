#!/usr/bin/env python3
"""Add timing-only instrumentation to a second, private copy of a loopback pilot."""
import argparse
import json
import os
from pathlib import Path
import shutil


def instrument(source):
    def insert(anchor, replacement):
        nonlocal source
        if source.count(anchor) != 1:
            raise ValueError(f'Expected one packaged renderer anchor: {anchor}')
        source = source.replace(anchor, replacement)

    insert('  function startBoundedResidency(){', '''  let regionTraceSequence=0,regionTraceEvents=[],regionTraceActive=false;
  function traceRegion(event,...args){
    if(!regionTraceActive)return;
    regionTraceEvents.push([++regionTraceSequence,Math.round(performance.timeOrigin+performance.now()),event,...args]);
    if(regionTraceEvents.length>40)regionTraceEvents.shift();
    stats.regionCrossingTrace=JSON.stringify(regionTraceEvents);
  }
  function startBoundedResidency(){''')
    insert('prepare:async(c,signal)=>{alive();const raw=await fetchWholeZoneBytes',
           '''prepare:async(c,signal)=>{alive();const required=boundedResidency.required.has(c.id);
        traceRegion('fetch',c.id,c.bytes,required);
        const raw=await fetchWholeZoneBytes''')
    insert('{signal});if(await digestWholeZoneBytes(raw)!==c.sha256)',
           '''{signal});traceRegion('read',c.id,required);
        if(await digestWholeZoneBytes(raw)!==c.sha256)''')
    insert("alive();if(signal.aborted)throw new Error('Cancelled bounded source');zoneStats",
           "alive();if(signal.aborted)throw new Error('Cancelled bounded source');traceRegion('verified',c.id,required);zoneStats")
    insert('createUpload:(c,buffer)=>createBoundedChunkUpload(gl,',
           "createUpload:(c,buffer)=>{traceRegion('upload-start',c.id,boundedResidency.required.has(c.id));return createBoundedChunkUpload(gl,")
    insert('zoneStats.preparedResources++;}}),',
           "zoneStats.preparedResources++;traceRegion('uploaded',c.id,boundedResidency.required.has(c.id));}});},")
    insert('    const ready=boundedResidency.select(required,optional,wholeZoneSelection.generationKey);',
           '''    const priorGeneration=stats.boundedResidency.generation,priorReady=stats.wholeZone.ready;
    const ready=boundedResidency.select(required,optional,wholeZoneSelection.generationKey);
    if(priorGeneration!==stats.boundedResidency.generation){
      const missing=required.filter(id=>!chunks.has(id));
      traceRegion('scope',required.length,missing.length,missing.filter(id=>optional.includes(id)).length);
    }
    if(ready&&!priorReady)traceRegion('coverage',stats.boundedResidency.uploads,stats.boundedResidency.totalTickMilliseconds,stats.boundedResidency.totalSliceMilliseconds||0);''')
    insert('regionTransition(lib,soft){alive();', '''regionTransition(lib,soft){alive();
      if(soft===true){regionTraceActive=true;traceRegion('transition',stats.boundedResidency?.uploads||0,stats.boundedResidency?.totalTickMilliseconds||0,stats.boundedResidency?.totalSliceMilliseconds||0);}''')
    insert('if(world&&!retainedWorld){boundedFrameGap?.resumed(now);',
           '''if(world&&!retainedWorld){
        const heldBefore=stats.boundedResidency?.completedHeldIntervals;
        boundedFrameGap?.resumed(now);
        if(heldBefore!==stats.boundedResidency?.completedHeldIntervals)traceRegion('fresh-frame',stats.boundedResidency.lastHeldFreshGapMilliseconds);''')
    return source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pilot', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    pilot = args.pilot.resolve()
    host = json.loads((pilot / 'stage.json').read_text())
    if host['control'] != 'http://127.0.0.1:41727/':
        raise ValueError('Expected a loopback control from the vertex reuse pilot')
    renderer_relative = Path('assets') / host['sourceBuild'] / 'webgl.js'
    modified = instrument((pilot / 'control' / renderer_relative).read_text())
    shutil.copytree(pilot / 'control', args.output, copy_function=os.link)
    renderer = args.output / renderer_relative
    renderer.unlink()
    renderer.write_text(modified)


if __name__ == '__main__':
    main()
