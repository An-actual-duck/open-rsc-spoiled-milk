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
    static int[] array(RSModel m,String name)throws Exception{Field f=RSModel.class.getDeclaredField(name);f.setAccessible(true);return (int[])f.get(m);}
    static void assign(RSModel m,String name,int[] value)throws Exception{Field f=RSModel.class.getDeclaredField(name);f.setAccessible(true);f.set(m,value);}
    static RSModel model(int[] xyz)throws Exception{
        RSModel m=new RSModel(xyz.length/3,1);m.vertHead=xyz.length/3;m.m_Yb=2;
        for(int v=0;v<m.vertHead;v++){m.vertX[v]=xyz[v*3];array(m,"vertY")[v]=xyz[v*3+1];m.vertZ[v]=xyz[v*3+2];}
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
        // Keep deferred arrays genuinely stale over successive moving frames, then
        // alternate mouse-near, near-plane and ordinary projection consumers.
        RSModel retainedA=model(new int[48]),retainedB=model(new int[48]);
        for(int frame=0;frame<512;frame++){
            for(int v=0;v<16;v++)for(RSModel m:new RSModel[]{retainedA,retainedB}){
                m.vertX[v]=frame*19+v*11-5000;array(m,"vertY")[v]=v*7-frame;
                m.vertZ[v]=frame%5==0?v-4:10000+frame*13+v*5;m.m_Yb=2;
            }
            int[] a={0,9,0,0,0,0,0,5,192,frame%2==0?Integer.MAX_VALUE:0,frame%2==0?Integer.MIN_VALUE:192};
            rotate(retainedA,a);boolean omitted=split(retainedB,a);
            require(Arrays.equals(retainedA.vertXRot,retainedB.vertXRot)&&Arrays.equals(retainedA.vertYRot,retainedB.vertYRot)&&Arrays.equals(retainedA.vertZRot,retainedB.vertZRot),"Retained-frame XYZ stale");
            if(omitted){deferred++;require(!ProjectedModelPickBounds.couldContainMouse(retainedA,a[8],a[9],a[10]),"Retained envelope excluded potential pick");}
            else require(Arrays.equals(retainedA.vertexParam6,retainedB.vertexParam6)&&Arrays.equals(retainedA.vertexParam2,retainedB.vertexParam2),"Deferred→needed projection stale");
            ints(digest,retainedB.vertXRot);ints(digest,retainedB.vertYRot);ints(digest,retainedB.vertZRot);
        }
        // Aliased reads/writes must retain original interleaving and final source
        // mutations, not only the same visible pixel values. Reflective setup keeps
        // frozen and candidate fixture source identical and covers private arrays.
        String[] outputs={"vertexParam6","vertexParam2","vertXRot","vertYRot","vertZRot"};
        String[] arrays={"vertexParam6","vertexParam2","vertXRot","vertYRot","vertZRot","vertX","vertY","vertZ","vertXTransform","vertYTransform","vertZTransform"};
        int[] seed=new int[48];for(int v=0;v<16;v++){seed[v*3]=v*13;seed[v*3+1]=v*17;seed[v*3+2]=10000+v*29;}
        for(int refresh:new int[]{0,1,2,3,-1,Integer.MIN_VALUE,Integer.MAX_VALUE})for(String output:outputs)for(String input:arrays){
            if(output.equals(input))continue;
            RSModel a=model(seed),b=model(seed);
            Field to=RSModel.class.getDeclaredField(output),from=RSModel.class.getDeclaredField(input);to.setAccessible(true);from.setAccessible(true);
            for(RSModel m:new RSModel[]{a,b}){to.set(m,from.get(m));m.m_Yb=refresh;}
            int[] args={0,9,0,0,0,0,0,5,192,Integer.MAX_VALUE,Integer.MIN_VALUE};
            require(failure(a,args,false).equals(failure(b,args,true)),"Alias error changed "+output+"/"+input);
            for(String name:arrays){Field field=RSModel.class.getDeclaredField(name);field.setAccessible(true);
                require(Arrays.equals((int[])field.get(a),(int[])field.get(b)),"Alias mutation changed "+output+"/"+input+"/"+name);ints(digest,(int[])field.get(b));}
        }
        // Also reject aliases into scalar face/material arrays or nested topology.
        for(String payload:new String[]{"faceTextureFront","faceIndexCount","facePickIndex","vertDiffuseLight","faceIndices","faceParam1"}){
            RSModel a=model(seed),b=model(seed);
            Field field=RSModel.class.getDeclaredField(payload);field.setAccessible(true);
            for(RSModel m:new RSModel[]{a,b})field.set(m,field.getType()==int[][].class?new int[][]{m.vertexParam6}:m.vertexParam6);
            int[] args={0,9,0,0,0,0,0,5,192,Integer.MAX_VALUE,Integer.MIN_VALUE};
            require(failure(a,args,false).equals(failure(b,args,true)),"Payload alias error changed");
            require(Arrays.equals(a.vertexParam6,b.vertexParam6),"Payload alias state changed "+payload);ints(digest,b.vertexParam6);
        }
        // Bad arrays must enter unchanged original before partial transforms/projection.
        for(int mutation=0;mutation<12;mutation++){
            RSModel a=model(new int[48]),b=model(new int[48]);
            for(RSModel m:new RSModel[]{a,b})switch(mutation){
                case 0:m.vertexParam6=null;break;case 1:m.vertexParam2=new int[1];break;
                case 2:m.vertXRot=new int[0];break;case 3:m.vertYRot=null;break;case 4:m.vertZRot=new int[1];break;
                case 5:assign(m,"vertXTransform",null);break;case 6:assign(m,"vertYTransform",new int[1]);break;case 7:assign(m,"vertZTransform",null);break;
                case 8:m.vertX=null;break;case 9:assign(m,"vertY",new int[0]);break;case 10:m.vertZ=null;break;case 11:m.vertHead=-1;break;
            }
            int[] args={0,9,0,0,0,0,0,5,192,1000,1000};
            require(failure(a,args,false).equals(failure(b,args,true)),"Malformed exception changed:"+mutation);
        }
        if(enabled&&fast!=null)require(deferred>100,"Vacuous split control");
        StringBuilder hex=new StringBuilder();for(byte value:digest.digest())hex.append(String.format("%02x",value&255));
        System.out.println("DIRECT_CHECKS="+checks+" DEFERRED="+deferred);System.out.println(hex);
    }
    static String witness()throws Exception {
        System.setProperty("spoiledmilk.browserModelPickBounds","true");
        System.setProperty("spoiledmilk.browserEarlyFaceCull","true");
        orsc.BrowserFrameSinkRegistry.install(null);orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
        BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(true);
        RSModel dense=BrowserModelPickBoundsFixture.dense();view.s.addModel(dense);
        boolean picks=false,anchors=false,faces=false,payload=false;
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(int distance:new int[]{20,100,600})for(int x:new int[]{0,256,511}){
            dense.setTranslate(x-256,-distance/3,x/2-128);
            BrowserWallRoofOwnershipFixture.Result value=view.draw(x,192,distance,768,0,-12345,false);
            picks|=!value.picks.isEmpty();anchors|=value.anchors.contains("101:")||value.anchors.contains("102:");
            faces|=!value.faces.isEmpty();payload|=!value.resident.isEmpty();
            for(String text:new String[]{value.picks,value.anchors,value.faces,value.resident})BrowserModelPickBoundsFixture.text(hash,text);
            ints(hash,value.pixels);
            // Scene has XYZ, not a level field; source mesh export explicitly covers
            // negative plane and shifted origin, including this mutable dense model.
            Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(-3,-17,19,-6000,12000,new RSModel[]{dense},1);
            for(int v=0;v<mesh.getVertexCount();v++){
                ints(hash,new int[]{mesh.getVertexCoord(v*3),mesh.getVertexCoord(v*3+1),mesh.getVertexCoord(v*3+2),Float.floatToIntBits(mesh.getVertexTextureU(v)),Float.floatToIntBits(mesh.getVertexTextureV(v)),mesh.getVertexLight(v)});
            }
            for(int n=0;n<mesh.getIndexCount();n++)ints(hash,new int[]{mesh.getIndex(n)});
            for(int n=0;n<mesh.getTriangleCount();n++)BrowserModelPickBoundsFixture.text(hash,mesh.getTriangleTexture(n)+":"+mesh.getTriangleFallbackColor(n)+":"+mesh.getTriangleModelKind(n));
        }
        orsc.BrowserFrameSinkRegistry.install(null);
        require(picks&&anchors&&faces&&payload,"Vacuous Scene pick/actor/capture/resident witness");
        StringBuilder hex=new StringBuilder();for(byte value:hash.digest())hex.append(String.format("%02x",value&255));return hex.toString();
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
            orsc.BrowserFrameSinkRegistry.install(null);orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
            require((Boolean)getter.invoke(view.s)==requested.equals("true"),"Requested/fullGPU receipt differs");
            orsc.BrowserFrameSinkRegistry.install(null);orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(true));
            require(!(Boolean)getter.invoke(view.s),"CPU-depth consumer enabled split");
            orsc.BrowserFrameSinkRegistry.install(null);orsc.BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
            require(!(Boolean)getter.invoke(new BrowserWallRoofOwnershipFixture.View(false).s),"Mixed ownership enabled split");
            System.setProperty("spoiledmilk.browserModelPickBounds","false");
            require(!(Boolean)getter.invoke(new BrowserWallRoofOwnershipFixture.View(true).s),"Disabled model bounds enabled split");
            orsc.BrowserFrameSinkRegistry.install(null);
        } catch(NoSuchMethodException frozenAuthority) { /* Feature absent in frozen source. */ }
        System.out.println("WITNESS_SHA="+witness());
        try{for(String name:new String[]{"TEST_PROJECTED_VERTICES","TEST_DEFERRED_VERTICES","TEST_PERSPECTIVE_DIVISIONS","TEST_BOUNDS_DIVISIONS"}){
            Field f=RSModel.class.getDeclaredField(name);System.out.println(name.substring(5)+"="+f.getLong(null));}}
        catch(NoSuchFieldException noCounters){/* Isolated source probes only. */}
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("direct")){direct(Boolean.parseBoolean(args[1]));return;}
        scene(args[1]);
    }
}
