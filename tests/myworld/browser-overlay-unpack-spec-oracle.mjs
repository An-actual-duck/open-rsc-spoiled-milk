/** CPU test model, authored independently from candidate upload code/mocks.
 * Scope: WebGL2 client-array RGBA/UNSIGNED_BYTE and RED/FLOAT transfers.
 * Sources: Khronos WebGL2 2.0.0 §3.7.6; GLES3.0.6 §3.7.2, equations3.12–3.13.
 * This is a specification arithmetic oracle, never a real-driver proof.
 */
export function readUnpackedPixels({view,width,height,format,type,srcOffset=0,
  rowLength=0,skipRows=0,skipPixels=0,alignment=4,pixelUnpackBuffer=null,flipY=false,premultiplyAlpha=false}) {
  const fail=message=>{throw new Error('INVALID_OPERATION: '+message);};
  const integer=n=>Number.isSafeInteger(n)&&n>=0;
  if(![width,height,srcOffset,rowLength,skipRows,skipPixels].every(integer))fail('Invalid transfer parameters');
  if(![1,2,4,8].includes(alignment))fail('Invalid unpack alignment');
  if(pixelUnpackBuffer!==null)fail('Client-array upload while PIXEL_UNPACK_BUFFER is bound');
  const channels=format==='RGBA'?4:format==='RED'?1:0;
  if(!channels||type!=='UNSIGNED_BYTE'&&type!=='FLOAT')fail('Unsupported oracle format/type');
  if(type==='UNSIGNED_BYTE'&&!(view instanceof Uint8Array)||type==='FLOAT'&&!(view instanceof Float32Array))fail('Client-array type mismatch');
  if(srcOffset>view.length)fail('Source offset exceeds supplied view');
  const elementBytes=view.BYTES_PER_ELEMENT,pixelBytes=channels*elementBytes;
  const sourceWidth=rowLength||width;
  if(skipPixels+width>sourceWidth)fail('Pixel-store row overlap');
  const stride=Math.ceil(sourceWidth*pixelBytes/alignment)*alignment;
  const first=srcOffset*elementBytes+skipRows*stride+skipPixels*pixelBytes;
  // Last-row trailing alignment is not read. Offsets are relative to the
  // supplied view, whose own byteOffset is applied by indexed view access.
  const end=width&&height?first+(height-1)*stride+width*pixelBytes:first;
  if(!Number.isSafeInteger(end)||end>view.byteLength)fail('Client-array view too small');
  const output=type==='FLOAT'?new Float32Array(width*height*channels):new Uint8Array(width*height*channels);
  for(let row=0;row<height;row++)for(let x=0;x<width*channels;x++)output[row*width*channels+x]=view[(first+(flipY?height-1-row:row)*stride)/elementBytes+x];
  // The hostile-state fixtures use only alpha0/255, where this conversion is
  // exact and has no implementation-dependent normalized rounding boundary.
  if(premultiplyAlpha&&channels===4&&type==='UNSIGNED_BYTE')for(let at=0;at<output.length;at+=4)
    for(let channel=0;channel<3;channel++)output[at+channel]=Math.round(output[at+channel]*output[at+3]/255);
  return output;
}

/** Replace only pixel-transfer semantics on a general lifecycle GL double.
 * The caller supplies ordinary shader/framebuffer lifecycle methods. */
