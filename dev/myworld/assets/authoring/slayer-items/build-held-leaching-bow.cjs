// Exact string-only palette swap. Grayscale wood remains tintable by the client.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const root=process.argv[2];if(!root)throw Error('Pass sprite export root');
const out=path.join(__dirname,'held-leaching-bow');
for(const d of ['source','frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const rows=fs.readFileSync(path.join(root,'sprite-manifest.csv'),'utf8').trim().split(/\r?\n/),keys=rows.shift().split(',');
const entries=rows.map(r=>Object.fromEntries(r.split(',').map((v,i)=>[keys[i],v]))).filter(e=>e.subspace==='equipment'&&e.entry==='longbow').sort((a,b)=>+a.frame-+b.frame);
assert.equal(entries.length,15);
const write=(file,pixels,w,h)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',file],{input:pixels});
const sheet=Buffer.alloc(192*510*4),frames=[];
for(const e of entries){
 const n=+e.frame,name='frame-'+String(n).padStart(2,'0')+'.png',file=path.join(root,e.path);
 fs.copyFileSync(file,path.join(out,'source',name),fs.constants.COPYFILE_EXCL);
 const src=execFileSync('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgba','-']),pixels=Buffer.from(src);
 const w=+e.width,h=+e.height,x=+e.x_shift,y=+e.y_shift;let changed=0;
 assert.equal(src.length,w*h*4);assert.equal(+e.bound_width,64);assert.equal(+e.bound_height,102);
 for(let i=0;i<src.length;i+=4){
  if(src[i+3]&&src.subarray(i,i+3).toString('hex')==='e7dfdb'){pixels.set([184,63,70],i);changed++;}
  else assert.ok(pixels.subarray(i,i+4).equals(src.subarray(i,i+4)));
  assert.equal(pixels[i+3],src[i+3]);
 }
 assert.ok(changed>0);write(path.join(out,'frames',name),pixels,w,h);
 const full=Buffer.alloc(64*102*4);
 for(let sy=0;sy<h;sy++)for(let sx=0;sx<w;sx++){
  const i=(sy*w+sx)*4,p=Buffer.from(pixels.subarray(i,i+4));
  // Same grayscale multiply used for wood tint; preview only, never baked into source frames.
  if(p[3]&&p[0]===p[1]&&p[1]===p[2]){const v=(p[0]*51)>>8;p[0]=p[1]=p[2]=v;}
  p.copy(full,((y+sy)*64+x+sx)*4);
  p.copy(sheet,((Math.floor(n/3)*102+y+sy)*192+(n%3)*64+x+sx)*4);
 }
 write(path.join(out,'full-canvas',name),full,64,102);
 frames.push({frame:n,source:e.path,width:w,height:h,xShift:x,yShift:y,boundWidth:64,boundHeight:102,stringPixelsChanged:changed});
}
write(path.join(out,'contact.png'),sheet,192,510);
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify({status:'review candidate; not installed',source:'Custom_Sprites.osar equipment:longbow',stringPalette:{e7dfdb:'b83f46'},previewWoodTint:'333333',frames},null,2)+'\n');
console.log('PASS: all 15 frames changed only string RGB; wood pixels, alpha, geometry and anchors preserved.');
