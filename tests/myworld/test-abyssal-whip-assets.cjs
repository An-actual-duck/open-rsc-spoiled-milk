const fs=require('node:fs'),assert=require('node:assert/strict'),{execFileSync:run}=require('node:child_process');
const art='dev/myworld/art-candidates/slayer-items/',base='dev/myworld/assets/',jar='Client_Base/Open_RSC_Client.jar';
const m=JSON.parse(fs.readFileSync(art+'held-abyssal-whip-v3/candidate-manifest.json')),client=fs.readFileSync('Client_Base/src/orsc/mudclient.java','utf8');
const padding=f=>Math.max(0,f.boundWidth-(f.frame<15?64:84)),offsets={};
for(const label of ['OFFSET_X','OFFSET_Y','BOUND_WIDTH'])offsets[label]=client.match(new RegExp('ABYSSAL_WHIP_'+label+' = new int\\[\\] \\{([^}]+)'))[1].split(',').map(Number);
assert.deepEqual(offsets.OFFSET_X,m.frames.map(f=>f.xShift+padding(f)));
assert.deepEqual(offsets.OFFSET_Y,m.frames.map(f=>f.yShift));
assert.deepEqual(offsets.BOUND_WIDTH,m.frames.map(f=>(f.frame<15?64:84)+2*padding(f)));
assert.ok(client.includes('orsc.graphics.two.SpriteArchive.Frame.LAYER.MAIN_HAND, ABYSSAL_WHIP_OFFSET_X'));
assert.ok(client.includes('? (mySpriteOffset < 15 ? 64 : 84) : something1'));
assert.equal(m.frames.length,18);
for(const f of m.frames){
 const n=String(f.frame).padStart(2,'0'),rel='sprites/equipment/abyssal-whip/numbered/'+n+'.png',p=fs.readFileSync(art+'held-abyssal-whip-v3/frames/frame-'+n+'.png');
 assert.deepEqual(p,fs.readFileSync(base+rel));assert.deepEqual(p,run('unzip',['-p',jar,'myworld-assets/'+rel]));
 const raw=run('ffmpeg',['-v','error','-i',base+rel,'-f','rawvideo','-pix_fmt','rgba','-']);
 const hx=Math.round(f.handX)-f.xShift,hy=Math.round(f.handY)-f.yShift;
 assert.equal(raw[(hy*f.width+hx)*4+3],0,'transparent fist '+n);
 assert.ok(offsets.OFFSET_X[f.frame]+f.width<=offsets.BOUND_WIDTH[f.frame]);
 const canonical=f.frame<15?64:84,pad=padding(f);
 assert.ok(Math.abs((f.handX+pad)-(offsets.BOUND_WIDTH[f.frame]-canonical)/2-f.handX)<1e-10);
 assert.ok(Math.abs(offsets.BOUND_WIDTH[f.frame]-(f.handX+pad)-pad-(canonical-f.handX))<1e-10);
}
assert.deepEqual(fs.readFileSync(art+'abyssal-whip-approved.png'),run('unzip',['-p',jar,'myworld-assets/sprites/items/inventory-ground/weapons/abyssal-whip-icon.png']));
console.log('PASS: 18 v3 whip assets embedded, transparent fist grips, symmetric bounds/anchors, approved icon');
