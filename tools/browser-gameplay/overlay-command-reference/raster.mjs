// Isolated integer C / Float32 T packet reference, not a production game bridge.
export function createRaster(width,height){
 const MAX_PIXELS=2097152,MAX_OPS=8192,MAX_WORK=16777216;
 if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width*height>MAX_PIXELS)throw Error('Surface bounds');
 const c=new Int32Array(width*height),t=new Float32Array(c.length);let generation=-1,sequence=-1;
 const int=v=>Number.isInteger(v)&&v>=-2147483648&&v<=2147483647;
 const overlaps=(a,b)=>a.buffer===b.buffer&&a.byteOffset<b.byteOffset+b.byteLength&&b.byteOffset<a.byteOffset+a.byteLength;
 function validate(input){
  if(!input||!Array.isArray(input.sources)||!Array.isArray(input.ops))throw Error('Packet arrays');
  const packet={schema:input.schema,width:input.width,height:input.height,generation:input.generation,sequence:input.sequence,sources:[],ops:[]};
  if(input.sources.length>128||input.ops.length>MAX_OPS)throw Error('Packet count budget');
  // Isolated v1 owns a bounded copy; no concurrent/shared source mutation accepted.
  let bytes=0;for(const source of input.sources){if(!(source instanceof Int8Array)||!(source.buffer instanceof ArrayBuffer)||source.length>MAX_PIXELS||overlaps(source,c)||overlaps(source,t))throw Error('Source ownership');bytes+=source.byteLength;if(bytes>MAX_PIXELS*4)throw Error('Source byte budget');packet.sources.push(new Int8Array(source));}
  for(const op of input.ops){if(!op)throw Error('Missing operation');packet.ops.push({kind:op.kind,order:op.order,head:op.head,width:op.width,height:op.height,rowStride:op.rowStride,color:op.color,source:op.source,sourceHead:op.sourceHead,sourceStride:op.sourceStride,aa:op.aa,alpha:op.alpha});}
  if(!packet||packet.schema!==1||packet.width!==width||packet.height!==height||!Number.isSafeInteger(packet.generation)||packet.generation<0||!Number.isSafeInteger(packet.sequence)||packet.sequence<0||packet.generation<=generation||packet.sequence<=sequence)throw Error('Packet identity/order');
  if(!Array.isArray(packet.sources)||packet.sources.length>128||!Array.isArray(packet.ops)||packet.ops.length<1||packet.ops.length>MAX_OPS||packet.ops[0].kind!=='clear')throw Error('Packet bounds/fresh clear');
  let sourceBytes=0,work=0;
  for(const source of packet.sources){if(!(source instanceof Int8Array)||source.length>MAX_PIXELS||overlaps(source,c)||overlaps(source,t))throw Error('Source type/bounds/alias');sourceBytes+=source.byteLength;}
  if(sourceBytes>MAX_PIXELS*4)throw Error('Source byte budget');
  for(let i=0;i<packet.ops.length;i++){const op=packet.ops[i];if(!op||op.order!==i)throw Error('Operation order');if(op.kind==='clear'){if(i!==0)throw Error('Midframe clear unsupported');continue;}
   if(!['glyph','alpha'].includes(op.kind)||![op.head,op.width,op.height,op.rowStride,op.color].every(int)||op.width<1||op.height<1||op.head<0||op.rowStride<0||op.width+op.rowStride!==width||op.head%width+op.width>width||op.head+(op.height-1)*width+op.width>c.length)throw Error('Destination bounds');
   work+=op.width*op.height;if(work>MAX_WORK)throw Error('Work budget');
   if(op.kind==='glyph'){if(!int(op.source)||op.source<0||op.source>=packet.sources.length||!int(op.sourceHead)||!int(op.sourceStride)||op.sourceHead<0||op.sourceStride<0||typeof op.aa!=='boolean')throw Error('Glyph source metadata');const source=packet.sources[op.source],end=op.sourceHead+(op.height-1)*(op.width+op.sourceStride)+op.width;if(end>source.length)throw Error('Glyph source bounds');}
   else if(!int(op.alpha)||op.alpha<0||op.alpha>256)throw Error('Alpha bounds');
  }return packet;
 }
 function apply(input){const packet=validate(input); // Complete pure pass before C/T or token mutation.
  for(const op of packet.ops){if(op.kind==='clear'){c.fill(0);t.fill(1);continue;}
   let dst=op.head,src=op.sourceHead;const source=op.kind==='glyph'?packet.sources[op.source]:null;
   for(let y=0;y<op.height;y++){for(let x=0;x<op.width;x++,dst++){
    if(op.kind==='glyph'){
     const a=source[src++]&255;if(op.aa){if(a<=30)continue;if(a>=230){c[dst]=op.color;t[dst]=0;continue;}
      const inv=256-a,old=c[dst];t[dst]=Math.fround(t[dst]*Math.fround(inv/256));
      const rb=(Math.imul(op.color&0xff00ff,a)+Math.imul(old&0xff00ff,inv))&0xff00ff00;
      const g=(Math.imul(inv,old&0xff00)+Math.imul(a,op.color&0xff00))&0xff0000;c[dst]=(rb+g)>>8;
     }else if(a!==0){c[dst]=op.color;t[dst]=0;}
    }else{
     const a=op.alpha,inv=256-a,old=c[dst],r=Math.imul(a,(op.color>>16)&255),g=Math.imul((op.color&0xffc4)>>8,a),b=Math.imul(a,op.color&255);
     // Preserve original drawBoxAlpha's 0xffc4 mask and signed Java int math.
     c[dst]=((Math.imul(inv,old&255)+b)>>8)+(((Math.imul(inv,(old&0xff00)>>8)+g)>>8)<<8)+(((r+Math.imul(inv,(old&0xff0000)>>16))>>8)<<16);
     t[dst]=Math.fround(t[dst]*Math.fround(inv/256));
    }
   }dst+=op.rowStride;if(source)src+=op.sourceStride;}
  }generation=packet.generation;sequence=packet.sequence;
 }
 return {apply,validate,snapshot(){return {c:c.slice(),t:t.slice(),generation,sequence};}};
}
