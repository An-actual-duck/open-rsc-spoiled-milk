const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const m=JSON.parse(fs.readFileSync(art+'leather-poison-integration.json'));
for(const a of m.armor){const rel='sprites/items/inventory-ground/armor/'+a.icon+'.png',expected=fs.readFileSync(art+'leather-sets/'+a.icon+'.png');assert.ok(expected.equals(fs.readFileSync(base+rel)));assert.ok(expected.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));}
const dagger=JSON.parse(fs.readFileSync(art+'held-dagger-of-terror/manifest.json'));
const read=f=>execFileSync('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-']);
for(const f of dagger.frames){const n=String(f.frame).padStart(2,'0'),rel='sprites/equipment/dagger-of-terror-poisoned/numbered/'+n+'.png',source=read(art+'held-dagger-of-terror/frames/frame-'+n+'.png'),p=read(base+rel);assert.equal(source.length,p.length);let count=0;
 let coords=[];for(let i=0;i<source.length;i+=4)if(source[i+3]&&m.poison.palette[source.subarray(i,i+3).toString('hex')])coords.push(f.frame===17?i/4%f.width:Math.floor(i/4/f.width));const end=f.frame===17?Math.max(...coords):Math.min(...coords);
 for(let i=0;i<p.length;i+=4){assert.equal(p[i+3],source[i+3]);if(!p.subarray(i,i+4).equals(source.subarray(i,i+4))){count++;assert.ok(Math.abs((f.frame===17?i/4%f.width:Math.floor(i/4/f.width))-end)<3);assert.ok(p[i+1]>p[i]&&p[i+1]>p[i+2]);}}
 assert.equal(count,m.poison.tipCounts[f.frame]);assert.ok(fs.readFileSync(base+rel).equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
}
assert.ok(fs.readFileSync(art+'dagger-of-terror-poisoned-approved.png').equals(execFileSync('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/weapons/dagger-of-terror-poisoned-icon.png'])));
console.log('PASS: 30 exact packaged armor icons, poisoned icon, all 18 green-tip frames; geometry/alpha/outside-tip pixels unchanged');
