import assert from 'node:assert/strict';
import {createWorldReadiness} from '../../tools/browser-gameplay/web/browser-world-ready.js';
import {createWorldCover} from '../../tools/browser-gameplay/web/browser-world-cover.js';
function fixture(){let now=100,signal=true,created=0,deleted=0;const stats={},gl={SYNC_GPU_COMMANDS_COMPLETE:1,ALREADY_SIGNALED:2,CONDITION_SATISFIED:3,WAIT_FAILED:4,TIMEOUT_EXPIRED:5,fenceSync:()=>({id:++created}),deleteSync:()=>deleted++,flush(){},clientWaitSync:()=>signal?3:5};const state=createWorldReadiness(gl,{stats,clock:()=>now});
 const data=(changes={})=>{const d=[1,2,1,4,4,0,0,0,1,960,540,3];for(const[k,v]of Object.entries(changes))d[k]=v;return new Int32Array(d);};
 const frame=(changes={},ms=30,actual={})=>{now+=ms;state.declare(data(changes));state.presented({world:true,width:960,height:540,textures:3,sprites:0,lighting:true,...actual});};
 return{state,stats,gl,data,frame,setSignal:v=>signal=v,get created(){return created},get deleted(){return deleted}};}
let x=fixture();for(let i=0;i<40;i++)x.frame({4:2},1000);assert.equal(x.stats.worldReadiness.phase,'loading');assert.equal(x.created,0,'incomplete world must not get a ready GPU fence');
for(let i=0;i<7;i++)x.frame();assert.equal(x.stats.worldReadiness.phase,'ready');assert.equal(x.deleted,1);
x.frame({4:3,5:1});assert.equal(x.stats.worldReadiness.phase,'ready','live animation/material work re-covered ready world');
x.frame({2:2,4:3});assert.equal(x.stats.worldReadiness.phase,'loading','new region did not cover incomplete world');
for(let i=0;i<7;i++)x.frame({2:2});assert.equal(x.stats.worldReadiness.phase,'ready');
x.frame({1:3},30,{world:false});assert.equal(x.stats.worldReadiness.phase,'ui');x.frame({2:2});assert.equal(x.stats.worldReadiness.phase,'ready','legitimate modal UI unnecessarily restarted world');
x.frame({1:1,8:0});assert.equal(x.stats.worldReadiness.phase,'loading');x.frame({1:0},30,{world:false});assert.equal(x.stats.worldReadiness.phase,'login');
x=fixture();x.setSignal(false);for(let i=0;i<15;i++)x.frame();assert.equal(x.stats.worldReadiness.phase,'loading');x.setSignal(true);x.frame();assert.equal(x.stats.worldReadiness.phase,'ready','GPU fence signal did not permit complete world');
x=fixture();for(let i=0;i<7;i++)x.frame({},500);assert.equal(x.stats.worldReadiness.phase,'ready','slow but complete device stayed loading');
x=fixture();for(let i=0;i<3;i++)x.frame({},1500);assert.equal(x.stats.worldReadiness.phase,'ready');assert.equal(x.stats.worldReadiness.warmupBoundReached,true);
x=fixture();for(let i=0;i<30;i++)x.frame({},i%2?4:100);assert.equal(x.stats.worldReadiness.phase,'ready');
x=fixture();for(let i=0;i<7;i++)x.frame({},30,{lighting:false});assert.equal(x.stats.worldReadiness.phase,'loading');assert.equal(x.created,0);
x=fixture();x.setSignal(false);x.frame();x.frame({},16000);assert.equal(x.stats.worldReadiness.phase,'error');x.frame({1:0});assert.equal(x.stats.worldReadiness.phase,'error','error hidden behind login/loader');x.state.dispose();assert.equal(x.deleted,1);
x=fixture();assert.throws(()=>x.state.presented({}),/missing/);assert.throws(()=>x.state.declare(new Int32Array(2)),/declaration/);x.state.declare(x.data());assert.throws(()=>x.state.presented({width:1,height:1}),/viewport/);
x=fixture();for(let i=0;i<7;i++)x.frame();x.frame({9:800},30,{width:800});assert.equal(x.stats.worldReadiness.phase,'loading','resize did not start a new load epoch');
// A real capture-style dispatch order; releases are deliberately passed through.
const handlers=new Map(),elements=[];function el(){const e={style:{},children:[],textContent:'',contains(n){return n===e||e.children.some(c=>c.contains?.(n));},setAttribute(){},append(...nodes){for(const n of nodes){n.parentElement=e;e.children.push(n);}},remove(){e.removed=true;},getBoundingClientRect(){return{left:10,top:20,right:110,bottom:100}}};elements.push(e);return e;}
const doc={createElement:el,addEventListener:(type,fn)=>{const list=handlers.get(type)||[];list.push(fn);handlers.set(type,list)},removeEventListener:(type,fn)=>handlers.set(type,(handlers.get(type)||[]).filter(f=>f!==fn))},display=el();let changes=0,vendor=0;
const cover=createWorldCover({document:doc,window:{},display,onGateChange:()=>changes++});
function dispatch(type,inside=true,extra={}){const e={type,key:'w',target:display,...extra,clientX:inside?50:0,clientY:inside?50:0,preventDefault(){this.prevented=true},stopImmediatePropagation(){this.stopped=true}};for(const f of handlers.get(type)||[]){f(e);if(e.stopped)break;}if(!e.stopped)vendor++;return e;}
cover.update({phase:'loading',required:4,uploaded:2},{left:'10px',top:'20px',width:'100px',height:'80px'});assert.equal(changes,1);assert.equal(cover.blocksWorldInput,true);
for(const t of ['mousedown','pointerdown','click','contextmenu','wheel','keydown'])assert.equal(dispatch(t).prevented,true);
assert.equal(vendor,0);for(const key of ['Tab','Escape','F5','F12','Home','Shift'])assert.equal(dispatch('keydown',true,{key}).prevented,undefined);for(const modifier of ['ctrlKey','metaKey','altKey'])assert.equal(dispatch('keydown',true,{[modifier]:true}).prevented,undefined);assert.equal(dispatch('keydown',true,{key:'Tab',shiftKey:true}).prevented,undefined);assert.equal(dispatch('keydown',true,{target:el()}).prevented,undefined);assert.equal(dispatch('mouseup').prevented,undefined);assert.equal(dispatch('keyup').prevented,undefined);assert.equal(dispatch('mousedown',false).prevented,undefined);
cover.update({phase:'loading',required:4,uploaded:3});assert.equal(changes,1);cover.update({phase:'ui'});assert.equal(changes,2);assert.equal(dispatch('keydown').prevented,undefined);
cover.update({phase:'ready'});assert.equal(changes,2);cover.update({phase:'error',reason:'Controlled graphics failure'});assert.equal(cover.blocksWorldInput,true);assert.match(display.children[0].children[1].textContent,/Stop and restart/);cover.dispose();assert.equal(cover.blocksWorldInput,false);assert.equal([...handlers.values()].flat().length,0);
console.log('PASS source completion, real GPU fence contract, bounded warm-up without FPS floor, live-update stability, area/viewport/login/modal/error states, input capture/release boundaries and cleanup. No GPU/FPS claim.');
