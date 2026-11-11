// Standalone owned synthetic texture data. No gameplay objects or borrowed arrays.
export const WIDTH=960,HEIGHT=540;
export const MODES=['full','row-bands','strided','packed','prepacked'];
export function validateRectangles(rects,w=WIDTH,h=HEIGHT){
 if(!Array.isArray(rects)||!rects.length||rects.length>128)throw Error('Rectangle bound');
 const seen=new Uint8Array(w*h);
 for(const r of rects){if(!Array.isArray(r)||r.length!==4||r.some(x=>!Number.isInteger(x)))throw Error('Integer rectangle');
  const [x,y,x1,y1]=r;if(x<0||y<0||x1<=x||y1<=y||x1>w||y1>h)throw Error('Rectangle bounds');
  for(let row=y;row<y1;row++)for(let col=x;col<x1;col++){const i=row*w+col;if(seen[i])throw Error('Overlapping rectangles');seen[i]=1;}
 }return seen;
}
export function rowBands(rects,w=WIDTH,h=HEIGHT){const rows=new Uint8Array(h);for(const [,y,,end]of rects)rows.fill(1,y,end);const out=[];for(let y=0;y<h;){if(!rows[y]){y++;continue;}const start=y;while(y<h&&rows[y])y++;out.push([0,start,w,y]);}return out;}
export function spanElements(r,channels,w=WIDTH){return ((r[3]-r[1]-1)*w+r[2]-r[0])*channels;}
export function pack(source,r,channels,staging,w=WIDTH){const [x,y,x1,y1]=r;let at=0;for(let row=y;row<y1;row++){const start=(row*w+x)*channels;staging.set(source.subarray(start,start+(x1-x)*channels),at);at+=(x1-x)*channels;}return staging.subarray(0,at);}
export function makeSources(rects,w=WIDTH,h=HEIGHT){const mask=validateRectangles(rects,w,h),frames=[];for(let phase=0;phase<2;phase++){const c=new Uint8Array(w*h*4),t=new Float32Array(w*h);for(let i=0;i<w*h;i++){const d=mask[i]?phase:0; // BGRA storage: unchanged shader-channel convention in every variant.
 c[i*4]=(i*13+d*31)&255;c[i*4+1]=(i*7+d*73)&255;c[i*4+2]=(i*3+d*97)&255;c[i*4+3]=255;t[i]=((i+d)%5)*0.25;}frames.push({c,t});}return frames;}
export function metric(rects,mode,w=WIDTH,h=HEIGHT){const rs=mode==='full'?[[0,0,w,h]]:mode==='row-bands'?rowBands(rects,w,h):rects;const area=rs.reduce((n,r)=>n+(r[2]-r[0])*(r[3]-r[1]),0);return {calls:rs.length*2,logicalTexelBytes:area*8,sourceViewSpanBytes:rs.reduce((n,r)=>n+(mode==='strided'?spanElements(r,1,w):(r[2]-r[0])*(r[3]-r[1]))*8,0),rectangles:rs};}
