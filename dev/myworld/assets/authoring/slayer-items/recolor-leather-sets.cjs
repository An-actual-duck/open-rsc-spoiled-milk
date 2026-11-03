// User-requested deterministic batch palette swaps; no image generation.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const slots=['coif','cuirass','gloves','boots','chaps'];
const families=[['giant-frog','Giant Frog',0x238fff],['naga','Naga',0x589d40],['terror-dog','Terror Dog',0xd34538],['bloodveld','Bloodveld',0xe3b59a],['dark-beast','Dark Beast',0x55565a],['ugthanki','Ugthanki',0xefd04b]];
const out=path.join(__dirname,'leather-sets');fs.mkdirSync(out,{recursive:true});
const read=file=>execFileSync('ffmpeg',['-v','error','-i',file,'-f','rawvideo','-pix_fmt','rgba','-']);
const write=(file,pixels,w=48,h=32)=>execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s',w+'x'+h,'-i','-','-frames:v','1',file],{input:pixels});
const bases=slots.map(s=>read(path.join(__dirname,'leather-bases',s+'.png')));
const sheet=Buffer.alloc(240*192*4),manifest=[];
let rows='';
families.forEach(([key,label,color],row)=>{
 const channels=[color>>16&255,color>>8&255,color&255];
 let cells='';
 slots.forEach((slot,col)=>{
  const original=bases[col];assert.equal(original.length,48*32*4);
  const pixels=Buffer.from(original);let changed=0;
  for(let i=0;i<pixels.length;i+=4){
   // Match client greyscale recolor mask; preserve colored trim and alpha.
   if(pixels[i+3]&&pixels[i]===pixels[i+1]&&pixels[i]===pixels[i+2]){
    const v=pixels[i];for(let c=0;c<3;c++)pixels[i+c]=v*channels[c]>>8;
    changed++;
   }
   assert.equal(pixels[i+3],original[i+3]);
   if(original[i]!==original[i+1]||original[i]!==original[i+2])assert.ok(pixels.subarray(i,i+4).equals(original.subarray(i,i+4)));
  }
  assert.ok(changed>0);
  const filename=key+'-'+slot+'.png';write(path.join(out,filename),pixels);
  for(let y=0;y<32;y++)pixels.copy(sheet,((row*32+y)*240+col*48)*4,y*48*4,(y+1)*48*4);
  manifest.push({family:key,slot,color:'#'+color.toString(16).padStart(6,'0'),file:filename,canvas:[48,32],recoloredPixels:changed,alphaUnchanged:true,coloredTrimUnchanged:true});
  cells+='<td><img src="leather-sets/'+filename+'"><img class="zoom" src="leather-sets/'+filename+'"></td>';
 });
 rows+='<tr><th>'+label+'<br><code>#'+color.toString(16).padStart(6,'0')+'</code></th>'+cells+'</tr>';
});
write(path.join(out,'contact.png'),sheet,240,192);
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify(manifest,null,2)+'\n');
fs.writeFileSync(path.join(__dirname,'leather-sets-preview.html'),'<!doctype html><html lang="en"><meta charset="utf-8"><title>Six leather armor palettes</title><style>body{background:#24272b;color:#eee;font:16px system-ui;padding:20px}table{border-collapse:collapse}td,th{padding:8px;border:1px solid #555}img{display:block;image-rendering:pixelated;background:#111}.zoom{width:192px;height:128px;margin-top:6px}code{font-size:12px}</style><h1>Six leather armor sets</h1><p>30 inventory/ground icons. Native 48×32 and 4× zoom. Existing runtime sprite geometry, positions and alpha preserved; grayscale tint only, colored trim unchanged. Review candidates, not installed. Worn sprites are a separate next step.</p><table><tr><th>Set</th>'+slots.map(s=>'<th>'+s+'</th>').join('')+'</tr>'+rows+'</table>');
console.log('Verified 30 icons: 48x32, unchanged alpha/shape/placement and colored trim.');
