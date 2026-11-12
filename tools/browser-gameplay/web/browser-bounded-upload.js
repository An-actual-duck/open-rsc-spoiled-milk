import {decodeWholeZoneChunk} from './browser-whole-zone.js';
/** One owned packed payload. Each call performs at most sliceBytes of CPU
 * validation/copy or GPU transfer, and restores bindings even in owned-state mode.
 * Allocate only after validation. Publish only after the final successful slice.
 */
export function createBoundedChunkUpload(gl,{chunk:c,buffer,program,attributes,check,
  reserve,allocated,released,publish,measure=()=>{}}){
  const {vertices,indices,batches:sourceBatches}=decodeWholeZoneChunk(buffer,c);
  const batches=new Int32Array(sourceBatches.length),bytes=vertices.byteLength+indices.byteLength;
  const bounds=new Float64Array([Infinity,Infinity,Infinity,-Infinity,-Infinity,-Infinity]);
  let stage='vertices',at=0,vao=null,vbo=null,ibo=null,owns=false,done=false,disposed=false;
  function bindings(fn){const oldVAO=gl.getParameter(gl.VERTEX_ARRAY_BINDING),oldArray=gl.getParameter(gl.ARRAY_BUFFER_BINDING);
    try{return fn();}finally{gl.bindVertexArray(oldVAO);gl.bindBuffer(gl.ARRAY_BUFFER,oldArray);}}
  function step(limit){
    if(!Number.isSafeInteger(limit)||limit<112||limit%4)throw new Error('Invalid bounded upload slice');
    if(disposed)throw new Error('Stale bounded upload');if(done)return true;
    const started=performance.now();
    try{
      if(stage==='vertices'){
        const end=Math.min(vertices.length,at+Math.max(28,Math.floor(limit/112)*28));
        for(;at<end;at+=28)for(let axis=0;axis<3;axis++){const n=vertices[at+axis];if(!Number.isFinite(n))throw new Error('Bounded nonfinite geometry');bounds[axis]=Math.min(bounds[axis],n);bounds[axis+3]=Math.max(bounds[axis+3],n);}
        if(at===vertices.length){stage='indices';at=0;}return false;
      }
      if(stage==='indices'){
        const end=Math.min(indices.length,at+Math.floor(limit/4));for(;at<end;at++)if(indices[at]<0||indices[at]>=c.vertexCount)throw new Error('Bounded invalid geometry index');
        if(at===indices.length){stage='batches';at=0;}return false;
      }
      if(stage==='batches'){
        const end=Math.min(sourceBatches.length,at+Math.max(5,Math.floor(limit/20)*5));
        for(;at<end;at+=5){if(sourceBatches[at+1]<0||sourceBatches[at+2]<0||sourceBatches[at+1]+sourceBatches[at+2]>indices.length)throw new Error('Bounded invalid material range');for(let n=0;n<5;n++)batches[at+n]=sourceBatches[at+n];}
        if(at===sourceBatches.length){stage='allocate';at=0;}return false;
      }
      if(stage==='allocate'){
        reserve(bytes);bindings(()=>{vao=gl.createVertexArray();vbo=gl.createBuffer();ibo=gl.createBuffer();if(!vao||!vbo||!ibo)throw new Error('Bounded GPU allocation failed');
          gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,vbo);gl.bufferData(gl.ARRAY_BUFFER,vertices.byteLength,gl.STATIC_DRAW);
          gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ibo);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,indices.byteLength,gl.STATIC_DRAW);
          for(const[name,size,offset]of attributes){const location=gl.getAttribLocation(program,name);if(location>=0){gl.enableVertexAttribArray(location);gl.vertexAttribPointer(location,size,gl.FLOAT,false,112,offset*4);}}check();});
        owns=true;allocated(bytes);stage='upload-vertices';return false;
      }
      bindings(()=>{gl.bindVertexArray(vao);const plane=stage==='upload-vertices'?vertices:indices,target=stage==='upload-vertices'?gl.ARRAY_BUFFER:gl.ELEMENT_ARRAY_BUFFER;
        gl.bindBuffer(target,stage==='upload-vertices'?vbo:ibo);const end=Math.min(plane.byteLength,at+limit);
        if(end>at)gl.bufferSubData(target,at,new Uint8Array(plane.buffer,plane.byteOffset+at,end-at));check();at=end;
        if(at===plane.byteLength){at=0;if(stage==='upload-vertices')stage='upload-indices';else{
          publish({vao,vbo,ibo,batches,bounds:vertices.length?bounds:null,bytes,vertexBytes:vertices.byteLength,indexBytes:indices.byteLength});owns=false;done=true;
        }}});return done;
    }finally{measure(performance.now()-started,stage);}
  }
  function dispose(){if(disposed||done)return;disposed=true;if(vao)gl.deleteVertexArray(vao);if(vbo)gl.deleteBuffer(vbo);if(ibo)gl.deleteBuffer(ibo);if(owns)released(bytes);owns=false;}
  return {step,dispose};
}
