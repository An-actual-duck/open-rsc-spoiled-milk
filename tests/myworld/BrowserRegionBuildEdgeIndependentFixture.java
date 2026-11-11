package orsc.graphics.three;
import java.io.*;import java.lang.reflect.*;import java.util.*;
/** Candidate-only private helper edge proof. Literal public insert is expected, never hash mirroring. */
public final class BrowserRegionBuildEdgeIndependentFixture {
 static int checks;static Method method(Class<?>c,String n,Class<?>...t)throws Exception{Method m=c.getDeclaredMethod(n,t);m.setAccessible(true);return m;}static Field field(Class<?>c,String n)throws Exception{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}static void check(boolean x){checks++;if(!x)throw new AssertionError("edge "+checks);}
 static void index()throws Exception{
  Class<?>c=Class.forName("orsc.graphics.three.RSModel$ConstructionVertexIndex");Constructor<?>ctor=c.getDeclaredConstructor(int.class);ctor.setAccessible(true);Method slot=method(c,"slot",RSModel.class,int.class,int.class,int.class),insert=method(RSModel.class,"insertConstructionVertex",c,int.class,int.class,int.class),clear=method(c,"clear");Field slots=field(c,"slots");RSModel actual=new RSModel(0,0),literal=new RSModel(0,0);Object table=ctor.newInstance(128);int length=((int[])slots.get(table)).length;Random r=new Random(24841);List<int[]>collisions=new ArrayList<int[]>();
  // Find collisions by interrogating the actual EMPTY table, not reimplementing its hash.
  for(int attempts=0;collisions.size()<32&&attempts<100000;attempts++){int[]p={r.nextInt(),r.nextInt(),r.nextInt()};if(((Integer)slot.invoke(table,actual,p[0],p[1],p[2]))==length-1)collisions.add(p);}check(collisions.size()==32);
  for(int[]p:collisions){int want=literal.insertVertex(p[0],p[1],p[2]);check(((Integer)insert.invoke(actual,table,p[0],p[1],p[2]))==want);}for(int i=31;i>=0;i--){int[]p=collisions.get(i);check(((Integer)insert.invoke(actual,table,p[0],p[1],p[2]))==literal.insertVertex(p[0],p[1],p[2]));}check(actual.vertHead==32);clear.invoke(table);check(slots.get(table)==null);
  // Unexpected occupancy crosses bounded half-load and falls back to literal behavior.
  actual=new RSModel(0,0);literal=new RSModel(0,0);table=ctor.newInstance(1);for(int n=0;n<20;n++)check(((Integer)insert.invoke(actual,table,n,Integer.MIN_VALUE+n,Integer.MAX_VALUE-n))==literal.insertVertex(n,Integer.MIN_VALUE+n,Integer.MAX_VALUE-n));check(slots.get(table)==null);for(int n=0;n<20;n++)check(((Integer)insert.invoke(actual,table,n,Integer.MIN_VALUE+n,Integer.MAX_VALUE-n))==literal.insertVertex(n,Integer.MIN_VALUE+n,Integer.MAX_VALUE-n));
  for(int maximum:new int[]{-1,131073,Integer.MAX_VALUE}){table=ctor.newInstance(maximum);check(slots.get(table)==null);check(((Integer)insert.invoke(actual,table,-1,-2,-3))==literal.insertVertex(-1,-2,-3));}
  // A malformed XYZ plane retains the literal ca.I exception shape.
  RSModel a=new RSModel(1,0),b=new RSModel(1,0);a.vertX=null;b.vertX=null;table=ctor.newInstance(1);String e1="",e2="";try{a.insertVertex(1,2,3);}catch(RuntimeException e){e1=e.getClass().getName()+":"+e.getMessage();}try{insert.invoke(b,table,1,2,3);}catch(InvocationTargetException e){Throwable t=e.getCause();e2=t.getClass().getName()+":"+t.getMessage();}check(e1.equals(e2));
 }
 static void arrays()throws Exception{
  final int count=1048581;
  for(String suffix:new String[]{"IntValues","FloatValues"}){Class<?>c=Class.forName("orsc.graphics.three.BrowserRegionBuild$"+suffix);Constructor<?>ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object a=ctor.newInstance();boolean floats=suffix.equals("FloatValues");Method add=method(c,"add",floats?float.class:int.class),array=method(c,"array");Field values=field(c,"values"),overflow=field(c,"overflow");
   for(int n=0;n<count;n++){if(floats)add.invoke(a,Float.intBitsToFloat(n%13==0?0x7fc01234:n%13==1?0x80000000:n*0x9e3779b9));else add.invoke(a,n*0x9e3779b9);if(n==1048575){check(((Object)values.get(a))!=null);check(Array.getLength(values.get(a))==1048576);check(overflow.get(a)==null);}}
   check(values.get(a)==null);check(((List<?>)overflow.get(a)).size()==count);Object result=array.invoke(a);check(Array.getLength(result)==count);for(int n=0;n<count;n++){if(floats)check(Float.floatToRawIntBits(Array.getFloat(result,n))==(n%13==0?0x7fc01234:n%13==1?0x80000000:n*0x9e3779b9));else check(Array.getInt(result,n)==n*0x9e3779b9);}if(floats)Array.setFloat(result,0,77f);else Array.setInt(result,0,77);Object again=array.invoke(a);if(floats)check(Float.floatToRawIntBits(Array.getFloat(again,0))==0x7fc01234);else check(Array.getInt(again,0)==0);
  }
 }
 public static void main(String[]args)throws Exception{new FileOutputStream(args[0]).close();if(Boolean.getBoolean("fixture.testLimb")){index();arrays();}System.out.println("PASS helper-edge-checks="+checks+" collision-wrap/firstmatch/growth/cap/fallback/rawfloatcopy; no GPU/FPS");}
}
