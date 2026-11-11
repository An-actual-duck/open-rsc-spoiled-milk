/** Private synchronous segmented TEXT raster. No GL, await, callbacks or retained
 * borrowed views. Status0 is transactional/no-write; -1 poisons the frame. */
export function createSegmentedTextKernel({allowFault=false}={}){
 const MAX_GLYPHS=4096,MAX_TEXTS=256,MAX_FONT=8388608,MAX_PIXELS=2097152,MAX_WORK=2097152;
 const resolved=new Int32Array(MAX_GLYPHS*8);
 const stats={attempts:0,commits:0,texts:0,glyphs:0,rejections:0,poisonedFrames:0,faults:0,views:null};
 let faultArmed=false;
 const typedPrototype=Object.getPrototypeOf(Uint8Array.prototype),intrinsic={};
 for(const name of ["buffer","byteLength","byteOffset","length"])intrinsic[name]=Object.getOwnPropertyDescriptor(typedPrototype,name).get;
 const typedName=Object.getOwnPropertyDescriptor(typedPrototype,Symbol.toStringTag).get,bufferBytes=Object.getOwnPropertyDescriptor(ArrayBuffer.prototype,"byteLength").get;
 let active=false,lastOwner=0,lastSegment=0,poisonOwner=0,poisonGeneration=-1,poisonSequence=-1;
 const range=(a,b)=>a.buffer===b.buffer&&a.byteOffset<b.byteOffset+b.byteLength&&b.byteOffset<a.byteOffset+a.byteLength;
 function adapt(a,type,max){
  const expected=type.name;let buffer,offset,length,bytes;
  if(ArrayBuffer.isView(a)){
   if(typedName.call(a)!==expected)return null;
   buffer=intrinsic.buffer.call(a);offset=intrinsic.byteOffset.call(a);length=intrinsic.length.call(a);bytes=intrinsic.byteLength.call(a);
   bufferBytes.call(buffer); // Reject SharedArrayBuffer without consulting shadowable properties.
   if(bytes>max)return null;return new type(buffer,offset,length);
  }
  try{bytes=bufferBytes.call(a);}catch(notBuffer){return null;}
  if(bytes>max||bytes%type.BYTES_PER_ELEMENT)return null;return new type(a);
 }
 const reject=()=>{stats.rejections++;return 0;};
 function native(lib,header,texts,plots,fonts,c,t,generation,sequence){
  stats.attempts++;if(active)return -1;active=true;let execution=false,owner=0,segment=0;
  try{
   header=adapt(header,Int32Array,44);texts=adapt(texts,Int32Array,MAX_TEXTS*8*4);plots=adapt(plots,Int32Array,MAX_GLYPHS*5*4);
   fonts=ArrayBuffer.isView(fonts)&&typedName.call(fonts)==="Uint8Array"?adapt(fonts,Uint8Array,MAX_FONT):adapt(fonts,Int8Array,MAX_FONT);c=adapt(c,Int32Array,MAX_PIXELS*4);t=adapt(t,Float32Array,MAX_PIXELS*4);
   if(!header||header.length!==11||!texts||!plots||!fonts||!c||!t||header[0]!==1||header[9]!==1||header[10]!==1||!Number.isSafeInteger(generation)||generation<0||!Number.isSafeInteger(sequence)||sequence<0)return reject();
   const [version,w,h,nt,ng,nf,expectedWork]=header;owner=header[7];segment=header[8];
   if(w<1||h<1||w*h>MAX_PIXELS||nt<8||nt>MAX_TEXTS||ng<0||ng>MAX_GLYPHS||nf<0||nf>MAX_FONT||expectedWork<0||expectedWork>MAX_WORK||texts.length!==nt*8||plots.length!==ng*5||fonts.length!==nf||c.length!==w*h||t.length!==w*h||owner<1||segment<1)return reject();
   if(owner===lastOwner&&segment<=lastSegment)return reject();
   if(owner===poisonOwner&&generation<=poisonGeneration&&sequence<=poisonSequence)return -1;
   if(range(c,t)||[header,texts,plots,fonts].some(v=>range(v,c)||range(v,t)))return reject();
   let fontCursor=0,glyphCursor=0,work=0;
   for(let text=0;text<nt;text++){
    const at=text*8,fo=texts[at],fl=texts[at+1],go=texts[at+2],count=texts[at+3],left=texts[at+4],top=texts[at+5],right=texts[at+6],bottom=texts[at+7];
    if(fo!==fontCursor||fl<0||fo+fl>nf||go!==glyphCursor||count<0||go+count>ng||left<0||top<0||right<left||bottom<top||right>w||bottom>h)return reject();
    fontCursor+=fl;glyphCursor+=count;
    for(let i=go;i<go+count;i++){
     const p=i*5,x=plots[p],y=plots[p+1],color=plots[p+2],address=plots[p+3],aa=plots[p+4];
     if(x < -32768||x>32768||y < -32768||y>32768||address<0||address>fl-8||(aa!==0&&aa!==1))return reject();
     const byte=k=>(fonts[fo+k]<<24)>>24;
     let gw=byte(address+3),gh=byte(address+4),gx=x+byte(address+5),gy=y-byte(address+6),src=(byte(address)<<14)+(byte(address+1)<<7)+byte(address+2),dst=gx+gy*w,ds=w-gw,ss=0;
     if(gy<top){const lost=top-gy;src+=lost*gw;dst+=w*lost;gh-=lost;gy=top;}
     if(gy+gh>=bottom)gh-=1+gy+gh-bottom;
     if(left>gx){const lost=left-gx;ss+=lost;gw-=lost;src+=lost;gx=left;ds+=lost;dst+=lost;}
     if(right<=gw+gx){const lost=gw+gx-right+1;ds+=lost;ss+=lost;gw-=lost;}
     const out=i*8;resolved[out+2]=0;
     if(gw<=0||gh<=0)continue;
     if(src<0||dst<0||ss<0||ds<0||gw+ds!==w||dst%w+gw>w||src+(gh-1)*(gw+ss)+gw>fl||dst+(gh-1)*w+gw>c.length||(work+=gw*gh)>MAX_WORK)return reject();
     resolved[out]=fo+src;resolved[out+1]=dst;resolved[out+2]=gw;resolved[out+3]=gh;resolved[out+4]=ss;resolved[out+5]=ds;resolved[out+6]=color;resolved[out+7]=aa;
    }
   }
   if(fontCursor!==nf||glyphCursor!==ng||work!==expectedWork)return reject();
   if(stats.views===null)stats.views={c:c.constructor.name,t:t.constructor.name,font:fonts.constructor.name,borrowedStorageShared:true,retainsBorrowedViews:false};
   execution=true;if(faultArmed){faultArmed=false;stats.faults++;c[0]=0xff00ff;throw new Error("One-shot private TEXT poison proof");}let wrote=false;
   for(let i=0;i<ng;i++){
    const at=i*8,gw=resolved[at+2];if(gw===0)continue;let src=resolved[at],dst=resolved[at+1];const gh=resolved[at+3],ss=resolved[at+4],ds=resolved[at+5],color=resolved[at+6],aa=resolved[at+7];
    for(let y=0;y<gh;y++){for(let x=0;x<gw;x++,dst++){
     const alpha=fonts[src++]&255;
     if(!aa){if(alpha){c[dst]=color;t[dst]=0;wrote=true;}}
     else if(alpha>30){if(alpha>=230){c[dst]=color;t[dst]=0;}else{const inv=256-alpha,old=c[dst],rb=(Math.imul(color&0xff00ff,alpha)+Math.imul(old&0xff00ff,inv))&0xff00ff00,g=(Math.imul(inv,old&0xff00)+Math.imul(alpha,color&0xff00))&0xff0000;c[dst]=(rb+g)>>8;t[dst]=Math.fround(t[dst]*Math.fround(inv/256));}wrote=true;}
    }src+=ss;dst+=ds;}
   }
   lastOwner=owner;lastSegment=segment;stats.commits++;stats.texts+=nt;stats.glyphs+=ng;return wrote?2:1;
  }catch(error){if(!execution)return reject();poisonOwner=owner;poisonGeneration=generation;poisonSequence=sequence;stats.poisonedFrames++;return -1;}
  finally{header=texts=plots=fonts=c=t=null;active=false;}
 }
 return{native,stats,armPoisonOnce(){if(!allowFault)throw new Error("Private TEXT fault control unavailable");faultArmed=true;}};
}
