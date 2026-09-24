// Mechanical candidate processing only: no painting, recoloring, or production installation.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync:run}=require('node:child_process');
const out=path.join(__dirname,'held-shears'),source=process.argv[2];
if(source)fs.copyFileSync(source,path.join(out,'generated.png'));
const read=f=>run('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:64*1024*1024});
const file=path.join(out,'generated.png'),info=JSON.parse(run('ffprobe',['-v','error','-show_entries','stream=width,height','-of','json',file])).streams[0],src=read(file);
const refs=JSON.parse(fs.readFileSync(path.join(__dirname,'held-abyssal-whip/candidate-manifest.json'))).frames;
const originals=JSON.parse(fs.readFileSync(path.join(out,'references/manifest.json'))).shears;
const scale=.13,frames=[],sheet=Buffer.alloc(192*510*4);
for(const dir of ['frames','full-canvas','original-full-canvas'])fs.mkdirSync(path.join(out,dir),{recursive:true});
const write=(f,p,w,h)=>run('ffmpeg',['-v','error','-y','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',f],{input:p});
for(let n=0;n<15;n++){
 const points=[],red=[];
 // Generation is a 3×5 layout but row3 extends beyond an exact third; split in clean gutters.
 const columns=[0,Math.round(info.width*.37),Math.round(info.width*.72),info.width];
 for(let y=Math.round(Math.floor(n/3)*info.height/5);y<Math.round((Math.floor(n/3)+1)*info.height/5);y++)for(let x=columns[n%3];x<columns[n%3+1];x++){
  const i=(y*info.width+x)*4;if(src[i+3]<16)continue;points.push({x,y});if(src[i+3]>=128&&src[i]>45&&src[i]>src[i+1]*1.5&&src[i]>src[i+2]*1.5)red.push({x,y});
 }
 assert.ok(points.length&&red.length,'visible sprite and red handle '+n);
 const l=Math.min(...points.map(p=>p.x)),r=Math.max(...points.map(p=>p.x)),t=Math.min(...points.map(p=>p.y)),b=Math.max(...points.map(p=>p.y));
 // Red handle centroid is an approximate grip anchor, not a claim of anatomical validation.
 const ax=red.reduce((s,p)=>s+p.x,0)/red.length,ay=red.reduce((s,p)=>s+p.y,0)/red.length,ref=refs[n];
 const w=Math.ceil((r-l+1)*scale),h=Math.ceil((b-t+1)*scale),x=Math.round(ref.handX-(ax-l)*scale),y=Math.round(ref.handY-(ay-t)*scale),p=Buffer.alloc(w*h*4),full=Buffer.alloc(64*102*4);
 assert.ok(x>=0&&y>=0&&x+w<=64&&y+h<=102,'native bounds '+n+' '+JSON.stringify({x,y,w,h}));
 for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++){const sx=Math.min(r,l+Math.floor((dx+.5)/scale)),sy=Math.min(b,t+Math.floor((dy+.5)/scale)),i=(sy*info.width+sx)*4,d=(dy*w+dx)*4;src.copy(p,d,i,i+4);p.copy(full,((y+dy)*64+x+dx)*4,d,d+4);}
 const name='frame-'+String(n).padStart(2,'0')+'.png';write(path.join(out,'frames',name),p,w,h);write(path.join(out,'full-canvas',name),full,64,102);
 for(let sy=0;sy<102;sy++)full.copy(sheet,((Math.floor(n/3)*102+sy)*192+n%3*64)*4,sy*64*4,(sy+1)*64*4);
 const old=originals[n],op=read(old.source),oc=Buffer.alloc(64*102*4);
 for(let sy=0;sy<old.height;sy++)for(let sx=0;sx<old.width;sx++){const i=(sy*old.width+sx)*4;op.copy(oc,((old.yShift+sy)*64+old.xShift+sx)*4,i,i+4);}write(path.join(out,'original-full-canvas',name),oc,64,102);
 frames.push({frame:n,width:w,height:h,xShift:x,yShift:y,boundWidth:64,boundHeight:102,handX:ref.handX,handY:ref.handY,sourceGrip:[ax,ay],sourceBounds:[l,t,r-l+1,b-t+1],gripAlpha:full[(Math.round(ref.handY)*64+Math.round(ref.handX))*4+3]});
}
write(path.join(out,'contact.png'),sheet,192,510);
fs.writeFileSync(path.join(out,'candidate-manifest.json'),JSON.stringify({status:'Review candidate only; not installed. Approximate handle-centroid registration; generated fist gaps imperfect and body occlusion unvalidated.',source:'Built-in imagegen second output exec-00cb31de-3703-4c48-8890-dfc12c787427.png',promptSummary:'15 held shears frames; red straight handles, gray tapered slightly parted blades matching authentic inventory icon; pickaxe/sword grips and hand gaps; front, diagonal front, side, diagonal away, rear; no attacks.',scale,alpha:'Original RGBA retained by nearest-neighbor sampling; no painted or binary-alpha edits.',walkCycle:[0,1,2,1],frames},null,2)+'\n');
const preview=path.join(out,'preview.html');
fs.writeFileSync(preview,fs.readFileSync(preview,'utf8').replace(/(<script id="grip-data" type="application\/json">)[\s\S]*?(<\/script>)/,'$1'+JSON.stringify(frames.map(f=>[f.handX,f.handY]))+'$2'));
console.log('PASS: 15 native frames, all inside64x102; alpha preserved; no runtime changes');
