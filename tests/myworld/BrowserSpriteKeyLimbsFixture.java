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
 static int checks,limbCases,wideCases,cacheCalls,dimensionCases;
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
 static final Class<?> LAYER;
 static final Constructor<?> LAYER_NEW,LAYER_COPY;
 static final Method LAYER_SAME;
 static final Object COUNTERS;
 static {
  try {
   LAYER=Class.forName("orsc.BrowserWorldSpriteCache$Layer");
   Class<?> counts=null;Object counter=null;
   try{counts=Class.forName("orsc.BrowserWorldSpriteCache$Counters");Constructor<?> c=counts.getDeclaredConstructor();c.setAccessible(true);counter=c.newInstance();}catch(ClassNotFoundException oldBaseline){}
   COUNTERS=counter;
   LAYER_NEW=counts==null?LAYER.getDeclaredConstructor(SpriteCommand.class,int.class,int.class):LAYER.getDeclaredConstructor(SpriteCommand.class,int.class,int.class,counts);
   LAYER_COPY=counts==null?LAYER.getDeclaredConstructor(LAYER):LAYER.getDeclaredConstructor(LAYER,counts);
   LAYER_SAME=counts==null?LAYER.getDeclaredMethod("same",LAYER):LAYER.getDeclaredMethod("same",LAYER,counts);
   LAYER_NEW.setAccessible(true);LAYER_COPY.setAccessible(true);LAYER_SAME.setAccessible(true);
  }catch(Exception error){throw new ExceptionInInitializerError(error);}
 }
 static Object layer(SpriteCommand c,int x,int y)throws Exception{return COUNTERS==null?LAYER_NEW.newInstance(c,x,y):LAYER_NEW.newInstance(c,x,y,COUNTERS);}
 static Object copy(Object layer)throws Exception{return COUNTERS==null?LAYER_COPY.newInstance(layer):LAYER_COPY.newInstance(layer,COUNTERS);}
 static boolean same(Object a,Object b)throws Exception{return (Boolean)(COUNTERS==null?LAYER_SAME.invoke(a,b):LAYER_SAME.invoke(a,b,COUNTERS));}
 static void layers(DataOutputStream out)throws Exception{
  Random r=new Random(914779);Sprite s=new Sprite(new int[]{0x123456,0,0x778899,1},2,2);
  int[] edge={Integer.MIN_VALUE,Integer.MIN_VALUE+1,-65537,-65536,-65535,-32769,-1,0,1,32767,65535,65536,65537,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
  for(int n=0;n<24000;n++){
   int x=n<edge.length*edge.length?edge[n/edge.length]:r.nextInt(),y=n<edge.length*edge.length?edge[n%edge.length]:r.nextInt();
   int cx=n<edge.length*edge.length?edge[n%edge.length]:r.nextInt(),cy=n<edge.length*edge.length?edge[n/edge.length]:r.nextInt();
   int top=n<edge.length*edge.length?edge[(n*7)%edge.length]:r.nextInt(),bottom=n<edge.length*edge.length?edge[(n*11)%edge.length]:r.nextInt();
   SpriteCommand c=command(s,cx,cy,top,bottom,r);Object layer=layer(c,x,y);long[] actual=metadata(layer);
   BigInteger bx=BigInteger.valueOf(x),by=BigInteger.valueOf(y),shift=bx.shiftLeft(16);
   BigInteger[] expected={BigInteger.valueOf(c.getX()).subtract(bx),BigInteger.valueOf(c.getY()).subtract(by),BigInteger.valueOf(c.getTopX16()).subtract(shift),BigInteger.valueOf(c.getBottomX16()).subtract(shift)};
   int[] at={0,1,13,14};for(int i=0;i<4;i++){check(BigInteger.valueOf(actual[at[i]]).equals(expected[i]),"Wide limb arithmetic changed");wideCases++;}
   int pixelHash=Arrays.hashCode(s.getPixels());check((Integer)get(layer,"hash")==31*(31*Arrays.hashCode(actual)+c.getTransform().hashCode())+pixelHash,"Original long hash formula changed");
   Object owned=copy(layer);check(same(layer,owned),"Owned key clone differs");check(get(layer,"pixels")!=get(owned,"pixels"),"Owned clone aliases mutable source");
   out.writeInt((Integer)get(layer,"hash"));for(long v:actual)out.writeLong(v);
   // Full source comparison must defeat a deterministic polynomial pixel collision.
   if(n%997==0){int keep=s.getPixels()[0];s.getPixels()[0]^=1;check(!same(layer,owned),"Mutable source change hidden");s.getPixels()[0]=keep;}
  }
 }
 static void dimensions(DataOutputStream out)throws Exception{
  Field width=Sprite.class.getDeclaredField("width"),height=Sprite.class.getDeclaredField("height");width.setAccessible(true);height.setAccessible(true);
  int max=BrowserWorldSpriteCache.MAX_PIXELS;int[] edges={Integer.MIN_VALUE,-2,-1,0,1,2,511,512,1024,2047,2048,2049,max-1,max,max+1,Integer.MAX_VALUE};
  List<int[]> pairs=new ArrayList<int[]>();for(int w:edges)for(int h:edges)pairs.add(new int[]{w,h});
  pairs.add(new int[]{1,max});pairs.add(new int[]{max,1});pairs.add(new int[]{2,max/2});pairs.add(new int[]{max/2,2});pairs.add(new int[]{1024,2048});
  Random r=new Random(781925);for(int i=0;i<1000;i++)pairs.add(new int[]{r.nextInt(),r.nextInt()});
  for(int n=0;n<pairs.size();n++){
   int w=pairs.get(n)[0],h=pairs.get(n)[1];BigInteger product=BigInteger.valueOf(w).multiply(BigInteger.valueOf(h));boolean valid=w>0&&h>0&&product.compareTo(BigInteger.valueOf(max))<=0;
   Sprite sprite=new Sprite(new int[]{1},1,1);SpriteCommand c=new SpriteCommand(sprite,0,0,1,1,255);width.setInt(sprite,w);height.setInt(sprite,h);
   if(n>=256&&n<261&&valid)sprite.setPixels(new int[product.intValueExact()]);else if(n%5==0)sprite.setPixels(null);
   boolean complete=valid&&sprite.getPixels()!=null&&sprite.getPixels().length>=product.intValueExact();String error="";Object layer=null;
   try{layer=layer(c,0,0);}catch(InvocationTargetException e){Throwable t=e.getCause();error=t.getClass().getName()+":"+t.getMessage();}
   check((layer!=null)==complete,"Count guard acceptance differs from BigInteger");
   if(!complete)check(error.equals("java.lang.IllegalArgumentException:"+(valid?"Incomplete source pixels":"Unsupported source dimensions")),"Count exception/order changed: "+error);
   out.writeInt(w);out.writeInt(h);out.writeUTF(error);out.writeBoolean(layer!=null);if(layer!=null){check((Integer)get(layer,"pixelCount")==product.intValueExact(),"Count narrowed before exact validation");out.writeInt((Integer)get(layer,"pixelCount"));out.writeInt((Integer)get(layer,"hash"));for(long v:metadata(layer))out.writeLong(v);}
   dimensionCases++;
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
  try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){layers(out);dimensions(out);caches(out);}
  boolean expect=Boolean.getBoolean("test.expectLimbs");
  if(Boolean.getBoolean("test.expectNoProperty"))check(System.getProperty("spoiledmilk.browserSpriteKeyLimbs")==null,"Default cohort accidentally has override");
  try{Method getter=BrowserWorldSpriteCache.class.getDeclaredMethod("isKeyLimbsEnabled");getter.setAccessible(true);check((Boolean)getter.invoke(null)==expect,"Actual cache mode getter disagrees");
   Method bridge=Class.forName("orsc.BrowserWebGLBridge").getMethod("isSpriteKeyLimbsEnabled");check((Boolean)bridge.invoke(null)==expect,"Installed bridge mode getter disagrees");
  }catch(NoSuchMethodException frozen){check(!expect,"Candidate mode getter missing");}
  check((limbCases>0)==expect,"Selected representation not exercised");
  System.out.println("PASS checks="+checks+" layerCases=24000 wideBigInteger="+wideCases+" limbCases="+limbCases+" cacheCalls="+cacheCalls+" dimensionCases="+dimensionCases);
 }
}
