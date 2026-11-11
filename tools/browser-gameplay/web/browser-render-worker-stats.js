/** Closed bounded diagnostics: never clone renderer caches, typed planes or inputs. */
export const WORKER_SAMPLE_FIELDS=Object.freeze(['frameSamples','worldFrameSamples','gpuWorkSamples','jsSubmissionSamples','javaBridgeWorkSamples','worldFramePolicies','worldFrameSpriteGroups','physicalOutputWorldFramePolicies']);
const OBJECTS=new Set(['contextAttributes','worldReadiness','physicalOutput','overlayDeclaration','pose','spriteCompositeCache','opaqueAlphaProof']);
const HISTORIES=new Set(['ownedStateTransitions','physicalOutputTransitions','worldStreamSamples','limitations']);
function bounded(value,depth=0,budget={left:512}){
  if(--budget.left<0||depth>4)throw new Error('Worker stats metadata budget');
  if(value===null||typeof value==='boolean')return value;
  if(typeof value==='number'){if(!Number.isFinite(value))throw new Error('Nonfinite worker statistic');return value;}
  if(typeof value==='string'){if(value.length>4096)throw new Error('Worker statistic string budget');return value;}
  if(ArrayBuffer.isView(value)||value instanceof ArrayBuffer)throw new Error('Worker statistic retained plane forbidden');
  if(Array.isArray(value)){if(value.length>16)throw new Error('Worker statistic array budget');return value.map(v=>bounded(v,depth+1,budget));}
  if(value&&typeof value==='object'&&Object.getPrototypeOf(value)===Object.prototype){const entries=Object.entries(value);if(entries.length>64)throw new Error('Worker statistic field budget');const result={};for(const[k,v]of entries)result[k]=bounded(v,depth+1,budget);return result;}
  throw new Error('Unsupported worker statistic metadata');
}
export function createWorkerStatsSnapshot(stats){
  const counts={},submitted={};
  for(const key of WORKER_SAMPLE_FIELDS){
    const array=stats[key];if(!Array.isArray(array))throw new Error('Missing worker statistics history');counts[key]=0;submitted[key]=0;
    // Track actual pushes, including equal intervals and rings at full capacity.
    Object.defineProperty(array,'push',{value:function(...items){counts[key]+=items.length;return Array.prototype.push.apply(this,items);}});
  }
  return function snapshot(){
    const value={},samples={};
    for(const [key,v]of Object.entries(stats)){
      if(key==='measurement'||WORKER_SAMPLE_FIELDS.includes(key))continue;
      if(v===null||['number','boolean','string'].includes(typeof v))value[key]=bounded(v);
      else if(OBJECTS.has(key))value[key]=bounded(v);
      else if(HISTORIES.has(key))value[key]=bounded(v.slice(-16));
      // Unknown objects are intentionally absent, never a broad cache escape.
    }
    for(const key of WORKER_SAMPLE_FIELDS){const n=counts[key]-submitted[key];if(n){const a=stats[key];samples[key]=a.slice(-Math.min(n,a.length,4096));submitted[key]=counts[key];}}
    return {value,samples,timeOrigin:performance.timeOrigin};
  };
}
