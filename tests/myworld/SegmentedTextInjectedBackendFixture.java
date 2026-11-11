package orsc.graphics.two;

import java.lang.reflect.Proxy;
import orsc.graphics.three.Renderer3DSettings;

/** JVM test-native injection; proves producer ordering, never claims actual JNI. */
public final class SegmentedTextInjectedBackendFixture {
    private static int calls,rejections,noWriteCommits;
    private static boolean priorColor,priorPartialT;
    private static final StringBuilder packets=new StringBuilder();private static int exported;
    private static void array(StringBuilder out,int[] data){out.append('[');for(int i=0;i<data.length;i++){if(i>0)out.append(',');out.append(data[i]);}out.append(']');}
    private static int[] bits(float[] input){int[] out=new int[input.length];for(int i=0;i<input.length;i++)out[i]=Float.floatToRawIntBits(input[i]);return out;}
    private static void export(Object[] a,int[] before,float[] beforeT,int result){
        if(exported>=12||((byte[])a[3]).length>1048576)return;
        if(exported++>0)packets.append(',');packets.append("{\"generation\":").append(a[6]).append(",\"sequence\":").append(a[7]).append(",\"status\":").append(result);
        String[] names={"header","texts","plots"};for(int i=0;i<3;i++){packets.append(",\"").append(names[i]).append("\":");array(packets,(int[])a[i]);}
        byte[] font=(byte[])a[3];int[] f=new int[font.length];for(int i=0;i<f.length;i++)f[i]=font[i];packets.append(",\"fonts\":");array(packets,f);
        packets.append(",\"beforeC\":");array(packets,before);packets.append(",\"beforeT\":");array(packets,bits(beforeT));packets.append(",\"afterC\":");array(packets,(int[])a[4]);packets.append(",\"afterT\":");array(packets,bits((float[])a[5]));packets.append('}');
    }
    public static void main(String[] args)throws Exception{
        final String mode=args[0];boolean candidate=args.length>1&&args[1].equals("candidate");
        Renderer3DSettings.setBrowserPresentationAvailable(true);
        boolean installed=false;
        try{
            Class<?> kernel=Class.forName("orsc.graphics.two.SegmentedTextRaster"),backend=Class.forName("orsc.graphics.two.SegmentedTextRaster$Backend");
            if(!mode.equals("unavailable")){
                Object implementation=Proxy.newProxyInstance(backend.getClassLoader(),new Class<?>[]{backend},(proxy,method,a)->{
                    if(!method.getName().equals("raster"))return null;
                    calls++;int[] c=(int[])a[4];float[] t=(float[])a[5];for(int v:c)priorColor|=v!=0;for(float v:t)priorPartialT|=v!=0f&&v!=1f;
                    if(mode.equals("reject")){rejections++;return 0;}
                    int[] before=args.length>3&&exported<12?c.clone():null;float[] beforeT=before==null?null:t.clone();
                    int result=SegmentedTextPacketReference.raster((int[])a[0],(int[])a[1],(int[])a[2],(byte[])a[3],c,t,((Double)a[6]).doubleValue(),((Double)a[7]).doubleValue());
                    if(before!=null)export(a,before,beforeT,result);
                    if(result==1)noWriteCommits++;return result;
                });
                kernel.getMethod("install",backend).invoke(null,implementation);installed=true;
            }
        }catch(ClassNotFoundException baseline){}
        OverlayJournalRenderingIndependentFixture.main(new String[0]);
        SegmentedTextSceneGuardFixture.run();
        // The runner supplies the expected activation property explicitly.
        boolean enabled=candidate&&installed&&Boolean.getBoolean(args.length>2?args[2]:"spoiledmilk.browserSegmentedTextRaster");
        if(enabled){if(calls<1||!priorColor||!priorPartialT)throw new AssertionError("Vacuous native run or missing arbitrary C/T start");if(mode.equals("reject")&&rejections!=calls)throw new AssertionError("Rejection test wrote native pixels");if(mode.equals("accept")&&noWriteCommits<1)throw new AssertionError("No zero-sample accepted native batch");}
        else if(calls!=0)throw new AssertionError("Native ran despite disabled/unavailable producer");
        if(args.length>3){if(exported<8)throw new AssertionError("Too few actual packet exports");java.nio.file.Files.write(java.nio.file.Paths.get(args[3]),("{\"scope\":\"Actual producer packets, independent old-glyph JVM expected outputs verified by frozen full-frame parity\",\"packets\":["+packets+"]}\n").getBytes("UTF-8"));}
        System.err.println("TEST-NATIVE calls="+calls+" rejected="+rejections+" noWriteCommits="+noWriteCommits+" priorColor="+priorColor+" priorPartialT="+priorPartialT+"; JVM simulation only");
    }
}
