/** WebGL2 client-array rectangle source contract. No state query or mutation
 * occurs until the caller has validated ALL plane/rectangle/token metadata. */
function fields(gl){return [gl.UNPACK_ROW_LENGTH,gl.UNPACK_SKIP_ROWS,gl.UNPACK_SKIP_PIXELS,gl.UNPACK_ALIGNMENT,gl.UNPACK_FLIP_Y_WEBGL,gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL];}
export function establishOverlayUnpack(gl){
  for(const [n,p]of fields(gl).entries())gl.pixelStorei(p,[0,0,0,4,false,false][n]);
  gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,null);
}
export function withOverlayUnpack(gl,logicalWidth,ownedFrame,upload){
  const canonical=ownedFrame?.active===true&&ownedFrame.ownsOverlayUnpack===true&&typeof ownedFrame.ensureOverlayUnpack==='function';
  if(canonical)ownedFrame.ensureOverlayUnpack();
  const parameters=fields(gl),saved=canonical?[0,0,0,4,false,false]:parameters.map(p=>gl.getParameter(p));
  const oldBuffer=canonical?null:gl.getParameter(gl.PIXEL_UNPACK_BUFFER_BINDING);
  const desired=[logicalWidth,0,0,4,false,false],changes=[];let bufferChanged=false,primary=null;
  try{
    for(let n=0;n<parameters.length;n++)if(saved[n]!==desired[n]){changes.push(n);gl.pixelStorei(parameters[n],desired[n]);}
    if(oldBuffer!==null){bufferChanged=true;gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,null);}
    return upload();
  }catch(error){primary=error;throw error;}
  finally{
    // Attempt every restoration even if one setter throws; preserve the primary
    // upload failure. Any restoration failure also makes the caller fail closed.
    let failure=null;
    if(bufferChanged)try{gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER,oldBuffer);}catch(error){failure=error;}
    for(let n=changes.length-1;n>=0;n--)try{const at=changes[n];gl.pixelStorei(parameters[at],saved[at]);}catch(error){failure||=error;}
    if(failure&&!primary)throw failure;
  }
}
