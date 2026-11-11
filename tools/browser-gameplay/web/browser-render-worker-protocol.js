/** Private owned-command transport. No Java/input view is retained or transferred. */
export const RENDER_WORKER_VERSION=1;
export const RENDER_WORKER_MAX_BYTES=64*1024*1024;
export const RENDER_WORKER_MAX_COMMANDS=1024;
const P='Java_orsc_BrowserWebGLBridge_';
// n finite scalar, i safe integer, b boolean, s bounded shader, I/F/B primitive plane.
export const RENDER_WORKER_SCHEMA=Object.freeze({
  initialize:['s','s'],initializeLighting:['s','s'],initializeSky:[],
  uploadLightingMask:['i','i','i','B','F'],lightingFrame:['F'],uploadSkyGeometry:['F','I','i'],skyFrame:['F','F'],
  overlayFrame:['i','i','i','n','n','i','b','N'],uploadChunk:['i','F','I','I'],replaceChunk:['i','F','I','I'],
  deleteTextures:['I'],uploadTexture:['i','i','i','I','b'],deleteChunks:['I'],
  present:['i','i','b','F','F','I','I'],frameWork:['F','I'],worldProgress:['I'],regionTransition:['b'],holdWorld:[],
  planWorldSprites:['i','n','I'],commitWorldSprites:['i','n','B','F'],
  '$owned':['b'],'$validation':['b'],'$physical':['b'],'$measurement':['b']
});
const TA=Object.getPrototypeOf(Uint8Array.prototype),get=(name)=>Object.getOwnPropertyDescriptor(TA,name).get;
const getBuffer=get('buffer'),getOffset=get('byteOffset'),getLength=get('byteLength'),getTag=Object.getOwnPropertyDescriptor(TA,Symbol.toStringTag).get;
const abLength=Object.getOwnPropertyDescriptor(ArrayBuffer.prototype,'byteLength').get;
function bytes(value,kind){
  let buffer,offset,length;
  try{buffer=getBuffer.call(value);offset=getOffset.call(value);length=getLength.call(value);const tag=getTag.call(value);if(!({I:['Int32Array'],F:['Float32Array'],B:['Int8Array','Uint8Array','Uint8ClampedArray']}[kind].includes(tag)))throw new Error('kind');}
  catch(error){try{length=abLength.call(value);buffer=value;offset=0;}catch(_){throw new Error('Expected genuine primitive render plane');}}
  // Intrinsic ArrayBuffer getter excludes SharedArrayBuffer and detached buffers.
  let total;try{total=abLength.call(buffer);}catch(_){throw new Error('Shared render plane unsupported');}
  if(!Number.isSafeInteger(length)||length<0||offset+length>total||length>RENDER_WORKER_MAX_BYTES||((kind==='I'||kind==='F')&&(length%4)))throw new Error('Invalid render plane bounds');
  return {buffer,offset,length};
}
export function inspectRenderCommand(name,args){
  const schema=RENDER_WORKER_SCHEMA[name];if(!schema||!Array.isArray(args)||args.length!==schema.length)throw new Error('Unknown render command/schema');
  let count=0;
  const planes=[];
  for(let n=0;n<schema.length;n++){
    const kind=schema[n],value=args[n];
    if(kind==='N'&&value===null)continue;
    if('IFBN'.includes(kind)){const actual=kind==='N'?'F':kind;const shape=bytes(value,actual);planes.push({n,kind:actual,...shape});count+=shape.length;}
    else if(kind==='s'){if(typeof value!=='string'||value.length>131072)throw new Error('Invalid shader text');count+=value.length*2;}
    else if(kind==='b'){if(typeof value!=='boolean')throw new Error('Invalid render boolean');}
    else if(typeof value!=='number'||!Number.isFinite(value)||(kind==='i'&&!Number.isSafeInteger(value)))throw new Error('Invalid render scalar');
  }
  if(count>RENDER_WORKER_MAX_BYTES)throw new Error('Render command payload cap');
  return {bytes:count,planes};
}
export function ownRenderCommand(name,args){
  const shape=inspectRenderCommand(name,args),owned=args.slice(),transfers=[];
  for(const p of shape.planes){const data=new Uint8Array(p.length);data.set(new Uint8Array(p.buffer,p.offset,p.length));owned[p.n]=p.kind==='I'?new Int32Array(data.buffer):p.kind==='F'?new Float32Array(data.buffer):data;transfers.push(data.buffer);}
  return {command:{name,args:owned},bytes:shape.bytes,transfers};
}
export function validateRenderLayout(layout){
  if(!layout||!Number.isSafeInteger(layout.epoch)||layout.epoch<1||!Number.isFinite(layout.width)||layout.width<0||layout.width>32768||!Number.isFinite(layout.height)||layout.height<0||layout.height>32768||!Number.isFinite(layout.dpr)||layout.dpr<=0||layout.dpr>16)throw new Error('Invalid render layout snapshot');
  return {epoch:layout.epoch,width:layout.width,height:layout.height,dpr:layout.dpr};
}
export function validateRenderBatch(packet){
  if(!packet||packet.version!==1||packet.type!=='batch'||!Number.isSafeInteger(packet.id)||packet.id<1||!Number.isSafeInteger(packet.frameId)||packet.frameId<0||!Array.isArray(packet.commands)||packet.commands.length<1||packet.commands.length>RENDER_WORKER_MAX_COMMANDS)throw new Error('Invalid render batch envelope');
  validateRenderLayout(packet.layout);
  let total=0;for(const c of packet.commands){if(!c||typeof c.name!=='string')throw new Error('Invalid render command');total+=inspectRenderCommand(c.name,c.args).bytes;if(total>RENDER_WORKER_MAX_BYTES)throw new Error('Render batch payload cap');}
  const presents=packet.commands.filter(c=>c.name==='present'),holds=packet.commands.filter(c=>c.name==='holdWorld'),works=packet.commands.filter(c=>c.name==='frameWork');
  const last=packet.commands[packet.commands.length-1];
  if(packet.frameId){
    if(holds.length===1){if(presents.length||works.length||last.name!=='holdWorld')throw new Error('Invalid held frame boundary');}
    else if(presents.length===1){if(presents[0].args[2]?(works.length!==1||last.name!=='frameWork'):(works.length!==0||last.name!=='present'))throw new Error('Invalid frame completion boundary');}
    else throw new Error('Missing frame presentation');
  }else if(presents.length||holds.length||works.length)throw new Error('Unacknowledged presentation boundary');
  if(total!==packet.bytes)throw new Error('Render byte receipt mismatch');
  return total;
}
export function renderWorkerPolicy(mode,options){
  const v=options.get('renderworker');if(v!==null&&!['on','off'].includes(v))throw new Error('Invalid private renderworker policy');
  const enabled=v==='on';if(enabled&&(mode!=='webgl'||!options.has('benchmark')||options.get('overlay')!=='coverage'||!['off',null].includes(options.get('overlaydelta'))||options.get('nativecalls')==='sync'))throw new Error('Render worker requires private async WebGL full coverage');
  return {requested:enabled,enabled};
}
export const renderNativeName=name=>P+name;
