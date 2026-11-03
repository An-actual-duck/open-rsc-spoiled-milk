// Palette-only wood correction; red tongue string, grey grip and alpha are exact originals.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{execFileSync}=require('node:child_process');
const source=path.join(__dirname,'leaching-bow-approved.png');
const input=execFileSync('ffmpeg',['-v','error','-i',source,'-f','rawvideo','-pix_fmt','rgba','-']);
assert.equal(input.length,48*32*4);
const output=Buffer.from(input);let changed=0;
for(let i=0;i<input.length;i+=4){
 const [r,g,b,a]=input.subarray(i,i+4);
 // Ochre/brown wood is separate from near-neutral grip and red/pink string.
 const wood=a && g>b*1.4 && r>g*1.2;
 if(wood){
  const shade=Math.round((r*.2126+g*.7152+b*.0722)*.32);
  output[i]=shade+2;output[i+1]=shade+1;output[i+2]=shade;
  changed++;
 }else assert.ok(output.subarray(i,i+4).equals(input.subarray(i,i+4)));
 assert.equal(output[i+3],a);
}
assert.ok(changed>50);
execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','48x32','-i','-','-frames:v','1',path.join(__dirname,'leaching-bow-ebony-candidate.png')],{input:output});
console.log('PASS: '+changed+' wood pixels recolored; shading order, string, grip, alpha and 48x32 geometry preserved.');
