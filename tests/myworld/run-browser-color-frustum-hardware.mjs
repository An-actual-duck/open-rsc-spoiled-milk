/** Explicit color-frustum hardware runner; never imported by prepare-only mode. */
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const [permission,runArg,...extra]=process.argv.slice(2);
if(permission!=='--run-hardware'||!runArg||extra.length)throw Error('Explicit --run-hardware RUN is required; default preparation never launches hardware');
const run=path.resolve(runArg),allowedRoot=fileURLToPath(new URL('../../output/tests/browser-color-frustum/hardware/',import.meta.url));
if(!run.startsWith(path.resolve(allowedRoot)+path.sep)||fs.lstatSync(run).isSymbolicLink())throw Error('Only isolated declared hardware stages may be served');
const web=path.join(run,'web'),meta=JSON.parse(fs.readFileSync(path.join(run,'stage.json'),'utf8'));
if(meta.gpuExecuted!==false||!meta.preparedOnly||!meta.webFiles||fs.existsSync(path.join(run,'hardware-result.json')))throw Error('A fresh prepare-only stage is required; preserve previous results');
const hash=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
const staged=new Map();for(const [url,item]of Object.entries(meta.webFiles)){if(!/^\/[a-zA-Z0-9/_.-]+$/.test(url)||url.includes('..'))throw Error('Unsafe stage URL');const file=path.resolve(web,'.'+url);if(!file.startsWith(web+path.sep)||fs.lstatSync(file).isSymbolicLink())throw Error('Stage must contain plain isolated files');const data=fs.readFileSync(file);if(hash(data)!==item.sha256||data.length!==item.bytes)throw Error('Frozen staged content changed: '+url);staged.set(url,data);}
if(hash(staged.get('/webgl.js'))!==meta.moduleSha256)throw Error('Frozen module receipt mismatch');
const profile=path.join(run,'chrome-profile');if(fs.existsSync(profile))throw Error('Dedicated browser profile must not already exist');fs.mkdirSync(profile,{mode:0o700});
const log=fs.createWriteStream(path.join(run,'console-events.jsonl')),chromeLog=fs.openSync(path.join(run,'chrome.log'),'wx');
const requests=[];let origin,browser,ws,browserWS,serverPort;const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const server=http.createServer((request,response)=>{try{if(request.method!=='GET'||request.headers.host!==`127.0.0.1:${serverPort}`||request.headers.origin&&request.headers.origin!==origin){response.writeHead(403).end();return;}const raw=request.url.split('?')[0],url=raw==='/'?'/index.html':decodeURIComponent(raw);requests.push({method:request.method,path:url});const data=staged.get(url);if(!data){response.writeHead(404).end();return;}const type=url.endsWith('.html')?'text/html':(url.endsWith('.js')||url.endsWith('.mjs'))?'application/javascript':url.endsWith('.json')?'application/json':'application/octet-stream';response.writeHead(200,{'Content-Type':type,'Content-Length':data.length,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Cross-Origin-Resource-Policy':'same-origin'});response.end(data);}catch{response.writeHead(400).end();}});
function connect(url){return new Promise((resolve,reject)=>{const socket=new WebSocket(url),pending=new Map();let seq=0;socket.onopen=()=>resolve({socket,call(method,params={}){return new Promise((accept,fail)=>{const id=++seq,timer=setTimeout(()=>{pending.delete(id);fail(Error('CDP timeout '+method));},15000);pending.set(id,{accept,fail,timer});socket.send(JSON.stringify({id,method,params}));});}});socket.onerror=reject;socket.onmessage=event=>{const value=JSON.parse(event.data);if(value.id){const p=pending.get(value.id);if(p){pending.delete(value.id);clearTimeout(p.timer);value.error?p.fail(Error(JSON.stringify(value.error))):p.accept(value.result);}}else if(['Runtime.exceptionThrown','Runtime.consoleAPICalled','Log.entryAdded'].includes(value.method))log.write(JSON.stringify(value)+'\n');};socket.onclose=()=>{for(const p of pending.values()){clearTimeout(p.timer);p.fail(Error('Dedicated browser closed'));}pending.clear();};});}
let result={completed:false,passed:false,gpuExecuted:true,stage:meta,driverSha256:hash(fs.readFileSync(new URL(import.meta.url))),scope:'Isolated actual-production-shader color frustum pixel differential; no gameplay, CheerpJ or FPS claim',publicChanged:false};
try{
 await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});serverPort=server.address().port;origin=`http://127.0.0.1:${serverPort}`;
 const argv=['--headless=new','--no-first-run','--no-default-browser-check','--disable-dev-shm-usage','--disable-extensions','--disable-background-networking','--disable-component-update','--disable-sync','--enable-gpu','--use-gl=angle','--use-angle=gl','--ozone-platform=x11','--disable-software-rasterizer','--remote-debugging-address=127.0.0.1','--remote-debugging-port=0',`--user-data-dir=${profile}`,'--window-size=800,600','about:blank'];result.chromeArguments=argv;result.servedOrigin=origin;
 browser=spawn('/usr/bin/google-chrome',argv,{stdio:['ignore',chromeLog,chromeLog]});let debuggerPort,debuggerPath,targets;
 for(let n=0;n<100;n++){if(browser.exitCode!==null)throw Error('Dedicated Chrome exited '+browser.exitCode);try{[debuggerPort,debuggerPath]=fs.readFileSync(path.join(profile,'DevToolsActivePort'),'utf8').trim().split('\n');targets=await(await fetch(`http://127.0.0.1:${Number(debuggerPort)}/json/list`)).json();if(targets.some(target=>target.type==='page'))break;}catch{}await delay(100);}
 if(!targets?.some(target=>target.type==='page'))throw Error('Dedicated Chrome debug endpoint unavailable');
 browserWS=await connect(`ws://127.0.0.1:${Number(debuggerPort)}${debuggerPath}`);result.systemInfo=await browserWS.call('SystemInfo.getInfo');result.browserVersion=await browserWS.call('Browser.getVersion');
 ws=await connect(targets.find(target=>target.type==='page').webSocketDebuggerUrl);for(const domain of ['Runtime','Page','Log'])await ws.call(domain+'.enable');await ws.call('Page.navigate',{url:origin+'/'});
 const end=Date.now()+120000;let proof;while(Date.now()<end){const value=await ws.call('Runtime.evaluate',{expression:'JSON.stringify(window.colorFrustumProof||null)',returnByValue:true});if(value.exceptionDetails)throw Error(JSON.stringify(value.exceptionDetails));proof=JSON.parse(value.result.value||'null');if(proof?.completed)break;await delay(100);}
 result.proof=proof;if(!proof?.completed)throw Error('Color frustum GPU proof timed out');if(!proof.passed)throw Error('Color frustum GPU proof failed: '+JSON.stringify(proof.errors));
 if(!/AMD|Radeon/i.test(proof.renderer)||!/ANGLE/i.test(proof.renderer)||!/OpenGL/i.test(proof.renderer)||/SwiftShader|llvmpipe|softpipe|software|WARP/i.test(proof.renderer))throw Error('Physical AMD ANGLE OpenGL renderer verification failed');
 if(proof.cases.length!==64||proof.cases.some(test=>test.mismatches!==0)||proof.uploadedChunks!==101||proof.culledChunkFrames<=100||proof.changedBytes!==0)throw Error('Expected64 byte-exact color frames with complete101-chunk residency/shadows and exercised culling');
 result.passed=true;result.completed=true;
}catch(error){result.error=String(error.stack||error);}
finally{
 result.httpRequests=requests;fs.writeFileSync(path.join(run,'hardware-result.json'),JSON.stringify(result,null,2)+'\n');
 try{if(browserWS)await browserWS.call('Browser.close');}catch{}ws?.socket.close();browserWS?.socket.close();
 if(browser&&browser.exitCode===null){await Promise.race([new Promise(resolve=>browser.once('exit',resolve)),delay(3000)]);if(browser.exitCode===null)browser.kill('SIGTERM');await Promise.race([new Promise(resolve=>browser.once('exit',resolve)),delay(3000)]);}
 await new Promise(resolve=>server.close(resolve));log.end();fs.closeSync(chromeLog);
}
console.log(JSON.stringify({run,passed:result.passed,renderer:result.proof?.renderer,error:result.error},null,2));if(!result.passed)process.exitCode=1;
