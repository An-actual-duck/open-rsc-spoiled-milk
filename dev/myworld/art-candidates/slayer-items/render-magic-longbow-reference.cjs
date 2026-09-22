// Reproduce verified client masks for item 656; leave authentic export untouched.
// EntityHandler: pictureMask 4210752 (0x404040), blueMask 44737 (0x00aec1).
// RendererSpriteTransform.apply: grey mask, blue mask, default RGB transform.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const input=path.resolve(__dirname,'../../reference-library/items/weapons/bows/id-02204.png');
const ebony=process.argv.includes('--ebony');
const mask=ebony?0x333333:0x404040, blueMask=ebony?0x6B4A2D:0x00AEC1;
const pixels=execFileSync('ffmpeg',['-v','error','-i',input,'-f','rawvideo','-pix_fmt','rgba','-']);
for(let i=0;i<pixels.length;i+=4){
 if(!pixels[i+3]) continue;
 let [r,g,b]=pixels.subarray(i,i+3);
 if(r===g&&g===b){r=r*((mask>>16)&255)>>8;g=g*((mask>>8)&255)>>8;b=b*(mask&255)>>8;}
 else if(r===g&&b!==g){const shifter=r*b;r=((blueMask>>16)&255)*shifter>>16;g=((blueMask>>8)&255)*shifter>>16;b=(blueMask&255)*shifter>>16;}
 pixels[i]=r*255>>8;pixels[i+1]=g*255>>8;pixels[i+2]=b*255>>8;
}
execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','48x32','-i','-','-frames:v','1',path.join(__dirname,`sources/${ebony?'ebony':'magic'}-longbow-reference.png`)],{input:pixels});
