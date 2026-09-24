// Size/registration only: retain generated design, fit 32px shield height at original shield centers.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const out=path.join(__dirname,'held-shield-of-mobility'),file=path.join(out,'generated.png');
const info=JSON.parse(execFileSync('ffprobe',['-v','error','-show_entries','stream=width,height','-of','json',file])).streams[0];
const src=execFileSync('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:64*1024*1024});
const refs=JSON.parse(fs.readFileSync(path.join(out,'squareshield-manifest.json'))),records=[],sheet=Buffer.alloc(252*612*4);
for(const d of ['frames','full-canvas'])fs.mkdirSync(path.join(out,d),{recursive:true});
const write=(file,p,w,h)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',file],{input:p});
for(let n=0;n<18;n++){
 const x0=Math.round(n%3*info.width/3),x1=Math.round((n%3+1)*info.width/3),y0=Math.round(Math.floor(n/3)*info.height/6),y1=Math.round((Math.floor(n/3)+1)*info.height/6);
 let l=x1,r=x0,t=y1,b=y0;
 for(let y=y0;y<y1;y++)for(let x=x0;x<x1;x++)if(src[(y*info.width+x)*4+3]>=128){l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);b=Math.max(b,y);}
 assert.ok(r>=l&&b>=t);const ref=refs[n],cx=+ref.x_shift+(+ref.width)/2;
 const maxWidth=Math.floor(2*Math.min(cx,+ref.bound_width-cx));
 const h=Math.min(32,Math.floor(maxWidth*(b-t+1)/(r-l+1))),w=Math.max(1,Math.round((r-l+1)*h/(b-t+1))),pixels=Buffer.alloc(w*h*4);
 const bw=+ref.bound_width,bh=+ref.bound_height,x=Math.round(+ref.x_shift+(+ref.width-w)/2),y=Math.round(+ref.y_shift+(+ref.height-h)/2);
 assert.ok(x>=0&&y>=0&&x+w<=bw&&y+h<=bh);
 for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++){
  const sx=l+Math.min(r-l,Math.floor((dx+.5)*(r-l+1)/w)),sy=t+Math.min(b-t,Math.floor((dy+.5)*(b-t+1)/h)),i=(sy*info.width+sx)*4;
  src.copy(pixels,(dy*w+dx)*4,i,i+4);
 }
 const full=Buffer.alloc(bw*bh*4),name='frame-'+String(n).padStart(2,'0')+'.png';
 for(let dy=0;dy<h;dy++)for(let dx=0;dx<w;dx++){let i=(dy*w+dx)*4;pixels.copy(full,((y+dy)*bw+x+dx)*4,i,i+4);pixels.copy(sheet,((Math.floor(n/3)*102+y+dy)*252+n%3*84+Math.floor((84-bw)/2)+x+dx)*4,i,i+4);}
 write(path.join(out,'frames',name),pixels,w,h);write(path.join(out,'full-canvas',name),full,bw,bh);
 records.push({frame:n,width:w,height:h,xShift:x,yShift:y,boundWidth:bw,boundHeight:bh});
}
write(path.join(out,'contact.png'),sheet,252,612);
fs.writeFileSync(path.join(out,'candidate-manifest.json'),JSON.stringify({status:'review candidate; not installed',registration:'square shield center; 32 pixel height; original canvas',frames:records},null,2)+'\n');
