const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync:run}=require('node:child_process');
const out=path.join(__dirname,'held-abyssal-whip-v3');
const read=f=>run('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:64*1024*1024});
if(process.argv[2])fs.copyFileSync(process.argv[2],path.join(out,'generated.png'));
const file=path.join(out,'generated.png'),info=JSON.parse(run('ffprobe',['-v','error','-show_entries','stream=width,height','-of','json',file])).streams[0],src=read(file);
const refs=JSON.parse(fs.readFileSync(path.join(__dirname,'held-abyssal-whip/candidate-manifest.json'))).frames;
// Reviewed transparent fist-gap centers in the new generation, not handle centroids.
const grips=[[111,161],[414,161],[717,161],[103,453],[410,453],[705,453],[126,733],[420,733],[725,733],[123,1015],[428,1015],[728,1015],[138,1312],[440,1312],[738,1312],[97,1634],[329,1634],[594,1633]];
const scale=.22,frames=[],sheet=Buffer.alloc(330*660*4);
for(const d of ['frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const write=(f,p,w,h)=>run('ffmpeg',['-v','error','-y','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',f],{input:p});
for(let n=0;n<18;n++){
 const ref=refs[n],points=[];
 const columns=n>=15?[0,295,565,info.width]:[0,Math.round(info.width/3),Math.round(info.width*2/3),info.width];
 for(let y=Math.round(Math.floor(n/3)*info.height/6);y<Math.round((Math.floor(n/3)+1)*info.height/6);y++)for(let x=columns[n%3];x<columns[n%3+1];x++)if(src[(y*info.width+x)*4+3]>=128)points.push({x,y});
 const l=Math.min(...points.map(p=>p.x)),r=Math.max(...points.map(p=>p.x)),t=Math.min(...points.map(p=>p.y)),b=Math.max(...points.map(p=>p.y));
 let [ax,ay]=grips[n],best=Infinity;
 // Locate the cleanest small opening near the visually reviewed fist position.
 for(let y=grips[n][1]-6;y<=grips[n][1]+6;y++)for(let x=grips[n][0]-4;x<=grips[n][0]+4;x++){
  let score=0;for(let j=-3;j<=3;j++)for(let i=-3;i<=3;i++)score+=src[((y+j)*info.width+x+i)*4+3];
  score+=Math.hypot(x-grips[n][0],y-grips[n][1])*5;if(score<best){best=score;ax=x;ay=y;}
 }
 const w=Math.ceil((r-l+1)*scale),h=Math.ceil((b-t+1)*scale),x=Math.round(ref.handX-(ax-l)*scale),y=Math.round(ref.handY-(ay-t)*scale);
 assert.ok(x>=0&&y>=0,'negative bounds '+n);
 const bw=Math.max(ref.boundWidth,x+w),bh=Math.max(ref.boundHeight,y+h),p=Buffer.alloc(w*h*4),full=Buffer.alloc(bw*bh*4);
 // Binary alpha for RSC sprites; faint generated transparency must not fill the fist gap.
 for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++){const sx=Math.min(r,l+Math.floor((dx+.5)/scale)),sy=Math.min(b,t+Math.floor((dy+.5)/scale)),i=(sy*info.width+sx)*4;if(src[i+3]<128)continue;const dst=(dy*w+dx)*4;src.copy(p,dst,i,i+3);p[dst+3]=255;p.copy(full,((y+dy)*bw+x+dx)*4,dst,dst+4);}
 const name='frame-'+String(n).padStart(2,'0')+'.png';write(path.join(out,'frames',name),p,w,h);write(path.join(out,'full-canvas',name),full,bw,bh);
 for(let sy=0;sy<bh;sy++)full.copy(sheet,((Math.floor(n/3)*110+sy)*330+n%3*110)*4,sy*bw*4,(sy+1)*bw*4);
 const hx=Math.round(ref.handX),hy=Math.round(ref.handY),alpha=full[(hy*bw+hx)*4+3];
 assert.equal(alpha,0,'grip must be transparent '+n);
 frames.push({frame:n,width:w,height:h,xShift:x,yShift:y,boundWidth:bw,boundHeight:bh,handX:ref.handX,handY:ref.handY,sourceGrip:[ax,ay],gripAlpha:alpha});
}
write(path.join(out,'contact.png'),sheet,330,660);
fs.writeFileSync(path.join(out,'candidate-manifest.json'),JSON.stringify({status:'New design review candidate; not installed. Mirror views are orientation previews, not full renderer body composites.',scale,frames},null,2)+'\n');
console.log(frames.map(f=>({frame:f.frame,bounds:[f.boundWidth,f.boundHeight],gripAlpha:f.gripAlpha})));
