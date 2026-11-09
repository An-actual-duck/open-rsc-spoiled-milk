package orsc;

import java.lang.reflect.Method;
import java.util.Arrays;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Browser-only bounded replay of the shared native sky. Arrays are borrowed
 * until the next prepare/clear; the synchronous bridge must copy/upload them. */
public final class BrowserSkyData {
    public static final int MAX_VERTICES=65536,MAX_PALETTE=1024;
    private final WorldSkyGeometry geometrySource=new WorldSkyGeometry();
    private final float[] palette=new float[MAX_PALETTE*4],values=new float[20];
    private final OpenGLWorldChunkRenderer matrixSource=new OpenGLWorldChunkRenderer(null,null,null,false,false,false,false,false,false,false);
    private final Method projection,view,multiply;
    private float radius=Float.NaN;
    private float[] geometry;
    private int[] ranges;
    private int paletteCount;
    private int geometryBuilds;
    public static final class Frame {
        public final boolean enabled;
        public final float[] values,palette,geometry;
        public final int[] ranges;
        Frame(boolean enabled,float[] values,float[] palette,float[] geometry,int[] ranges){this.enabled=enabled;this.values=values;this.palette=palette;this.geometry=geometry;this.ranges=ranges;}
    }
    public BrowserSkyData() {
        try {
            projection=OpenGLWorldChunkRenderer.class.getDeclaredMethod("projectionMatrix",Renderer3DFrame.class);projection.setAccessible(true);
            view=OpenGLWorldChunkRenderer.class.getDeclaredMethod("skyViewMatrix",Renderer3DFrame.class);view.setAccessible(true);
            multiply=OpenGLWorldChunkRenderer.class.getDeclaredMethod("multiply",float[].class,float[].class);multiply.setAccessible(true);
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Native sky camera source unavailable",error);}
    }
    /** Preserves current signed-level plus legacy primary-terrain heuristic.
     * Screen-space legacy sky is deliberately outside this world-dome adapter. */
    public static boolean enabled(Renderer3DFrame frame) {
        if(frame==null||frame.isUnderground()||OpenGLSkySettings.getMode()!=OpenGLSkySettings.Mode.WORLD_DOME)return false;
        Renderer3DWorldChunkFrame world=frame.getWorldChunkFrame();if(world==null)return true;
        Renderer3DWorldChunkFrame.ChunkMesh first=null;
        for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
            if(chunk.getChunkRole()!=Renderer3DWorldChunkFrame.CHUNK_ROLE_WORLD)continue;
            if(first==null)first=chunk;
            if(chunk.getTerrainTriangles()>0){first=chunk;break;}
        }
        return first==null||first.getPlane()==0&&Math.floorDiv(first.getOriginWorldZ(),128)<3000;
    }
    public Frame prepare(Renderer3DFrame frame) {
        values[0]=0;
        if(!enabled(frame))return new Frame(false,values,palette,null,null);
        Renderer3DFrame previous=RendererFrameLighting.bind(frame);
        try {
            RendererDayNightCycle.Presentation presentation=RendererDayNightCycle.currentPresentation();
            float nextRadius=Math.max(2048f,frame.getFogDistance()*.92f);boolean changed=geometry==null||Float.floatToIntBits(nextRadius)!=Float.floatToIntBits(radius);
            if(changed){GeometrySink sink=new GeometrySink();geometrySource.emitAllGeometry(nextRadius,presentation,sink);sink.finish();geometry=sink.vertices();ranges=sink.ranges;paletteCount=sink.paletteCount;radius=nextRadius;geometryBuilds++;}
            PaletteSink sink=new PaletteSink();int used=geometrySource.updatePalette(presentation,sink);
            if(used!=paletteCount||sink.highest!=paletteCount)throw new IllegalStateException("Sky geometry/palette source mismatch");
            float[] projectionMatrix=(float[])projection.invoke(matrixSource,frame),viewMatrix=(float[])view.invoke(matrixSource,frame);
            float[] combined=(float[])multiply.invoke(matrixSource,projectionMatrix,viewMatrix);
            values[0]=1;values[1]=WorldSkyGeometry.starVisibility(presentation)>.001f?1:0;values[2]=WorldSkyGeometry.cloudVisibility(presentation)>.001f?1:0;values[3]=paletteCount;
            for(int column=0;column<4;column++)for(int row=0;row<4;row++)values[4+column*4+row]=combined[row*4+column];
            return new Frame(true,values,palette,changed?geometry:null,changed?ranges:null);
        }catch(Exception error){throw new IllegalStateException("Browser native sky export failed",error);}
        finally{RendererFrameLighting.restore(previous);}
    }
    public void clear(){geometry=null;ranges=null;radius=Float.NaN;paletteCount=0;values[0]=0;}
    int geometryBuilds(){return geometryBuilds;}
    private abstract static class Sink implements WorldSkyGeometry.Sink {
        public void glEnable(int value){if(value!=GL_BLEND)throw new IllegalArgumentException("Unexpected sky enable state");}
        public void glDisable(int value){if(value!=GL_BLEND)throw new IllegalArgumentException("Unexpected sky disable state");}
        public void glBlendFunc(int source,int destination){if(source!=GL_SRC_ALPHA||destination!=GL_ONE_MINUS_SRC_ALPHA)throw new IllegalArgumentException("Unexpected sky blend function");}
    }
    private final class PaletteSink extends Sink {
        int highest;
        public void layer(int layer) { }
        public void glBegin(int mode){throw new IllegalStateException("Palette export generated geometry");}
        public void glEnd(){throw new IllegalStateException("Palette export generated geometry");}
        public void glVertex3f(float x,float y,float z){throw new IllegalStateException("Palette export generated geometry");}
        public void glColor4f(int slot,float r,float g,float b,float a){if(slot<0||slot>=MAX_PALETTE)throw new IllegalArgumentException("Sky palette bound exceeded");int at=slot*4;palette[at]=r;palette[at+1]=g;palette[at+2]=b;palette[at+3]=a;highest=Math.max(highest,slot+1);}
    }
    private static final class GeometrySink extends Sink {
        private float[] data=new float[16384];
        final int[] ranges=new int[12];
        private final float[] quad=new float[16];
        private int count,layer=-1,mode,quadCount,currentPalette,paletteCount;
        public void layer(int next){if(mode!=0)throw new IllegalStateException("Sky layer changed inside primitive");if(layer>=0)ranges[layer*3+1]=count-ranges[layer*3];if(next!=layer+1||next>3)throw new IllegalStateException("Sky layer order changed");layer=next;ranges[layer*3]=count;ranges[layer*3+2]=layer==0?0:1;}
        public void glBegin(int next){if(mode!=0||next!=GL_TRIANGLES&&next!=GL_QUADS)throw new IllegalStateException("Unsupported sky primitive");mode=next;quadCount=0;}
        public void glEnd(){if(mode==0||quadCount!=0)throw new IllegalStateException("Incomplete sky primitive");mode=0;}
        public void glColor4f(int slot,float r,float g,float b,float a){if(slot<0||slot>=MAX_PALETTE)throw new IllegalArgumentException("Sky palette bound exceeded");currentPalette=slot;paletteCount=Math.max(paletteCount,slot+1);}
        public void glVertex3f(float x,float y,float z){if(mode==GL_TRIANGLES)append(x,y,z,currentPalette);else if(mode==GL_QUADS){int at=quadCount++*4;quad[at]=x;quad[at+1]=y;quad[at+2]=z;quad[at+3]=currentPalette;if(quadCount==4){for(int corner:new int[]{0,1,2,0,2,3})append(quad[corner*4],quad[corner*4+1],quad[corner*4+2],quad[corner*4+3]);quadCount=0;}}else throw new IllegalStateException("Sky vertex outside primitive");}
        private void append(float x,float y,float z,float slot){if(count>=MAX_VERTICES||!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))throw new IllegalArgumentException("Sky vertex bound exceeded");int at=count++*4;if(at+4>data.length)data=Arrays.copyOf(data,Math.min(MAX_VERTICES*4,data.length*2));data[at]=x;data[at+1]=y;data[at+2]=z;data[at+3]=slot;}
        void finish(){if(layer!=3||mode!=0)throw new IllegalStateException("Incomplete sky generation");ranges[layer*3+1]=count-ranges[layer*3];}
        float[] vertices(){return Arrays.copyOf(data,count*4);}
    }
}
