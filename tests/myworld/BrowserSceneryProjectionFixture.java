package orsc.graphics.three;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import orsc.MiscFunctions;

/** Real Scene frozen differential and same-frame fresh-coordinate/interval controls. */
public final class BrowserSceneryProjectionFixture {
    static int checks, deferred;
    static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static Method optional(String name,Class<?>... types)throws Exception{
        try {Method m=RSModel.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
        catch(NoSuchMethodException old){return null;}
    }
    static final Class<?>[] SIGNATURE={int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class,int.class};
    static Method fast,complete;
    static RSModel model(int[] xyz){
        RSModel m=new RSModel(xyz.length/3,1);m.vertHead=xyz.length/3;m.m_Yb=2;
        for(int v=0;v<m.vertHead;v++){m.vertX[v]=xyz[v*3];m.vertY[v]=xyz[v*3+1];m.vertZ[v]=xyz[v*3+2];}
        return m;
    }
    static void rotate(RSModel m,int[] a){m.rotate1024(a[0],a[1],a[2],(byte)-122,a[3],a[4],a[5],a[6],a[7]);}
    static boolean split(RSModel m,int[] a)throws Exception{
        if(fast==null){rotate(m,a);return false;}
        try{return (Boolean)fast.invoke(m,a[0],a[1],a[2],a[3],a[4],a[5],a[6],a[7],a[8],a[9],a[10]);}
        catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}
    }
    static String failure(RSModel m,int[] a,boolean useSplit)throws Exception{
        try {if(useSplit)split(m,a);else rotate(m,a);return "ok";}
        catch(RuntimeException e){return e.getClass().getName()+":"+e.getMessage();}
    }
    static void ints(MessageDigest digest,int[] values){ByteBuffer b=ByteBuffer.allocate(values.length*4);for(int value:values)b.putInt(value);digest.update(b.array());}
    static void direct(boolean enabled)throws Exception{
        fast=enabled?optional("rotate1024ForBrowserPick",SIGNATURE):null;
        complete=optional("completeBrowserProjection",int.class,int.class);
        Random random=new Random(32581751);MessageDigest digest=MessageDigest.getInstance("SHA-256");
        int[] extremes={Integer.MIN_VALUE,Integer.MIN_VALUE+1,-1000000000,-1,0,1,1000000000,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
        MiscFunctions.frustumMinX=MiscFunctions.frustumMaxX=MiscFunctions.frustumMinY=MiscFunctions.frustumMaxY=MiscFunctions.frustumNearZ=MiscFunctions.frustumFarZ=0;
        for(int trial=0;trial<20000;trial++){
            int[] xyz=new int[48];for(int n=0;n<xyz.length;n++)xyz[n]=trial%7==0?extremes[random.nextInt(extremes.length)]:random.nextInt(20000)-10000;
            if(trial%4==0)for(int v=0;v<16;v++)xyz[v*3+2]=10000+random.nextInt(10000);
            RSModel reference=model(xyz),candidate=model(xyz);
            int scale=new int[]{1,2,3,14,1000,Integer.MAX_VALUE}[trial%6];reference.setPickBoundsScale(scale);candidate.setPickBoundsScale(scale);
            int[] a={trial%7==0?random.nextInt():0,trial%9==0?random.nextInt():9,trial%7==0?random.nextInt():0,trial%7==0?random.nextInt():0,
                trial%4==0?0:random.nextInt(1024),trial%4==0?0:random.nextInt(1024),trial%4==0?0:random.nextInt(1024),5,
                trial%7==0?random.nextInt():192,trial%3==0?Integer.MAX_VALUE:random.nextInt(1024)-512,trial%3==0?Integer.MIN_VALUE:random.nextInt(768)-384};
            rotate(reference,a);boolean omitted=split(candidate,a);
            require(reference.m_dc==candidate.m_dc,"Fresh visibility changed");
            require(Arrays.equals(reference.vertXRot,candidate.vertXRot)&&Arrays.equals(reference.vertYRot,candidate.vertYRot)&&Arrays.equals(reference.vertZRot,candidate.vertZRot),"Fresh XYZ changed at"+trial);
            if(omitted){deferred++;require(!ProjectedModelPickBounds.couldContainMouse(reference,a[8],a[9],a[10]),"Deferred a potentially picked model at"+trial);complete.invoke(candidate,a[1],a[7]);}
            require(Arrays.equals(reference.vertexParam6,candidate.vertexParam6)&&Arrays.equals(reference.vertexParam2,candidate.vertexParam2),"Projection changed at"+trial);
            ints(digest,candidate.vertXRot);ints(digest,candidate.vertYRot);ints(digest,candidate.vertZRot);ints(digest,candidate.vertexParam6);ints(digest,candidate.vertexParam2);
        }
        // Bad arrays must enter unchanged original before partial transforms/projection.
        for(int mutation=0;mutation<12;mutation++){
            RSModel a=model(new int[48]),b=model(new int[48]);
            for(RSModel m:new RSModel[]{a,b})switch(mutation){
                case 0:m.vertexParam6=null;break;case 1:m.vertexParam2=new int[1];break;
                case 2:m.vertXRot=new int[0];break;case 3:m.vertYRot=null;break;case 4:m.vertZRot=new int[1];break;
                case 5:m.vertXTransform=null;break;case 6:m.vertYTransform=new int[1];break;case 7:m.vertZTransform=null;break;
                case 8:m.vertX=null;break;case 9:m.vertY=new int[0];break;case 10:m.vertZ=null;break;case 11:m.vertHead=-1;break;
            }
            int[] args={0,9,0,0,0,0,0,5,192,1000,1000};
            require(failure(a,args,false).equals(failure(b,args,true)),"Malformed exception changed:"+mutation);
        }
        if(enabled&&fast!=null)require(deferred>100,"Vacuous split control");
        StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format("%02x",value&255));
        System.out.println("DIRECT_CHECKS="+checks+" DEFERRED="+deferred);System.out.println(hex);
    }
    static void scene(String requested)throws Exception{
        if(requested.equals("default"))System.clearProperty("spoiledmilk.browserSceneryProjectionWork");
        else System.setProperty("spoiledmilk.browserSceneryProjectionWork",requested);
        // The unchanged real Scene fixture mutates geometry, topology, material, camera,
        // picking scales/points, ownership and forced fallback over576frames, with actors
        // and an interaction-only proxy. Both source versions use these SAME fixtures.
        BrowserModelPickBoundsFixture.main(new String[]{"current","true"});
        // Actual policy receipt is eligibility, never a claim every model deferred.
        try {
            Method getter=Scene.class.getMethod("isBrowserSceneryProjectionWorkEnabled");
            orsc.BrowserFrameSinkRegistry.install(null);
            BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(true);
            require(!(Boolean)getter.invoke(view.s),"Native launch enabled split");
            orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
            require((Boolean)getter.invoke(view.s)==requested.equals("true"),"Requested/fullGPU receipt differs");
            orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(true));
            require(!(Boolean)getter.invoke(view.s),"CPU-depth consumer enabled split");
            orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
            require(!(Boolean)getter.invoke(new BrowserWallRoofOwnershipFixture.View(false).s),"Mixed ownership enabled split");
            System.setProperty("spoiledmilk.browserModelPickBounds","false");
            require(!(Boolean)getter.invoke(new BrowserWallRoofOwnershipFixture.View(true).s),"Disabled model bounds enabled split");
            orsc.BrowserFrameSinkRegistry.install(null);
        } catch(NoSuchMethodException frozenAuthority) { /* Feature absent in frozen source. */ }
        try{Field f=RSModel.class.getDeclaredField("TEST_PROJECTED_VERTICES");System.out.println("PROJECTED_VERTICES="+f.getLong(null));
            f=RSModel.class.getDeclaredField("TEST_DEFERRED_VERTICES");System.out.println("DEFERRED_VERTICES="+f.getLong(null));}
        catch(NoSuchFieldException noCounters){/* Isolated source probes only. */}
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("direct")){direct(Boolean.parseBoolean(args[1]));return;}
        scene(args[1]);
    }
}
