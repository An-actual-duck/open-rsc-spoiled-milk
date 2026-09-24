const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const manifest=JSON.parse(fs.readFileSync(art+'worn-sullen-pendant/manifest.json'));
const client=fs.readFileSync('Client_Base/src/orsc/mudclient.java','utf8').split('private void loadExternalNeckEquipmentSprite(')[1].split('private static final int[] GLOVE')[0];
for(const axis of ['X','Y'])assert.deepEqual(client.match(new RegExp('offset'+axis+' = new int\\[\\] \\{([^}]+)'))[1].split(',').map(Number),manifest.frames.map(f=>f[axis==='X'?'xShift':'yShift']));
assert.ok(client.includes('LAYER.NECK'));assert.equal(manifest.frames.length,18);
for(let i=0;i<18;i++){
 const n=String(i).padStart(2,'0'),rel='sprites/equipment/sullen-pendant/numbered/'+n+'.png',expected=fs.readFileSync(art+'worn-sullen-pendant/frames/frame-'+n+'.png');
 assert.ok(expected.equals(fs.readFileSync(base+rel)));
 assert.ok(expected.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
}
assert.ok(fs.readFileSync(art+'sullen-pendant-approved.png').equals(execFileSync('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/jewelry/sullen-pendant-icon.png'])));
console.log('PASS: approved pendant icon, 18 exact worn frames and original neck anchors');
