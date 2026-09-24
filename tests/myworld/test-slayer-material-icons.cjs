const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),os=require('node:os'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),art=path.join(root,'dev/myworld/art-candidates/slayer-items'),jar=path.join(root,'Client_Base/Open_RSC_Client.jar');
const manifest=JSON.parse(fs.readFileSync(path.join(art,'material-icons-integration.json'))),ids=new Set(),args=[];
const defs=new Map(JSON.parse(fs.readFileSync(path.join(root,'server/conf/server/defs/ItemDefsCustom.json'))).items.map(e=>[e.id,e]));
for(const e of JSON.parse(fs.readFileSync(path.join(root,'server/conf/server/defs/ItemDefsMyWorld.json'))).items)if(defs.has(e.id))Object.assign(defs.get(e.id),e);
for(const e of manifest){
 const rel='sprites/items/inventory-ground/resources/'+e.name+'.png',source=fs.readFileSync(path.join(art,e.source));
 assert.ok(source.equals(fs.readFileSync(path.join(root,'dev/myworld/assets',rel))));
 assert.ok(source.equals(execFileSync('unzip',['-p',jar,'myworld-assets/'+rel])));
 for(const id of e.ids){assert.ok(!ids.has(id));ids.add(id);args.push(id+'|'+e.sprite+'|'+defs.get(id).basePrice);}
}
assert.equal(ids.size,38);assert.ok(!ids.has(3334),'Retired Banshee hide remains untouched');
const temp=fs.mkdtempSync(path.join(os.tmpdir(),'slayer-material-icons-'));
execFileSync('javac',['-cp',jar,'-d',temp,path.join(__dirname,'SlayerMaterialIconsFixture.java')],{stdio:'inherit'});
execFileSync('java',['-cp',temp+':'+jar,'orsc.SlayerMaterialIconsFixture',...args],{cwd:path.join(root,'Client_Base'),stdio:'inherit'});
console.log('PASS: 28 approved PNGs packaged byte-for-byte, no new artwork or scaling');
