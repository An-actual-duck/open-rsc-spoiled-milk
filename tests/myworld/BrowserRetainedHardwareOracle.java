package orsc;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import orsc.graphics.two.MudClientGraphics;
/** Fresh actual GraphicsController oracle; array row0 is explicitly the GPU
 * bottom row in this functional fixture, not a conversion of a Java image. */
public final class BrowserRetainedHardwareOracle {
 private static final int WIDTH=256,HEIGHT=128;
 private static void write(Path root,String name,int[] pixels)throws Exception {
  try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(root.resolve(name))))){for(int pixel:pixels){out.writeByte(pixel>>16);out.writeByte(pixel>>8);out.writeByte(pixel);out.writeByte(255);}}
 }
 public static void main(String[] args)throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true;
  Path root=Paths.get(args[0]);Files.createDirectories(root);
  MudClientGraphics surface=new MudClientGraphics(WIDTH,HEIGHT,1);
  int[] pattern=new int[WIDTH*HEIGHT];
  for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
   int color=(x<<16)|(((x*31+y*17)&255)<<8)|((y*2)&255);
   if(y<16)color=x*0x010101;
   else if(y<32)color=x<<16;
   else if(y<48)color=x<<8;
   else if(y<64)color=x;
   if(x<8)color=(y&1)==0?0:0xffffff;
   pattern[y*WIDTH+x]=color;
  }
  write(root,"pattern.rgba",pattern);
  for(int count=1;count<=16;count++){System.arraycopy(pattern,0,surface.pixelData,0,pattern.length);for(int i=0;i<count;i++)surface.fade2black(16316665);write(root,"count-"+count+".rgba",surface.pixelData);}
  System.arraycopy(pattern,0,surface.pixelData,0,pattern.length);
  StringBuilder steps=new StringBuilder();
  for(int step=1;step<=50;step++){
   surface.fade2black(16316665);write(root,"base-"+step+".rgba",surface.pixelData);
   int x=16+(step*7)%170,y=16+(step*11)%70,width=19,height=9,color=(step*47&255)<<16|(step*73&255)<<8|(step*101&255);
   surface.drawBox(x,y,width,height,color);write(root,"full-"+step+".rgba",surface.pixelData);
   if(step>1)steps.append(',');steps.append("{\"step\":").append(step).append(",\"x\":").append(x).append(",\"y\":").append(y).append(",\"width\":").append(width).append(",\"height\":").append(height).append(",\"color\":").append(color).append('}');
  }
  String json="{\"width\":"+WIDTH+",\"height\":"+HEIGHT+",\"origin\":\"bottom-left GPU fixture convention\",\"countCases\":16,\"steps\":["+steps+"]}";
  Files.write(root.resolve("oracle.json"),json.getBytes(StandardCharsets.UTF_8));
  System.out.println("PASS generated117 actual GraphicsController RGBA8 oracle frames; coverage remains default-off");
 }
}
