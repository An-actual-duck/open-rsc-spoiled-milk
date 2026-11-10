package orsc;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Browser-only zero-contribution demand: far CLIP plus full shadow-map volumes. */
final class BrowserFogDemand {
    static final double FLOAT_EPS=0x1.0p-23, COLOR_MARGIN=4*128.0, MAX_COORD=0x1.0p24;
    static final boolean POSITION_CONTRACT=contract();
    static final class Selection {
        final boolean[] required;final int sourceChunks,requiredChunks,sourceVertices,requiredVertices;final boolean enabled;
        Selection(boolean[] r,int source,int required,int vertices,int demanded,boolean enabled){this.required=r;sourceChunks=source;requiredChunks=required;sourceVertices=vertices;requiredVertices=demanded;this.enabled=enabled;}
    }
    static Selection select(Renderer3DFrame frame,BrowserLightingData.ShadowPlan plan) {
        Renderer3DWorldChunkFrame world=frame.getWorldChunkFrame();int count=world.getChunkCount(),required=0,total=0,vertices=0;
        boolean supported=POSITION_CONTRACT && RendererFogSettings.getMode()==RendererFogSettings.Mode.ON && plan!=null;
        float[] view=OpenGLWorldChunkRenderer.backendWorldViewMatrix(frame),clip=OpenGLWorldChunkRenderer.multiply(OpenGLWorldChunkRenderer.projectionMatrix(frame),view);
        supported&=finite(clip)&&frame.getFogDistance()>0&&frame.getFogDistance()<1048576;
        boolean[] flags=new boolean[count];int offX=GeometryShadowCoordinates.worldOffsetX(world),offZ=GeometryShadowCoordinates.worldOffsetZ(world);
        int n=0;for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
            boolean keep=true;
            if(supported&&chunk.hasVertexBounds()) {
                double[] b={chunk.getMinVertexX(),chunk.getMinVertexY(),chunk.getMinVertexZ(),chunk.getMaxVertexX(),chunk.getMaxVertexY(),chunk.getMaxVertexZ()};
                if(bounded(b,offX,offZ,chunk.getVertexOffsetX(),chunk.getVertexOffsetZ())) {
                    keep=!outsideFar(b,clip);
                    if(!keep)for(float[] light:plan.demandMatrices)if(!outsideShadow(b,light,offX,offZ)){keep=true;break;}
                }
            }
            flags[n++]=keep;total=Math.addExact(total,chunk.getVertexCount());if(keep){required++;vertices=Math.addExact(vertices,chunk.getVertexCount());}
        }
        // Existing readiness requires >0. Retain a real full-source anchor rather
        // than fabricate a zero/zero authoritative world or change its protocol.
        if(required==0&&count>0){flags[0]=true;required=1;vertices=world.getChunks().get(0).getVertexCount();}
        return new Selection(flags,count,required,total,vertices,supported);
    }
    static boolean outsideFar(double[] bounds,float[] matrix) {
        if(!finite(matrix)||bounds.length!=6)return false;
        double[] b=bounds.clone();for(int a=0;a<3;a++){b[a]-=COLOR_MARGIN;b[a+3]+=COLOR_MARGIN;}
        // The actual Float32 combined clip matrix, not a radial/player distance.
        // GPU clipping rejects z>w; independently rounded z/w need an error bound.
        return positivePlane(b,matrix,2,1,3,-1);
    }
    static boolean outsideShadow(double[] bounds,float[] matrix,int offX,int offZ) {
        if(!finite(matrix)||bounds.length!=6)return false;
        double[] b=bounds.clone();double magnitude=Math.max(Math.abs(offX),Math.abs(offZ));for(double v:b)magnitude=Math.max(magnitude,Math.abs(v));
        double pad=16+128*FLOAT_EPS*(magnitude+1);for(int a=0;a<3;a++){b[a]-=pad;b[a+3]+=pad;}b[0]+=offX;b[3]+=offX;b[2]+=offZ;b[5]+=offZ;
        for(int row=0;row<3;row++)if(positivePlane(b,matrix,row,1,3,-1)||positivePlane(b,matrix,row,-1,3,-1))return true;
        return false;
    }
    private static boolean positivePlane(double[] b,float[] m,int rowA,int signA,int rowB,int signB) {
        double low=signA*m[rowA*4+3]+signB*m[rowB*4+3],magnitude=Math.abs(m[rowA*4+3])+Math.abs(m[rowB*4+3]);
        for(int axis=0;axis<3;axis++) {
            double a=signA*m[rowA*4+axis]+signB*m[rowB*4+axis];low+=Math.min(a*b[axis],a*b[axis+3]);
            magnitude+=(Math.abs(m[rowA*4+axis])+Math.abs(m[rowB*4+axis]))*Math.max(Math.abs(b[axis]),Math.abs(b[axis+3]));
        }
        return Double.isFinite(low)&&Double.isFinite(magnitude)&&magnitude<1e30&&low>128*FLOAT_EPS*magnitude+1e-5;
    }
    private static boolean bounded(double[] b,int... values){for(double v:b)if(!Double.isFinite(v)||Math.abs(v)>MAX_COORD)return false;for(int v:values)if(Math.abs((double)v)>MAX_COORD)return false;return b[0]<=b[3]&&b[1]<=b[4]&&b[2]<=b[5];}
    private static boolean finite(float[] matrix){if(matrix==null||matrix.length!=16)return false;for(float f:matrix)if(!Float.isFinite(f))return false;return true;}
    private static boolean contract(){try {Field f=OpenGLShaderProgram.class.getDeclaredField("RESIDENT_CHUNK_PARITY_VERTEX_SHADER");f.setAccessible(true);return digest((String)f.get(null)).equals("7af2917e332f05cc614aa50c40fba1509546e820dceb54900f8f0e56bbdfb3df")&&digest(BrowserLightingData.depthVertex()).equals("354aec4d0338ccdf0f988bdf5524ee00443f48e5bbe8d944ee0179571ff315a9");}catch(Exception error){return false;}}
    private static String digest(String s)throws Exception{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))b.append(String.format("%02x",v&255));return b.toString();}
}
