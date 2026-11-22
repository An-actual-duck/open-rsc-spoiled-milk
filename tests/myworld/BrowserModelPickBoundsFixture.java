package orsc.graphics.three;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import orsc.BrowserFrameSinkRegistry;
import orsc.MiscFunctions;
import orsc.util.FastMath;

/** Frozen Scene differential plus direct conservative rejection/exception oracles. */
public final class BrowserModelPickBoundsFixture {
    static int checks,accepted,rejected;
    static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final Field MOUSE=field("m_K"),MX=field("m_j"),MY=field("m_Wb"),OFFSET=field("m_Nb");
    static Field field(String name){try{Field f=Scene.class.getDeclaredField(name);f.setAccessible(true);return f;}catch(Exception e){throw new AssertionError(e);}}
    static void text(MessageDigest hash,String value)throws Exception{hash.update(value.getBytes("UTF-8"));hash.update((byte)0);}
    static boolean active(Scene s)throws Exception{try{return (Boolean)Scene.class.getMethod("isBrowserModelPickBoundsEnabled").invoke(s);}catch(NoSuchMethodException old){return false;}}
    static String cause(Callable action){try{action.call();return "ok";}catch(Throwable e){while(e instanceof InvocationTargetException)e=((InvocationTargetException)e).getCause();return e.getClass().getName()+":"+e.getMessage();}}
    interface Callable{void call()throws Exception;}
    static void math()throws Exception{
        Scene scene=new Scene(new orsc.graphics.two.MudClientGraphics(64,64,1),4,16,4);MOUSE.setBoolean(scene,true);
        Method old=Scene.class.getDeclaredMethod("projectedFaceCouldContainMouse",RSModel.class,int[].class,int.class);old.setAccessible(true);
        Method fast=Scene.class.getDeclaredMethod("shouldSkipBoundedResidentProjectedFace",RSModel.class,int.class,int[].class,int.class,boolean.class,boolean.class,boolean.class,boolean.class);fast.setAccessible(true);
        Random random=new Random(810344);int[] extremes={Integer.MIN_VALUE,Integer.MIN_VALUE+1,-1000000000,-1,0,1,1000000000,Integer.MAX_VALUE-1,Integer.MAX_VALUE};
        for(int trial=0;trial<200000;trial++){
            int count=1+random.nextInt(12);RSModel m=new RSModel(count,1);m.vertHead=count;m.vertexParam6=new int[count+2];m.vertexParam2=new int[count+2];
            for(int v=0;v<count;v++){m.vertexParam6[v]=trial%4==0?random.nextInt(5000)-2500:extremes[random.nextInt(extremes.length)];m.vertexParam2[v]=trial%4==0?random.nextInt(5000)-2500:random.nextInt();}
            int[] face=new int[1+random.nextInt(8)];for(int n=0;n<face.length;n++)face[n]=random.nextInt(count);
            int scale=new int[]{1,2,3,14,1000,Integer.MAX_VALUE}[trial%6];m.setPickBoundsScale(scale);
            int offset=trial%3==0?random.nextInt():192,mx=trial%4==0?random.nextInt(5000)-2500:extremes[random.nextInt(extremes.length)],my=trial%4==0?random.nextInt(5000)-2500:random.nextInt();
            MX.setInt(scene,mx);MY.setInt(scene,my);OFFSET.setInt(scene,offset);
            boolean original=(Boolean)old.invoke(scene,m,face,face.length),model=ProjectedModelPickBounds.couldContainMouse(m,offset,mx,my);
            require(!original||model,"Model excluded an original face pick at trial"+trial+" scale"+scale);require(ProjectedModelPickBounds.hasOnlyRotatedVertexIndices(m,face,face.length),"Valid face guard rejected");if(original)accepted++;if(!model)rejected++;
        }
        require(accepted>1000&&rejected>1000,"Vacuous conservative math corpus");
        final RSModel m=new RSModel(4,1);m.vertHead=2;m.vertexParam6=new int[]{1000,1100,-10,10};m.vertexParam2=new int[]{1000,1100,-10,10};m.m_db=false;m.m_zb=new byte[]{0};m.facePickIndex=new int[]{1};MX.setInt(scene,0);MY.setInt(scene,0);OFFSET.setInt(scene,0);
        require(!ProjectedModelPickBounds.couldContainMouse(m,0,0,0),"Fixture never selected model rejection");
        final int[][] malformed={null,new int[0],new int[]{-1},new int[]{4},new int[]{2,3},new int[]{0}};final int[] counts={1,1,1,1,2,2};
        for(int n=0;n<malformed.length;n++){final int[] face=malformed[n];final int count=counts[n];require(!ProjectedModelPickBounds.hasOnlyRotatedVertexIndices(m,face,count),"Malformed/spare-capacity face bypassed original");
            final boolean[] oldPick={false};String a=cause(()->oldPick[0]=(Boolean)old.invoke(scene,m,face,count));String b=cause(()->{boolean skipped=(Boolean)fast.invoke(scene,m,0,face,count,true,false,true,true);if(a.equals("ok"))require(skipped==!oldPick[0],"Fallback changed spare-capacity/null/empty face semantics");});require(a.equals(b),"Fallback exception changed:"+a+" != "+b);
        }
        m.vertexParam6=null;require(ProjectedModelPickBounds.couldContainMouse(m,0,0,0),"Missing projected array was guessed");m.vertexParam6=new int[1];require(ProjectedModelPickBounds.couldContainMouse(m,0,0,0),"Short projected array was guessed");
        System.out.println("PASS conservative model bounds checks="+checks+" acceptedFaces="+accepted+" rejectedModels="+rejected+"; int-wrap/huge scales/malformed/spare-capacity fallback; no timing claim");
        cameraBoundsMath(scene, old);
    }

