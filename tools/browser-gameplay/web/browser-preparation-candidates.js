/** Startup controls for separately reviewed private experiments, never ordinary defaults. */
export function privatePreparationCandidates(mode,options) {
  if(mode!=='webgl'||!options.has('benchmark'))return {properties:[],requested:null};
  function selected(key){const value=options.get(key)||'off';if(!['on','off'].includes(value))throw new Error('Invalid private '+key+' policy');return value==='on';}
  const region=selected('regionprep'),descriptor=selected('descriptorwrite'),halo=selected('halolookup'),sceneryProjection=selected('sceneryprojection'),chunkCoverage=selected('chunkcoverage'),overlayDeltaTransport=selected('overlaydelta');
  const rectMode=options.get('overlayrect')||'off';
  if(!['off','observe','on','packed'].includes(rectMode))throw new Error('Invalid private overlayrect policy');
  if(rectMode!=='off'&&!overlayDeltaTransport)throw new Error('Private overlayrect requires overlaydelta=on');
  const overlayWriterSpans=selected('overlaywriterspans');
  if(overlayWriterSpans&&(!overlayDeltaTransport||!['on','packed'].includes(rectMode)))throw new Error('Private overlaywriterspans requires active rectangle transport');
  return {rectMode,properties:['spoiledmilk.browserRegionPreparation='+region,'spoiledmilk.browserResidentDescriptorWriting='+descriptor,'spoiledmilk.browserHaloHeightLookup='+halo,'spoiledmilk.browserSceneryProjectionWork='+sceneryProjection,'spoiledmilk.browserPrimitiveChunkCoverage='+chunkCoverage,'spoiledmilk.browserOverlayDeltaTransport='+overlayDeltaTransport,'spoiledmilk.browserOverlayRectMode='+rectMode,'spoiledmilk.browserOverlayWriterSpans='+overlayWriterSpans],requested:{region,descriptor,halo,sceneryProjection,chunkCoverage,overlayDeltaTransport,overlayWriterSpans}};
}
