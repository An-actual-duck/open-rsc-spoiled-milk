#!/usr/bin/env python3
"""Validate unique native telemetry events and a populated acknowledged route."""
import argparse,csv,json,math
from pathlib import Path

def summary(values):
    if not values: raise ValueError('No events')
    values=sorted(values);percentile=lambda q:values[max(0,math.ceil(q*len(values))-1)]/1e6
    return dict(count=len(values),p50Ms=percentile(.5),p95Ms=percentile(.95),p99Ms=percentile(.99),maxMs=max(values)/1e6,over16_667ms=sum(x>1e9/60 for x in values),over33_334ms=sum(x>1e9/30 for x in values),over50ms=sum(x>50e6 for x in values),over100ms=sum(x>100e6 for x in values))

def events(path):
    stages={}
    for row in csv.DictReader(path.open()):
        stage=row['stage'];seq=int(row['sequence']);end=int(row['anchorNanos']);duration=int(row['durationNanos'])
        if seq<0 or end<0 or duration<0:raise ValueError('Invalid event')
        seen=stages.setdefault(stage,{})
        if seq in seen:raise ValueError('Duplicate sequence')
        seen[seq]=(end,duration)
    for stage,values in stages.items():
        seqs=sorted(values)
        if len(seqs)!=seqs[-1]-seqs[0]+1:raise ValueError('Missing events: '+stage)
    return stages

def analyze(capture):
    if not (capture/'CLIENT-DONE').is_file():raise ValueError('Incomplete client run')
    route=json.loads((capture/'route.json').read_text());plan=json.loads((capture/'route-plan.json').read_text())
    if not route.get('accepted') or len(route['legs'])!=10:raise ValueError('Incomplete route')
    overhead=json.loads((capture/'observer-overhead.json').read_text())
    if overhead['failed']:raise ValueError('Observer failed')
    stages=events(capture/'unique-frame-events.csv');cadence=stages['openGLFrameIntervalStats'];legs=[]
    for i,leg in enumerate(route['legs']):
        step=leg['step'];state=leg['server'];after=leg['after'];before=leg['before']
        if step!=plan['legs'][i] or leg['index']!=i:raise ValueError('Plan mismatch')
        for key in ['x','y','level']:
            if state[key]!=step[key] or after[key]!=step[key]:raise ValueError('Arrival mismatch')
        if state['routeId']!=i+1 or state['controlledPlayers']!=16 or state['controlledNpcs']!=32:raise ValueError('Authority/population mismatch')
        for observed in (before,after):
            if (observed.get('viewportWidth'),observed.get('viewportHeight'))!=(1920,1080):raise ValueError('Route viewport mismatch')
            if (observed.get('shadowQuality'),observed.get('profile'),observed.get('lighting'),observed.get('fog'),observed.get('zoom'),observed.get('rotation'))!=('HIGH','REMASTER','DIRECTIONAL','ON',75,128):raise ValueError('Route rendering settings mismatch')
        if not after.get('ready') or after.get('players',0)<16 or after.get('npcs',0)<32:raise ValueError('Received crowd/readiness missing')
        if step['kind']=='walk' and (before['baseX'],before['baseZ'])==(after['baseX'],after['baseZ']):raise ValueError('No actual boundary crossing')
        start=leg['requestNanos'];end=leg['endNanos']
        if not start<leg['observedArrivalNanos']<end:raise ValueError('Invalid leg timestamps')
        # Include entire intervals intersecting the leg; never clip a crossing pause.
        values=[duration for finish,duration in cadence.values() if finish>=start and finish-duration<=end]
        legs.append(dict(index=i,kind=step['kind'],arrivalReadyUpperBoundMs=(leg['observedArrivalNanos']-start)/1e6,frames=summary(values)))
    if sum(x['kind']=='walk' for x in legs)!=6:raise ValueError('Six adjacent crossings required')
    context=[json.loads(x) for x in (capture/'client-samples.jsonl').read_text().splitlines()]
    if not context or any((r['contentViewportWidth'],r['contentViewportHeight'])!=(1920,1080) or r['shadowQuality']!='HIGH' for r in context):raise ValueError('Normal 1080p High quality not observed')
    return dict(accepted=True,observerOverhead=overhead,stages={k:summary([x[1] for x in v.values()]) for k,v in stages.items()},legs=legs,
        command=json.loads((capture/'command.json').read_text()),artifact=json.loads((capture.parent/'RENDERER-BENCHMARK.json').read_text()),
        limits=['Instrumented cadence with observer overhead recorded; not uninstrumented FPS.',
        'Hidden-window post-swap CPU-observed intervals, not scanout or visible presentation acceptance.',
        'First coherent useful presented world unavailable: frozen baseline has no completed-frame source identity.',
        'Arrival/readiness is polling upper bound, not first-frame latency. Per-leg intersecting intervals may overlap adjacent windows.',
        'No cold/warm labels: repeated visits are retained but not separately controlled cold/warm cohorts.',
        'Only cadence anchorNanos is reconstructed event-end time. Non-cadence anchorNanos is the latest completed-frame drain anchor, NOT its stage event timestamp; whole-run aggregates only, never per-leg stage attribution.',
        'World/render/upload stage counts are independent; do not join them to frame intervals by equal sequence.'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('capture',type=Path);a=p.parse_args();result=analyze(a.capture)
    (a.capture/'native-route-summary.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'stages':result['stages'],'legs':result['legs']},indent=2))
if __name__=='__main__':main()
