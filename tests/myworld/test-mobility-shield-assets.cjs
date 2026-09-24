const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const manifest=JSON.parse(fs.readFileSync(art+'held-shield-of-mobility/candidate-manifest.json'));
const client=fs.readFileSync('Client_Base/src/orsc/mudclient.java','utf8');
for(const axis of ['X','Y'])assert.deepEqual(client.match(new RegExp('MOBILITY_SHIELD_OFFSET_'+axis+' = new int\\[\\] \\{([^}]+)'))[1].split(',').map(Number),manifest.frames.map(f=>f[axis==='X'?'xShift':'yShift']));
assert.ok(client.includes('orsc.graphics.two.SpriteArchive.Frame.LAYER.OFF_HAND, MOBILITY_SHIELD_OFFSET_X'));
assert.equal(manifest.frames.length,18);
for(const f of manifest.frames){
 const n=String(f.frame).padStart(2,'0'),rel='sprites/equipment/shield-of-mobility/numbered/'+n+'.png',expected=fs.readFileSync(art+'held-shield-of-mobility/frames/frame-'+n+'.png');
 assert.ok(expected.equals(fs.readFileSync(base+rel)));
 assert.ok(expected.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
 assert.equal(f.boundWidth,f.frame<15?64:84);assert.equal(f.boundHeight,102);
}
assert.ok(fs.readFileSync(art+'shield-of-mobility-approved.png').equals(execFileSync('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/armor/shield-of-mobility-icon.png'])));
console.log('PASS: 18 exact shield frames, approved icon, off-hand layer and registered offsets');
