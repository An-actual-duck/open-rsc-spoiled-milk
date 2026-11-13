package orsc;
import java.lang.reflect.*;import java.nio.*;import java.util.*;import orsc.graphics.three.*;
public final class NativeMaterialBatchReuseFixture {
    static Method method(String name,Class<?>...types)throws Exception{Method m=OpenGLWorldChunkRenderer.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static String[] batches(Object[] objects)throws Exception{List<String> out=new ArrayList<String>();for(Object o:objects){StringBuilder s=new StringBuilder();for(Field f:o.getClass().getDeclaredFields()){if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);s.append(f.getName()).append('=').append(f.get(o)).append(';');}out.add(s.toString());}return out.toArray(new String[0]);}
    public static void main(String[] args)throws Exception{
        OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,null,null,true,false,false,true,true,true,false);int checks=0;
        for(int triangles:new int[]{0,4,1000,262145}){
            int vertices=triangles*3;int[] xyz=new int[vertices*3],indices=new int[vertices],textures=new int[triangles],colors=new int[triangles];Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];
            for(int i=0;i<vertices;i++){indices[i]=i;xyz[i*3]=i%3200;xyz[i*3+2]=(i*127)%3200;}
            for(int i=0;i<triangles;i++){textures[i]=i%3-1;colors[i]=0x224466+i%5;kinds[i]=Renderer3DModelKind.values()[i%Renderer3DModelKind.values().length];}
            Renderer3DWorldChunkFrame.ChunkMesh chunk=new Renderer3DWorldChunkFrame.ChunkMesh(0,0,0,0,0,xyz,new float[vertices],new float[vertices],new int[vertices],indices,textures,colors,kinds,0,0,0,1);
            method("ensureUploadBuffers",int.class,int.class).invoke(renderer,vertices,indices.length);
            for(boolean replacement:new boolean[]{false,true})for(boolean spatial:new boolean[]{false,true}){
                int[] reference=null;String[] referenceBatches=null;
                for(boolean enabled:new boolean[]{false,true}){
                    System.setProperty("spoiledmilk.nativeMaterialBatchReuse",Boolean.toString(enabled));Object[] result=(Object[])method("copyMaterialIndices",Renderer3DWorldChunkFrame.ChunkMesh.class,int.class,boolean.class,boolean.class).invoke(renderer,chunk,indices.length,replacement,spatial);
                    IntBuffer packed=((IntBuffer)field(renderer,"materialIndexUploadBuffer")).duplicate();int[] data=new int[packed.remaining()];packed.get(data);String[] description=batches(result);
                    if(!enabled){reference=data;referenceBatches=description;}else if(!Arrays.equals(reference,data)||!Arrays.equals(referenceBatches,description))throw new AssertionError("order/material/bounds mismatch");checks++;
                }
            }
        }
        System.out.println("PASS material grouping exact first-seen order/index/bounds/filter/spatial/cap fallback checks="+checks);
    }
}
