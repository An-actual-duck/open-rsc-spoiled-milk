package orsc.graphics.three;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import orsc.BrowserFrameSinkRegistry;
import orsc.MiscFunctions;
import orsc.util.FastMath;

/** Actual Scene differential, bounds rejection oracle and malformed-input guards.
 * No browser/GPU/FPS claim. */
public final class BrowserStaticModelPreparationFixture {
    static long frames,skipped,avoidedVertices,avoidedFaces,validationVertices,validationFaces;
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static boolean active(Scene scene)throws Exception{
        try{return(Boolean)scene.getClass().getMethod("isBrowserStaticModelPreparationEnabled").invoke(scene);}catch(NoSuchMethodException old){return false;}
    }
    static int[] work(Scene scene)throws Exception{
        try{return(int[])scene.getClass().getMethod("getBrowserStaticModelPreparationWork").invoke(scene);}catch(NoSuchMethodException old){return new int[7];}
    }
    static void text(MessageDigest h,String value)throws Exception{h.update(value.getBytes("UTF-8"));h.update((byte)0);}
    static RSModel grid(Renderer3DModelKind kind,int key){
        int side=12;RSModel m=new RSModel((side+1)*(side+1),side*side);
        for(int z=0;z<=side;z++)for(int x=0;x<=side;x++)m.insertVertex((x-6)*12,100+(x+z)%3,(z-6)*12);
        for(int z=0;z<side;z++)for(int x=0;x<side;x++){int i=z*(side+1)+x;m.insertFace(4,new int[]{i,i+1,i+side+2,i+side+1},-12345,-12345,false);}
        m.key=key;m.setRenderer3DModelKind(kind);return m;
    }
    static void differential(String version,String policy)throws Exception{
        boolean current=version.equals("current"),on=policy.equals("true");
        if(policy.equals("default"))System.clearProperty("spoiledmilk.browserStaticModelPreparation");
        else System.setProperty("spoiledmilk.browserStaticModelPreparation",policy);
        for(String flag:new String[]{"renderer3DGeometryCapture","openglWorldMesh","openglWorldMeshTexturedVisible","openglWorldMeshTexturedStaticVisible","openglWorldChunksTexturedVisible","openglWorldChunksReplacementComposite","openglWorldChunksTrustedReplacement","openglWorldChunksResidentObjects","skipLegacyWorldRaster","browserOverlayCoverage","browserModelPickBounds"})System.setProperty("spoiledmilk."+flag,"true");
        System.setProperty("spoiledmilk.renderer3DVisibleWorld","false");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(int mode=0;mode<4;mode++)for(boolean early:new boolean[]{false,true}){
            BrowserFrameSinkRegistry.install(null);
            if(mode<3)BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(mode==2));
            System.setProperty("spoiledmilk.browserEarlyFaceCull",String.valueOf(early));
            BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(mode!=1);
            RSModel[] dense={grid(Renderer3DModelKind.TERRAIN,70),grid(Renderer3DModelKind.WALL,71),grid(Renderer3DModelKind.ROOF,72),grid(Renderer3DModelKind.GAME_OBJECT,73)};
            for(RSModel m:dense)view.s.addModel(m);
            check(active(view.s)==(current&&on&&mode==0&&early),"feature enabled outside browser GPU-owned scope");
            for(int frame=0;frame<120;frame++){
                for(int n=0;n<dense.length;n++){
                    RSModel m=dense[n];m.setTranslate((frame%5-2)*120+n*160,frame%3*5,-n*80);
                    m.setPickBoundsScale(new int[]{1,2,3,14,Integer.MAX_VALUE}[frame%5]);
                    m.setInteractionOnly(n==2&&frame%7==0);
                    // Same-array topology edits and vertex motion exercise refresh and guard behavior.
                    int[] face=m.faceIndices[frame%m.faceHead];int t=face[0];face[0]=face[1];face[1]=t;
                    if(frame%13==0)m.addRotation(0,1,0);
                }
                view.s.setRendererSignedWorldLevel(new int[]{-2,-1,0,1,10}[frame%5]);
                // Off-model -> central -> opposite -> central: skipped model must
                // return to literal picking on the very next moved-mouse frame.
                int mx=new int[]{0,256,511,256}[frame%4],my=new int[]{0,192,383,192}[frame%4];
                BrowserWallRoofOwnershipFixture.Result v=view.draw(mx,my,new int[]{20,100,600}[frame%3],new int[]{640,768,900}[frame%3],frame%2*256,new int[]{-12345,0,Scene.TRANSPARENT}[frame%3],frame%11==0);
                text(digest,v.picks);text(digest,v.anchors);text(digest,v.faces);text(digest,v.resident);
                Renderer3DWorldChunkFrame.ChunkMesh mesh=RSModel.buildRenderer3DObjectChunkMesh(0,0,0,0,0,dense,dense.length);
                ByteBuffer geometry=ByteBuffer.allocate(mesh.getVertexCount()*20+mesh.getIndexCount()*4);
                for(int n=0;n<mesh.getVertexCount()*3;n++)geometry.putInt(mesh.getVertexCoord(n));
                for(int n=0;n<mesh.getVertexCount();n++)geometry.putFloat(mesh.getVertexTextureU(n)).putFloat(mesh.getVertexTextureV(n));
                for(int n=0;n<mesh.getIndexCount();n++)geometry.putInt(mesh.getIndex(n));
                digest.update(geometry.array());text(digest,"shadow="+mesh.getShadowCasterCount()+" glow="+mesh.getGlowEmitterCount());
                ByteBuffer pixels=ByteBuffer.allocate(v.pixels.length*4);for(int pixel:v.pixels)pixels.putInt(pixel);digest.update(pixels.array());
                int[] w=work(view.s);if(mode==0&&early&&frame%11!=0){skipped+=w[1];avoidedVertices+=w[2];avoidedFaces+=w[3];validationVertices+=w[4];validationFaces+=w[5];}
                frames++;
            }
        }
        exceptionDifferential(digest);
        BrowserFrameSinkRegistry.install(null);
        if(current&&on)check(skipped>0&&avoidedVertices>0&&avoidedFaces>0,"Corpus never skipped an actual eligible model");
        StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));
        System.out.println("frames="+frames+" skipped="+skipped+" avoidedVertices="+avoidedVertices+" avoidedFaces="+avoidedFaces+" validationVertices="+validationVertices+" validationFaces="+validationFaces);
        System.out.println(hex);
    }
    static void exceptionDifferential(MessageDigest digest)throws Exception {
        BrowserFrameSinkRegistry.install(BrowserWallRoofOwnershipFixture.sink(false));
        System.setProperty("spoiledmilk.browserEarlyFaceCull","true");
        for(int mutation=0;mutation<9;mutation++) {
            BrowserWallRoofOwnershipFixture.View view=new BrowserWallRoofOwnershipFixture.View(true);
            RSModel m=grid(Renderer3DModelKind.TERRAIN,90);view.s.addModel(m);
            view.draw(0,0,600,768,0,-12345,false);
            if(mutation==0)m.faceIndices[0][0]=-1;
            if(mutation==1)m.faceIndices[0][0]=m.vertHead;
            if(mutation==2)m.faceIndices[0]=null;
            if(mutation==3)m.faceIndexCount[0]=m.faceIndices[0].length+1;
            if(mutation==4)m.vertexParam2=null;
            if(mutation==5)m.vertXRot=m.vertYRot;
            if(mutation==6)((int[])field(RSModel.class,"vertXTransform").get(m))[0]=100000;
            if(mutation==7)field(RSModel.class,"minX").setInt(m,100000);
            if(mutation==8)m.faceIndices[0]=new int[0];
            try {
                BrowserWallRoofOwnershipFixture.Result v=view.draw(256,192,600,768,0,-12345,false);
                text(digest,"ok"+v.picks+v.anchors+v.faces+Arrays.toString(v.pixels));
            } catch(RuntimeException error) { text(digest,error.getClass().getName()+":"+error.getMessage()); }
        }
    }
    static int[] point(int x,int y,int z,int rx,int ry,int rz,int shift,int near){
        int t;
        if(rz!=0){int c=FastMath.trigTable1024[1024+rz],s=FastMath.trigTable1024[rz];t=y*s+c*x>>15;y=y*c-x*s>>15;x=t;}
        if(ry!=0){int c=FastMath.trigTable1024[1024+ry],s=FastMath.trigTable1024[ry];t=c*x+z*s>>15;z=c*z-x*s>>15;x=t;}
        if(rx!=0){int c=FastMath.trigTable1024[1024+rx],s=FastMath.trigTable1024[rx];t=y*c-s*z>>15;z=s*y+c*z>>15;y=t;}
        return new int[]{z<near?x<<shift:(x<<shift)/z,z<near?y<<shift:(y<<shift)/z,z};
    }
    static void math()throws Exception{
        Random random=new Random(337119883);int rejections=0,hits=0;
        Scene scene=new Scene(new orsc.graphics.two.MudClientGraphics(64,64,1),4,16,4);
        field(Scene.class,"m_K").setBoolean(scene,true);
        Method original=Scene.class.getDeclaredMethod("projectedFaceCouldContainMouse",RSModel.class,int[].class,int.class);original.setAccessible(true);
        Field mx=field(Scene.class,"m_j"),my=field(Scene.class,"m_Wb"),vy=field(Scene.class,"m_Nb");
        RSModel model=new RSModel(12,1);model.vertHead=12;model.vertexParam6=new int[12];model.vertexParam2=new int[12];int[] face=new int[12];for(int n=0;n<12;n++)face[n]=n;
        for(int trial=0;trial<200000;trial++){
            int x=random.nextInt(12001)-6000,y=random.nextInt(4001)-2000,z=100+random.nextInt(14000),sx=random.nextInt(1200),sy=random.nextInt(1200),sz=random.nextInt(1200);
            if(trial%17==0)x=random.nextInt();
            int rx=trial%4==0?0:random.nextInt(1024),ry=trial%3==0?0:random.nextInt(1024),rz=trial%5==0?0:random.nextInt(1024),shift=new int[]{0,4,9,15,20}[trial%5],near=1+random.nextInt(100);
            double[] b={(double)x,x+(double)sx,(double)y,y+(double)sy,(double)z,z+(double)sz};
            // Values outside int domains are an explicit uncertainty fallback.
            int px=trial%2==0?random.nextInt(20001)-10000:random.nextInt(),py=trial%2==0?random.nextInt(20001)-10000:random.nextInt(),offset=trial%11==0?random.nextInt():192;
            int scale=new int[]{1,2,3,14,1000,Integer.MAX_VALUE}[trial%6];model.setPickBoundsScale(scale);
            boolean excluded=BrowserStaticModelBounds.excludesMouse(b,shift,rx,ry,rz,near,offset,px,py,scale);
            if(excluded)rejections++;
            for(int n=0;n<12;n++){
                int xx=n<8?x+((n&1)==0?0:sx):x+random.nextInt(sx+1),yy=n<8?y+((n&2)==0?0:sy):y+random.nextInt(sy+1),zz=n<8?z+((n&4)==0?0:sz):z+random.nextInt(sz+1);
                int[] p=point(xx,yy,zz,rx,ry,rz,shift,near);model.vertexParam6[n]=p[0];model.vertexParam2[n]=p[1];
            }
            mx.setInt(scene,px);my.setInt(scene,py);vy.setInt(scene,offset);
            boolean hit=(Boolean)original.invoke(scene,model,face,face.length);if(hit)hits++;
            check(!excluded||!hit,"Interval rejection hid a literal face bound at trial "+trial);
        }
        check(rejections>1000&&hits>1000,"Vacuous bounds corpus "+rejections+"/"+hits);
        System.out.println("PASS "+checks+" fixed-point/overflow/near-plane/padding/viewport interval checks; rejected="+rejections+" originalHits="+hits);
    }
    static boolean skip(RSModel model)throws Exception{
        MiscFunctions.frustumMinX=MiscFunctions.frustumMinY=MiscFunctions.frustumNearZ=1000000;
        MiscFunctions.frustumMaxX=MiscFunctions.frustumMaxY=MiscFunctions.frustumFarZ=-1000000;
        return model.canSkipBrowserCameraPreparation(0,9,0,-1000,0,0,0,16,192,-10000,-10000,new double[6],new int[7]);
    }
    static void guards()throws Exception{
        RSModel m=grid(Renderer3DModelKind.TERRAIN,1);check(skip(m),"Guard fixture never eligible");
        int old=m.faceIndices[0][0];m.faceIndices[0][0]=m.vertHead;check(!skip(m),"Spare-capacity face was skipped");m.faceIndices[0][0]=-1;check(!skip(m),"Negative index skipped");m.faceIndices[0][0]=old;
        int[] row=m.faceIndices[0];m.faceIndices[0]=null;check(!skip(m),"Null face skipped");m.faceIndices[0]=row;
        int count=m.faceIndexCount[0];m.faceIndexCount[0]=row.length+1;check(!skip(m),"Short index storage skipped");m.faceIndexCount[0]=count;
        int[] a=m.vertXRot;m.vertXRot=m.vertYRot;check(!skip(m),"Aliased camera arrays skipped");m.vertXRot=a;
        Field transformed=field(RSModel.class,"vertXTransform");int[] coords=(int[])transformed.get(m);int x=coords[0];coords[0]=100000;check(!skip(m),"In-place world bounds edit skipped");coords[0]=x;
        Field bound=field(RSModel.class,"minX");int minimum=bound.getInt(m);bound.setInt(m,minimum+20);check(!skip(m),"Stale narrowed AABB skipped");bound.setInt(m,minimum);
        m.vertexParam2=null;check(!skip(m),"Missing camera output skipped");
        System.out.println("PASS "+checks+" mutation/alias/spare/null/short/bounds guard checks");
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("math"))math();else if(args[0].equals("guards"))guards();else differential(args[0],args[1]);
    }
}
