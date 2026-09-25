const fs=require('fs'),path=require('path'),os=require('os'),assert=require('assert/strict'),{execFileSync:run}=require('child_process');
const root=path.resolve(__dirname,'../..'),jar=path.join(root,'Client_Base/Open_RSC_Client.jar'),temp=fs.mkdtempSync(path.join(os.tmpdir(),'antidote-icons-'));
const read=f=>run('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-']);
const ref=read(path.join(root,'dev/myworld/reference-library/items/materials/bones-and-monster-parts/id-02250.png'));
for(const [name,green] of [['giant-spider-eggs',false],['jungle-spider-eggs',true]]){
 const rel='sprites/items/inventory-ground/resources/'+name+'.png',f=path.join(root,'dev/myworld/assets',rel),p=read(f);
 assert.ok(fs.readFileSync(f).equals(run('unzip',['-p',jar,'myworld-assets/'+rel])),'packaged PNG');
 assert.equal(p.length,48*32*4);
 for(let i=0;i<p.length;i+=4){assert.equal(p[i+3],ref[i+3],'alpha silhouette');const [r,g,b]=ref.subarray(i,i+3);
  if(ref[i+3]&&r>g&&r>b)assert.deepEqual([...p.subarray(i,i+3)],green?[g,r,b]:[r,r,b],'pigment recolor');else assert.ok(p.subarray(i,i+4).equals(ref.subarray(i,i+4)),'neutral pixels unchanged');
 }
}
run('javac',['-cp',jar,'-d',temp,path.join(__dirname,'AntidoteIconsFixture.java')],{stdio:'inherit'});
run('java',['-cp',temp+':'+jar,'orsc.AntidoteIconsFixture'],{cwd:temp,stdio:'inherit'});
