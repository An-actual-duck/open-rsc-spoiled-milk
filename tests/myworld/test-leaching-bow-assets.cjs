const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const manifest=JSON.parse(fs.readFileSync(art+'held-leaching-bow/manifest.json'));
const client=fs.readFileSync('Client_Base/src/orsc/mudclient.java','utf8').split('private void loadExternalLeachingBowSprite()')[1].split('private void loadExternalNeckEquipmentSprite')[0];
for(const axis of ['X','Y'])assert.deepEqual(client.match(new RegExp('offset'+axis+' = \\{([^}]+)'))[1].split(',').map(Number),manifest.frames.map(f=>f[axis==='X'?'xShift':'yShift']));
assert.ok(client.includes('LAYER.OFF_HAND'));assert.ok(client.includes('PLAYER_EQUIPPABLE_NOCOMBAT'));
for(let i=0;i<15;i++){
 const n=String(i).padStart(2,'0'),rel='sprites/equipment/leaching-bow/numbered/'+n+'.png',expected=fs.readFileSync(art+'held-leaching-bow/frames/frame-'+n+'.png');
 assert.ok(expected.equals(fs.readFileSync(base+rel)));
 assert.ok(expected.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
}
assert.ok(fs.readFileSync(art+'leaching-bow-approved.png').equals(execFileSync('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/weapons/leaching-bow-icon.png'])));
console.log('PASS: approved bow icon and 15 frames packaged exactly; longbow anchors and off-hand type preserved');
