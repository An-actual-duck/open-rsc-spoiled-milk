// Read-only export of existing runtime inventory bases, using OSAR's palette format.
const fs=require('node:fs'),zlib=require('node:zlib'),path=require('node:path');
const {execFileSync}=require('node:child_process');
const root=path.resolve(__dirname,'../../../..');
const data=zlib.gunzipSync(fs.readFileSync(path.join(root,'Client_Base/Cache/video/Custom_Sprites.osar')));
let p=0;const u8=()=>data[p++],u16=()=>{const v=data.readUInt16BE(p);p+=2;return v;},i16=()=>{const v=data.readInt16BE(p);p+=2;return v;};
const str=()=>{const start=p;while(data[p++]!==0){}return data.toString('latin1',start,p-1);};
const wanted={'5':'coif','7':'cuirass','17':'gloves','223':'boots','590':'chaps'};
fs.mkdirSync(path.join(__dirname,'leather-bases'),{recursive:true});
for(let s=0,n=u8();s<n;s++){
 const space=str(),count=u16();
 for(let e=0;e<count;e++){
  const id=str(),type=u8();if(type>=1&&type<=3)u8();const frames=u8();
  const colors=Array.from({length:u8()+1},()=>[u8(),u8(),u8()]);
  for(let f=0;f<frames;f++){
   const w=u16(),h=u16(),shift=u8(),ox=i16(),oy=i16(),bw=u16(),bh=u16();
   const indices=data.subarray(p,p+w*h);p+=w*h;
   if(space!=='items'||!wanted[id]||f!==0)continue;
   const pixels=Buffer.alloc(bw*bh*4);
   for(let y=0;y<h;y++)for(let x=0;x<w;x++){
    const rgb=colors[indices[y*w+x]],dx=x+(shift?ox:0),dy=y+(shift?oy:0);
    if(rgb.every(v=>v===0)||dx<0||dy<0||dx>=bw||dy>=bh)continue;
    const i=(dy*bw+dx)*4;pixels.set([...rgb,255],i);
   }
   const dest=path.join(__dirname,'leather-bases',wanted[id]+'.png');
   execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',bw+'x'+bh,'-i','-','-frames:v','1',dest],{input:pixels});
   console.log({id,slot:wanted[id],w,h,shift,ox,oy,bw,bh});
  }
 }
}
