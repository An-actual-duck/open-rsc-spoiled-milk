/** Private whole-overworld admission and independent actual GPU residency policy.
 * Ordinary paths and the full-GPU reference keep their previous capacities. */
export const WHOLE_ZONE_SOURCE=Object.freeze({
  fingerprint:'7e04367b1de143053f66d67a6e5c9b7ff02c00cedb4c917fc249e3902626c8cc',
  manifestSha256:'42e20c36f19bd7c5a2ce7cfd2d65c91809c8671ff4bdf7b4e8b45014e8290c2b',
  sectors:Object.freeze({0:445,1:444,2:441}),
  placements:Object.freeze({0:22981,1:1260,2:339})
});
export const WHOLE_ZONE_GPU_BYTES=3*1024*1024*1024;
export const WHOLE_ZONE_MAX_CHUNKS=8192;
export function wholeZonePolicy(mode,options){
  const value=options.get('wholezone');
  if(value!==null&&!['on','off'].includes(value))throw new Error('Invalid private wholezone policy');
  const enabled=value==='on';
  if(enabled&&(mode!=='webgl'||!options.has('benchmark')||options.get('renderworker')!=='on'||options.get('overlay')!=='coverage'||!['off',null].includes(options.get('overlaydelta'))))throw new Error('Whole zone requires private Worker WebGL full coverage');
  const residency=options.get('gpuresidency')||'full';
  if(!['full','bounded'].includes(residency)||residency==='bounded'&&!enabled)throw new Error('Bounded GPU residency requires private wholezone admission');
  const staticScene=options.get('staticscene')||'off';
  if(!['on','off'].includes(staticScene)||staticScene==='on'&&!enabled)throw new Error('Static scene reuse requires private wholezone admission');
  return {requested:enabled,enabled,residency,properties:enabled?['spoiledmilk.browserWholeZone=true',...(staticScene==='on'?['spoiledmilk.browserStaticSceneReuse=true']:[])]:[]};
}
export function wholeZoneCapacity(options){
  if(options.wholeZone!==true)return {enabled:false,gpuBytes:256*1024*1024,maxChunks:512};
  if(options.benchmarkControl!==true)throw new Error('Whole zone capacity is private benchmark only');
  const bounded=options.gpuResidency==='bounded';
  if(options.gpuResidency!==undefined&&!['full','bounded'].includes(options.gpuResidency))throw new Error('Invalid GPU residency policy');
  return {enabled:true,bounded,gpuBytes:bounded?256*1024*1024:WHOLE_ZONE_GPU_BYTES,maxChunks:bounded?512:WHOLE_ZONE_MAX_CHUNKS};
}
