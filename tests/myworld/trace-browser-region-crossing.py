#!/usr/bin/env python3
"""Prepare a traced loopback control or a private scenery prewarm candidate."""
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

    insert('  function startBoundedResidency(){', '''  let regionTraceSequence=0,regionTraceEvents=[],regionTraceActive=false,regionTraceNeedsFrame=false;
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
      if(soft===true){regionTraceActive=true;regionTraceNeedsFrame=true;traceRegion('transition',stats.boundedResidency?.uploads||0,stats.boundedResidency?.totalTickMilliseconds||0,stats.boundedResidency?.totalSliceMilliseconds||0);}''')
    insert('if(world&&!retainedWorld){boundedFrameGap?.resumed(now);',
           '''if(world&&!retainedWorld){
        const heldBefore=stats.boundedResidency?.completedHeldIntervals;
        boundedFrameGap?.resumed(now);
        if(heldBefore!==stats.boundedResidency?.completedHeldIntervals)traceRegion('fresh-frame',stats.boundedResidency.lastHeldFreshGapMilliseconds);
        if(regionTraceNeedsFrame){regionTraceNeedsFrame=false;traceRegion('first-scope-frame',lastWorld>0?now-lastWorld:null);}''')
    return source


def add_current_footprint(source):
    anchor = '    cached=out;return cached;'
    if source.count(anchor) != 1:
        raise ValueError('Expected the packaged cardinal prefetch planner')
    return source.replace(anchor, """    // These authored meshes are optional cache entries. The live merge still
    // selects the only drawable IDs, and required coverage still has priority.
    for(let cx=(x-2)*2;cx<(x+3)*2;cx++)for(let cy=(y-2)*2;cy<(y+3)*2;cy++){
      const id=cells.get('1:0:'+cx+':'+cy);
      if(id!==undefined&&!seen.has(id)){out.push(id);seen.add(id);}
    }
    cached=out;return cached;""")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pilot', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--prewarm-scenery', action='store_true')
    args = parser.parse_args()
    pilot = args.pilot.resolve()
    host = json.loads((pilot / 'stage.json').read_text())
    if host['control'] != 'http://127.0.0.1:41727/':
        raise ValueError('Expected a loopback control from the vertex reuse pilot')
    renderer_relative = Path('assets') / host['sourceBuild'] / 'webgl.js'
    modified = instrument((pilot / 'control' / renderer_relative).read_text())
    files = {renderer_relative: modified}
    if args.prewarm_scenery:
        planner = renderer_relative.with_name('browser-bounded-prefetch.js')
        files[planner] = add_current_footprint((pilot / 'control' / planner).read_text())
    shutil.copytree(pilot / 'control', args.output, copy_function=os.link)
    for path, source in files.items():
        staged = args.output / path
        staged.unlink()
        staged.write_text(source)


if __name__ == '__main__':
    main()
