// Fold-over movement revision. Approved attack pixels and metadata are immutable.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync:run}=require('node:child_process');
const old=path.join(__dirname,'held-abyssal-whip'),out=path.join(__dirname,'held-abyssal-whip-v2');
fs.mkdirSync(out,{recursive:true});
if(process.argv[2])fs.copyFileSync(process.argv[2],path.join(out,'generated.png'));
const read=f=>run('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:64*1024*1024});
const file=path.join(out,'generated.png'),info=JSON.parse(run('ffprobe',['-v','error','-show_entries','stream=width,height','-of','json',file])).streams[0],src=read(file);
const previous=JSON.parse(fs.readFileSync(path.join(old,'candidate-manifest.json'))),scale=previous.sharedScale,sheet=Buffer.alloc(252*612*4),frames=[];
for(const d of ['frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const write=(f,p,w,h)=>{if(fs.existsSync(f)){assert.deepEqual(read(f),p,'existing output must match '+f);return;}run('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',f],{input:p});};
for(let n=0;n<18;n++){
 const name='frame-'+String(n).padStart(2,'0')+'.png',ref={...previous.frames[n]};let f,full;
 // User-approved right-edge extension only; preserve hand anchor and scale.
 if(n===11)ref.boundWidth=66;
 if(n>=15){
  for(const d of ['frames','full-canvas']){fs.copyFileSync(path.join(old,d,name),path.join(out,d,name));assert.deepEqual(fs.readFileSync(path.join(old,d,name)),fs.readFileSync(path.join(out,d,name)));}
  f={...ref,preservedOriginalAttack:true};full=read(path.join(out,'full-canvas',name));
 }else{
  const points=[];
  for(let y=Math.round(Math.floor(n/3)*info.height/6);y<Math.round((Math.floor(n/3)+1)*info.height/6);y++)for(let x=Math.round(n%3*info.width/3);x<Math.round((n%3+1)*info.width/3);x++){const i=(y*info.width+x)*4;if(src[i+3]>=128)points.push({x,y,r:src[i],g:src[i+1],b:src[i+2]});}
  const l=Math.min(...points.map(p=>p.x)),r=Math.max(...points.map(p=>p.x)),t=Math.min(...points.map(p=>p.y)),b=Math.max(...points.map(p=>p.y));
  // The straight ivory shaft has the longest bright vertical run in upper half.
  const counts=new Map();for(const p of points)if(p.y<t+(b-t)*.5&&p.r>185&&p.g>175&&p.b>135)counts.set(p.x,(counts.get(p.x)||0)+1);
  const best=Math.max(...counts.values()),cols=[...counts].filter(([x,c])=>c>=best*.85).map(([x])=>x),ax=cols.reduce((a,b)=>a+b,0)/cols.length;
  // Lower dark grip is below the ivory shaft, not at the top attachment.
  const ay=t+(b-t)*.39,w=Math.round((r-l+1)*scale),h=Math.round((b-t+1)*scale),x=Math.round(ref.handX-(ax-l)*scale),y=Math.round(ref.handY-(ay-t)*scale);
  assert.ok(x>=0&&y>=0&&x+w<=ref.boundWidth&&y+h<=ref.boundHeight,'existing canvas bounds '+n+' '+JSON.stringify({x,y,w,h,ax,ay,l,r,t,b}));
  const p=Buffer.alloc(w*h*4);full=Buffer.alloc(ref.boundWidth*ref.boundHeight*4);
  for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++){const sx=l+Math.min(r-l,Math.floor((dx+.5)*(r-l+1)/w)),sy=t+Math.min(b-t,Math.floor((dy+.5)*(b-t+1)/h)),i=(sy*info.width+sx)*4;src.copy(p,(dy*w+dx)*4,i,i+4);src.copy(full,((y+dy)*ref.boundWidth+x+dx)*4,i,i+4);}
  write(path.join(out,'frames',name),p,w,h);write(path.join(out,'full-canvas',name),full,ref.boundWidth,ref.boundHeight);
  f={...ref,l,r,t,b,ax,ay,width:w,height:h,xShift:x,yShift:y};
 }
 for(let y=0;y<f.boundHeight;y++)for(let x=0;x<f.boundWidth;x++){const i=(y*f.boundWidth+x)*4;full.copy(sheet,((Math.floor(n/3)*102+y)*252+n%3*84+(n<15?10:0)+x)*4,i,i+4);}
 frames.push(f);
}
write(path.join(out,'contact.png'),sheet,252,612);
fs.writeFileSync(path.join(out,'candidate-manifest.json'),JSON.stringify({status:'Review candidate; grip/body occlusion requires in-game validation; not installed',sharedScale:scale,frames},null,2)+'\n');
console.log('PASS: 15 revised movement frames; only frame 11 widened to approved 66px; 3 byte-identical attacks; original scale retained.');
