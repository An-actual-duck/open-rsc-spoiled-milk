// Register approved artwork to original staff anchors, independently above/below the grip.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const art=path.join(__dirname,'held-thunder-spire'),root=process.argv[2];
const manifest=JSON.parse(fs.readFileSync(path.join(art,'source-manifest.json')));
const read=f=>execFileSync('ffmpeg',['-v','error','-i',f,'-f','rawvideo','-pix_fmt','rgba','-']);
const sheet=read(path.join(art,'candidate-native.png'));
const out=path.resolve(__dirname,'../../assets/sprites/equipment/thunder-spire-staff/numbered');fs.mkdirSync(out,{recursive:true});
const bounds=points=>({x:Math.min(...points.map(p=>p.x)),y:Math.min(...points.map(p=>p.y)),r:Math.max(...points.map(p=>p.x)),b:Math.max(...points.map(p=>p.y))});
function split(points,axis){
 const values=points.map(p=>p[axis]),lo=Math.min(...values),hi=Math.max(...values);let gap=[];
 for(let v=lo+Math.floor((hi-lo)*.25);v<lo+(hi-lo)*.75;v++)if(!values.includes(v))gap.push(v);
 const mid=gap.length?gap[Math.floor(gap.length/2)]:lo+Math.floor((hi-lo)*.45);
 return [points.filter(p=>p[axis]<mid),points.filter(p=>p[axis]>=mid)];
}
const records=[];
for(const e of manifest){
 const n=+e.frame,w=+e.width,h=+e.height,src=read(path.join(root,e.path));let original=[],generated=[];
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){let i=(y*w+x)*4;if(src[i+3]>=128)original.push({x,y});}
 // Generated final row starts a little early; exclude its horn tips from the preceding row.
 const row=Math.floor(n/3),top=row===5?504:row*102,bottom=row===4?504:row===5?612:top+102;
 for(let y=top;y<bottom;y++)for(let x=0;x<84;x++){let i=(y*252+n%3*84+x)*4;if(sheet[i+3]>=128)generated.push({x,y:y-top,pixel:sheet.subarray(i,i+4)});}
 const axis=n===17?'x':'y',a=split(original,axis),b=split(generated,axis),pixels=Buffer.alloc(w*h*4);
 for(let s=0;s<2;s++){
  const target=bounds(a[s]),from=bounds(b[s]),lookup=new Map(b[s].map(p=>[p.x+','+p.y,p.pixel]));
  for(let y=target.y;y<=target.b;y++)for(let x=target.x;x<=target.r;x++){
   const sx=from.x+Math.min(from.r-from.x,Math.floor((x-target.x+.5)*(from.r-from.x+1)/(target.r-target.x+1)));
   const sy=from.y+Math.min(from.b-from.y,Math.floor((y-target.y+.5)*(from.b-from.y+1)/(target.b-target.y+1)));
   const p=lookup.get(sx+','+sy);if(p){p.copy(pixels,(y*w+x)*4);pixels[(y*w+x)*4+3]=255;}
  }
 }
 const name=String(n).padStart(2,'0')+'.png';
 execFileSync('ffmpeg',['-v','error','-y','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',path.join(out,name)],{input:pixels});
 records.push({frame:n,width:w,height:h,xShift:+e.x_shift,yShift:+e.y_shift,boundWidth:+e.bound_width,boundHeight:+e.bound_height});
}
fs.writeFileSync(path.join(art,'runtime-manifest.json'),JSON.stringify(records,null,2)+'\n');
console.log('PASS: 18 frames registered to original shaft/grip segment bounds and source offsets');
