const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),art=path.join(root,'dev/myworld/assets/authoring/slayer-items');
const base=path.join(root,'dev/myworld/assets/runtime/sprites');
const manifest=JSON.parse(fs.readFileSync(path.join(art,'held-dagger-of-terror/manifest.json')));
const client=fs.readFileSync(path.join(root,'Client_Base/src/orsc/mudclient.java'),'utf8');
for(const [axis,key] of [['X','xShift'],['Y','yShift']]){
 const offsets=client.match(new RegExp('SWORD_EQUIPMENT_OFFSET_'+axis+' = new int\\[\\] \\{([^}]+)'))[1].split(',').map(Number);
 assert.deepEqual(offsets,manifest.frames.map(f=>f[key]));
}
const jar=path.join(root,'Client_Base/Open_RSC_Client.jar');
for(const f of manifest.frames){
 const n=String(f.frame).padStart(2,'0');
 const rel='sprites/equipment/dagger-of-terror/numbered/'+n+'.png';
 const expected=fs.readFileSync(path.join(art,'held-dagger-of-terror/frames/frame-'+n+'.png'));
 assert.ok(expected.equals(fs.readFileSync(path.join(base,'equipment/dagger-of-terror/numbered/'+n+'.png'))));
 assert.ok(expected.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
}
const icon=fs.readFileSync(path.join(art,'dagger-of-terror-approved.png'));
assert.ok(icon.equals(execFileSync('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/weapons/dagger-of-terror-icon.png'])));
const appearances=JSON.parse(fs.readFileSync(path.join(root,'tools/generators/slayer-art-appearances.json'))).definitions;
assert.equal(appearances.find(i=>i.itemId===3353).family,'daggerofterror');
assert.equal(appearances.find(i=>i.itemId===3354).family,'daggerofterrorpoisoned');
console.log('PASS: server mapping, 18 exact packaged frames, anchor arrays, approved packaged icon; poison variant uses dedicated appearance');
