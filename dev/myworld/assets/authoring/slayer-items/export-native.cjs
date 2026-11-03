// Deterministic packaging of imagegen art; does not draw or repaint sprites.
// node export-native.cjs source.png output.png width height
const {execFileSync}=require('node:child_process');
const [source,destination,bw,bh]=process.argv.slice(2);
const maxW=Number(bw),maxH=Number(bh);
if(!source||!destination||!Number.isInteger(maxW)||!Number.isInteger(maxH)||maxW<1||maxW>48||maxH<1||maxH>32) throw Error('Expected source, destination, width <=48, height <=32');
const {width:w,height:h}=JSON.parse(execFileSync('ffprobe',['-v','error','-select_streams','v:0','-show_entries','stream=width,height','-of','json',source])).streams[0];
const pixels=execFileSync('ffmpeg',['-v','error','-i',source,'-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:64*1024*1024});
let x0=w,y0=h,x1=-1,y1=-1;
for(let y=0;y<h;y++) for(let x=0;x<w;x++) if(pixels[(y*w+x)*4+3]>=128){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}
if(x1<0) throw Error('No opaque artwork');
const sw=x1-x0+1,sh=y1-y0+1,scale=Math.min(maxW/sw,maxH/sh);
const tw=Math.round(sw*scale),th=Math.round(sh*scale),ox=Math.round((48-tw)/2),oy=Math.round((32-th)/2);
const out=Buffer.alloc(48*32*4);
for(let y=0;y<th;y++) for(let x=0;x<tw;x++){
  const sx=Math.min(x1,x0+Math.floor((x+.5)*sw/tw)),sy=Math.min(y1,y0+Math.floor((y+.5)*sh/th));
  const a=(sy*w+sx)*4,b=((oy+y)*48+ox+x)*4;
  if(pixels[a+3]>=128){pixels.copy(out,b,a,a+3);out[b+3]=255;}
}
execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','48x32','-i','-','-frames:v','1',destination],{input:out});
console.log(JSON.stringify({canvas:[48,32],sourceBounds:[x0,y0,sw,sh],fittedBox:[ox,oy,tw,th],destination},null,2));
