// User-requested batch palette swaps, matching the six armor palettes exactly.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const armor=JSON.parse(fs.readFileSync(path.join(__dirname,'leather-sets/manifest.json'),'utf8'));
const families=armor.filter(x=>x.slot==='coif');
assert.equal(families.length,6);
const ids={'giant-frog':[3333,3356],naga:[3335,3368],'terror-dog':[3336,3374],bloodveld:[3337,3380],'dark-beast':[3338,3386],ugthanki:[3392,3393]};
const source=path.join(__dirname,'hide-leather-bases/hide-leather.png');
const original=execFileSync('ffmpeg',['-v','error','-i',source,'-f','rawvideo','-pix_fmt','rgba','-']);
assert.equal(original.length,48*32*4);
const out=path.join(__dirname,'hide-leathers');fs.mkdirSync(out,{recursive:true});
const write=(file,pixels,w=48,h=32)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',file],{input:pixels});
const sheet=Buffer.alloc(96*192*4),manifest=[];let rows='';
families.forEach(({family,color},row)=>{
 const tint=parseInt(color.slice(1),16),channels=[tint>>16&255,tint>>8&255,tint&255];
 const pixels=Buffer.from(original);let count=0;
 for(let i=0;i<pixels.length;i+=4){
  if(original[i+3]&&original[i]===original[i+1]&&original[i]===original[i+2]){
   for(let c=0;c<3;c++)pixels[i+c]=original[i]*channels[c]>>8;
   count++;
  }
  assert.equal(pixels[i+3],original[i+3]);
  if(original[i]!==original[i+1]||original[i]!==original[i+2])assert.ok(pixels.subarray(i,i+4).equals(original.subarray(i,i+4)));
 }
 assert.ok(count>0);let cells='';
 ['hide','leather'].forEach((kind,col)=>{
  const file=family+'-'+kind+'.png';write(path.join(out,file),pixels);
  for(let y=0;y<32;y++)pixels.copy(sheet,((row*32+y)*96+col*48)*4,y*48*4,(y+1)*48*4);
  manifest.push({family,kind,itemId:ids[family][col],file,color,source:'items:69',canvas:[48,32],recoloredPixels:count,alphaUnchanged:true});
  cells+='<td><img src="hide-leathers/'+file+'"><img class="zoom" src="hide-leathers/'+file+'"></td>';
 });
 rows+='<tr><th>'+family+'<br>'+color+'</th>'+cells+'</tr>';
});
write(path.join(out,'contact.png'),sheet,96,192);
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
fs.writeFileSync(path.join(__dirname,'hide-leathers-preview.html'),'<!doctype html><html lang="en"><meta charset="utf-8"><title>Slayer hides and leathers</title><style>body{background:#24272b;color:#eee;font:16px system-ui;padding:20px}table{border-collapse:collapse}td,th{padding:8px;border:1px solid #555}img{display:block;image-rendering:pixelated;background:#111}.zoom{width:192px;height:128px;margin-top:5px}</style><h1>Six hides and six tanned leathers</h1><p>Matching armor palettes. Both forms reuse items:69, as existing definitions do. Native 48×32 plus 4× zoom. Source shading, silhouette, offsets and alpha preserved. Artwork candidates only; not installed.</p><table><tr><th>Family</th><th>Raw hide</th><th>Tanned leather</th></tr>'+rows+'</table>');
console.log('PASS: 12 native 48x32 icons, six matching armor palettes, original alpha and non-gray pixels preserved.');
