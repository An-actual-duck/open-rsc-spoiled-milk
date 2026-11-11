/** Startup controls for separately reviewed private experiments, never ordinary defaults. */
export function privatePreparationCandidates(mode,options) {
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],requested:null};
  function selected(key){const value=options.get(key)||'off';if(!['on','off'].includes(value))throw new Error('Invalid private '+key+' policy');return value==='on';}
  const region=selected('regionprep'),descriptor=selected('descriptorwrite'),halo=selected('halolookup'),sceneryProjection=selected('sceneryprojection'),chunkCoverage=selected('chunkcoverage'),overlayDeltaTransport=selected('overlaydelta'),terrainQueryIndex=selected('terrainquery');
  const regionBuild=selected('regionbuild');
  const journalMode=options.get('overlayjournal')||'off';
  if(!['off','observe'].includes(journalMode))throw new Error('Invalid private overlayjournal policy');
  if(journalMode==='observe'&&!overlayDeltaTransport)throw new Error('Private overlayjournal requires overlaydelta=on');
  const rectMode=options.get('overlayrect')||'off';
  if(!['off','observe','on','packed'].includes(rectMode))throw new Error('Invalid private overlayrect policy');
  if(rectMode!=='off'&&!overlayDeltaTransport)throw new Error('Private overlayrect requires overlaydelta=on');
  return {rectMode,journalObserve:journalMode==='observe',properties:['spoiledmilk.browserRegionBuild='+regionBuild,'spoiledmilk.browserOverlayJournalObserve='+(journalMode==='observe'),'spoiledmilk.browserRegionPreparation='+region,'spoiledmilk.browserResidentDescriptorWriting='+descriptor,'spoiledmilk.browserHaloHeightLookup='+halo,'spoiledmilk.browserSceneryProjectionWork='+sceneryProjection,'spoiledmilk.browserPrimitiveChunkCoverage='+chunkCoverage,'spoiledmilk.browserOverlayDeltaTransport='+overlayDeltaTransport,'spoiledmilk.browserOverlayRectMode='+rectMode,'spoiledmilk.browserTerrainQueryIndex='+terrainQueryIndex],requested:{regionBuild,region,descriptor,halo,sceneryProjection,chunkCoverage,overlayDeltaTransport,terrainQueryIndex}};
}
