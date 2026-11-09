package orsc;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.Random;
import orsc.graphics.two.MudClientGraphics;
/** Executes the actual legacy GraphicsController method; no copied fade formula. */
public final class BrowserRetainedFadeFixture {
 public static void main(String[] args)throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true;
  int[] inputs=new int[16384];Random random=new Random(0x7e7a1eedL);
  for(int n=0;n<inputs.length;n++)inputs[n]=n<256?n*0x010101:n<1024?(n%256)<<((n/256-1)*8):random.nextInt()&0xffffff;
  MudClientGraphics surface=new MudClientGraphics(inputs.length,1,1);
  try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[0]))){
   out.writeInt(inputs.length*16);
   for(int count=1;count<=16;count++){
    System.arraycopy(inputs,0,surface.pixelData,0,inputs.length);
    for(int step=0;step<count;step++)surface.fade2black(16316665);
    for(int n=0;n<inputs.length;n++){out.writeInt(inputs[n]);out.writeInt(count);out.writeInt(surface.pixelData[n]);}
   }
  }
  surface.pixelData[0]=0xffffff;
  for(int n=1;n<=50;n++){surface.fade2black(16316665);if(n==10&&surface.pixelData[0]!=0x797979||n==30&&surface.pixelData[0]!=0x101010||n==50&&surface.pixelData[0]!=0)throw new AssertionError("Retained fade reference changed at "+n);}
  System.out.println("PASS actual GraphicsController 262144 fade corpus entries and retained 10/30/50-step white oracle");
 }
}
