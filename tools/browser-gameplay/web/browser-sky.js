/** Bounded replay of the shared native world sky. Geometry/palette values are
 * exported by BrowserSkyData; this module adds no alternate visual formulas. */
export function createBrowserSky(gl,{stats={}}={}) {
  const MAX_VERTICES=65536,PALETTE_SIZE=1024,PALETTE_BYTES=PALETTE_SIZE*16,PEAK_BUDGET=MAX_VERTICES*16*2+PALETTE_BYTES;
  let program=null,vertexShader=null,fragmentShader=null,paletteTexture=null,vbo=null,vao=null,ranges=null,geometryBytes=0,values=null,initialized=false,disposed=false;
  let matrixLocation,paletteLocation;stats.skyResourceBytes=0;stats.skyPeakBudgetBytes=PEAK_BUDGET;stats.skyGeometryUploads=0;stats.skyPaletteUploads=0;stats.skyDrawCalls=0;
  const vertexSource='#version 300 es\nprecision highp float;precision highp sampler2D;in vec3 position;in float paletteSlot;uniform mat4 projection;uniform sampler2D palette;out vec4 color;void main(){color=texelFetch(palette,ivec2(int(paletteSlot),0),0);gl_Position=projection*vec4(position,1.0);}';
  const fragmentSource='#version 300 es\nprecision highp float;in vec4 color;out vec4 outputColor;void main(){outputColor=color;}';
  function alive(){if(disposed||!initialized)throw new Error('Browser sky is not initialized');}
  function compile(type,source){const shader=gl.createShader(type);if(!shader)throw new Error('Sky shader allocation failed');gl.shaderSource(shader,source);gl.compileShader(shader);if(!gl.getShaderParameter(shader,gl.COMPILE_STATUS)){const message=gl.getShaderInfoLog(shader);gl.deleteShader(shader);throw new Error('Browser sky shader: '+message);}return shader;}
  function check(label){const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('Browser sky '+label+' error 0x'+error.toString(16));}
  function initialize(){
    if(initialized||disposed)throw new Error('Browser sky initialization state invalid');
    const active=gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0);const binding=gl.getParameter(gl.TEXTURE_BINDING_2D);
    try {
      if(gl.getParameter(gl.MAX_TEXTURE_SIZE)<PALETTE_SIZE)throw new Error('Browser sky palette exceeds texture capability');
      vertexShader=compile(gl.VERTEX_SHADER,vertexSource);fragmentShader=compile(gl.FRAGMENT_SHADER,fragmentSource);program=gl.createProgram();if(!program)throw new Error('Sky program allocation failed');
      gl.attachShader(program,vertexShader);gl.attachShader(program,fragmentShader);gl.bindAttribLocation(program,0,'position');gl.bindAttribLocation(program,1,'paletteSlot');gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw new Error('Browser sky link: '+gl.getProgramInfoLog(program));
      paletteTexture=gl.createTexture();if(!paletteTexture)throw new Error('Sky palette allocation failed');gl.bindTexture(gl.TEXTURE_2D,paletteTexture);
      gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA32F,PALETTE_SIZE,1,0,gl.RGBA,gl.FLOAT,null);check('initialization');
      matrixLocation=gl.getUniformLocation(program,'projection');paletteLocation=gl.getUniformLocation(program,'palette');initialized=true;stats.skyResourceBytes=PALETTE_BYTES;
    } catch(error){dispose();throw error;}
    finally {gl.bindTexture(gl.TEXTURE_2D,binding);gl.activeTexture(active);}
  }
  function uploadGeometry(vertexInput,rangeInput,paletteCount){
    alive();if(vertexInput.length%4||vertexInput.length<12||vertexInput.length/4>MAX_VERTICES||rangeInput.length!==12||!Number.isInteger(paletteCount)||paletteCount<1||paletteCount>PALETTE_SIZE)throw new Error('Browser sky geometry schema invalid');
    for(const value of rangeInput)if(!Number.isInteger(value)||value<0||value>MAX_VERTICES)throw new Error('Browser sky layer range bounds invalid');
    const vertices=vertexInput instanceof Float32Array?vertexInput:new Float32Array(vertexInput),nextRanges=new Int32Array(rangeInput),count=vertices.length/4;
    for(let at=0;at<vertices.length;at+=4)if(!Number.isFinite(vertices[at])||!Number.isFinite(vertices[at+1])||!Number.isFinite(vertices[at+2])||!Number.isInteger(vertices[at+3])||vertices[at+3]<0||vertices[at+3]>=paletteCount)throw new Error('Browser sky vertex bounds invalid');
    let end=0;for(let layer=0;layer<4;layer++){const at=layer*3;if(nextRanges[at]!==end||nextRanges[at+1]<0||nextRanges[at+1]%3||nextRanges[at+2]!==Number(layer!==0))throw new Error('Browser sky layer ranges invalid');end+=nextRanges[at+1];}if(end!==count)throw new Error('Browser sky range coverage invalid');
    if(stats.skyResourceBytes+vertices.byteLength>PEAK_BUDGET)throw new Error('Browser sky peak resource budget exceeded');
    const oldVAO=gl.getParameter(gl.VERTEX_ARRAY_BINDING),oldArray=gl.getParameter(gl.ARRAY_BUFFER_BINDING);let restoreVAO=oldVAO,restoreArray=oldArray,nextVBO=null,nextVAO=null;
    try {
      nextVBO=gl.createBuffer();nextVAO=gl.createVertexArray();if(!nextVBO||!nextVAO)throw new Error('Sky geometry allocation failed');gl.bindVertexArray(nextVAO);gl.bindBuffer(gl.ARRAY_BUFFER,nextVBO);gl.bufferData(gl.ARRAY_BUFFER,vertices,gl.STATIC_DRAW);
      gl.enableVertexAttribArray(0);gl.vertexAttribPointer(0,3,gl.FLOAT,false,16,0);gl.enableVertexAttribArray(1);gl.vertexAttribPointer(1,1,gl.FLOAT,false,16,12);check('geometry upload');
      // Preserve bindings that refer to replaced owned objects by following their replacement.
      // On failure the old resources remain alive and the original bindings are restored.
      if(vao&&oldVAO===vao)restoreVAO=nextVAO;if(vbo&&oldArray===vbo)restoreArray=nextVBO;
      if(vbo)gl.deleteBuffer(vbo);if(vao)gl.deleteVertexArray(vao);vbo=nextVBO;vao=nextVAO;nextVBO=nextVAO=null;ranges=nextRanges;geometryBytes=vertices.byteLength;stats.skyResourceBytes=PALETTE_BYTES+geometryBytes;stats.skyGeometryUploads++;
    } finally {if(nextVBO)gl.deleteBuffer(nextVBO);if(nextVAO)gl.deleteVertexArray(nextVAO);gl.bindVertexArray(restoreVAO);gl.bindBuffer(gl.ARRAY_BUFFER,restoreArray);}
  }
  function prepare(input,paletteInput){
    alive();if(input.length!==20||paletteInput.length!==PALETTE_SIZE*4)throw new Error('Browser sky frame schema invalid');const next=new Float32Array(input);
    if(next[0]!==0&&next[0]!==1||![0,1].includes(next[1])||![0,1].includes(next[2])||!Number.isInteger(next[3])||next[3]<0||next[3]>PALETTE_SIZE)throw new Error('Browser sky frame values invalid');
    if(next[0]===1){if(!vao||!ranges||next[3]<1)throw new Error('Browser sky geometry not uploaded');for(let n=4;n<20;n++)if(!Number.isFinite(next[n]))throw new Error('Browser sky matrix invalid');
      const palette=paletteInput instanceof Float32Array?paletteInput:new Float32Array(paletteInput);for(let n=0;n<next[3]*4;n++)if(!Number.isFinite(palette[n])||palette[n]<0||palette[n]>1)throw new Error('Browser sky palette bounds invalid');
      const active=gl.getParameter(gl.ACTIVE_TEXTURE);gl.activeTexture(gl.TEXTURE0);const binding=gl.getParameter(gl.TEXTURE_BINDING_2D);
      try {gl.bindTexture(gl.TEXTURE_2D,paletteTexture);gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,PALETTE_SIZE,1,gl.RGBA,gl.FLOAT,palette);check('palette upload');stats.skyPaletteUploads++;}
      finally{gl.bindTexture(gl.TEXTURE_2D,binding);gl.activeTexture(active);}
    }
    values=next;
  }
  function draw(){
    alive();stats.skyDrawCalls=0;if(!values||values[0]!==1)return;
    const old={program:gl.getParameter(gl.CURRENT_PROGRAM),vao:gl.getParameter(gl.VERTEX_ARRAY_BINDING),active:gl.getParameter(gl.ACTIVE_TEXTURE),depth:gl.isEnabled(gl.DEPTH_TEST),depthMask:gl.getParameter(gl.DEPTH_WRITEMASK),blend:gl.isEnabled(gl.BLEND),cull:gl.isEnabled(gl.CULL_FACE),colorMask:gl.getParameter(gl.COLOR_WRITEMASK),srcRGB:gl.getParameter(gl.BLEND_SRC_RGB),dstRGB:gl.getParameter(gl.BLEND_DST_RGB),srcAlpha:gl.getParameter(gl.BLEND_SRC_ALPHA),dstAlpha:gl.getParameter(gl.BLEND_DST_ALPHA),equationRGB:gl.getParameter(gl.BLEND_EQUATION_RGB),equationAlpha:gl.getParameter(gl.BLEND_EQUATION_ALPHA)};
    gl.activeTexture(gl.TEXTURE0);const binding=gl.getParameter(gl.TEXTURE_BINDING_2D);
    try {
      gl.useProgram(program);gl.bindVertexArray(vao);gl.bindTexture(gl.TEXTURE_2D,paletteTexture);gl.uniform1i(paletteLocation,0);gl.uniformMatrix4fv(matrixLocation,false,values.subarray(4));gl.disable(gl.DEPTH_TEST);gl.depthMask(false);gl.disable(gl.CULL_FACE);gl.colorMask(true,true,true,true);gl.blendEquation(gl.FUNC_ADD);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);
      for(let layer=0;layer<4;layer++){if((layer===1||layer===2)&&values[1]!==1||layer===3&&values[2]!==1)continue;const at=layer*3;ranges[at+2]?gl.enable(gl.BLEND):gl.disable(gl.BLEND);gl.drawArrays(gl.TRIANGLES,ranges[at],ranges[at+1]);stats.skyDrawCalls++;}
    }finally{
      gl.bindTexture(gl.TEXTURE_2D,binding);gl.activeTexture(old.active);gl.useProgram(old.program);gl.bindVertexArray(old.vao);gl.depthMask(old.depthMask);gl.colorMask(...old.colorMask);gl.blendFuncSeparate(old.srcRGB,old.dstRGB,old.srcAlpha,old.dstAlpha);gl.blendEquationSeparate(old.equationRGB,old.equationAlpha);for(const [cap,on]of [[gl.DEPTH_TEST,old.depth],[gl.BLEND,old.blend],[gl.CULL_FACE,old.cull]])on?gl.enable(cap):gl.disable(cap);
    }
  }
  function dispose(){if(disposed)return;if(vbo)gl.deleteBuffer(vbo);if(vao)gl.deleteVertexArray(vao);if(paletteTexture)gl.deleteTexture(paletteTexture);if(program)gl.deleteProgram(program);if(vertexShader)gl.deleteShader(vertexShader);if(fragmentShader)gl.deleteShader(fragmentShader);program=vertexShader=fragmentShader=paletteTexture=vbo=vao=null;ranges=values=null;geometryBytes=0;stats.skyResourceBytes=0;disposed=true;}
  return {initialize,uploadGeometry,prepare,draw,dispose,stats};
}
