import {createSynchronousNativeCall} from './browser-native-call-dispatch.js';
const proof=window.nativeRuntimeProof={completed:false,passed:false,events:[],errors:[],calls:{},console:[],windowErrors:[],runtime:'5.0',helperFailures:0,unhandled:[],scope:'Authentic CheerpJ scalar/int[]/JavaThrowable contract only; no game/GPU/FPS claim'};
const describe=error=>({message:String(error),stack:error?.stack?String(error.stack):null});
const bounded=value=>{try{return (value instanceof Error?String(value)+'\n'+(value.stack||''):typeof value==='string'?value:JSON.stringify(value)??String(value)).slice(0,3000);}catch{return '[unprintable diagnostic]';}};
// Diagnostic probe only: preserve ordinary console behavior and retain at most
// 64 bounded entries before loading CheerpJ, including class/JAR load failures.
for(const level of ['debug','log','info','warn','error']){const original=console[level];console[level]=function(...args){if(proof.console.length<64)proof.console.push({level,args:args.slice(0,8).map(bounded)});return original.apply(this,args);};}
window.addEventListener('error',event=>{if(proof.windowErrors.length<16)proof.windowErrors.push({message:String(event.message),error:event.error?describe(event.error):null,filename:event.filename,line:event.lineno,column:event.colno});});
window.addEventListener('unhandledrejection',event=>{if(proof.unhandled.length<16)proof.unhandled.push(describe(event.reason));});
const failureText='native-dispatch-probe-failure';
const scalar=(_lib,a,b)=>(Math.imul(a,1664525)+b)|0;
const vector=(_lib,seed,values)=>{const result=new Int32Array(values.length);for(let i=0;i<values.length;i++)result[i]=values[i]^seed;return result;};
const failure=()=>{throw new Error(failureText);};
const sync=(name,body)=>createSynchronousNativeCall(body,{name,failure:()=>{proof.helperFailures++;}});
const asyncWrap=body=>async(...args)=>{try{return await body(...args);}catch(error){throw error;}};
const natives={
 Java_orsc_browser_NativeDispatchProbe_scalarAsync:asyncWrap(async(...args)=>scalar(...args)),
 Java_orsc_browser_NativeDispatchProbe_scalarSync:sync('scalarSync',scalar),
 Java_orsc_browser_NativeDispatchProbe_vectorAsync:asyncWrap(async(...args)=>vector(...args)),
 Java_orsc_browser_NativeDispatchProbe_vectorSync:sync('vectorSync',vector),
 Java_orsc_browser_NativeDispatchProbe_failureAsync:asyncWrap(async()=>failure()),
 Java_orsc_browser_NativeDispatchProbe_failureSync:sync('failureSync',failure),
 Java_orsc_browser_NativeDispatchProbe_unexpectedThenable:sync('unexpectedThenable',()=>Promise.reject(new Error('consumed future rejection'))),
 async Java_orsc_browser_NativeDispatchProbe_report(_lib,name,value){proof.events.push({name:String(name),value:String(value)});},
};
for(const name of Object.keys(natives)){const call=natives[name];natives[name]=(...args)=>{proof.calls[name]=(proof.calls[name]||0)+1;return call(...args);};}
try{
 await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://cjrtnc.leaningtech.com/5.0/loader.js';script.onload=resolve;script.onerror=()=>reject(new Error('CheerpJ loader failed'));document.head.append(script);});
 await cheerpjInit({version:8,natives});
 const exit=await cheerpjRunMain('orsc.browser.NativeDispatchProbe','/app/probe.jar');proof.javaExit=exit;
 await new Promise(resolve=>setTimeout(resolve,0));
 const values=Object.fromEntries(proof.events.map(({name,value})=>[name,value]));
 if(exit!==0||values.completed!=='true'||Number(values.checks)<1900||proof.helperFailures!==2||proof.unhandled.length)throw Error('JNI contract probe incomplete/failed: '+JSON.stringify({exit,values,helperFailures:proof.helperFailures,unhandled:proof.unhandled}));
 proof.passed=true;
}catch(error){proof.errors.push(describe(error));}
finally{proof.completed=true;}
