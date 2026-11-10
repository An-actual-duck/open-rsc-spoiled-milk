/** World-material minification only. Source-visible level0 RGBA, actor/UI nearest
 * sampling and point-sampled shadow alpha remain separate contracts. */
const MAX_PIXELS=262144;
export function worldMipBytes(width,height){
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<1||height<1||width*height>MAX_PIXELS)throw new Error('World mip dimensions exceed bound');
  let bytes=0;for(;;){bytes+=width*height*4;if(width===1&&height===1)return bytes;width=Math.max(1,width>>1);height=Math.max(1,height>>1);}
}
/** Only fully invisible RGB is extended, never alpha or source-visible pixels.
 * Nearest-color propagation prevents black fringes when straight RGBA is filtered. */
function extendInvisibleRgb(pixels,width,height){
  const out=pixels.slice(),count=width*height,seen=new Uint8Array(count),queue=new Int32Array(count);let head=0,tail=0;
  for(let n=0;n<count;n++)if(out[n*4+3]){seen[n]=1;queue[tail++]=n;}
  while(head<tail){const n=queue[head++],x=n%width,y=(n/width)|0;
    for(let dir=0;dir<4;dir++){const m=dir===0?(x>0?n-1:-1):dir===1?(x+1<width?n+1:-1):dir===2?(y>0?n-width:-1):(y+1<height?n+width:-1);if(m>=0&&!seen[m]){
      seen[m]=1;for(let c=0;c<3;c++)out[m*4+c]=out[n*4+c];queue[tail++]=m;
    }}
  }return out;
}
/** Area resampling includes odd/NPOT edge texels. Alpha-weighted RGB prevents
 * invisible black from tinting foliage; total alpha stays within rounding bound. */
export function buildWorldCutoutMips(width,height,rgba){
  const bytes=worldMipBytes(width,height);if(!(rgba instanceof Uint8Array)||rgba.length!==width*height*4)throw new Error('World mip pixel dimensions mismatch');
  const baseCount=width*height;let baseAlpha=0;for(let n=3;n<rgba.length;n+=4)baseAlpha+=rgba[n];
  let source=extendInvisibleRgb(rgba,width,height);const levels=[{width,height,pixels:source}];
  while(width>1||height>1){const w=Math.max(1,width>>1),h=Math.max(1,height>>1),out=new Uint8Array(w*h*4);let alphaTotal=0;
    for(let y=0;y<h;y++)for(let x=0;x<w;x++){
      const left=x*width,right=(x+1)*width,top=y*height,bottom=(y+1)*height;
      let a=0,r=0,g=0,b=0;
      for(let sy=Math.floor(top/h);sy<Math.ceil(bottom/h);sy++)for(let sx=Math.floor(left/w);sx<Math.ceil(right/w);sx++){
        const weight=(Math.min(right,(sx+1)*w)-Math.max(left,sx*w))*(Math.min(bottom,(sy+1)*h)-Math.max(top,sy*h));
        const at=(sy*width+sx)*4,wa=weight*source[at+3];a+=wa;r+=wa*source[at];g+=wa*source[at+1];b+=wa*source[at+2];
      }
      const at=(y*w+x)*4;out[at]=a?Math.round(r/a):0;out[at+1]=a?Math.round(g/a):0;out[at+2]=a?Math.round(b/a):0;out[at+3]=Math.round(a/(width*height));alphaTotal+=out[at+3];
    }
    // At most one LSB per adjusted texel: preserve base mean coverage rather
    // than accumulate rounding loss through successive levels.
    let delta=Math.round(baseAlpha*w*h/baseCount)-alphaTotal;
    for(let pass=0;delta&&pass<255;pass++)for(let n=3;n<out.length&&delta;n+=4){if(delta>0&&out[n]<255){out[n]++;delta--;}else if(delta<0&&out[n]>0){out[n]--;delta++;}}
    source=extendInvisibleRgb(out,w,h);levels.push({width:w,height:h,pixels:source});width=w;height=h;
  }
  return {levels,bytes};
}
export function createWorldTextureSampling(gl,{enabled=false,stats={}}={}){
  let sampler=null,disposed=false;
  stats.worldTextureSampling=enabled?'mip-minification / nearest-magnification':'nearest';stats.worldMipUploadedBytes=0;stats.worldMipBuildMilliseconds=0;
  function initialize(){if(!enabled||sampler)return;if(disposed)throw new Error('World texture sampling disposed');let next=null;
    try{next=gl.createSampler();if(!next)throw new Error('World sampler allocation failed');
      for(const [name,value]of [[gl.TEXTURE_MIN_FILTER,gl.LINEAR_MIPMAP_LINEAR],[gl.TEXTURE_MAG_FILTER,gl.NEAREST],[gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE],[gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE]])gl.samplerParameteri(next,name,value);
      const error=gl.getError();if(error!==gl.NO_ERROR)throw new Error('World sampler initialization GL error 0x'+error.toString(16));sampler=next;next=null;
    }finally{if(next)gl.deleteSampler(next);}
  }
  function plan(width,height,rgba,transparent){if(!enabled)return{bytes:width*height*4,base:rgba,levels:null,generate:false};const start=performance.now();
    const result=transparent?buildWorldCutoutMips(width,height,rgba):{bytes:worldMipBytes(width,height),levels:null};stats.worldMipBuildMilliseconds+=performance.now()-start;
    return{...result,base:rgba,generate:!transparent};
  }
  function uploadBound(plan,width,height,reuse){
    if(plan.levels){for(let n=0;n<plan.levels.length;n++){const level=plan.levels[n];if(reuse)gl.texSubImage2D(gl.TEXTURE_2D,n,0,0,level.width,level.height,gl.RGBA,gl.UNSIGNED_BYTE,level.pixels);else gl.texImage2D(gl.TEXTURE_2D,n,gl.RGBA8,level.width,level.height,0,gl.RGBA,gl.UNSIGNED_BYTE,level.pixels);}}
    else{if(reuse)gl.texSubImage2D(gl.TEXTURE_2D,0,0,0,width,height,gl.RGBA,gl.UNSIGNED_BYTE,plan.base);else gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,width,height,0,gl.RGBA,gl.UNSIGNED_BYTE,plan.base);if(plan.generate)gl.generateMipmap(gl.TEXTURE_2D);}
    stats.worldMipUploadedBytes+=plan.bytes;
  }
  function baseSampling(){if(enabled)gl.bindSampler(0,null);}
  function colorSampling(){if(enabled){initialize();gl.bindSampler(0,sampler);}}
  function dispose(){if(disposed)return;if(sampler){gl.bindSampler(0,null);gl.deleteSampler(sampler);}sampler=null;disposed=true;}
  return {enabled,initialize,plan,uploadBound,baseSampling,colorSampling,dispose};
}
