const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const root=process.argv[2],out=path.join(__dirname,'held-abyssal-whip');fs.mkdirSync(out,{recursive:true});
const rows=fs.readFileSync(path.join(root,'sprite-manifest.csv'),'utf8').trim().split(/\r?\n/),keys=rows.shift().split(',');
const entries=rows.map(r=>Object.fromEntries(r.split(',').map((v,i)=>[keys[i],v]))).filter(e=>e.subspace==='equipment'&&e.entry==='sword').sort((a,b)=>+a.frame-+b.frame);
const sheet=Buffer.alloc(252*612*4);
for(const e of entries){const w=+e.width,h=+e.height,n=+e.frame,bw=+e.bound_width,x=+e.x_shift,y=+e.y_shift;
 const b=execFileSync('ffmpeg',['-v','error','-i',path.join(root,e.path),'-f','rawvideo','-pix_fmt','rgba','-']);
 for(let sy=0;sy<h;sy++)for(let sx=0;sx<w;sx++){let i=(sy*w+sx)*4;b.copy(sheet,((Math.floor(n/3)*102+y+sy)*252+n%3*84+Math.floor((84-bw)/2)+x+sx)*4,i,i+4);}
}
execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','252x612','-i','-','-frames:v','1',path.join(out,'sword-grip-reference.png')],{input:sheet});
fs.writeFileSync(path.join(out,'source-manifest.json'),JSON.stringify(entries,null,2)+'\n');
