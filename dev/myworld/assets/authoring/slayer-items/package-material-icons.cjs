// Install accepted art verbatim. No generation, resizing or recoloring.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..'),dest=path.join(root,'dev/myworld/assets/sprites/items/inventory-ground/resources');
const entries=[];
for(const [name,ids] of Object.entries({
 'slime-solvent':[3318,3319,3320],'eye-drops':[3321,3322,3323],
 'wax-earplugs':[3324,3325,3326],'dog-treats':[3327,3328,3329],'static-wipes':[3330,3331,3332],
 'cockatrice-feathers':[3339],'slimey-residue':[3340],'sticky-saliva-gland':[3341],
 'cockatrice-eye':[3342],'frozen-tear':[3343],'terror-fang':[3344],'leach-tongue':[3345],
 'lightning-horn':[3346],'abyssal-vertibrae':[3347],'abyssal-rib':[3348],'ectoplasm':[3399]
})) entries.push({name,ids,source:name+'-approved.png'});
for(const item of JSON.parse(fs.readFileSync(path.join(__dirname,'hide-leathers-v2/manifest.json'))))
 entries.push({name:item.family+'-'+item.kind,ids:[item.itemId],source:'hide-leathers-v2/'+item.file});
fs.mkdirSync(dest,{recursive:true});
for(const e of entries){
 const src=path.join(__dirname,e.source),pixels=execFileSync('ffmpeg',['-v','error','-i',src,'-f','rawvideo','-pix_fmt','rgba','-']);
 assert.equal(pixels.length,48*32*4);let xs=[],ys=[];
 for(let i=0;i<pixels.length;i+=4)if(pixels[i+3]>=64){xs.push(i/4%48);ys.push(Math.floor(i/4/48));}
 e.width=Math.max(...xs)-Math.min(...xs)+1;e.height=Math.max(...ys)-Math.min(...ys)+1;
 assert.ok(e.width<=46&&e.height<=30);e.sprite='external-png:'+e.name+'@'+e.width+'x'+e.height;
 fs.copyFileSync(src,path.join(dest,e.name+'.png'));
}
fs.writeFileSync(path.join(__dirname,'material-icons-integration.json'),JSON.stringify(entries,null,2)+'\n');
console.log('Installed '+entries.length+' approved icons for '+entries.reduce((n,e)=>n+e.ids.length,0)+' item definitions');
