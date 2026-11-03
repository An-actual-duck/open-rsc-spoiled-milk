const fs=require('node:fs'),path=require('node:path'),{execFileSync:run}=require('node:child_process');
const out=path.join(__dirname,'held-abyssal-whip-v3');fs.mkdirSync(out,{recursive:true});
const times=['18-01-54','18-01-58','18-02-04','18-02-08','18-02-12','18-02-15','18-02-18','18-02-21'],w=320,h=360,sheet=Buffer.alloc(w*4*h*2*4);
times.forEach((t,n)=>{const p=run('ffmpeg',['-v','error','-i','/home/justin/Pictures/8 directional held sword/Screenshot from 2026-09-23 '+t+'.png','-vf','crop=320:360:800:380','-f','rawvideo','-pix_fmt','rgba','-'],{maxBuffer:32*1024*1024});for(let y=0;y<h;y++)p.copy(sheet,((Math.floor(n/4)*h+y)*w*4+(n%4)*w)*4,y*w*4,(y+1)*w*4)});
run('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','1280x720','-i','-','-frames:v','1',path.join(out,'eight-direction-reference.png')],{input:sheet});
