const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const manifest=JSON.parse(fs.readFileSync(art+'held-thunder-spire/runtime-manifest.json'));
const source=JSON.parse(fs.readFileSync(art+'held-thunder-spire/source-manifest.json'));
const client=fs.readFileSync('Client_Base/src/orsc/mudclient.java','utf8');
for(const axis of ['X','Y'])assert.deepEqual(client.match(new RegExp('STAFF_EQUIPMENT_OFFSET_'+axis+' = new int\\[\\] \\{([^}]+)'))[1].split(',').map(Number),manifest.map(f=>f[axis==='X'?'xShift':'yShift']));
assert.equal(manifest.length,18);
for(const f of manifest){
 const n=String(f.frame).padStart(2,'0'),rel='sprites/equipment/thunder-spire-staff/numbered/'+n+'.png',expected=fs.readFileSync(base+rel);
 assert.ok(expected.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
 assert.equal(expected.readUInt32BE(16),+source[f.frame].width);
 assert.equal(expected.readUInt32BE(20),+source[f.frame].height);
 assert.equal(f.xShift,+source[f.frame].x_shift);assert.equal(f.yShift,+source[f.frame].y_shift);
 assert.equal(f.boundWidth,f.frame<15?64:84);assert.equal(f.boundHeight,102);
}
assert.ok(fs.readFileSync(art+'thunder-spire-staff-approved.png').equals(execFileSync('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/weapons/thunder-spire-staff-icon.png'])));
console.log('PASS: 18 packaged staff frames, source bounds/anchors, approved icon');
