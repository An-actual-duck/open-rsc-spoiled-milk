/** Startup controls for separately reviewed private experiments, never ordinary defaults. */
export function privatePreparationCandidates(mode,options) {
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],requested:null};
  function selected(key){const value=options.get(key)||'off';if(!['on','off'].includes(value))throw new Error('Invalid private '+key+' policy');return value==='on';}
  const region=selected('regionprep'),descriptor=selected('descriptorwrite'),halo=selected('halolookup');
  return {properties:['spoiledmilk.browserRegionPreparation='+region,'spoiledmilk.browserResidentDescriptorWriting='+descriptor,'spoiledmilk.browserHaloHeightLookup='+halo],requested:{region,descriptor,halo}};
}
