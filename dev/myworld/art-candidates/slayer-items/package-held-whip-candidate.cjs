// Register generated whip handles to sword-derived hand anchors with ONE shared scale.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const out=path.join(__dirname,'held-abyssal-whip'),root=process.argv[2],file=path.join(out,'generated.png');
const read=f=>execFileSync('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:64*1024*1024});
const info=JSON.parse(execFileSync('ffprobe',['-v','error','-show_entries','stream=width,height','-of','json',file])).streams[0],src=read(file),refs=JSON.parse(fs.readFileSync(path.join(out,'source-manifest.json'))),frames=[];
const avg=a=>a.reduce((s,v)=>s+v,0)/a.length;
let scale=.23;
for(let n=0;n<18;n++){
 const ref=refs[n],w=+ref.width,h=+ref.height,source=read(path.join(root,ref.path)),gold=[],blade=[];
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){let i=(y*w+x)*4;if(!source[i+3])continue;const [r,g,b]=source.subarray(i,i+3);if(r>g&&g>b)gold.push({x,y});else blade.push({x,y});}
 assert.ok(blade.length);
 // Some rear poses hide the guard; their remaining bottom pommel anchors the grip.
 const hiddenGuard=!gold.length;
 if(hiddenGuard){const bottom=Math.max(...blade.map(p=>p.y));gold.push(...blade.filter(p=>p.y>=bottom-1));}
 const gx=avg(gold.map(p=>p.x)),gy=avg(gold.map(p=>p.y)),vx=avg(blade.map(p=>p.x))-gx,vy=avg(blade.map(p=>p.y))-gy,len=Math.hypot(vx,vy);
 const handX=+ref.x_shift+gx-(hiddenGuard?0:vx/len*3),handY=+ref.y_shift+gy-(hiddenGuard?0:vy/len*3);
 let points=[];const x0=Math.round(n%3*info.width/3),x1=Math.round((n%3+1)*info.width/3),y0=Math.round(Math.floor(n/3)*info.height/6),y1=Math.round((Math.floor(n/3)+1)*info.height/6);
 for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++){let i=(y*info.width+x)*4;if(src[i+3]>=128)points.push({x,y,r:src[i],g:src[i+1],b:src[i+2]});}
 const l=Math.min(...points.map(p=>p.x)),r=Math.max(...points.map(p=>p.x)),t=Math.min(...points.map(p=>p.y)),b=Math.max(...points.map(p=>p.y));
 const handles=points.filter(p=>p.r>170&&p.g>160&&p.b>120&&(n<15?p.y<t+(b-t)*.27:n===17?p.x<l+(r-l)*.25:p.y>b-(b-t)*.27));
 assert.ok(handles.length,'detect ivory handle '+n);const ax=avg(handles.map(p=>p.x)),ay=avg(handles.map(p=>p.y)),bw=+ref.bound_width,bh=+ref.bound_height;
 for(const [space,extent] of [[handX-1,ax-l],[bw-handX-2,r-ax],[handY-1,ay-t],[bh-handY-2,b-ay]])if(extent>0)scale=Math.min(scale,space/extent);
 frames.push({frame:n,l,r,t,b,ax,ay,handX,handY,boundWidth:bw,boundHeight:bh});
}
assert.ok(scale>0);const sheet=Buffer.alloc(252*612*4);
for(const d of ['frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const write=(f,p,w,h)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',f],{input:p});
for(const f of frames){
 const w=Math.max(1,Math.round((f.r-f.l+1)*scale)),h=Math.max(1,Math.round((f.b-f.t+1)*scale)),x=Math.round(f.handX-(f.ax-f.l)*scale),y=Math.round(f.handY-(f.ay-f.t)*scale),p=Buffer.alloc(w*h*4),full=Buffer.alloc(f.boundWidth*f.boundHeight*4);
 assert.ok(x>=0&&y>=0&&x+w<=f.boundWidth&&y+h<=f.boundHeight);
 for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++){
  const sx=f.l+Math.min(f.r-f.l,Math.floor((dx+.5)*(f.r-f.l+1)/w)),sy=f.t+Math.min(f.b-f.t,Math.floor((dy+.5)*(f.b-f.t+1)/h)),i=(sy*info.width+sx)*4;
  src.copy(p,(dy*w+dx)*4,i,i+4);src.copy(full,((y+dy)*f.boundWidth+x+dx)*4,i,i+4);src.copy(sheet,((Math.floor(f.frame/3)*102+y+dy)*252+f.frame%3*84+Math.floor((84-f.boundWidth)/2)+x+dx)*4,i,i+4);
 }
 const name='frame-'+String(f.frame).padStart(2,'0')+'.png';write(path.join(out,'frames',name),p,w,h);write(path.join(out,'full-canvas',name),full,f.boundWidth,f.boundHeight);
 Object.assign(f,{width:w,height:h,xShift:x,yShift:y});
}
write(path.join(out,'contact.png'),sheet,252,612);
fs.writeFileSync(path.join(out,'candidate-manifest.json'),JSON.stringify({status:'review candidate; not installed; body occlusion needs in-game verification',sharedScale:scale,frames},null,2)+'\n');
console.log('PASS: 18 hand-registered candidates, shared scale '+scale.toFixed(4)+', all within existing bounds');
