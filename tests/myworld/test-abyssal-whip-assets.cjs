const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync:run}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const m=JSON.parse(fs.readFileSync(art+'held-abyssal-whip-v2/candidate-manifest.json')),client=fs.readFileSync('Client_Base/src/orsc/mudclient.java','utf8');
for(const [label,key] of [['OFFSET_X','xShift'],['OFFSET_Y','yShift'],['BOUND_WIDTH','boundWidth']])assert.deepEqual(client.match(new RegExp('ABYSSAL_WHIP_'+label+' = new int\\[\\] \\{([^}]+)'))[1].split(',').map(Number),m.frames.map(f=>f[key]));
assert.ok(client.includes('orsc.graphics.two.SpriteArchive.Frame.LAYER.MAIN_HAND, ABYSSAL_WHIP_OFFSET_X'));
assert.equal(m.frames.length,18);
for(const f of m.frames){const n=String(f.frame).padStart(2,'0'),rel='sprites/equipment/abyssal-whip/numbered/'+n+'.png',p=fs.readFileSync(art+'held-abyssal-whip-v2/frames/frame-'+n+'.png');assert.deepEqual(p,fs.readFileSync(base+rel));assert.deepEqual(p,run('unzip',['-p',jar,'myworld-assets/'+rel]));assert.equal(f.boundWidth,f.frame===11?66:f.frame<15?64:84);assert.ok(f.xShift>=0&&f.yShift>=0&&f.xShift+f.width<=f.boundWidth&&f.yShift+f.height<=102);if(f.frame>=15)assert.deepEqual(p,fs.readFileSync(art+'held-abyssal-whip/frames/frame-'+n+'.png'));}
assert.deepEqual(fs.readFileSync(art+'abyssal-whip-approved.png'),run('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/weapons/abyssal-whip-icon.png']));
console.log('PASS: 18 whip assets embedded, grip offsets/bounds, unchanged attacks, approved icon');
