package orsc;
import java.io.*;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;
/** Independent process oracle: the identical fixture runs against frozen OLD and candidate classes. */
public final class BrowserVertexPackingFixture {
    static Renderer3DWorldChunkFrame.ChunkMesh chunk(Random r,int trial,int plane){
        int triangles=trial<2?trial:1+r.nextInt(60),vertices=triangles*3;
        int[] xyz=new int[vertices*3],light=new int[vertices],blend=new int[vertices],strength=new int[vertices],indices=new int[vertices],tex=new int[triangles],color=new int[triangles],masks=new int[triangles];
        float[] u=new float[vertices],v=new float[vertices];Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];
        float[] units={-2,-.01f,-0.0f,0,.001f,.5f,1,1.01f,3,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY};
        int terrains=0,walls=0,roofs=0;
        for(int n=0;n<vertices;n++){
            xyz[n*3]=(n%6)*128+(trial%7==0?0:r.nextInt(20));xyz[n*3+1]=r.nextInt(512)-256;xyz[n*3+2]=(n/6)*128;
            light[n]=r.nextInt(700)-200;blend[n]=r.nextInt(0x1000000);strength[n]=r.nextInt(256);u[n]=units[r.nextInt(units.length)];v[n]=units[r.nextInt(units.length)];indices[n]=n;
        }
        for(int t=0;t<triangles;t++){
            tex[t]=new int[]{-2,-1,0,1,4,Integer.MAX_VALUE}[r.nextInt(6)];color[t]=trial%7==0?t%2:r.nextInt(0x1000000);
            kinds[t]=Renderer3DModelKind.values()[(trial+t)%Renderer3DModelKind.values().length];masks[t]=r.nextInt(256);
            if(kinds[t]==Renderer3DModelKind.TERRAIN)terrains++;if(kinds[t]==Renderer3DModelKind.WALL)walls++;if(kinds[t]==Renderer3DModelKind.ROOF)roofs++;
        }
        return new Renderer3DWorldChunkFrame.ChunkMesh(plane,10,9,1000,2000,xyz,u,v,light,blend,strength,indices,tex,color,kinds,null,null,masks,null,0,0,terrains,walls,roofs,trial%2==0,trial%3,trial+1);
    }
    static Renderer3DWorldChunkFrame.ChunkMesh modelChunk(Random r,int trial,int plane){
        RSModel m=new RSModel(24,12);m.setRenderer3DModelKind(Renderer3DModelKind.values()[trial%Renderer3DModelKind.values().length]);
        for(int f=0;f<8;f++){
            int a=m.insertVertex(f*32,-r.nextInt(256),r.nextInt(128)),b=m.insertVertex(f*32+128,-r.nextInt(256),r.nextInt(128)),c=m.insertVertex(f*32,-r.nextInt(256),128+r.nextInt(128));
            m.insertFace(3,new int[]{a,b,c},trial%2==0?0:-2,trial%3==0?1:0x234567,false);
        }
        m.translate2(trial%4==0?-6144:0,0,trial%4==0?6144:0);
        return RSModel.buildRenderer3DObjectChunkMesh(plane,10,9,1000,2000,new RSModel[]{m},1,trial%2==0?1:2);
    }
    static void write(DataOutputStream out,float[] values)throws Exception{out.writeInt(values.length);for(float value:values)out.writeInt(Float.floatToRawIntBits(value));}
    public static void main(String[] args)throws Exception{
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Method ensure=OpenGLWorldChunkRenderer.class.getDeclaredMethod("ensureUploadBuffers",int.class,int.class),nativeCopy=OpenGLWorldChunkRenderer.class.getDeclaredMethod("copyChunkVertices",Renderer3DWorldChunkFrame.ChunkMesh.class,Renderer3DFrame.class,int.class,boolean.class,List.class);ensure.setAccessible(true);nativeCopy.setAccessible(true);
        Field buffer=OpenGLWorldChunkRenderer.class.getDeclaredField("vertexUploadBuffer");buffer.setAccessible(true);
        OpenGLWorldTextureCache textureCache=new OpenGLWorldTextureCache(null);Field regions=OpenGLWorldTextureCache.class.getDeclaredField("textureRegionsById");regions.setAccessible(true);
        @SuppressWarnings("unchecked")Map<Integer,OpenGLTextureRegion> map=(Map<Integer,OpenGLTextureRegion>)regions.get(textureCache);
        map.put(0,new OpenGLTextureRegion(7,13,29,64,64,13.5f/2048,29.5f/2048,76.5f/2048,92.5f/2048,false));map.put(1,new OpenGLTextureRegion(7,193,103,128,128,193.5f/2048,103.5f/2048,320.5f/2048,230.5f/2048,true));
        Class<?> caster=Class.forName("orsc.OpenGLWorldChunkRenderer$ShadowProofCaster");Constructor<?> ctor=caster.getDeclaredConstructors()[0];ctor.setAccessible(true);Object shadow=ctor.newInstance(1L,0f,0f,128f,0f,0f,256f,512f,128f,.35f);
        Scene scene=new Scene(new MudClientGraphics(64,64,2),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.setFrustum(0,11,7,2);
        byte[] pixels=new byte[4096];Arrays.fill(pixels,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x778899},0,pixels);for(int n=0;n<pixels.length;n+=3)pixels[n]=0;scene.loadTexture(1,new int[]{0xff00ff,0x337755},0,pixels);
        Random random=new Random(918233);int cases=600;long floats=0;
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))){
            out.writeInt(cases);
            for(int trial=0;trial<cases;trial++){
                RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.values()[trial%3]);RendererLightingSettings.setMode(RendererLightingSettings.Mode.values()[(trial/3)%2]);RendererFogSettings.setMode(RendererFogSettings.Mode.values()[(trial/6)%2]);RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.values()[(trial/12)%3]);
                RendererToneSettings.Mode[] tones={RendererToneSettings.Mode.DAY,RendererToneSettings.Mode.SUNRISE_AMBER,RendererToneSettings.Mode.ROSE_DUSK,RendererToneSettings.Mode.COOL_NIGHT,RendererToneSettings.Mode.DEEP_BLUE};RendererToneSettings.setMode(tones[(trial/36)%tones.length]);
                int plane=new int[]{0,-1,-8,1,10}[trial%5];scene.setRendererSignedWorldLevel(plane);scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame();Renderer3DFrame previous=RendererFrameLighting.bind(frame);
                try{
                    Renderer3DWorldChunkFrame.ChunkMesh mesh=trial%3==2?modelChunk(random,trial,plane):chunk(random,trial,plane);
                    OpenGLWorldChunkRenderer browser=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,trial%4!=0,trial%4==1);
                    Renderer3DFrame sourceFrame=trial%11==0?null:frame;
                    float[] packed=browser.copyBackendChunkVertices(mesh,sourceFrame);write(out,packed);floats+=packed.length;
                    if(args.length>1&&args[1].equals("new"))for(String name:new String[]{"vertexUploadBuffer","indexUploadBuffer","materialIndexUploadBuffer"}){Field f=OpenGLWorldChunkRenderer.class.getDeclaredField(name);f.setAccessible(true);if(f.get(browser)!=null)throw new AssertionError("Browser allocated direct workspace: "+name);}
                    OpenGLWorldChunkRenderer nativeRenderer=new OpenGLWorldChunkRenderer(null,textureCache,null,true,true,true,false,true,trial%4!=0,trial%4==1);ensure.invoke(nativeRenderer,mesh.getVertexCount(),mesh.getIndexCount());
                    for(boolean atlas:new boolean[]{false,true}){
                        nativeCopy.invoke(nativeRenderer,mesh,sourceFrame,mesh.getVertexCount(),atlas,trial%2==0?Collections.singletonList(shadow):Collections.emptyList());FloatBuffer b=(FloatBuffer)buffer.get(nativeRenderer);if(!b.isDirect()||b.position()!=0)throw new AssertionError("Native direct ownership/flip changed");float[] values=new float[b.remaining()];b.get(values);write(out,values);floats+=values.length;
                    }
                }finally{RendererFrameLighting.restore(previous);frame.release();}
            }
        }
        System.out.println("PASS emitted"+floats+" raw float fields across600 production mesh/model/material/settings/level cases; browser +native plain/atlas/proof streams");
    }
}
