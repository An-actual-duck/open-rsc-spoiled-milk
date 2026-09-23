// Keep approved art untouched outside the tip; composite imagegen's tip colors.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const assert=require('node:assert/strict');
const read=name=>execFileSync('ffmpeg',['-v','error','-i',path.join(__dirname,name),'-f','rawvideo','-pix_fmt','rgba','-']);
const original=read('dagger-of-terror-approved.png');
const generated=read('poison-dagger-generated-native.png');
assert.equal(original.length,48*32*4);assert.equal(generated.length,original.length);
const result=Buffer.from(original);let changed=0;
for(let y=0;y<32;y++)for(let x=31;x<48;x++){
 const i=(y*48+x)*4;
 if(!original[i+3]||!generated[i+3])continue;
 for(let c=0;c<3;c++)result[i+c]=generated[i+c];
 if(!result.subarray(i,i+3).equals(original.subarray(i,i+3)))changed++;
}
for(let i=0;i<result.length;i+=4){
 assert.equal(result[i+3],original[i+3]);
 if((i/4)%48<31)assert.ok(result.subarray(i,i+4).equals(original.subarray(i,i+4)));
}
assert.ok(changed>0);
execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','48x32','-i','-','-frames:v','1',path.join(__dirname,'dagger-of-terror-poisoned-candidate.png')],{input:result});
console.log(JSON.stringify({changedPixels:changed,canvas:[48,32],alphaUnchanged:true,outsideTipUnchanged:true}));
