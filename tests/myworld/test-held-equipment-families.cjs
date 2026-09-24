const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),jar=path.join(root,'Client_Base/Open_RSC_Client.jar');
execFileSync('python3',['tools/generators/generate-held-equipment-families.py','--check'],{cwd:root,stdio:'inherit'});
const spec=JSON.parse(fs.readFileSync(path.join(root,'tools/generators/held-equipment-families.json')));
assert.equal(spec.definitions.length,21);assert.equal(spec.mappings.length,36);
for(const weapon of ['fire-sword','ice-sword'])for(let frame=0;frame<18;frame++){
 const rel='sprites/equipment/'+weapon+'/numbered/'+String(frame).padStart(2,'0')+'.png';
 assert.ok(fs.readFileSync(path.join(root,'dev/myworld/assets',rel)).equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])),'elemental frame package');
}
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'held-family-test-'));
execFileSync('javac',['-cp',jar,'-d',temp,path.join(__dirname,'HeldEquipmentClientAudit.java')],{stdio:'inherit'});
const reference=process.argv[2];
execFileSync('java',['-cp',temp+':'+jar,'HeldEquipmentClientAudit',...(reference?[reference]:[])],{cwd:root,stdio:'inherit'});
console.log('PASS: generated catalog parity and packaged elemental sword frames');
