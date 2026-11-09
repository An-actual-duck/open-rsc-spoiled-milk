import fs from 'node:fs';
import assert from 'node:assert/strict';
import {RETAINED_FRAGMENT_SOURCE} from '../../tools/browser-gameplay/web/browser-retained-frame.js';
// Interpret the GLSL uint expression itself using JS uint bitwise semantics.
// This is a CPU algorithm oracle, not shader compilation/execution proof.
const expression=RETAINED_FRAGMENT_SOURCE.match(/uint fadePacked\(uint v\)\{return ([^;]+);\}/)[1].replace(/(0x[0-9a-f]+|\d+)u\b/gi,'$1').replace(/>>/g,'>>>');
const fade=new Function('v',`return (${expression})>>>0;`);
const bytes=fs.readFileSync(process.argv[2]);const count=bytes.readUInt32BE(0);assert.equal(bytes.length,4+count*12);
for(let n=0,at=4;n<count;n++,at+=12){let actual=bytes.readUInt32BE(at);const steps=bytes.readUInt32BE(at+4),expected=bytes.readUInt32BE(at+8);for(let i=0;i<steps;i++)actual=fade(actual);assert.equal(actual,expected,`GLSL packed expression disagrees with native fade at ${n}`);}
assert(RETAINED_FRAGMENT_SOURCE.includes('texelFetch(previousFrame,ivec2(gl_FragCoord.xy),0)'),'retained pixels must not filter or flip');
assert(RETAINED_FRAGMENT_SOURCE.includes('i<16')&&RETAINED_FRAGMENT_SOURCE.includes('i>=fadeCount'),'fade loop must be bounded');
console.log(`PASS GLSL uint expression vs ${count} actual GraphicsController fade outputs; no actual shader/GPU claim.`);
