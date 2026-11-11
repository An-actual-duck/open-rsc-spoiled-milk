private static native int raster(byte[] font,int[] commands,int[] surface,int[] c,float[] t,double generation,double sequence,int validateOnly);
private static native void report(String json);
static final class Plot {int x,y,color,address,aa;Plot(int i){x=4+(i%7)*6;y=18+(i%3);color=(i%3==0)?0:((i%3==1)?0xabcdef:0x123456);address=(i%2==0)?0:6*9;aa=i&1;}}
static Plot[] plots;static int[] commands=new int[128*5],surface=new int[10];static byte[] font;static double serial=0;static long calls=0,compared=0;
static void prepare(LiteralOverlayReference r,int count){surface[0]=1;surface[1]=r.width2;surface[2]=r.height2;surface[3]=count;surface[4]=0;surface[5]=0;surface[6]=r.width2;surface[7]=r.height2;surface[8]=5;surface[9]=0;for(int i=0;i<count;i++){Plot p=plots[i];int at=i*5;commands[at]=p.x;commands[at+1]=p.y;commands[at+2]=p.color;commands[at+3]=p.address;commands[at+4]=p.aa;}}
static void literal(LiteralOverlayReference r,int count){for(int i=0;i<count;i++){Plot p=plots[i];r.plotCharacter(p.aa!=0,font,p.x,p.color,p.address,p.y);}}
static void once(LiteralOverlayReference r,int count,int mode){r.coverage.beginFrame();r.coverage.begin(r.pixelData,r.width2,r.height2);if(mode==0){literal(r,count);return;}prepare(r,count);WorldOverlayCoverage.Frame frame=r.coverage.borrow(r.pixelData,r.width2,r.height2);int status=raster(font,commands,surface,r.pixelData,frame.transmittance,0,++serial,mode==1?1:0);calls++;if(status<0)throw new IllegalStateException("Native poisoned");if(mode==1){if(status!=0)throw new IllegalStateException("Validation-only status");literal(r,count);}else if(status!=1&&status!=2)throw new IllegalStateException("Supported raster rejected");}
static LiteralOverlayReference create(){LiteralOverlayReference r=new LiteralOverlayReference();r.width2=67;r.height2=43;r.clipRight=r.width2;r.clipBottom=r.height2;r.pixelData=new int[r.width2*r.height2];return r;}
public static void main(String[] args)throws Exception{
 try(DataInputStream in=new DataInputStream(LiteralOverlayReference.class.getResourceAsStream("/font.bin"))){font=new byte[in.readInt()];in.readFully(font);}
 plots=new Plot[128];for(int i=0;i<plots.length;i++)plots[i]=new Plot(i);
 int[] counts={0,1,8,32,128};LiteralOverlayReference r=create();
 if(args.length==2&&args[0].equals("--literal-oracle")){try(DataOutputStream out=new DataOutputStream(new FileOutputStream(args[1]))){out.writeInt(r.width2);out.writeInt(r.height2);out.writeInt(counts.length);for(int count:counts){once(r,count,0);out.writeInt(count);for(int c:r.pixelData)out.writeInt(c);for(float t:r.coverage.borrow(r.pixelData,r.width2,r.height2).transmittance)out.writeInt(Float.floatToRawIntBits(t));}}return;}
 // Immediate Java reads after native return establish actual synchronous visibility.
 for(int count:counts){once(r,count,0);int[] expectedC=r.pixelData.clone();float[] expectedT=r.coverage.borrow(r.pixelData,r.width2,r.height2).transmittance.clone();for(int mode=1;mode<=2;mode++){once(r,count,mode);float[] actualT=r.coverage.borrow(r.pixelData,r.width2,r.height2).transmittance;for(int i=0;i<expectedC.length;i++){if(r.pixelData[i]!=expectedC[i]||Float.floatToRawIntBits(actualT[i])!=Float.floatToRawIntBits(expectedT[i]))throw new IllegalStateException("C/T mismatch count"+count+" mode"+mode+" pixel"+i);compared+=2;}}}
 report("{\"phase\":\"exact-pass\",\"exactWords\":"+compared+",\"nativeCalls\":"+calls+",\"sameCallVisibility\":true}");
 String[] names={"literal","validate-then-literal","native-raster"};
 // Warm all cases before either measured order; identical clears remain inside each once().
 for(int count:counts)for(int mode=0;mode<3;mode++){long start=System.nanoTime();do{for(int j=0;j<8;j++)once(r,count,mode);}while(System.nanoTime()-start<100000000L);}
 for(int order=0;order<2;order++)for(int ci=0;ci<counts.length;ci++)for(int mi=0;mi<3;mi++){
  int count=counts[order==0?ci:counts.length-1-ci],mode=order==0?mi:2-mi;Thread.sleep(2);long start=System.nanoTime(),iterations=0,before=calls;do{for(int j=0;j<8;j++)once(r,count,mode);iterations+=8;}while(System.nanoTime()-start<500000000L&&iterations<1000000);long nanos=System.nanoTime()-start;
  report("{\"phase\":\"result\",\"order\":"+order+",\"mode\":\""+names[mode]+"\",\"glyphs\":"+count+",\"iterations\":"+iterations+",\"elapsedNanos\":"+nanos+",\"nativeCalls\":"+(calls-before)+",\"packetBuildIncluded\":"+(mode!=0)+",\"clearIncluded\":true}");
 }
 report("{\"phase\":\"finished\",\"nativeCalls\":"+calls+",\"exactWords\":"+compared+"}");
}
