// User requested the original feather unchanged except cockatrice palette.
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const input=path.resolve(__dirname,'../../reference-library/items/ammunition/components/id-02326.png');
const pixels=execFileSync('ffmpeg',['-v','error','-i',input,'-f','rawvideo','-pix_fmt','rgba','-']);
const stops=[[0,[38,30,23]],[90,[83,65,42]],[160,[143,120,76]],[220,[199,180,123]],[255,[231,219,169]]];
for(let i=0;i<pixels.length;i+=4){
 if(!pixels[i+3])continue;
 const v=Math.round((pixels[i]+pixels[i+1]+pixels[i+2])/3);
 let n=1;while(n<stops.length-1&&v>stops[n][0])n++;
 const [lo,a]=stops[n-1],[hi,b]=stops[n],t=(v-lo)/(hi-lo);
 for(let c=0;c<3;c++)pixels[i+c]=Math.round(a[c]+(b[c]-a[c])*t);
}
execFileSync('ffmpeg',['-v','error','-n','-f','rawvideo','-pix_fmt','rgba','-s','48x32','-i','-','-frames:v','1',path.join(__dirname,'cockatrice-feathers-candidate.png')],{input:pixels});
