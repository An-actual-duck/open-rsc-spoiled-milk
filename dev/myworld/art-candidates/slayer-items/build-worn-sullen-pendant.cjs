// Recolor only the circular amulet; keep the warm white/grey neck string exact.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const root=process.argv[2],out=path.join(__dirname,'worn-sullen-pendant');
for(const d of ['source','frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const rows=fs.readFileSync(path.join(root,'sprite-manifest.csv'),'utf8').trim().split(/\r?\n/),keys=rows.shift().split(',');
const entries=rows.map(r=>Object.fromEntries(r.split(',').map((v,i)=>[keys[i],v]))).filter(e=>e.subspace==='equipment'&&e.entry==='amulet').sort((a,b)=>+a.frame-+b.frame);
assert.equal(entries.length,18);
// Pale crystalline blue from the inventory pendant, with a darker blue rim for readability.
const palette={ffffff:'c1d5fa',eaeaea:'a8c2eb',c6c6c6:'6f91ba',d4d4ff:'d8e7ff',b8b8ff:'8fafdf'};
const write=(file,pixels,w,h)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',file],{input:pixels});
const sheet=Buffer.alloc(252*612*4),frames=[];
for(const e of entries){
 const n=+e.frame,name='frame-'+String(n).padStart(2,'0')+'.png',file=path.join(root,e.path);
 fs.copyFileSync(file,path.join(out,'source',name),fs.constants.COPYFILE_EXCL);
 const src=execFileSync('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgba','-']),pixels=Buffer.from(src);
 const w=+e.width,h=+e.height,x=+e.x_shift,y=+e.y_shift,bw=+e.bound_width,bh=+e.bound_height;let changed=0;
 for(let i=0;i<src.length;i+=4){
  const key=src.subarray(i,i+3).toString('hex');
  if(src[i+3]&&palette[key]){pixels.set(Buffer.from(palette[key],'hex'),i);changed++;}
  else assert.ok(pixels.subarray(i,i+4).equals(src.subarray(i,i+4)));
  assert.equal(pixels[i+3],src[i+3]);
 }
 if(n>=9&&n<=14)assert.equal(changed,0,'rear-view string must stay unchanged');else assert.ok(changed>0);
 write(path.join(out,'frames',name),pixels,w,h);
 const full=Buffer.alloc(bw*bh*4);
 for(let sy=0;sy<h;sy++)for(let sx=0;sx<w;sx++){
  const i=(sy*w+sx)*4;pixels.copy(full,((y+sy)*bw+x+sx)*4,i,i+4);
  pixels.copy(sheet,((Math.floor(n/3)*102+y+sy)*252+n%3*84+Math.floor((84-bw)/2)+x+sx)*4,i,i+4);
 }
 write(path.join(out,'full-canvas',name),full,bw,bh);
 frames.push({frame:n,width:w,height:h,xShift:x,yShift:y,boundWidth:bw,boundHeight:bh,changedPixels:changed});
}
write(path.join(out,'contact.png'),sheet,252,612);
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify({status:'review candidate; not installed',source:'Custom_Sprites.osar equipment:amulet',palette,frames},null,2)+'\n');
console.log('PASS: 18 frames; string, alpha, geometry, anchors and rear occlusion preserved');
