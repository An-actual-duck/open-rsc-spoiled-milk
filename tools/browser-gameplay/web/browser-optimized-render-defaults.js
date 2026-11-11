/** Adopted browser startup defaults. Benchmark controls retain their literal
 * omitted=OFF semantics; URL switches are not product settings. */
export function optimizedRenderDefaults(mode,options) {
  if(mode!=='webgl')return {properties:[],requested:null,automatic:false};
  const automatic=!options.has('benchmark');
  function selected(key){if(automatic)return true;const value=options.get(key)||'off';if(!['on','off'].includes(value))throw new Error('Invalid private '+key+' policy');return value==='on';}
  const modelBatch=selected('modelbatch'),terrainQuery=selected('terrainquery');
  return {automatic,requested:{modelBatch,terrainQuery},properties:['spoiledmilk.browserBatchedModelKernel='+modelBatch,'spoiledmilk.browserTerrainQueryIndex='+terrainQuery]};
}
/** The two adopted properties have one startup authority, regardless of legacy
 * experiment-list composition. No duplicated-key ordering determines behavior. */
export function canonicalRenderProperties(properties,resolved) {
  const keys=new Set(['spoiledmilk.browserBatchedModelKernel','spoiledmilk.browserTerrainQueryIndex']);
  return properties.filter(value=>!keys.has(value.slice(0,value.indexOf('=')))).concat(resolved.properties);
}
