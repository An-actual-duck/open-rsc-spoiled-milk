package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import com.openrsc.client.model.Sector;
import orsc.graphics.three.World;

/** Serializes the real World sampler across immutable scope and live tile changes. */
public final class HaloMatchReuseFixture {
 static final String SHA = String.join("", Collections.nCopies(64, "a"));
 static NativeLayeredTerrainSnapshot snap(String id,String space,int level,int cx,int cy,int height) {
  return new NativeLayeredTerrainSnapshot(id,"1.0.0",SHA,24,space,level,cx,cy,
   NativeLayeredTerrainSnapshot.UNIFORM_ENCODING,SHA,height,1,0,0,0,0,0);
 }
 static void set(Object o,String n,Object v)throws Exception {BrowserResidentInputsFixture.set(o,n,v);}
 static Object get(Object o,String n)throws Exception {return BrowserResidentInputsFixture.get(o,n);}
 public static void main(String[] args)throws Exception {
  BrowserResidentInputsFixture.definitions();
  World w=(World)get(BrowserResidentInputsFixture.client(),"world");
  DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])));
  Random random=new Random(748322);
  NativeLayeredTerrainSnapshot base=snap("fixture","main",-2,2,0,17);
  NativeLayeredTerrainSnapshot[] scopes={null,base,snap("fixture","main",-2,2,0,41),
   snap("other","main",-2,2,0,29),snap("fixture","other",-2,2,0,31),
   snap("fixture","main",-3,2,0,37),snap("fixture","main",-2,3,0,43),
   snap("fixture","main",-2,2,1,47)};
  Sector[] sectors=(Sector[])get(w,"sectors");
  for(int phase=0;phase<3;phase++) {
   for(int i=0;i<sectors.length;i++)sectors[i]=phase==2?null:Sector.blankLoaded();
   if(phase!=2)for(Sector sector:sectors)for(int x=0;x<48;x++)for(int z=0;z<48;z++)
    sector.getTile(x,z).groundElevation=random.nextInt(256);
   for(NativeLayeredTerrainSnapshot active:scopes)for(NativeLayeredTerrainSnapshot visual:scopes) {
    set(w,"nativeLayeredTerrainSnapshot",active);set(w,"symmetricNativeLayeredTerrainVisualSnapshot",visual);
    for(int n=0;n<1200;n++) {
     int x=n%5==0?random.nextInt():random.nextInt(40000)-10000;
     int z=n%5==0?random.nextInt():random.nextInt(40000)-10000;
     try {out.writeByte(1);out.writeInt(w.getPresentationTerrainElevation(x,z));}
     catch(RuntimeException ex) {out.writeByte(2);out.writeUTF(ex.getClass().getName());}
     try {out.writeByte(3);out.writeBoolean(w.isPresentationTerrainFaceTile(x>>7,z>>7));}
     catch(RuntimeException ex) {out.writeByte(4);out.writeUTF(ex.getClass().getName());}
    }
    if(phase!=2) {
     int before=w.getPresentationTerrainElevation(1280,1280);
     sectors[0].getTile(10,10).groundElevation=(before/3+1)%256;
     int after=w.getPresentationTerrainElevation(1280,1280);
     if(after==before)throw new AssertionError("Loaded editor height cached");
     out.writeInt(before);out.writeInt(after);
    }
   }
  }
  out.close();
  // The new memo can reuse only the immutable identity relation, not source heights.
  try {
   Method memo=World.class.getDeclaredMethod("presentationHaloMatches",NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class);
   Method old=World.class.getDeclaredMethod("matchesActiveHalo",NativeLayeredTerrainSnapshot.class,NativeLayeredTerrainSnapshot.class);
   memo.setAccessible(true);old.setAccessible(true);
   for(NativeLayeredTerrainSnapshot a:scopes)for(NativeLayeredTerrainSnapshot b:scopes) {
    Object expected=old.invoke(null,a,b);Object actual=memo.invoke(w,a,b);
    if(!expected.equals(actual))throw new AssertionError("Immutable scope relation changed");
    Object receipt=get(w,"presentationHaloMatch");
    boolean enabled=!"false".equalsIgnoreCase(System.getProperty("spoiledmilk.presentationHaloMatchReuse","true"));
    if((receipt!=null)!=enabled)throw new AssertionError("Reuse policy not active");
    if(!memo.invoke(w,a,b).equals(expected)||get(w,"presentationHaloMatch")!=receipt)
     throw new AssertionError("Repeated immutable relation not reused");
   }
   // Racing independent readers may replace the one-entry hint; every local result stays correct.
   List<Throwable> failures=Collections.synchronizedList(new ArrayList<Throwable>());
   Thread[] readers=new Thread[4];for(int t=0;t<readers.length;t++){final int id=t;
    readers[t]=new Thread(()->{try{for(int n=0;n<5000;n++){
     NativeLayeredTerrainSnapshot a=scopes[(n+id)%scopes.length],b=scopes[(n*3+id)%scopes.length];
     if(!old.invoke(null,a,b).equals(memo.invoke(w,a,b)))throw new AssertionError("Mixed relation publication");
    }}catch(Throwable e){failures.add(e);}});readers[t].start();}
   for(Thread reader:readers)reader.join();if(!failures.isEmpty())throw new AssertionError(failures.toString());
   memo.invoke(w,base,base);set(w,"symmetricNativeLayeredTerrainVisualSnapshot",base);
   BrowserResidentInputsFixture.call(w,"clearSymmetricRenderer3DWorldChunkFrame",
    new Class<?>[]{NativeLayeredTerrainSnapshot.class},new Object[]{null});
   if(get(w,"presentationHaloMatch")!=null||get(w,"symmetricNativeLayeredTerrainVisualSnapshot")!=null)
    throw new AssertionError("Obsolete scope retained halo receipt");
  } catch(NoSuchMethodException frozenReference) { /* Frozen production World is the output oracle. */ }
  System.out.println("PASS real World mutable heights, scope replacement, null/mismatch and extreme coordinates");
 }
}
