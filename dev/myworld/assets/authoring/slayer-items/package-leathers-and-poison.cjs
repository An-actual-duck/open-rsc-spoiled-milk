const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const out=path.resolve(__dirname,'../../assets/sprites'),read=f=>execFileSync('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-']);
const sets=[['giant-frog',3357,0x238fff],['naga',3369,0x589d40],['terror-dog',3375,0xd34538],['bloodveld',3381,0xe3b59a],['dark-beast',3387,0x55565a],['ugthanki',3394,0xefd04b]];
const slots=['coif','gloves','boots','chaps','cuirass'],names=['mediumhelm','hidegloves','hideboots','chainmaillegs','chainmail'];
const records=[];fs.mkdirSync(path.join(out,'items/inventory-ground/armor'),{recursive:true});
for(let s=0;s<sets.length;s++)for(let j=0;j<5;j++){
 const [family,start,color]=sets[s],name=family+'-'+slots[j],src=path.join(__dirname,'leather-sets',name+'.png'),p=read(src);let xs=[],ys=[];
 for(let i=0;i<p.length;i+=4)if(p[i+3]>=64){xs.push(i/4%48);ys.push(Math.floor(i/4/48));}
 const w=Math.max(...xs)-Math.min(...xs)+1,h=Math.max(...ys)-Math.min(...ys)+1;
 fs.copyFileSync(src,path.join(out,'items/inventory-ground/armor',name+'.png'),fs.constants.COPYFILE_EXCL);
 records.push({itemId:start+j,family,slot:slots[j],color,animation:names[j],appearanceId:1099+s*5+j,icon:name,width:w,height:h});
}
const target=path.join(out,'equipment/dagger-of-terror-poisoned/numbered');fs.mkdirSync(target,{recursive:true});
const manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'held-dagger-of-terror/manifest.json')));
const palette={f4e7ce:[139,199,74],887165:[63,108,40]};let tipCounts=[];
for(const f of manifest.frames){
 const n=String(f.frame).padStart(2,'0'),src=read(path.join(__dirname,'held-dagger-of-terror/frames/frame-'+n+'.png')),p=Buffer.from(src),axis=f.frame===17?'x':'y';
 let coords=[];for(let i=0;i<src.length;i+=4)if(src[i+3]&&palette[src.subarray(i,i+3).toString('hex')])coords.push(axis==='x'?i/4%f.width:Math.floor(i/4/f.width));
 const end=axis==='x'?Math.max(...coords):Math.min(...coords);let count=0;
 for(let i=0;i<p.length;i+=4){const key=src.subarray(i,i+3).toString('hex'),v=axis==='x'?i/4%f.width:Math.floor(i/4/f.width);if(src[i+3]&&palette[key]&&Math.abs(v-end)<3){p.set(palette[key],i);count++;}else assert.ok(p.subarray(i,i+4).equals(src.subarray(i,i+4)));assert.equal(p[i+3],src[i+3]);}
 assert.ok(count>0);tipCounts.push(count);
 execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',f.width+'x'+f.height,'-i','-','-frames:v','1',path.join(target,n+'.png')],{input:p});
}
fs.copyFileSync(path.join(__dirname,'dagger-of-terror-poisoned-approved.png'),path.join(out,'items/inventory-ground/weapons/dagger-of-terror-poisoned-icon.png'),fs.constants.COPYFILE_EXCL);
fs.writeFileSync(path.join(__dirname,'leather-poison-integration.json'),JSON.stringify({armor:records,poison:{itemId:3354,appearanceId:1098,tipCounts,palette}},null,2)+'\n');
console.log('PASS: 30 approved armor icons copied; 18 dagger frames changed only within 3-pixel blade tip; alpha unchanged');
