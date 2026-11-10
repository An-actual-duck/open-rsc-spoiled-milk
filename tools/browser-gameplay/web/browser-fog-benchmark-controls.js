/** Source-identical startup controls for the private load investigation only. */
export function browserFogBenchmarkControls(mode,options) {
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],requested:null};
  const policy=options.get('fogbounded');
  if(policy!==null&&policy!=='on'&&policy!=='off')throw new Error('fogbounded must be on or off');
  return {properties:['spoiledmilk.browserFogBoundedPreparation='+(policy==='on'),'spoiledmilk.browserLoadProfile='+(options.get('loadprofile')==='1')],requested:policy==='on'};
}
