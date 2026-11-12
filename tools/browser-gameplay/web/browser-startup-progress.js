/** Startup-only observations. None of these records admits a world frame. */
const STAGES=Object.freeze(['opening','archives','display','runtime-download','runtime-start','data-download','assets','data','client']);
const LABELS=Object.freeze({opening:'Opening game…',archives:'Loading and checking game files…',display:'Preparing graphics…','runtime-download':'Loading game runtime…','runtime-start':'Starting game runtime…','data-download':'Loading game data…',assets:'Preparing game files…',data:'Loading and preparing game data…',client:'Opening sign-in screen…'});
const LIMIT=128,MAX_BYTES=2**40,MAX_FILES=100000;
const KEYS=['version','sequence','stage','bytes','totalBytes','writtenBytes','files','totalFiles','elapsedMillis','workMillis'].sort().join('|');
const count=(n,max)=>Number.isSafeInteger(n)&&n>=0&&n<=max;
export function validStartupProgress(value){
  return !!value&&Object.keys(value).sort().join('|')===KEYS&&value.version===1&&count(value.sequence,1e9)&&value.sequence>0&&STAGES.includes(value.stage)&&
    count(value.bytes,MAX_BYTES)&&(value.totalBytes===null||count(value.totalBytes,MAX_BYTES)&&value.totalBytes>=value.bytes)&&
    (value.writtenBytes===null||count(value.writtenBytes,MAX_BYTES)&&(value.totalBytes===null||value.writtenBytes<=value.totalBytes))&&
    count(value.files,MAX_FILES)&&(value.totalFiles===null||count(value.totalFiles,MAX_FILES)&&value.totalFiles>=value.files)&&
    Number.isFinite(value.elapsedMillis)&&value.elapsedMillis>=0&&value.elapsedMillis<=7*86400000&&
    (value.workMillis===null||Number.isFinite(value.workMillis)&&value.workMillis>=0&&value.workMillis<=7*86400000);
}
function follows(previous,next){
  return !previous||next.sequence>previous.sequence&&next.elapsedMillis>=previous.elapsedMillis&&
    STAGES.indexOf(next.stage)>=STAGES.indexOf(previous.stage)&&
    (next.stage!==previous.stage||next.bytes>=previous.bytes&&next.files>=previous.files&&
      (previous.writtenBytes===null||next.writtenBytes!==null&&next.writtenBytes>=previous.writtenBytes)&&
      (previous.totalBytes===null||next.totalBytes===previous.totalBytes)&&
      (previous.totalFiles===null||next.totalFiles===previous.totalFiles));
}
function remember(state,value,at){
  const prior=state.phase;
  if(prior&&prior!==value.stage)state.phases[prior].completedMillis=at;
  const phase=state.phases[value.stage]??={startedMillis:at};
  Object.assign(phase,{latestMillis:at,bytes:value.bytes,writtenBytes:value.writtenBytes,files:value.files,totalBytes:value.totalBytes,totalFiles:value.totalFiles,workMillis:value.workMillis});
  state.phase=value.stage;
}
function completePhase(state,at){if(state.phase)state.phases[state.phase].completedMillis=at;}
/** Child clock begins after its module graph. Parent records click-to-child separately. */
export function createStartupProgress({now=()=>performance.now(),emit=()=>{}}={}){
  const start=now(),records=[];let previous=null,closed=false,sequence=0;
  const state={scope:'Startup observations only; byte counts name downloaded or written work, not world readiness',records,phases:{},complete:false};
  return {stats:state,update(stage,changes={}){
    if(closed)return false;
    const prior=previous?.stage===stage?previous:{bytes:0,totalBytes:null,writtenBytes:null,files:0,totalFiles:null,workMillis:null};
    const value={version:1,sequence:sequence+1,stage,bytes:changes.bytes??prior.bytes,totalBytes:changes.totalBytes??prior.totalBytes,writtenBytes:changes.writtenBytes??prior.writtenBytes,files:changes.files??prior.files,totalFiles:changes.totalFiles??prior.totalFiles,elapsedMillis:Math.max(0,now()-start),workMillis:changes.workMillis??prior.workMillis};
    if(!validStartupProgress(value)||!follows(previous,value))return false;
    previous=value;sequence++;records.push(value);if(records.length>LIMIT)records.shift();state.latest=value;remember(state,value,value.elapsedMillis);
    try{emit({...value});}catch{state.observerErrors=Math.min(1000,(state.observerErrors||0)+1);}return true;
  },finish(mode){if(closed||!['LOGIN','GAME'].includes(mode))return false;closed=true;state.complete=true;state.mode=mode;state.elapsedMillis=Math.max(0,now()-start);completePhase(state,state.elapsedMillis);return true;},close(){closed=true;state.closed=true;}};
}
/** Owner creates a fresh receiver for each iframe. Closed receivers never reopen. */
export function createStartupReceiver({now=()=>performance.now(),render=()=>{}}={}){
  const start=now(),records=[];let previous=null,closed=false;
  const state={scope:'Parent Play-to-login observations; initial world readiness remains independently guarded',records,phases:{opening:{startedMillis:0,latestMillis:0}},phase:'opening',complete:false};
  return {stats:state,accept(value){
    if(closed||!validStartupProgress(value)||!follows(previous,value))return false;
    const receivedMillis=Math.max(0,now()-start);previous={...value};const owned={...value,receivedMillis};
    records.push(owned);if(records.length>LIMIT)records.shift();state.latest=owned;remember(state,value,receivedMillis);
    render(startupPresentation(value));return true;
  },finish(mode){if(closed||!['LOGIN','GAME'].includes(mode))return false;closed=true;state.complete=true;state.mode=mode;state.elapsedMillis=Math.max(0,now()-start);completePhase(state,state.elapsedMillis);return true;},close(){closed=true;state.closed=true;}};
}
export function startupPresentation(value){
  const label=LABELS[value.stage];let detail='',valueNow=null,max=null;
  if(value.totalBytes!==null&&value.totalBytes>0){
    const mb=n=>(n/1000000).toFixed(1);detail=`${mb(value.bytes)} of ${mb(value.totalBytes)} MB`;
    if(value.stage==='data'&&value.writtenBytes!==null){
      valueNow=value.writtenBytes;
      if(value.bytes===0){detail=`${mb(value.writtenBytes)} of ${mb(value.totalBytes)} MB prepared`;valueNow=value.writtenBytes;}
      else detail+=` loaded · ${mb(value.writtenBytes)} MB prepared`;
    }else if(value.stage==='assets'||value.stage==='data')detail+=' prepared';
    else if(value.stage==='archives')detail+=' received';
    else detail+=' loaded';
    if(valueNow===null)valueNow=value.bytes;max=value.totalBytes;
  }else if(value.totalFiles!==null&&value.totalFiles>0){detail=`${value.files} of ${value.totalFiles} files prepared`;valueNow=value.files;max=value.totalFiles;}
  return {label,detail,value:valueNow,max};
}
