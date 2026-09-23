// Palette-only edit of the existing custom dagger, including its combat frames.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const crypto=require('node:crypto');
const exportRoot=process.argv[2];if(!exportRoot)throw Error('Pass sprite export root');
const out=path.join(__dirname,'held-dagger-of-terror');
for(const d of ['source','frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const lines=fs.readFileSync(path.join(exportRoot,'sprite-manifest.csv'),'utf8').trim().split(/\r?\n/),keys=lines.shift().split(',');
const entries=lines.map(l=>Object.fromEntries(l.split(',').map((v,i)=>[keys[i],v]))).filter(e=>e.subspace==='equipment'&&e.entry==='dagger').sort((a,b)=>+a.frame-+b.frame);
assert.equal(entries.length,18);
// Ivory highlight / shaded bone / bone hilt. Each is sampled from the approved icon.
const palette={d2d2d2:'f4e7ce',a5a5a5:'887165',ffc932:'e6d2ab'};
const icon=execFileSync('ffmpeg',['-v','error','-i',path.join(__dirname,'dagger-of-terror-approved.png'),'-f','rawvideo','-pix_fmt','rgba','-']);
const approved=new Set();for(let i=0;i<icon.length;i+=4)if(icon[i+3])approved.add(icon.subarray(i,i+3).toString('hex'));
for(const rgb of Object.values(palette))assert.ok(approved.has(rgb));
const write=(file,pixels,w,h)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',file],{input:pixels});
const sheet=Buffer.alloc(252*612*4),manifest=[];
for(const e of entries){
 const frame=+e.frame;assert.equal(frame,manifest.length);
 const name='frame-'+String(frame).padStart(2,'0')+'.png',file=path.join(exportRoot,e.path);
 fs.copyFileSync(file,path.join(out,'source',name),fs.constants.COPYFILE_EXCL);
 const src=execFileSync('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgba','-']);
 const w=+e.width,h=+e.height,bw=+e.bound_width,bh=+e.bound_height,x=+e.x_shift,y=+e.y_shift;
 assert.equal(src.length,w*h*4);assert.equal(bh,102);assert.equal(bw,frame<15?64:84);
 const pixels=Buffer.from(src);
 for(let i=0;i<pixels.length;i+=4){
  if(src[i+3]){const key=src.subarray(i,i+3).toString('hex');assert.ok(palette[key],key);pixels.set(Buffer.from(palette[key],'hex'),i);}
  assert.equal(pixels[i+3],src[i+3]);
 }
 write(path.join(out,'frames',name),pixels,w,h);
 const full=Buffer.alloc(bw*bh*4);
 for(let sy=0;sy<h;sy++)for(let sx=0;sx<w;sx++){
  assert.ok(x+sx<bw&&y+sy<bh&&x+sx>=0&&y+sy>=0);
  const i=(sy*w+sx)*4;pixels.copy(full,((y+sy)*bw+x+sx)*4,i,i+4);
  // Contact-sheet padding is display-only; actual frame anchors are unchanged.
  const dx=frame%3*84+Math.floor((84-bw)/2)+x+sx,dy=Math.floor(frame/3)*102+y+sy;
  pixels.copy(sheet,(dy*252+dx)*4,i,i+4);
 }
 write(path.join(out,'full-canvas',name),full,bw,bh);
 manifest.push({frame,source:e.path,sourceSha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),width:w,height:h,requiresShift:e.requires_shift,xShift:x,yShift:y,boundWidth:bw,boundHeight:bh,alphaUnchanged:true});
}
write(path.join(out,'contact.png'),sheet,252,612);
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify({source:'Custom_Sprites.osar equipment:dagger',status:'review candidate, not installed',palette,frames:manifest},null,2)+'\n');
console.log('PASS: 18 frames; source dimensions, offsets, alpha and gaps unchanged. All output colors sampled from approved inventory icon.');
