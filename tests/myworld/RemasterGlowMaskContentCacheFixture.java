package orsc;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.*;
import orsc.graphics.three.*;

/** Real production masks/cache; no GL or browser required. */
public final class RemasterGlowMaskContentCacheFixture {
    private static int checks;
    private static void require(boolean ok,String message) { checks++; if(!ok)throw new AssertionError(message); }
    private static Renderer3DWorldChunkFrame.GlowEmitter emitter(int x,int y,int z,int radius,int color,int intensity,Renderer3DModelKind kind) {
        return new Renderer3DWorldChunkFrame.GlowEmitter(kind,x,y,z,radius,color,intensity);
    }
    private static final Renderer3DWorldChunkFrame.GlowEmitter A = emitter(64,0,96,320,0xff8040,220,Renderer3DModelKind.GAME_OBJECT);
    private static Renderer3DWorldChunkFrame.ChunkMesh chunk(int extent,long signature,int plane,int triangles,Renderer3DWorldChunkFrame.GlowEmitter... emitters) {
        int[] indices=new int[triangles*3];for(int i=0;i<indices.length;i++)indices[i]=i%3;
        int[] materials=new int[triangles];Arrays.fill(materials,-1);
        Renderer3DModelKind[] kinds=new Renderer3DModelKind[triangles];Arrays.fill(kinds,Renderer3DModelKind.GAME_OBJECT);
        return new Renderer3DWorldChunkFrame.ChunkMesh(plane,50+(int)signature,50,plane*128,plane*256,
            new int[]{-1000,0,-1000,extent,30,-1000,-1000,20,1000},new float[3],new float[3],new int[3],
            indices,materials,new int[triangles],kinds,null,emitters,0,0,0,true,
            Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,signature);
    }
    private static Renderer3DWorldChunkFrame frame(int extent,long signature,int plane,int triangles,Renderer3DWorldChunkFrame.GlowEmitter... emitters) {
        return Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(chunk(extent,signature,plane,triangles,emitters)));
    }
    private static byte[] pixels(RemasterGlowMask mask) {ByteBuffer source=mask.pixels();byte[] result=new byte[source.remaining()];source.get(result);return result;}
    private static void sameMask(RemasterGlowMask left,RemasterGlowMask right,String label) {
        require(left.width==right.width&&left.height==right.height&&left.visiblePixels==right.visiblePixels,label+" dimensions/visibility");
        require(Float.floatToIntBits(left.minX)==Float.floatToIntBits(right.minX)&&Float.floatToIntBits(left.minZ)==Float.floatToIntBits(right.minZ)
            &&Float.floatToIntBits(left.invSpanX)==Float.floatToIntBits(right.invSpanX)&&Float.floatToIntBits(left.invSpanZ)==Float.floatToIntBits(right.invSpanZ),label+" exact coordinate mapping");
        require(Arrays.equals(pixels(left),pixels(right)),label+" exact rendered pixels");
    }
    private static Map<?,?> cache(RemasterGlowMaskBuilder builder)throws Exception {Field f=RemasterGlowMaskBuilder.class.getDeclaredField("cacheByInputs");f.setAccessible(true);return (Map<?,?>)f.get(builder);}
    private static void changed(RemasterGlowMaskBuilder builder,Renderer3DWorldChunkFrame input,RemasterGlowMask prior,String label,boolean differentPixels) {
        RemasterGlowMaskBuild result=builder.build(input);require(result!=null&&result.rebuild&&!result.cacheHit,label+" rebuild");
        require(result.mask.signature!=prior.signature&&result.mask.signature>0,label+" distinct upload revision");
        sameMask(new RemasterGlowMaskBuilder().build(input).mask,result.mask,label+" fresh oracle");
        if(differentPixels)require(!Arrays.equals(pixels(prior),pixels(result.mask)),label+" really changes pixels");
    }
    public static void main(String[] args)throws Exception {
        Renderer3DWorldChunkFrame original=frame(1000,11,0,1,A);
        RemasterGlowMaskBuilder builder=new RemasterGlowMaskBuilder();RemasterGlowMask first=builder.build(original).mask;
        byte[] originalBytes=pixels(first);
        Renderer3DWorldChunkFrame changedGeometry=Renderer3DWorldChunkFrame.fromChunks(Arrays.asList(chunk(1000,991,7,3,A),chunk(1000,992,3,2)));
        require(original.getTotalTriangleCount()!=changedGeometry.getTotalTriangleCount(),"fixture must alter real geometry");
        RemasterGlowMaskBuild same=builder.build(changedGeometry);require(same.cacheHit&&!same.rebuild&&same.mask==first,"unrelated triangles/chunks/metadata reuse exact mask");
        sameMask(new RemasterGlowMaskBuilder().build(changedGeometry).mask,first,"geometry-independent fresh mask");
        RemasterGlowMaskBuild heightKind=builder.build(frame(1000,99,2,2,emitter(64,999,96,320,0xff8040,220,Renderer3DModelKind.TERRAIN)));
        require(heightKind.cacheHit&&heightKind.mask==first,"unused height/kind do not invalidate XZ field");
        sameMask(new RemasterGlowMaskBuilder().build(frame(1000,99,2,2,emitter(64,999,96,320,0xff8040,220,Renderer3DModelKind.TERRAIN))).mask,first,"height/kind fresh mask");
        changed(builder,frame(1000,11,0,1,emitter(65,0,96,320,0xff8040,220,Renderer3DModelKind.GAME_OBJECT)),first,"one-unit X",true);
        changed(builder,frame(1000,11,0,1,emitter(64,0,97,320,0xff8040,220,Renderer3DModelKind.GAME_OBJECT)),first,"one-unit Z",true);
        changed(builder,frame(1000,11,0,1,emitter(64,0,96,321,0xff8040,220,Renderer3DModelKind.GAME_OBJECT)),first,"one-unit radius",true);
        changed(builder,frame(1000,11,0,1,emitter(64,0,96,320,0x40ff80,220,Renderer3DModelKind.GAME_OBJECT)),first,"RGB",true);
        changed(builder,frame(1000,11,0,1,emitter(64,0,96,320,0xff8040,219,Renderer3DModelKind.GAME_OBJECT)),first,"one-unit intensity",true);
        changed(builder,frame(1001,11,0,1,A),first,"one-unit vertex extent",true);
        Renderer3DWorldChunkFrame translated = Renderer3DWorldChunkFrame.fromChunks(Collections.singletonList(
            new Renderer3DWorldChunkFrame.ChunkMesh(0,58,46,1024,-512,
                new int[]{24,0,-1512,2024,30,-1512,24,20,488},new float[3],new float[3],new int[3],new int[]{0,1,2},new int[]{-1},new int[]{0},
                new Renderer3DModelKind[]{Renderer3DModelKind.GAME_OBJECT},null,
                new Renderer3DWorldChunkFrame.GlowEmitter[]{emitter(1088,0,-416,320,0xff8040,220,Renderer3DModelKind.GAME_OBJECT)},
                0,0,0,true,Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS,11)));
        changed(builder,translated,first,"translated world/bounds",false);
        changed(builder,frame(1000,11,0,1,A,A),first,"duplicate emitter multiplicity",true);
        Renderer3DWorldChunkFrame.GlowEmitter b=emitter(80,12,100,290,0x00ff00,200,Renderer3DModelKind.GAME_OBJECT);
        Renderer3DWorldChunkFrame.GlowEmitter c=emitter(60,15,120,310,0x4400ff,190,Renderer3DModelKind.GAME_OBJECT);
        RemasterGlowMask ordered=builder.build(frame(1000,11,0,1,A,b,c)).mask;
        changed(builder,frame(1000,11,0,1,c,b,A),ordered,"emitter order",false);
        require(Arrays.equals(originalBytes,pixels(first)),"later rebuilds cannot mutate prior immutable pixel buffers");

        // Hash collisions must neither hit a different input nor reuse its GPU token.
        RemasterGlowMaskBuilder collisionBuilder=new RemasterGlowMaskBuilder();
        Renderer3DWorldChunkFrame ca=frame(1000,1,0,1,emitter(64,0,96,320,0x00ff00,200,Renderer3DModelKind.GAME_OBJECT));
        Renderer3DWorldChunkFrame cb=frame(1000,1,0,1,emitter(64,0,96,320,0x00ff01,169,Renderer3DModelKind.GAME_OBJECT));
        RemasterGlowMask ma=collisionBuilder.build(ca).mask;RemasterGlowMaskBuild mb=collisionBuilder.build(cb);
        Object[] keys=cache(collisionBuilder).keySet().toArray();require(keys.length==2&&keys[0].hashCode()==keys[1].hashCode()&&!keys[0].equals(keys[1]),"fixture produces a real exact-key hash collision");
        require(mb.rebuild&&mb.mask.signature!=ma.signature,"hash collision gets distinct mask/upload revision");
        require(!Arrays.equals(pixels(ma),pixels(mb.mask)),"colliding inputs have different actual pixel bytes");
        RemasterGlowMaskBuild back=collisionBuilder.build(ca);require(back.cacheHit&&back.mask==ma&&back.mask.signature!=mb.mask.signature,"A to B to cached A retains correct upload identity");
        collisionBuilder.clear();require(cache(collisionBuilder).isEmpty(),"context clear empties cache");
        RemasterGlowMaskBuild cleared=collisionBuilder.build(ca);require(cleared.rebuild&&cleared.mask.signature!=ma.signature&&cleared.mask.signature!=mb.mask.signature,"clear never resets upload sequence");sameMask(ma,cleared.mask,"clear/same input");

        // The configured two-entry LRU retains the hot mask and evicts the other.
        require(cache(collisionBuilder).size()==1,"clear followed by one input");
        RemasterGlowMask second=collisionBuilder.build(cb).mask;collisionBuilder.build(ca);
        collisionBuilder.build(frame(1000,2,0,1,c));require(cache(collisionBuilder).size()==2,"cache remains bounded");
        require(collisionBuilder.build(ca).cacheHit,"LRU preserves hot A");
        RemasterGlowMaskBuild evicted=collisionBuilder.build(cb);require(evicted.rebuild&&evicted.mask.signature!=second.signature,"evicted input receives fresh upload revision");sameMask(second,evicted.mask,"evicted rebuild");
        require(cache(collisionBuilder).size()==2,"cache bounded after eviction/rebuild");
        require(builder.build(null)==null&&builder.build(Renderer3DWorldChunkFrame.EMPTY)==null,"empty inputs");
        require(builder.build(frame(1000,2,0,1,emitter(64,0,96,320,0xff0000,0,Renderer3DModelKind.GAME_OBJECT)))==null,"zero intensity remains absent");
        Field next=RemasterGlowMaskBuilder.class.getDeclaredField("nextUploadRevision");next.setAccessible(true);next.setLong(collisionBuilder,Long.MAX_VALUE);
        int retained=cache(collisionBuilder).size();try{collisionBuilder.build(frame(1000,3,0,1,b));throw new AssertionError("revision overflow accepted");}catch(ArithmeticException expected){checks++;}
        require(cache(collisionBuilder).size()==retained,"overflow cannot replace cached masks");
        System.out.println("PASS glow-mask content cache: "+checks+" checks; exact pixel dependencies, unrelated-geometry hits, collision-safe revisions, clear/LRU and immutable buffers");
    }
}
