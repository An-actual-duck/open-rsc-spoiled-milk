package orsc;
import java.util.*;
import java.nio.*;
import java.lang.reflect.*;
import orsc.graphics.three.*;
public final class TerrainDefinitionFieldFixture {
    static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
    static int channel(byte[] bytes,int width,int x,int z,int channel){return bytes[(z*width+x)*4+channel]&255;}
    static Renderer3DWorldChunkFrame.ChunkMesh mesh(int plane,long signature) {
        ArrayList<Integer> coords=new ArrayList<Integer>(),order=new ArrayList<Integer>();
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)for(int[] d:new int[][]{{0,0},{1,0},{1,1},{0,0},{1,1},{0,1}}){int xx=x+d[0],zz=z+d[1];coords.add(xx*128);coords.add(128-(xx-4)*(xx-4)*8-(zz-4)*(zz-4)*8);coords.add(zz*128);order.add(order.size());}
        int[] c=new int[coords.size()],indices=new int[order.size()];for(int i=0;i<c.length;i++)c[i]=coords.get(i);for(int i=0;i<indices.length;i++)indices[i]=i;
        int n=indices.length/3;Renderer3DModelKind[] kinds=new Renderer3DModelKind[n];Arrays.fill(kinds,Renderer3DModelKind.TERRAIN);int[] tex=new int[n];Arrays.fill(tex,-2);
        return new Renderer3DWorldChunkFrame.ChunkMesh(plane,10,9,432*128,384*128,c,new float[indices.length],new float[indices.length],new int[indices.length],indices,tex,new int[n],kinds,n,0,0,signature);
    }
    static Renderer3DFrame frame(Renderer3DWorldChunkFrame world,long epoch,int plane)throws Exception {
        for(Constructor<?> c:Renderer3DFrame.class.getDeclaredConstructors()){
            Class<?>[] types=c.getParameterTypes();if(types[types.length-1]!=long.class)continue;c.setAccessible(true);Object[] args=new Object[types.length];
            for(int i=0;i<types.length;i++)args[i]=types[i]==int.class?0:types[i]==long.class?epoch:new Renderer3DTextureData[0];
            Renderer3DFrame frame=(Renderer3DFrame)c.newInstance(args);frame.setWorldChunkFrame(world);frame.setRoofVisibility(null,plane);return frame;
        }throw new AssertionError("Frame factory unavailable");
    }
    public static void main(String[] args)throws Exception {
        int w=11,h=11;int[] flat=new int[w*h],slope=new int[w*h],bowl=new int[w*h],ridge=new int[w*h];
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){int i=z*w+x;slope[i]=x*37+z*19;bowl[i]=300-(x-5)*(x-5)*12-(z-5)*(z-5)*12;ridge[i]=-bowl[i];}
        byte[] f=TerrainDefinitionField.build(flat,w,h),s=TerrainDefinitionField.build(slope,w,h),b=TerrainDefinitionField.build(bowl,w,h),r=TerrainDefinitionField.build(ridge,w,h);
        require(channel(f,w,5,5,0)==0&&channel(f,w,5,5,1)==128,"Flat surface changed");
        require(channel(s,w,5,5,0)==0&&channel(s,w,5,5,1)==128,"Planar slope mistaken for ambient occlusion/curvature");
        require(channel(b,w,5,5,0)>25&&channel(b,w,5,5,1)>128,"Bowl has no shelter/concavity");
        require(channel(r,w,5,5,1)<128,"Ridge did not reverse signed curvature");
        flat[5*w+5]=Integer.MIN_VALUE;require(channel(TerrainDefinitionField.build(flat,w,h),w,5,5,3)==0,"Unknown height not neutral");
        Renderer3DWorldChunkFrame.ChunkMesh mesh=mesh(0,73);TerrainDefinitionField cache=new TerrainDefinitionField();Renderer3DFrame first=frame(Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(mesh)),1,0);
        require(cache.update(first),"No actual immutable field built");int rebuilds=cache.rebuilds;ByteBuffer pixels=cache.pixels();int minX=cache.minX,minZ=cache.minZ;
        require(cache.update(first)&&cache.rebuilds==rebuilds,"Steady frame rescanned height vertices");
        Renderer3DWorldChunkFrame moved=Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(mesh.rebasePresentation(-6144,6144)));cache.update(frame(moved,1,0));
        require(cache.minX==minX&&cache.minZ==minZ&&pixels.equals(cache.pixels()),"Presentation rebase moved world-aligned probes");
        cache.update(frame(moved,2,0));require(cache.rebuilds==rebuilds+2,"Context did not invalidate height cache");
        require(!cache.update(frame(moved,3,1))&&cache.width==0,"Wrong plane reused terrain field");
        cache.clear();require(cache.update(first),"Cache failed after session clear");
        System.out.println("PASS planar neutrality, sheltered bowl, signed ridge, unknowns, actual frame cache/context/plane/rebase and session recovery");
    }
}
