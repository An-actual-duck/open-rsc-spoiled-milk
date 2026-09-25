// Deterministic authored recolors and append-only item definitions. No AI artwork.
const fs=require('node:fs'),path=require('node:path'),{execFileSync:run}=require('node:child_process');
const root=path.resolve(__dirname,'../..'),defs=path.join(root,'server/conf/server/defs');
const base=JSON.parse(fs.readFileSync(path.join(defs,'ItemDefs.json'))).item,custom=JSON.parse(fs.readFileSync(path.join(defs,'ItemDefsCustom.json'))).items;
const eggs=base.find(x=>x.id===219),potion=custom.find(x=>x.id===1474),items=[];
for(const [id,name] of [[3400,'Giant Spider Eggs'],[3401,'Jungle Spider Eggs']])items.push({...eggs,id,name,description:name+' used to brew an antidote.'});
for(const [first,name,power,price] of [[3402,'Weak Antidote',5,144],[3405,'Strong Antidote',20,432]])for(let n=0;n<3;n++)items.push({...potion,id:first+n,name,description:(3-n)+' dose(s). Adds '+power+' poison cleansing power per pulse for 10 minutes.',basePrice:Math.round(price*(3-n)/3)});
fs.writeFileSync(path.join(defs,'AntidoteItemDefs.json'),JSON.stringify({items},null,2)+'\n');
const source=path.join(root,'dev/myworld/reference-library/items/materials/bones-and-monster-parts/id-02250.png');
const original=run('ffmpeg',['-v','error','-i',source,'-f','rawvideo','-pix_fmt','rgba','-']);
for(const [name,green] of [['giant-spider-eggs',false],['jungle-spider-eggs',true]]){
 const pixels=Buffer.from(original);
 // Rotate only red pigment, preserving neutral pixels, alpha, geometry and value extrema.
 for(let i=0;i<pixels.length;i+=4){const r=original[i],g=original[i+1],b=original[i+2];if(!original[i+3]||r<=g||r<=b)continue;pixels[i]=green?g:r;pixels[i+1]=r;pixels[i+2]=b;}
 run('ffmpeg',['-v','error','-y','-f','rawvideo','-pix_fmt','rgba','-s','48x32','-i','-','-frames:v','1',path.join(root,'dev/myworld/assets/sprites/items/inventory-ground/resources',name+'.png')],{input:pixels});
}
