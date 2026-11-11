/** Observe ONLY. No input writes, GL calls, production draw decisions or ACKs. */
const MAX_PIXELS=2097152,MAX_OPS=4096,MAX_GLYPHS=4096,MAX_FONTS=8388608,STRIDE=16;
function typed(value,type,max){if(value instanceof type&&ArrayBuffer.isView(value)&&value.buffer instanceof ArrayBuffer&&value.byteLength<=max)return value;if(value instanceof ArrayBuffer&&value.byteLength<=max&&value.byteLength%type.BYTES_PER_ELEMENT===0)return new type(value);throw Error('Journal genuine bounded view required');}
const overlap=(a,b)=>a.buffer===b.buffer&&a.byteOffset<b.byteOffset+b.byteLength&&b.byteOffset<a.byteOffset+a.byteLength;
function ownedScalars(view,cap){const n=Math.min(view.length,cap),out=new Array(n);for(let i=0;i<n;i++)out[i]=view[i];return out;}
function require(value,message){if(!value)throw Error(message);}
function token(hi,lo){require(hi>=0&&hi<=2097151,'Journal token high limb');return hi*4294967296+(lo>>>0);}
export function validateJournal(input){
 const {width:w,height:h,generation:g,sequence:s}=input;
 require(Number.isInteger(w)&&Number.isInteger(h)&&w>0&&h>0&&w<=32768&&h<=32768&&w*h<=MAX_PIXELS,'Journal surface bounds');
 require(Number.isSafeInteger(g)&&g>=0&&Number.isSafeInteger(s)&&s>=0,'Journal expected token');
 const c=typed(input.c,Int32Array,MAX_PIXELS*4),t=typed(input.t,Float32Array,MAX_PIXELS*4),header=typed(input.header,Int32Array,64),ops=typed(input.ops,Int32Array,MAX_OPS*STRIDE*4),glyphs=typed(input.glyphs,Int32Array,MAX_GLYPHS*5*4),fonts=typed(input.fonts,Int8Array,MAX_FONTS),runs=typed(input.runs,Int32Array,(MAX_OPS+1)*6*4),stats=typed(input.stats,Int32Array,128);
 require(header.length===16&&header[0]===1&&header[1]===w&&header[2]===h&&[0,1].includes(header[3])&&header[11]===1,'Journal schema');
 require(token(header[12],header[13])===g&&token(header[14],header[15])===s,'Journal owner epoch');
 require(c.length===w*h&&t.length===w*h&&stats.length===32&&!overlap(c,t),'Journal plane shape/alias');
 for(const a of [header,ops,glyphs,fonts,runs,stats])require(!overlap(a,c)&&!overlap(a,t),'Journal input/output alias');
 const count=header[4],glyphCount=header[5],textCount=header[6],fontBytes=header[7],barriers=header[8],work=header[9],runCount=header[10];
 require(count>=0&&count<=MAX_OPS&&glyphCount>=0&&glyphCount<=MAX_GLYPHS&&textCount>=0&&textCount<=256&&fontBytes>=0&&fontBytes<=MAX_FONTS&&barriers>=0&&work>=0&&work<=MAX_PIXELS&&runCount>=0&&runCount<=MAX_OPS+1,'Journal count caps');
 require(ops.length===count*STRIDE&&glyphs.length===glyphCount*5&&fonts.length===fontBytes&&runs.length===runCount*6,'Journal exact payload bounds');
 for(let i=0;i<32;i++)require(stats[i]>=0,'Journal negative counter');
 // Bounded scalar geometry only. No scratch pixel plane or retained trace exists yet.
 const resolved=new Int32Array(glyphCount*7),rects=new Int32Array(count*4);let nextGlyph=0,nextFont=0,texts=0,seenBarriers=0,pixels=0;
 const domain=n=>n>=-32768&&n<=32768;
 for(let i=0;i<count;i++){
  const at=i*STRIDE,kind=ops[at],left=ops[at+6],top=ops[at+7],right=ops[at+8],bottom=ops[at+9];
  require(ops[at+1]===i&&ops[at+10]>=0&&ops[at+10]<=3,'Journal op order/phase');
  if(kind===9){seenBarriers++;require(ops[at+2]>=1&&ops[at+2]<=17,'Journal barrier kind');continue;}
  require(kind>=1&&kind<=5&&left>=0&&top>=0&&right>=left&&bottom>=top&&right<=w&&bottom<=h,'Journal op/clip');
  if(kind===1){
   const font=ops[at+2],length=ops[at+3],first=ops[at+4],n=ops[at+5];
   require(font===nextFont&&length>=0&&length<=fontBytes-font&&first===nextGlyph&&n>=0&&n<=glyphCount-first,'Journal source ownership/order');nextFont+=length;nextGlyph+=n;texts++;
   for(let j=first;j<first+n;j++){
    const a=j*5,x=glyphs[a],y=glyphs[a+1],address=glyphs[a+3],aa=glyphs[a+4];require(domain(x)&&domain(y)&&address>=0&&address<=length-7&&[0,1].includes(aa),'Journal font header');
    let gw=fonts[font+address+3],gh=fonts[font+address+4],gx=x+fonts[font+address+5],gy=y-fonts[font+address+6],src=(fonts[font+address]<<14)+(fonts[font+address+1]<<7)+fonts[font+address+2],sourceStride=gw;
    if(gy<top){const lost=top-gy;src+=lost*gw;gh-=lost;gy=top;}if(gy+gh>=bottom)gh-=1+gy+gh-bottom;
    if(gx<left){const lost=left-gx;src+=lost;gw-=lost;gx=left;}if(gx+gw>=right)gw-=1+gx+gw-right;
    const out=j*7;if(gw<=0||gh<=0){resolved[out+2]=0;continue;}
    require(src>=0&&sourceStride>=0&&src+(gh-1)*sourceStride+gw<=length&&gx>=0&&gy>=0&&gx+gw<=w&&gy+gh<=h,'Journal glyph footprint');
    resolved.set([font+src,gx+gy*w,gw,gh,sourceStride-gw,w-gw,glyphs[a+2]],out);pixels+=gw*gh;require(pixels<=MAX_PIXELS,'Journal work cap');
   }
  }else{
   const x=ops[at+2],y=ops[at+3],rw=ops[at+4],rh=ops[at+5],alpha=ops[at+12];require(domain(x)&&domain(y)&&rw>=0&&rw<=32768&&rh>=0&&rh<=32768&&alpha>=0&&alpha<=256,'Journal rect inputs');
   require(kind!==4||rh===1,'Journal horizontal shape');require(kind!==5||rw===1,'Journal vertical shape');
   const loX=Math.max(left,x),loY=Math.max(top,y),hiX=Math.min(right,x+rw),hiY=Math.min(bottom,y+rh),dw=Math.max(0,hiX-loX),dh=Math.max(0,hiY-loY);rects.set([loX,loY,dw,dh],i*4);pixels+=dw*dh;require(pixels<=MAX_PIXELS,'Journal work cap');
  }
 }
 require(nextGlyph===glyphCount&&nextFont===fontBytes&&texts===textCount&&pixels===work,'Journal aggregate coherence');
 require(seenBarriers<=barriers&&(header[3]===0||barriers===0),'Journal completeness/barrier');
 let previous=0,coveredKnown=0;for(let i=0;i<runCount;i++){const at=i*6,start=runs[at],end=runs[at+1];require(start>=previous&&end>start&&end<=count,'Journal run bounds');for(let j=previous;j<start;j++)require(ops[j*STRIDE]===9,'Journal missing known run');let texts=0,rectangles=0,g=0,area=0;
  for(let j=start;j<end;j++){const k=j*STRIDE;require(ops[k]!==9,'Journal run crossed barrier');if(ops[k]===1){texts++;g+=ops[k+5];for(let n=ops[k+4];n<ops[k+4]+ops[k+5];n++)area+=resolved[n*7+2]*resolved[n*7+3];}else{rectangles++;area+=rects[j*4+2]*rects[j*4+3];}}
  require(runs[at+2]===texts&&runs[at+3]===rectangles&&runs[at+4]===g&&runs[at+5]===area&&texts+rectangles>0,'Journal run counts');previous=end;coveredKnown+=texts+rectangles;
 }
 for(let j=previous;j<count;j++)require(ops[j*STRIDE]===9,'Journal missing trailing run');
 require(coveredKnown===count-seenBarriers,'Journal complete run partition');
 return {w,h,g,s,c,t,header,ops,glyphs,fonts,runs,stats,resolved,rects};
}
export function replayJournal(plan){
 require(plan.header[3]===1,'Incomplete journal cannot replay');const c=new Int32Array(plan.w*plan.h),t=new Float32Array(c.length);t.fill(1);
 for(let op=0;op<plan.header[4];op++){const at=op*STRIDE,kind=plan.ops[at];
  if(kind===1){for(let n=plan.ops[at+4];n<plan.ops[at+4]+plan.ops[at+5];n++){const r=n*7,w=plan.resolved[r+2],h=plan.resolved[r+3],color=plan.resolved[r+6],aa=plan.glyphs[n*5+4];let src=plan.resolved[r],dst=plan.resolved[r+1];
   for(let y=0;y<h;y++){for(let x=0;x<w;x++,dst++){const a=plan.fonts[src++]&255;if(!aa){if(a){c[dst]=color;t[dst]=0;}}else if(a>30){if(a>=230){c[dst]=color;t[dst]=0;}else{const inv=256-a,old=c[dst],rb=(Math.imul(color&0xff00ff,a)+Math.imul(old&0xff00ff,inv))&0xff00ff00,green=(Math.imul(inv,old&0xff00)+Math.imul(a,color&0xff00))&0xff0000;c[dst]=(rb+green)>>8;t[dst]=Math.fround(t[dst]*Math.fround(inv/256));}}}src+=plan.resolved[r+4];dst+=plan.resolved[r+5];}
  }}else{
   const r=op*4,x=plan.rects[r],y=plan.rects[r+1],w=plan.rects[r+2],h=plan.rects[r+3],color=plan.ops[at+11],a=plan.ops[at+12],inv=256-a;
   for(let row=0;row<h;row++)for(let col=0;col<w;col++){const dst=(y+row)*plan.w+x+col;if(kind!==3){c[dst]=color;t[dst]=0;}else{const old=c[dst];c[dst]=((Math.imul(inv,old&255)+Math.imul(a,color&255))>>8)+(((Math.imul(inv,(old&0xff00)>>8)+Math.imul((color&0xffc4)>>8,a))>>8)<<8)+(((Math.imul(a,(color>>16)&255)+Math.imul(inv,(old&0xff0000)>>16))>>8)<<16);t[dst]=Math.fround(t[dst]*Math.fround(inv/256));}}
  }
 }
 return {c,t};
}
export function createOverlayJournalObserver(stats={}){
 const counters={frames:0,complete:0,incomplete:0,matched:0,mismatched:0,rejected:0,last:null};stats.overlayJournalObserver=counters;stats.overlayJournalObserverEnabled=true;
 function failure(error){counters.rejected++;counters.last={valid:false,error:String(error).slice(0,512)};}
 function consume(input){try{const plan=validateJournal(input),h=plan.header;let mismatches=null,first=null;
  if(h[3]){const replay=replayJournal(plan),actualBits=new Uint32Array(plan.t.buffer,plan.t.byteOffset,plan.t.length),expectedBits=new Uint32Array(replay.t.buffer);mismatches=0;for(let i=0;i<plan.c.length;i++)if(plan.c[i]!==replay.c[i]||actualBits[i]!==expectedBits[i]){mismatches++;if(first===null)first={index:i,actualC:plan.c[i],expectedC:replay.c[i],actualT:actualBits[i],expectedT:expectedBits[i]};}}
  counters.frames++;counters[h[3]?'complete':'incomplete']++;if(mismatches!==null)counters[mismatches?'mismatched':'matched']++;
  counters.last={valid:true,rasterReplayComplete:h[3]===1,generation:plan.g,sequence:plan.s,texts:h[6],glyphs:h[5],candidatePixelWork:h[9],barriers:h[8],fontSnapshotBytes:h[7],runCount:h[10],mismatches,first,
   runs:ownedScalars(plan.runs,64*6),runsTruncated:h[10]>64,ops:ownedScalars(plan.ops,64*STRIDE),opsTruncated:h[4]>64,lifetime:ownedScalars(plan.stats,32)};
 }catch(error){failure(error);}}
 return {consume,failure,counters};
}
/** Observer failure cannot prevent or duplicate the unchanged original callback. */
export function observedOverlayDelta(observer,original){return function(...args){
 try{observer.consume({width:args[1],height:args[2],generation:args[3],sequence:args[4],c:args[11],t:args[12],header:args[13],ops:args[14],glyphs:args[15],fonts:args[16],runs:args[17],stats:args[18]});}
 catch(error){try{observer.failure(error);}catch(ignored){}}
 return original(args[0],args[1],args[2],args[3],args[4],args[5],args[6],args[7],args[8],args[9],args[10],args[11],args[12]);
};}
