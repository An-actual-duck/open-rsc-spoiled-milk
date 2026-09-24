const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict'),{execFileSync:run}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),jar=path.join(root,'Client_Base/Open_RSC_Client.jar'),temp=fs.mkdtempSync(path.join(os.tmpdir(),'held-shears-test-'));
for(let n=0;n<15;n++){
 const number=String(n).padStart(2,'0')+'.png',rel='sprites/equipment/shears/numbered/'+number;
 const candidate=fs.readFileSync(path.join(root,'dev/myworld/art-candidates/slayer-items/held-shears/full-canvas/frame-'+number));
 assert.ok(candidate.equals(fs.readFileSync(path.join(root,'dev/myworld/assets',rel))),'exact candidate pixels');
 assert.ok(candidate.equals(run('unzip',['-p',jar,'myworld-assets/'+rel])),'exact packaged PNG');
}
const client=fs.readFileSync(path.join(root,'Client_Base/src/orsc/mudclient.java'),'utf8');
assert.ok(client.includes('loadExternalMainHandEquipmentSprite("shears", getExternalEquipmentNumberedFolder("shears"), new int[15], new int[15])'),'zero-offset full-canvas loader');
assert.ok(client.includes('actualAnimDir != 5 || EntityHandler.getAnimationDef(animID).hasA()'),'combat suppresses no-attack tool');
run('javac',['-cp',jar,'-d',temp,path.join(__dirname,'HeldShearsClientFixture.java')],{stdio:'inherit'});
run('java',['-cp',temp+':'+jar,'orsc.HeldShearsClientFixture',root,temp],{cwd:temp,stdio:'inherit'});
