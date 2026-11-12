/** Small source-space cardinal ring, never drawable membership. No diagonal
 * corners, extra planes, source admission or mutation authority are invented. */
export function createBoundedPrefetch(catalog){
  const cells=new Map();for(const c of catalog.chunks)cells.set(c.role+':'+c.plane+':'+c.cellX+':'+c.cellY,c.id);
  let previous=null,direction=[0,0],signature=null,cached=[];
  function plan(scope){
    if(!scope?.[1]){previous=null;direction=[0,0];signature=null;cached=[];return cached;}
    const x=scope[5],y=scope[6];
    if(previous){const dx=Math.sign(x-previous[0]),dy=Math.sign(y-previous[1]);if(dx||dy)direction=[dx,dy];}
    previous=[x,y];const key=x+':'+y+':'+direction.join(':');if(key===signature)return cached;signature=key;
    const sides=[[1,0],[-1,0],[0,1],[0,-1]].sort((a,b)=>(b[0]*direction[0]+b[1]*direction[1])-(a[0]*direction[0]+a[1]*direction[1]));
    const out=[],seen=new Set();
    for(const [dx,dy]of sides)for(const role of [0,1])for(let plane=0;plane<(role===0?3:1);plane++){
      const scale=role===0?1:2;
      for(let along=-2*scale;along<3*scale;along++)for(let thickness=0;thickness<scale;thickness++){
        const outside=dx>0||dy>0?3*scale+thickness:-3*scale+thickness;
        const cx=x*scale+(dx?outside:along),cy=y*scale+(dy?outside:along);
        const id=cells.get(role+':'+plane+':'+cx+':'+cy);if(id!==undefined&&!seen.has(id)){out.push(id);seen.add(id);}
      }
    }
    cached=out;return cached;
  }
  return {plan};
}
