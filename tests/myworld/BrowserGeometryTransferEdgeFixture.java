package orsc;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import orsc.graphics.three.*;
import orsc.graphics.two.MudClientGraphics;

/** Frozen-process oracle for partial/malformed shapes, poses and texture metadata.
 * No hand-written expected packing math and no native/GPU context. */
public final class BrowserGeometryTransferEdgeFixture {
    static Renderer3DWorldChunkFrame.ChunkMesh mesh(Random random,int trial,int plane) {
        int triangles=trial<3?trial:1+random.nextInt(35);
        int vertices=triangles*3+(trial%5==0?1:trial%5==1?2:0);
        int metadata=triangles+(trial%5<2?1:0);
        // Deliberately missing per-triangle metadata exercises original failures.
        if(trial%13==0&&metadata>0)metadata--;
        int indexCount=Math.max(0,triangles*3-trial%3);
        int[] xyz=new int[vertices*3],lights=new int[vertices],blend=new int[vertices],strength=new int[vertices],indices=new int[indexCount];
        float[] u=new float[vertices],v=new float[vertices];
        float[] units={-Float.MAX_VALUE,-1,-0.0f,0,.5f,1,Float.MAX_VALUE,Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY};
        for(int n=0;n<vertices;n++) {
            xyz[n*3]=random.nextInt(30000)-15000;xyz[n*3+1]=random.nextInt(6000)-3000;xyz[n*3+2]=random.nextInt(30000)-15000;
            lights[n]=random.nextInt(1200)-600;blend[n]=random.nextInt();strength[n]=random.nextInt(800)-200;
            u[n]=units[random.nextInt(units.length)];v[n]=units[random.nextInt(units.length)];
        }
        for(int n=0;n<indexCount;n++)indices[n]=vertices==0?0:trial%7==0?(n%4==0?-1:vertices+1):random.nextInt(vertices);
        int[] textures=new int[metadata],fallbacks=new int[metadata],masks=new int[metadata];
        Renderer3DModelKind[] kinds=new Renderer3DModelKind[metadata];
        int[] textureIds={12345678,-2,-1,0,1,2,3,Integer.MAX_VALUE};
        for(int n=0;n<metadata;n++) {
            textures[n]=textureIds[random.nextInt(textureIds.length)];fallbacks[n]=textureIds[random.nextInt(textureIds.length)];
            kinds[n]=Renderer3DModelKind.values()[(trial+n)%Renderer3DModelKind.values().length];
            masks[n]=random.nextInt();
        }
        boolean object=trial%2==0;
        Renderer3DWorldChunkFrame.ChunkMesh mesh=new Renderer3DWorldChunkFrame.ChunkMesh(plane,9,10,12544,20864,xyz,u,v,lights,blend,strength,indices,textures,fallbacks,kinds,null,null,masks,null,0,0,0,0,0,object,object?1:0,trial+1);
        if(trial%3==0)mesh=object?mesh.rebaseStaticObjectPresentation(10,11,-6144,6144):mesh.rebasePresentation(-6144,6144);
        return mesh;
    }
    static void floats(DataOutputStream out,float[] values)throws Exception {out.writeInt(values.length);for(float value:values)out.writeInt(Float.floatToRawIntBits(value));}
    static void ints(DataOutputStream out,int[] values)throws Exception {out.writeInt(values.length);for(int value:values)out.writeInt(value);}
    public static void main(String[] args)throws Exception {
        System.setProperty("spoiledmilk.renderer3DGeometryCapture","true");
        Scene scene=new Scene(new MudClientGraphics(64,64,2),4,16,4);scene.setMidpoints(32,true,64,32,32,9,64);scene.setCamera(0,-4,0,768,0,0,600);scene.setFrustum(0,11,7,3);
        byte[] pixels=new byte[4096];Arrays.fill(pixels,(byte)1);scene.loadTexture(0,new int[]{0xff00ff,0x778899},0,pixels);
        for(int n=0;n<pixels.length;n+=2)pixels[n]=0;scene.loadTexture(1,new int[]{0xff00ff,0x337755},0,pixels);
        scene.loadTexture(2,new int[]{0xff00ff,0x334466},1,new byte[16384]);
        Method pack=OpenGLWorldChunkRenderer.class.getDeclaredMethod(Boolean.getBoolean("fixture.resolved")?"copyBackendChunkVerticesResolved":"copyBackendChunkVertices",Renderer3DWorldChunkFrame.ChunkMesh.class,Renderer3DFrame.class);pack.setAccessible(true);
        Random random=new Random(8927411);int success=0,failed=0;
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))) {
            out.writeInt(1200);
            for(int trial=0;trial<1200;trial++) {
                RendererGeometrySettings.setMode(RendererGeometrySettings.Mode.values()[trial%RendererGeometrySettings.Mode.values().length]);
                RendererLightingSettings.setMode(RendererLightingSettings.Mode.values()[(trial/3)%RendererLightingSettings.Mode.values().length]);
                RendererToneSettings.setMode(RendererToneSettings.Mode.DAY);
                RendererBrightnessSettings.setMode(RendererBrightnessSettings.Mode.values()[(trial/9)%RendererBrightnessSettings.Mode.values().length]);
                int plane=new int[]{-8,-1,0,1,10}[trial%5];scene.setRendererSignedWorldLevel(plane);
                if(trial==17){byte[] changed=new byte[4096];Arrays.fill(changed,(byte)1);scene.loadTexture(1,new int[]{0xff00ff,random.nextInt(0x1000000)},0,changed);}
                scene.endScene(0);Renderer3DFrame frame=scene.getRenderer3DFrame(),previous=RendererFrameLighting.bind(frame);
                try {
                    Renderer3DWorldChunkFrame.ChunkMesh mesh=mesh(random,trial,plane);int[] before=mesh.copyVertexCoords();
                    OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,trial%4!=0,trial%4==1);
                    try {
                        float[] values=(float[])pack.invoke(renderer,mesh,trial%11==0?null:frame);
                        out.writeBoolean(true);floats(out,values);success++;
                    } catch(InvocationTargetException failure) {
                        Throwable cause=failure.getCause();out.writeBoolean(false);out.writeUTF(cause.getClass().getName());out.writeUTF(String.valueOf(cause.getMessage()));failed++;
                    }
                    if(!Arrays.equals(before,mesh.copyVertexCoords()))throw new AssertionError("Packing mutated source coordinates");
                    // Material grouping/index capacity remains byte-identical too.
                    BrowserChunkMaterials.Buffers materials=BrowserChunkMaterials.pack(renderer,frame,mesh);
                    ints(out,materials.indices);ints(out,materials.batches);
                } finally {RendererFrameLighting.restore(previous);frame.release();}
            }
        }
        if(success==0||failed==0)throw new AssertionError("Vacuous edge oracle");
        System.out.println("PASS emitted "+success+" edge streams and "+failed+" baseline failure outcomes; grouped indices/materials and source coordinates checked");
    }
}