export function installUnpackSpecOracle(gl) {
  const constants={UNPACK_ROW_LENGTH:0x0cf2,UNPACK_SKIP_ROWS:0x0cf3,UNPACK_SKIP_PIXELS:0x0cf4,
    UNPACK_IMAGE_HEIGHT:0x806e,UNPACK_SKIP_IMAGES:0x806d,UNPACK_FLIP_Y_WEBGL:0x9240,UNPACK_PREMULTIPLY_ALPHA_WEBGL:0x9241,PIXEL_UNPACK_BUFFER:0x88ec,PIXEL_UNPACK_BUFFER_BINDING:0x88ef,ARRAY_BUFFER:0x8892,ARRAY_BUFFER_BINDING:0x8894};
  Object.assign(gl,constants);
  const state=new Map([[gl.UNPACK_ALIGNMENT,4],[gl.UNPACK_ROW_LENGTH,0],[gl.UNPACK_SKIP_ROWS,0],
    [gl.UNPACK_SKIP_PIXELS,0],[gl.UNPACK_FLIP_Y_WEBGL,false],[gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,false],[gl.UNPACK_IMAGE_HEIGHT,0],[gl.UNPACK_SKIP_IMAGES,0],[gl.PIXEL_UNPACK_BUFFER_BINDING,null],[gl.ARRAY_BUFFER_BINDING,null]]);
  const trace=[],uploads=[],oldGet=gl.getParameter;
  gl.getParameter=p=>{trace.push(['getParameter',p]);return state.has(p)?state.get(p):oldGet(p);};
  gl.pixelStorei=(p,v)=>{trace.push(['pixelStorei',p,v]);if(!state.has(p)||p===gl.PIXEL_UNPACK_BUFFER_BINDING||p===gl.ARRAY_BUFFER_BINDING)throw new Error('Unknown oracle pixel store');state.set(p,v);};
  gl.bindBuffer=(p,v)=>{trace.push(['bindBuffer',p,v]);if(p===gl.PIXEL_UNPACK_BUFFER)state.set(gl.PIXEL_UNPACK_BUFFER_BINDING,v);else if(p===gl.ARRAY_BUFFER)state.set(gl.ARRAY_BUFFER_BINDING,v);else throw new Error('Unknown oracle buffer target');};
  gl.bufferSubData=(...args)=>{trace.push(['bufferSubData',...args]);};
  let failPlane=null;
  const read=(format,type,data,offset,w,h)=>readUnpackedPixels({view:data,width:w,height:h,
    format:format===gl.RED?'RED':format===gl.RGBA?'RGBA':'?',type:type===gl.FLOAT?'FLOAT':type===gl.UNSIGNED_BYTE?'UNSIGNED_BYTE':'?',
    srcOffset:offset,rowLength:state.get(gl.UNPACK_ROW_LENGTH),skipRows:state.get(gl.UNPACK_SKIP_ROWS),
    skipPixels:state.get(gl.UNPACK_SKIP_PIXELS),alignment:state.get(gl.UNPACK_ALIGNMENT),pixelUnpackBuffer:state.get(gl.PIXEL_UNPACK_BUFFER_BINDING),flipY:state.get(gl.UNPACK_FLIP_Y_WEBGL),premultiplyAlpha:state.get(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL)});
  gl.texImage2D=(_target,_level,internal,w,h,border,format,type,data,offset=0)=>{
    trace.push(['texImage2D',format,type,w,h]);if(border!==0)throw new Error('Unexpected border');
    if(failPlane===format)throw new Error('Injected plane upload failure');
    const pixels=data===null?(type===gl.FLOAT?new Float32Array(w*h):new Uint8Array(w*h*4)):read(format,type,data,offset,w,h);
    const texture=gl.getParameter(gl.TEXTURE_BINDING_2D);if(!texture)throw new Error('No texture bound');
    Object.assign(texture,{format:internal,width:w,height:h,pixels});uploads.push({texture,w,h,x:0,y:0,data,offset});
  };
  gl.texSubImage2D=(_target,_level,x,y,w,h,format,type,data,offset=0)=>{
    trace.push(['texSubImage2D',format,type,x,y,w,h,offset]);if(failPlane===format)throw new Error('Injected plane upload failure');
    const pixels=read(format,type,data,offset,w,h),texture=gl.getParameter(gl.TEXTURE_BINDING_2D),channels=format===gl.RED?1:4;
    if(!texture||x<0||y<0||x+w>texture.width||y+h>texture.height)throw new Error('Destination rectangle outside texture');
    for(let row=0;row<h;row++)texture.pixels.set(pixels.subarray(row*w*channels,(row+1)*w*channels),((row+y)*texture.width+x)*channels);
    uploads.push({texture,x,y,w,h,data,offset});
  };
  return {state,trace,uploads,setFailure(format){failPlane=format;},snapshot(){return [...state].map(([k,v])=>[k,v]);}};
}
