import {createSynchronousNativeCall} from './browser-native-call-dispatch.js';
const mode=new URLSearchParams(location.search).get('mode')||'values';
const proof=window.nativeRuntimeProof={completed:false,passed:false,mode,events:[],errors:[],calls:{},voidMarkers:[],console:[],windowErrors:[],runtime:'5.0',poisoned:false,poisonCount:0,poisonMessages:[],unhandled:[],scope:'Authentic CheerpJ value/fatal-boundary contract only; fresh page/JVM per mode; no game/GPU/FPS claim'};
const describe=error=>({message:String(error),stack:error?.stack?String(error.stack):null});
const bounded=value=>{try{return (value instanceof Error?String(value)+'\n'+(value.stack||''):typeof value==='string'?value:JSON.stringify(value)??String(value)).slice(0,3000);}catch{return '[unprintable diagnostic]';}};
const failureText='native-dispatch-probe-failure',consumedText='consumed future rejection';
let resolveFatal;
const fatalSignal=new Promise(resolve=>{resolveFatal=resolve;});
function inspectFatal(){
 if(proof.poisoned&&proof.console.some(entry=>entry.args.join(' ').includes('Exception in native code')&&entry.args.join(' ').includes('thread stopped'))){proof.runtimeFatal=true;resolveFatal({kind:'native-fatal'});}
}
for(const level of ['debug','log','info','warn','error']){const original=console[level];console[level]=function(...args){if(proof.console.length<64)proof.console.push({level,args:args.slice(0,8).map(bounded)});const result=original.apply(this,args);inspectFatal();return result;};}
window.addEventListener('error',event=>{if(proof.windowErrors.length<16)proof.windowErrors.push({message:String(event.message),error:event.error?describe(event.error):null,filename:event.filename,line:event.lineno,column:event.colno});});
window.addEventListener('unhandledrejection',event=>{if(proof.unhandled.length<16)proof.unhandled.push(describe(event.reason));inspectFatal();});
function poison(error){if(!proof.poisoned){proof.poisoned=true;proof.poisonCount++;proof.poisonMessages.push(String(error));}inspectFatal();}
const scalar=(_lib,a,b)=>(Math.imul(a,1664525)+b)|0;
const vector=(_lib,seed,values)=>{const result=new Int32Array(values.length);for(let i=0;i<values.length;i++)result[i]=values[i]^seed;return result;};
const failure=()=>{throw new Error(failureText);};
const sync=(name,body)=>createSynchronousNativeCall(body,{name,failure:poison});
const asyncWrap=body=>async(...args)=>{try{return await body(...args);}catch(error){poison(error);throw error;}};
const natives={
 Java_orsc_browser_NativeDispatchProbe_scalarAsync:asyncWrap(async(...args)=>scalar(...args)),
 Java_orsc_browser_NativeDispatchProbe_scalarSync:sync('scalarSync',scalar),
 Java_orsc_browser_NativeDispatchProbe_vectorAsync:asyncWrap(async(...args)=>vector(...args)),
 Java_orsc_browser_NativeDispatchProbe_vectorSync:sync('vectorSync',vector),
 Java_orsc_browser_NativeDispatchProbe_voidAsync:asyncWrap(async(_lib,marker)=>{proof.voidMarkers.push({mode:'async',marker});}),
 Java_orsc_browser_NativeDispatchProbe_voidSync:sync('voidSync',(_lib,marker)=>{proof.voidMarkers.push({mode:'sync',marker});}),
 Java_orsc_browser_NativeDispatchProbe_failureAsync:asyncWrap(async()=>failure()),
 Java_orsc_browser_NativeDispatchProbe_failureSync:sync('failureSync',failure),
 Java_orsc_browser_NativeDispatchProbe_unexpectedThenable:sync('unexpectedThenable',()=>Promise.reject(new Error(consumedText))),
 async Java_orsc_browser_NativeDispatchProbe_report(_lib,name,value){proof.events.push({name:String(name),value:String(value)});},
};
for(const name of Object.keys(natives)){const call=natives[name];natives[name]=(...args)=>{proof.calls[name]=(proof.calls[name]||0)+1;return call(...args);};}
async function boundedWait(promise,label,ms){let timer;try{return await Promise.race([promise,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error(label+' timed out')),ms);})]);}finally{clearTimeout(timer);}}
try{
 if(!['values','async-fatal','sync-fatal','thenable-fatal'].includes(mode))throw new Error('Invalid probe mode');
 await boundedWait(new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://cjrtnc.leaningtech.com/5.0/loader.js';script.onload=resolve;script.onerror=()=>reject(new Error('CheerpJ loader failed'));document.head.append(script);}), 'loader',45000);
 await boundedWait(cheerpjInit({version:8,natives}),'runtime initialization',45000);
 const run=cheerpjRunMain('orsc.browser.NativeDispatchProbe','/app/probe.jar',mode).then(exit=>{proof.javaExit=exit;return {kind:'exit',exit};},error=>{proof.runMainError=describe(error);return {kind:'run-error'};});
 const outcome=await boundedWait(mode==='values'?run:Promise.race([run,fatalSignal]),'Java native probe',45000);proof.outcome=outcome;
 await new Promise(resolve=>setTimeout(resolve,250));
 const values=Object.fromEntries(proof.events.map(({name,value})=>[name,value]));
 if(mode==='values'){
  if(proof.javaExit!==0||values.completed!=='true'||Number(values.checks)!==1903||proof.poisoned||proof.unhandled.length||JSON.stringify(proof.voidMarkers)!==JSON.stringify([{mode:'async',marker:-12345},{mode:'sync',marker:-2147483648}]))throw Error('Value conversion probe failed: '+JSON.stringify({outcome,values,voidMarkers:proof.voidMarkers}));
 }else{
  const expected=mode==='thenable-fatal'?'Unexpected asynchronous result':failureText;
  if(values.beforeNative!==mode||values.afterNative!==undefined||values.completed!==undefined||!proof.runtimeFatal||proof.poisonCount!==1||!proof.poisonMessages[0]?.includes(expected))throw Error('Fatal native boundary failed: '+JSON.stringify({outcome,values,poison:proof.poisonMessages}));
  if(proof.unhandled.some(error=>error.message.includes(consumedText)||!error.message.includes(expected)))throw Error('Unexpected/orphan native rejection: '+JSON.stringify(proof.unhandled));
 }
 proof.passed=true;
}catch(error){proof.errors.push(describe(error));}
finally{proof.completed=true;}