    private static void cameraBoundsMath(Scene scene, Method literalPick) throws Exception {
        int[] trig = referenceTrig();
        wideFrustum();
        ProjectedModelPickBounds.CameraBounds bounds = new ProjectedModelPickBounds.CameraBounds();
        Random random = new Random(473231);
        int misses = 0, hits = 0;
        MessageDigest decisions = MessageDigest.getInstance("SHA-256");
        for (int trial = 0; trial < 6000; trial++) {
            RSModel model = new RSModel(6, 2);
            int[][] faces = new int[2][3];
            for (int f = 0; f < 2; f++) {
                for (int v = 0; v < 3; v++)
                    faces[f][v] = model.insertVertex(random.nextInt(256)-128, random.nextInt(256)-128, random.nextInt(256)-128);
                model.insertFace(3, faces[f], -12345, -12345, false);
            }
            model.setPickBoundsScale(new int[]{1, 2, 14, Integer.MAX_VALUE}[trial % 4]);
            int x = random.nextInt(2000) - 1000, y = random.nextInt(2000) - 1000, z = random.nextInt(2000) - 1000;
            int pitch = random.nextInt(1024), yaw = random.nextInt(1024), roll = trial % 3 == 0 ? 0 : random.nextInt(1024);
            int shift = trial % 7 == 0 ? 29 : 9, near = 1 + random.nextInt(400);
            model.rotate1024(y, shift, x, (byte) -122, z, yaw, roll, pitch, near);
            require(model.m_dc, "Wide frustum unexpectedly rejected the test model");
            int viewportY = trial % 9 == 0 ? random.nextInt() : 192;
            int mx = trial % 2 == 0 ? model.vertexParam6[0] : random.nextInt(3000) - 1500;
            int my = trial % 2 == 0 ? viewportY + model.vertexParam2[0] : random.nextInt(3000) - 1500;
            MX.setInt(scene, mx);
            MY.setInt(scene, my);
            OFFSET.setInt(scene, viewportY);
            boolean hit = false;
            for (int[] face : faces) hit |= (Boolean) literalPick.invoke(scene, model, face, face.length);
            bounds.set(x, y, z, roll, yaw, pitch, shift, near, viewportY, mx, my, trig);
            boolean miss = bounds.missesMouse(model);
            decisions.update((byte) (miss ? 1 : 0));
            require(!hit || !miss, "Camera bounds rejected a literal face at trial " + trial);
            if (hit) hits++;
            if (miss) misses++;
        }
        require(hits > 500 && misses > 500, "Vacuous camera bounds corpus");
        System.out.println("PASS camera bounds 6000 fixed-point projection cases, literal hits=" + hits + " proven misses=" + misses);
        System.out.println("Camera rejection digest=" + hex(decisions.digest()));
    }

