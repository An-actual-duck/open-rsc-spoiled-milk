package orsc;
import com.openrsc.client.model.Sprite;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.Renderer2DFrame.SpriteCommand;
import orsc.graphics.RendererSpriteTransform;
import java.io.*;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.*;

/** Executes the literal real cache in isolated frozen/current JVMs. */
public final class BrowserSpriteKeyLimbsFixture {
 static int checks,limbCases,wideCases,cacheCalls;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static SpriteCommand command(Sprite s,int x,int y,int top,int bottom,Random r){
  return new SpriteCommand(s,x,y,1+r.nextInt(15),1+r.nextInt(12),0,0,s.getWidth(),s.getHeight(),r.nextInt(),r.nextInt(),r.nextInt(),r.nextInt(),r.nextInt(),RendererSpriteTransform.IDENTITY,top,bottom,r.nextBoolean(),r.nextBoolean(),r.nextInt(),r.nextInt(),r.nextInt(),Renderer2DFrame.Phase.SCENE,r.nextInt());
 }
 static long[] metadata(Object layer)throws Exception{
  long[] old=(long[])get(layer,"metadata");if(old!=null)return old;
  int[] a=(int[])get(layer,"metadataLimbs");check(a.length==23,"Unexpected limb schema");limbCases++;
  long[] out=new long[19];int j=0;
  for(int i=0;i<19;i++)if(i==0||i==1||i==13||i==14){out[i]=((long)a[j]<<32)|(a[j+1]&0xffffffffL);j+=2;}else out[i]=a[j++];
  return out;
 }
 static void layers(DataOutputStream out)throws Exception{
  Class<?> type=Class.forName("orsc.BrowserWorldSpriteCache$Layer");Constructor<?> ctor=type.getDeclaredConstructor(SpriteCommand.class,int.class,int.class);ctor.setAccessible(true);
  Constructor<?> copy=type.getDeclaredConstructor(type);copy.setAccessible(true);Method same=type.getDeclaredMethod("same",type);same.setAccessible(true);
  Random r=new Random(914779);Sprite s=new Sprite(new int[]{0x123456,0,0x778899,1},2,2);
  int[] edge={Integer.MIN_VALUE,Integer.MIN_VALUE+1,-65537,-65536,-65535,-32769,-1,0,1,32767,65535,65536,65537,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
  for(int n=0;n<24000;n++){
   int x=n<edge.length*edge.length?edge[n/edge.length]:r.nextInt(),y=n<edge.length*edge.length?edge[n%edge.length]:r.nextInt();
   int cx=n<edge.length*edge.length?edge[n%edge.length]:r.nextInt(),cy=n<edge.length*edge.length?edge[n/edge.length]:r.nextInt();
   int top=n<edge.length*edge.length?edge[(n*7)%edge.length]:r.nextInt(),bottom=n<edge.length*edge.length?edge[(n*11)%edge.length]:r.nextInt();
   SpriteCommand c=command(s,cx,cy,top,bottom,r);Object layer=ctor.newInstance(c,x,y);long[] actual=metadata(layer);
   BigInteger bx=BigInteger.valueOf(x),by=BigInteger.valueOf(y),shift=bx.shiftLeft(16);
   BigInteger[] expected={BigInteger.valueOf(c.getX()).subtract(bx),BigInteger.valueOf(c.getY()).subtract(by),BigInteger.valueOf(c.getTopX16()).subtract(shift),BigInteger.valueOf(c.getBottomX16()).subtract(shift)};
   int[] at={0,1,13,14};for(int i=0;i<4;i++){check(BigInteger.valueOf(actual[at[i]]).equals(expected[i]),"Wide limb arithmetic changed");wideCases++;}
   int pixelHash=Arrays.hashCode(s.getPixels());check((Integer)get(layer,"hash")==31*(31*Arrays.hashCode(actual)+c.getTransform().hashCode())+pixelHash,"Original long hash formula changed");
   Object owned=copy.newInstance(layer);check((Boolean)same.invoke(layer,owned),"Owned key clone differs");check(get(layer,"pixels")!=get(owned,"pixels"),"Owned clone aliases mutable source");
   out.writeInt((Integer)get(layer,"hash"));for(long v:actual)out.writeLong(v);
   // Full source comparison must defeat a deterministic polynomial pixel collision.
   if(n%997==0){int keep=s.getPixels()[0];s.getPixels()[0]^=1;check(!(Boolean)same.invoke(layer,owned),"Mutable source change hidden");s.getPixels()[0]=keep;}
  }
 }
 static SpriteCommand scene(Sprite s,int x,int y,int w,int h,int skew,boolean mirror,boolean ordered,int alpha){
  return new SpriteCommand(s,x,y,w,h,0,0,s.getWidth(),s.getHeight(),0,0,(s.getWidth()<<16)/w,(s.getHeight()<<16)/h,alpha,RendererSpriteTransform.IDENTITY,(x<<16)+skew,x<<16,mirror,ordered,17,29,31,Renderer2DFrame.Phase.SCENE,37);
 }
 static List<SpriteCommand> translate(List<SpriteCommand> a,int dx,int dy){List<SpriteCommand>b=new ArrayList<SpriteCommand>();for(SpriteCommand c:a){if(c==null){b.add(null);continue;}b.add(new SpriteCommand(c.getSprite(),c.getX()+dx,c.getY()+dy,c.getWidth(),c.getHeight(),c.getSourceX(),c.getSourceY(),c.getSourceWidth(),c.getSourceHeight(),c.getSourceStartX16(),c.getSourceStartY16(),c.getSourceScaleX16(),c.getSourceScaleY16(),c.getAlpha(),c.getTransform(),c.getTopX16()+(dx<<16),c.getBottomX16()+(dx<<16),c.isMirrorX(),c.requiresOrderedReplay(),999,999,999,Renderer2DFrame.Phase.UNKNOWN,999));}return b;}
 static void record(DataOutputStream out,BrowserWorldSpriteCache cache,List<SpriteCommand> commands,OpenGLPixelWorkspace scratch)throws Exception{
  cacheCalls++;String failure="";BrowserWorldSpriteCache.Result v=null;try{v=cache.compose(commands,scratch);}catch(RuntimeException e){failure=e.getClass().getName()+":"+e.getMessage();}out.writeUTF(failure);out.writeBoolean(v!=null);
  if(v!=null){out.writeInt(v.x);out.writeInt(v.y);out.writeInt(v.width);out.writeInt(v.height);out.writeInt(v.id);out.writeInt(v.rgba.length);out.write(v.rgba);}
  out.writeLong(cache.getHits());out.writeLong(cache.getMisses());out.writeLong(cache.getCacheBytes());out.writeInt(cache.getEntryCount());out.writeInt(cache.getEpoch());
  for(Object entry:(List<?>)get(cache,"entries")){out.writeInt((Integer)get(entry,"id"));out.writeLong((Long)get(entry,"cost"));out.writeInt((Integer)get(get(entry,"key"),"hash"));}
 }
 static void caches(DataOutputStream out)throws Exception{
  Random r=new Random(78141);Sprite[] sprites=new Sprite[12];for(int i=0;i<sprites.length;i++){int[]p=new int[12];for(int j=0;j<p.length;j++)p[j]=r.nextInt(0xffffff);sprites[i]=new Sprite(p,3,4);}
  OpenGLPixelWorkspace scratch=new OpenGLPixelWorkspace();
  for(long budget:new long[]{0,1500,8192,16L*1024*1024}){
   BrowserWorldSpriteCache cache=new BrowserWorldSpriteCache(budget,11);List<SpriteCommand> prev=null;List<BrowserWorldSpriteCache.Result> held=new ArrayList<BrowserWorldSpriteCache.Result>();List<byte[]> copies=new ArrayList<byte[]>();
   for(int n=0;n<900;n++){
    List<SpriteCommand> commands;
    if(prev!=null&&n%3!=0)commands=n%3==1?prev:translate(prev,5,-3);
    else{commands=new ArrayList<SpriteCommand>();for(int layer=0;layer<1+n%3;layer++){Sprite s=sprites[r.nextInt(sprites.length)];commands.add(scene(s,r.nextInt(6)-3,r.nextInt(6)-3,2+r.nextInt(12),2+r.nextInt(10),r.nextInt(100001)-50000,r.nextBoolean(),r.nextBoolean(),r.nextInt(256)));}if(n%17==0)commands.add(null);}
    if(n%13==0){Sprite s=sprites[n%sprites.length];s.getPixels()[n%s.getPixels().length]^=0x1234;}
    if(n%97==0)cache.clear();record(out,cache,commands,scratch);prev=commands;
    BrowserWorldSpriteCache.Result result=cache.compose(commands,scratch);if(result!=null&&held.size()<16){held.add(result);copies.add(result.rgba.clone());}for(int i=0;i<held.size();i++)check(Arrays.equals(copies.get(i),held.get(i).rgba),"Evicted held RGBA mutated");
   }
   // Equal polynomial pixel hashes, unequal content: neither metadata nor RGBA may alias.
   Sprite a=new Sprite(new int[]{0,31},2,1),b=new Sprite(new int[]{1,0},2,1);check(Arrays.hashCode(a.getPixels())==Arrays.hashCode(b.getPixels()),"Collision fixture invalid");
   List<SpriteCommand> ca=Collections.singletonList(scene(a,0,0,4,2,0,false,false,255)),cb=Collections.singletonList(scene(b,0,0,4,2,0,false,false,255));
   record(out,cache,ca,scratch);long misses=cache.getMisses();record(out,cache,cb,scratch);check(cache.getMisses()==misses+1,"Unequal colliding pixels became hit");record(out,cache,ca,scratch);
   for(List<SpriteCommand> bad:Arrays.asList(Collections.<SpriteCommand>emptyList(),Collections.<SpriteCommand>singletonList(null),Collections.singletonList(new SpriteCommand(a,0,Integer.MAX_VALUE,2,2,255)),Collections.nCopies(257,ca.get(0))))record(out,cache,bad,scratch);
  }
 }
 public static void main(String[]args)throws Exception{
  try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){layers(out);caches(out);}
  boolean expect=Boolean.getBoolean("test.expectLimbs");check((limbCases>0)==expect,"Selected representation not exercised");
  System.out.println("PASS checks="+checks+" layerCases=24000 wideBigInteger="+wideCases+" limbCases="+limbCases+" cacheCalls="+cacheCalls);
 }
}
