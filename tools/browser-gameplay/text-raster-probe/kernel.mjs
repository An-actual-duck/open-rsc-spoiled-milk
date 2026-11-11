// Private ABI/cost probe only. No runtime registration or production adoption.
export function createTextKernel(){
 const MAX_GLYPHS=128,MAX_PIXELS=2097152,MAX_WORK=2097152,STRIDE=5,RESOLVED=7,scratch=new Int32Array(MAX_GLYPHS*RESOLVED);
 const stats={calls:0,validated:0,rejections:0,rasterCalls:0,validateOnlyCalls:0,zeroWriteCalls:0,wroteCalls:0,glyphs:0,poisoned:false,views:null,rawKinds:null,adaptedBuffers:0,error:null};let active=false;
 function range(a,b){return a.buffer===b.buffer&&a.byteOffset<b.byteOffset+b.byteLength&&b.byteOffset<a.byteOffset+a.byteLength;}
 function genuine(a,type){return a instanceof type&&ArrayBuffer.isView(a)&&a.buffer instanceof ArrayBuffer;}
 function adapt(value,type,maxBytes){
  if(genuine(value,type))return value.byteLength<=maxBytes?value:null;
  if(value instanceof ArrayBuffer&&value.byteLength<=maxBytes&&value.byteLength%type.BYTES_PER_ELEMENT===0){stats.adaptedBuffers++;return new type(value);}return null;
 }
 const shape=a=>({constructor:a.constructor.name,elementBytes:a.BYTES_PER_ELEMENT,length:a.length,byteOffset:a.byteOffset,byteLength:a.byteLength,bufferKind:Object.prototype.toString.call(a.buffer),bufferBytes:a.buffer.byteLength});
 function native(lib,font,commands,surface,c,t,generation,sequence,validateOnly){
  stats.calls++;if(stats.poisoned)return -1;if(active){stats.poisoned=true;stats.error='Reentrant text native';return -1;}active=true;let execution=false;
  try{
   if(stats.rawKinds===null)stats.rawKinds={font:Object.prototype.toString.call(font),commands:Object.prototype.toString.call(commands),surface:Object.prototype.toString.call(surface),c:Object.prototype.toString.call(c),t:Object.prototype.toString.call(t)};
   font=font instanceof Uint8Array?adapt(font,Uint8Array,MAX_PIXELS):adapt(font,Int8Array,MAX_PIXELS);commands=adapt(commands,Int32Array,MAX_GLYPHS*STRIDE*4);surface=adapt(surface,Int32Array,40);c=adapt(c,Int32Array,MAX_PIXELS*4);t=adapt(t,Float32Array,MAX_PIXELS*4);
   if(!(genuine(font,Int8Array)||genuine(font,Uint8Array))||!genuine(commands,Int32Array)||!genuine(surface,Int32Array)||!genuine(c,Int32Array)||!genuine(t,Float32Array)||surface.length!==10||surface[0]!==1||surface[3]<0||surface[3]>MAX_GLYPHS||commands.length<surface[3]*STRIDE||font.length>MAX_PIXELS||!Number.isSafeInteger(generation)||generation<0||!Number.isSafeInteger(sequence)||sequence<0||![0,1].includes(validateOnly))return reject();
   const w=surface[1],h=surface[2],count=surface[3],left=surface[4],top=surface[5],right=surface[6],bottom=surface[7];
   if(w<1||h<1||w*h>MAX_PIXELS||c.length!==w*h||t.length!==w*h||left<0||top<0||right> w||bottom>h||right<left||bottom<top||surface[8]!==STRIDE||surface[9]!==0)return reject();
   const readers=[font,commands,surface];if(range(c,t)||readers.some(a=>range(a,c)||range(a,t)))return reject();
   if(stats.views===null)stats.views={font:shape(font),commands:shape(commands),surface:shape(surface),c:shape(c),t:shape(t),sharedBackingBuffersAllowedWhenDisjoint:true,zeroCopyBorrow:true};
   let work=0;
   for(let i=0;i<count;i++){
    const at=i*STRIDE,x=commands[at],y=commands[at+1],color=commands[at+2],address=commands[at+3],aa=commands[at+4];if(address<0||address>font.length-7||!([0,1].includes(aa)))return reject();
    const byte=k=>(font[k]<<24)>>24;
    let gw=byte(address+3),gh=byte(address+4),gx=(x+byte(address+5))|0,gy=(y-byte(address+6))|0,src=((byte(address)<<14)+(byte(address+1)<<7)+byte(address+2))|0,dst=(gx+Math.imul(gy,w))|0,ds=(w-gw)|0,ss=0;
    if(gy<top){const lost=(top-gy)|0;src=(src+Math.imul(lost,gw))|0;dst=(dst+Math.imul(w,lost))|0;gh=(gh-lost)|0;gy=top;}
    if(((gy+gh)|0)>=bottom)gh=(gh-(((1+gy+gh)|0)-bottom))|0;
    if(left>gx){const lost=(left-gx)|0;ss=(ss+lost)|0;gw=(gw-lost)|0;src=(src+lost)|0;gx=left;ds=(ds+lost)|0;dst=(dst+lost)|0;}
    if(right<=((gw+gx)|0)){const lost=(((gw+gx)|0)-right+1)|0;ds=(ds+lost)|0;ss=(ss+lost)|0;gw=(gw-lost)|0;}
    const out=i*RESOLVED;if(gw<=0||gh<=0){scratch[out+2]=0;continue;}
    // Safe integer extents validate every literal inner-loop row before writes.
    if(src<0||dst<0||ss<0||ds<0||gw+ds!==w||dst%w+gw>w||src+(gh-1)*(gw+ss)+gw>font.length||dst+(gh-1)*w+gw>c.length||(work+=gw*gh)>MAX_WORK)return reject();
    scratch[out]=src;scratch[out+1]=dst;scratch[out+2]=gw;scratch[out+3]=gh;scratch[out+4]=ss;scratch[out+5]=ds;scratch[out+6]=color;
   }
   stats.validated++;stats.glyphs+=count;if(validateOnly){stats.validateOnlyCalls++;return 0;}
   execution=true;let wrote=false;
   for(let i=0;i<count;i++){const at=i*RESOLVED,gw=scratch[at+2];if(gw===0)continue;let src=scratch[at],dst=scratch[at+1];const gh=scratch[at+3],ss=scratch[at+4],ds=scratch[at+5],color=scratch[at+6],aa=commands[i*STRIDE+4];
    for(let y=0;y<gh;y++){for(let x=0;x<gw;x++,dst++){const a=font[src++]&255;if(!aa){if(a){c[dst]=color;t[dst]=0;wrote=true;}}else if(a>30){if(a>=230){c[dst]=color;t[dst]=0;}else{const inv=256-a,old=c[dst],rb=(Math.imul(color&0xff00ff,a)+Math.imul(old&0xff00ff,inv))&0xff00ff00,g=(Math.imul(inv,old&0xff00)+Math.imul(a,color&0xff00))&0xff0000;c[dst]=(rb+g)>>8;t[dst]=Math.fround(t[dst]*Math.fround(inv/256));}wrote=true;}}
     src+=ss;dst+=ds;}
   }
   stats.rasterCalls++;stats[wrote?'wroteCalls':'zeroWriteCalls']++;return wrote?2:1;
  }catch(error){if(execution){stats.poisoned=true;stats.error=String(error).slice(0,400);return -1;}return reject();}
  finally{font=commands=surface=c=t=null;active=false;}
 }
 function reject(){stats.rejections++;return 0;}
 return {native,stats};
}