    private static void wideFrustum() {
        MiscFunctions.frustumMinX = MiscFunctions.frustumMinY = MiscFunctions.frustumNearZ = Integer.MAX_VALUE;
        MiscFunctions.frustumMaxX = MiscFunctions.frustumMaxY = MiscFunctions.frustumFarZ = Integer.MIN_VALUE;
    }

    private static int[] referenceTrig() {
        int[] trig = FastMath.trigTable1024 = new int[2048];
        for (int i = 0; i < 1024; i++) {
            double angle = i * Math.PI / 512;
            trig[i] = (int) (Math.sin(angle) * 32768);
            trig[i + 1024] = (int) (Math.cos(angle) * 32768);
        }
        return trig;
    }

    private static void cachedRebaseParity() throws Exception {
        referenceTrig();
        List<Field> arrays = new ArrayList<>();
        for (String name : new String[]{"vertXTransform", "vertYTransform", "vertZTransform", "vertexParam6",
                "vertexParam2", "vertXRot", "vertYRot", "vertZRot", "faceMinX", "faceMaxX", "faceMinY", "faceMaxY",
                "faceMinZ", "faceMaxZ", "faceNormX", "faceNormY", "faceNormZ", "faceDiffuseLight",
                "vertDiffuseLight", "scenePolyNormalShift"}) {
            Field field = RSModel.class.getDeclaredField(name);
            field.setAccessible(true);
            arrays.add(field);
        }
        List<Field> bounds = new ArrayList<>();
        for (String name : new String[]{"minX", "maxX", "minY", "maxY", "minZ", "maxZ", "maxFaceDimension",
                "appliedTransform", "translateX", "translateY", "translateZ"}) {
            Field field = RSModel.class.getDeclaredField(name);
            field.setAccessible(true);
            bounds.add(field);
        }
        wideFrustum();
        int cached = 0, literal = 0;
        for (int trial = 0; trial < 64; trial++) {
            RSModel reference = dense(), candidate = dense();
            int x = trial % 8 == 0 ? 999900 : trial * 19 - 600;
            for (RSModel model : new RSModel[]{reference, candidate}) {
                model.setTranslate(x, trial * 3, -x / 2);
                model.setRot256(trial & 31, 31 - (trial & 31), trial * 3);
                model.setDiffuseLightAndColor(-50, -10, -50, 48, 48, trial % 2 == 0, -74);
                model.rotate1024(0, 9, 0, (byte) -122, -1024, 113, 189, 57, 8);
            }
            for (int step = 0; step < 5; step++) {
                reference.scenePolyNormalShift[0] = candidate.scenePolyNormalShift[0] = 3;
                int dx = step % 2 == 0 ? 6144 : -6144, dz = (step - 2) * 128;
                reference.translate2(dx, 0, dz);
                candidate.rebaseWorldOrigin(dx, dz);
                if (candidate.m_Yb == 0) cached++; else literal++;
                reference.rotate1024(19, 9, -43, (byte) -122, -1024, 113, 189, 57, 8);
                candidate.rotate1024(19, 9, -43, (byte) -122, -1024, 113, 189, 57, 8);
                require(reference.m_dc == candidate.m_dc, "Rebase changed visibility");
                for (Field field : bounds) require(field.getInt(reference) == field.getInt(candidate), "Rebase changed " + field.getName());
                for (Field field : arrays) require(Arrays.equals((int[]) field.get(reference), (int[]) field.get(candidate)),
                    "Rebase changed " + field.getName());
            }
        }
        require(cached > 100 && literal > 0, "Rebase corpus did not exercise both paths");
        System.out.println("PASS " + cached + " cached origin shifts and " + literal + " literal fallbacks");
    }
    static RSModel dense(){
        int side=12;RSModel m=new RSModel((side+1)*(side+1),side*side);for(int z=0;z<=side;z++)for(int x=0;x<=side;x++)m.insertVertex((x-6)*8,110,(z-6)*8);
        for(int z=0;z<side;z++)for(int x=0;x<side;x++){int at=z*(side+1)+x;m.insertFace(4,new int[]{at,at+1,at+side+2,at+side+1},-12345,-12345,false);}
        m.key=77;m.setRenderer3DModelKind(Renderer3DModelKind.GAME_OBJECT);return m;
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("math")){math();return;}
        if(args[0].equals("cached-rebase")){cachedRebaseParity();return;}
        boolean defaults=args[1].equals("default"),enabled=defaults||Boolean.parseBoolean(args[1]),current=args[0].equals("current");if(defaults)System.clearProperty("spoiledmilk.browserModelPickBounds");else System.setProperty("spoiledmilk.browserModelPickBounds",String.valueOf(enabled));
        for(String name:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster","browserOverlayCoverage"})System.setProperty("spoiledmilk."+name,"true");System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        long omitted = 0;
        for(int mode=0;mode<4;mode++)for(boolean early:new boolean[]{false,true}){
            BrowserFrameSinkRegistry.install(null);if(mode<3)BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(mode==2));System.setProperty("spoiledmilk.browserEarlyFaceCull",String.valueOf(early));
            BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(mode!=1);RSModel dense=dense();view.s.addModel(dense);
            RSModel fixed = dense();
            fixed.setTranslate(96, 0, 128);
            view.s.addModel(fixed);
            require(active(view.s)==(current&&enabled&&mode==0),"Actual feature gate changed native/software/mixed ownership");
            for(int frame=0;frame<72;frame++){
                if (frame > 0 && frame % 18 == 0) fixed.rebaseWorldOrigin(frame % 36 == 0 ? -128 : 128, 0);
                int scale=new int[]{1,2,3,14,Integer.MAX_VALUE}[frame%5];view.front.setPickBoundsScale(scale);view.models[2].setPickBoundsScale(scale);dense.setPickBoundsScale(scale);dense.setTranslate((frame%5-2)*120,frame%3*5,(frame%7-3)*90);dense.setRenderer3DModelKind(Renderer3DModelKind.values()[frame%Renderer3DModelKind.values().length]);dense.faceIndices[frame%dense.faceHead]=frame%2==0?new int[]{0,1,14,13}:new int[]{13,14,1,0};
                BrowserWallRoofOwnershipFixture.Result value=view.draw(frame%4==0?0:frame%4==1?511:256,frame%4==0?0:frame%4==1?383:192,new int[]{20,100,600}[frame%3],new int[]{640,768,900}[frame%3],frame%2*256,new int[]{-12345,0,Scene.TRANSPARENT}[frame%3],frame%11==0);
                text(hash,value.picks);text(hash,value.anchors);text(hash,value.faces);text(hash,value.resident);ByteBuffer pixels=ByteBuffer.allocate(value.pixels.length*4);for(int p:value.pixels)pixels.putInt(p);hash.update(pixels.array());
            }
            if (Boolean.getBoolean("spoiledmilk.browserResidentCameraCull"))
                omitted += view.s.getBrowserOmittedProjection()[1];
        }
        if (Boolean.getBoolean("spoiledmilk.browserResidentCameraCull"))
            require(omitted > 0, "The exact Scene fixture did not omit resident model projections");
        BrowserFrameSinkRegistry.install(null);
        try {Field f=Scene.class.getDeclaredField("TEST_FACE_BOUNDS_CALLS");f.setAccessible(true);System.out.println("PROBE_FACE_CALLS="+f.getLong(null));
            for(String name:new String[]{"TEST_MODEL_SCANS","TEST_MODEL_REJECTIONS","TEST_FACE_VALIDATIONS"}){Field c=ProjectedModelPickBounds.class.getDeclaredField(name);c.setAccessible(true);System.out.println(name+"="+c.getLong(null));}}
        catch(NoSuchFieldException production){/* No counters or extra work in maintained production source. */}
        System.out.println(hex(hash.digest()));
    }

    private static String hex(byte[] digest) {
        StringBuilder text = new StringBuilder();
        for (byte value : digest) text.append(String.format("%02x", value & 255));
        return text.toString();
    }
}
