// Reference composition only: original pixels and canonical placement, no new artwork.
const fs=require('node:fs'),path=require('node:path'),{execFileSync:run}=require('node:child_process');
const root=process.argv[2],out=path.join(__dirname,'held-shears/references');
fs.mkdirSync(out,{recursive:true});
const rows=fs.readFileSync(path.join(root,'sprite-manifest.csv'),'utf8').trim().split(/\r?\n/),keys=rows.shift().split(',');
const all=rows.map(r=>Object.fromEntries(r.split(',').map((v,i)=>[keys[i],v])));
const ox=[17,15,13,18,21,25,27,32,36,44,49,44,40,41,42],oy=[27,29,27,32,31,28,30,29,27,28,24,25,41,36,23];
const metadata={};
for(const family of ['pickaxe','sword','shears']){
 const entries=family==='shears'?Array.from({length:15},(_,n)=>({frame:n,x_shift:ox[n],y_shift:oy[n],path:path.resolve(__dirname,'../../assets/sprites/equipment/shears/numbered',String(n).padStart(2,'0')+'.png')})):all.filter(e=>e.subspace==='equipment'&&e.entry===family&&+e.frame<15).sort((a,b)=>+a.frame-+b.frame);
 const sheet=Buffer.alloc(252*510*4);metadata[family]=[];
 for(const e of entries){
  const file=path.isAbsolute(e.path)?e.path:path.join(root,e.path),info=JSON.parse(run('ffprobe',['-v','error','-show_entries','stream=width,height','-of','json',file])).streams[0];
  const {width:w,height:h}=info,n=+e.frame,x=+e.x_shift,y=+e.y_shift;
  const pixels=run('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgba','-']);
  for(let sy=0;sy<h;sy++)for(let sx=0;sx<w;sx++){const i=(sy*w+sx)*4;pixels.copy(sheet,((Math.floor(n/3)*102+y+sy)*252+n%3*84+10+x+sx)*4,i,i+4);}
  metadata[family].push({frame:n,width:w,height:h,xShift:x,yShift:y,boundWidth:64,boundHeight:102,source:file});
 }
 run('ffmpeg',['-v','error','-y','-f','rawvideo','-pix_fmt','rgba','-s','252x510','-i','-','-vf','scale=756:1530:flags=neighbor','-frames:v','1',path.join(out,family+'-3x.png')],{input:sheet});
}
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(metadata,null,2)+'\n');
