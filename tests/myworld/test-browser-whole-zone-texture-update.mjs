// Actual Java-produced RGBA/material table oracle. CPU only; no WebGL or browser.
import assert from'node:assert/strict';import fs from'node:fs';import path from'node:path';import crypto from'node:crypto';
import {validateWholeZoneTexture,decodeWholeZoneChunk,validateWholeZoneManifest}from'../../tools/browser-gameplay/web/browser-whole-zone.js';
const dir=process.argv[2];if(!dir)throw Error('Explicit immutable actual Java pilot directory required');
const manifest=fs.readFileSync(path.join(dir,'manifest.json'));const m=JSON.parse(manifest);let checks=0;
for(let n=0;n<m.textures.length;n++){
 const t=m.textures[n],source=fs.readFileSync(path.join(dir,t.path));assert.equal(crypto.createHash('sha256').update(source).digest('hex'),t.sha256);checks++;
 const pixels=new Int32Array(source.buffer.slice(source.byteOffset,source.byteOffset+source.byteLength)),expected=m.materialMetadata.slice(n*5,n*5+5);
 assert.equal(validateWholeZoneTexture(expected,t.width,t.height,pixels,t.transparent),true);checks++;
 const rotated=new Int32Array(pixels.length);rotated.set(pixels.subarray(1));rotated[rotated.length-1]=pixels[0];assert.equal(validateWholeZoneTexture(expected,t.width,t.height,rotated,t.transparent),true);checks++;
 const changed=pixels.slice();changed.fill((((expected[3]^0xffffff)&0xffffff)<<8)|255);assert.throws(()=>validateWholeZoneTexture(expected,t.width,t.height,changed,t.transparent),/material changed/);checks++;
 assert.throws(()=>validateWholeZoneTexture(expected,t.width+1,t.height,pixels,t.transparent),/layout/);checks++;
 assert.throws(()=>validateWholeZoneTexture(expected,t.width,t.height,pixels,!t.transparent),/layout/);checks++;
 const hostile=pixels.slice();Object.defineProperties(hostile,{length:{value:0},byteOffset:{value:999},byteLength:{value:0},buffer:{get(){throw Error('shadowed getter must not run');}}});assert.equal(validateWholeZoneTexture(expected,t.width,t.height,hostile,t.transparent),true);checks++;
 if(typeof SharedArrayBuffer==='function'){assert.throws(()=>validateWholeZoneTexture(expected,t.width,t.height,new Int32Array(new SharedArrayBuffer(pixels.byteLength)),t.transparent),/genuine/);checks++;}
}
for(const c of m.chunks){const source=fs.readFileSync(path.join(dir,c.path)),buffer=source.buffer.slice(source.byteOffset,source.byteOffset+source.byteLength);const p=decodeWholeZoneChunk(buffer,c);assert.equal(p.vertices.length,c.vertexCount*28);assert.equal(p.indices.length,c.indexCount);assert.equal(p.batches.length,c.batchIntCount);checks+=3;}
assert.throws(()=>validateWholeZoneManifest(m),/source identity/);checks++;
console.log(JSON.stringify({passed:true,checks,actualJavaTextureResources:m.textures.length,actualJavaChunkResources:m.chunks.length,pilotManifestSha256:crypto.createHash('sha256').update(manifest).digest('hex'),scope:'CPU actual Java exported RGBA/table and binary-array layout; rotation accepted, material/layout changes refused. Incomplete pilot rejected, not full-world/GPU proof.'}));
