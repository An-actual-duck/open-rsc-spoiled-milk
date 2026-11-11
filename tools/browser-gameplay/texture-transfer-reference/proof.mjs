import assert from 'node:assert/strict';
import fs from 'node:fs';
import {validateRectangles,rowBands,spanElements,pack,makeSources,metric,MODES}from './transfer.mjs';
let checks=0;
function prove(rects,w,h){const mask=validateRectangles(rects,w,h),frames=makeSources(rects,w,h);for(const channels of [1,4]){const key=channels===1?'t':'c',source=frames[1][key];for(const mode of MODES){const m=metric(rects,mode,w,h),out=frames[0][key].slice();let span=0,logical=0;
 for(const r of m.rectangles){const [x,y,x1,y1]=r;const packed=pack(source,r,channels,new source.constructor((x1-x)*(y1-y)*channels),w);assert.equal(packed.length,(x1-x)*(y1-y)*channels);const view=source.subarray((y*w+x)*channels,((y1-1)*w+x1)*channels);assert.equal(view.length,spanElements(r,channels,w));for(let row=y;row<y1;row++){const start=(row*w+x)*channels,part=packed.subarray((row-y)*(x1-x)*channels,(row-y+1)*(x1-x)*channels);assert.deepEqual(view.subarray((row-y)*w*channels,(row-y)*w*channels+part.length),part);out.set(part,start);}span+=view.byteLength;logical+=packed.byteLength;checks++;}
 assert.deepEqual(new Uint8Array(out.buffer),new Uint8Array(source.buffer));checks++;
 }}for(let i=0;i<mask.length;i++)if(!mask[i])assert.equal(frames[0].t[i],frames[1].t[i]);}
prove([[0,0,1,17]],1,17);prove([[1,1,4,3],[0,4,7,5]],7,5);
for(const rects of [[],[[0,0,0,1]],[[0,0,2,2],[1,1,2,2]],[[0,0,9,1]],[[0,0,1.5,1]]]){assert.throws(()=>validateRectangles(rects,7,5));checks++;}
if(process.argv[2]){const d=JSON.parse(fs.readFileSync(process.argv[2],'utf8'));for(const l of d.layouts)prove(l.coarse??l.rectangles,960,540);}
console.log(`PASS ${checks} reference layout/packing checks (CPU only; no WebGL proof)`);
