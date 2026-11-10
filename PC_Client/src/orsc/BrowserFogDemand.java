package orsc;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.WeakHashMap;
import orsc.graphics.three.Renderer3DFrame;
import orsc.graphics.three.Renderer3DWorldChunkFrame;

/** Browser-only zero-contribution demand: far CLIP plus full shadow-map volumes. */
final class BrowserFogDemand {
    static final double FLOAT_EPS=0x1.0p-23, COLOR_MARGIN=4*128.0, MAX_COORD=0x1.0p24;
    static final boolean POSITION_CONTRACT=contract();
    private static final WeakHashMap<Renderer3DWorldChunkFrame.ChunkMesh,Boolean> VALID_INDICES=new WeakHashMap<Renderer3DWorldChunkFrame.ChunkMesh,Boolean>();
    interface RebaseGuard {double uploadedOffsetMagnitude(Renderer3DWorldChunkFrame.ChunkMesh chunk);}
    static final class Selection {
        final boolean[] required;final int sourceChunks,requiredChunks,sourceVertices,requiredVertices;final boolean enabled;
        Selection(boolean[] r,int source,int required,int vertices,int demanded,boolean enabled){this.required=r;sourceChunks=source;requiredChunks=required;sourceVertices=vertices;requiredVertices=demanded;this.enabled=enabled;}
    }
    static Selection select(Renderer3DFrame frame,BrowserLightingData.ShadowPlan plan) {return select(frame,plan,null);}
    static Selection select(Renderer3DFrame frame,BrowserLightingData.ShadowPlan plan,RebaseGuard rebase) {
        double[] b=new double[6],scratch=new double[6];
        Renderer3DWorldChunkFrame world=frame.getWorldChunkFrame();int count=world.getChunkCount(),required=0,total=0,vertices=0;
        boolean supported=POSITION_CONTRACT && RendererFogSettings.getMode()==RendererFogSettings.Mode.ON && plan!=null;
        float[] view=OpenGLWorldChunkRenderer.backendWorldViewMatrix(frame),clip=OpenGLWorldChunkRenderer.multiply(OpenGLWorldChunkRenderer.projectionMatrix(frame),view);
        supported&=finite(clip)&&frame.getFogDistance()>0&&frame.getFogDistance()<1048576;
        boolean[] flags=new boolean[count];int offX=GeometryShadowCoordinates.worldOffsetX(world),offZ=GeometryShadowCoordinates.worldOffsetZ(world);
        int n=0;for(Renderer3DWorldChunkFrame.ChunkMesh chunk:world.getChunks()) {
            boolean keep=true;
            if(supported&&chunk.hasVertexBounds()) {
                b[0]=chunk.getMinVertexX();b[1]=chunk.getMinVertexY();b[2]=chunk.getMinVertexZ();
                b[3]=chunk.getMaxVertexX();b[4]=chunk.getMaxVertexY();b[5]=chunk.getMaxVertexZ();
                double oldMagnitude=rebase==null?0:rebase.uploadedOffsetMagnitude(chunk);
                if(Double.isFinite(oldMagnitude)&&oldMagnitude<=MAX_COORD&&bounded(b,offX,offZ,chunk.getVertexOffsetX(),chunk.getVertexOffsetZ())) {
                    keep=!outsideFar(b,clip,scratch);
                    if(!keep)for(int projection=0;projection<plan.demandMatrices.length;projection++) {
                        // Production cached far maps exclude animated-object
                        // chunks. Their only shadow dependency is the near map.
                        if(plan.demandStaticOnly[projection]&&chunk.getChunkRole()==Renderer3DWorldChunkFrame.CHUNK_ROLE_ANIMATED_OBJECTS)continue;
                        if(!outsideShadow(b,plan.demandMatrices[projection],offX,offZ,oldMagnitude,scratch)){keep=true;break;}
                    }
                }
            }
            if(!keep&&!validForOmission(chunk))keep=true;
            flags[n++]=keep;total=Math.addExact(total,chunk.getVertexCount());if(keep){required++;vertices=Math.addExact(vertices,chunk.getVertexCount());}
        }
        // Existing readiness requires >0. Retain a real full-source anchor rather
        // than fabricate a zero/zero authoritative world or change its protocol.
        if(required==0&&count>0){flags[0]=true;required=1;vertices=world.getChunks().get(0).getVertexCount();}
        return new Selection(flags,count,required,total,vertices,supported);
    }
    private static boolean validForOmission(Renderer3DWorldChunkFrame.ChunkMesh chunk) {
        if(chunk.getVertexCount()>524288||chunk.getIndexCount()>1572864)return false;
        Boolean known=VALID_INDICES.get(chunk);
        if(known!=null)return known.booleanValue();
        boolean valid=true;
        for(int n=0;n<chunk.getIndexCount();n++)if(chunk.getIndex(n)<0||chunk.getIndex(n)>=chunk.getVertexCount()){valid=false;break;}
        if(VALID_INDICES.size()>=256)VALID_INDICES.clear();
        VALID_INDICES.put(chunk,Boolean.valueOf(valid));
        return valid;
    }
    static boolean outsideFar(double[] bounds,float[] matrix) {return outsideFar(bounds,matrix,new double[6]);}
    private static boolean outsideFar(double[] bounds,float[] matrix,double[] b) {
        if(!finite(matrix)||bounds.length!=6)return false;
        for(int a=0;a<3;a++){b[a]=bounds[a]-COLOR_MARGIN;b[a+3]=bounds[a+3]+COLOR_MARGIN;}
        // The actual Float32 combined clip matrix, not a radial/player distance.
        // GPU clipping rejects z>w; independently rounded z/w need an error bound.
        return positivePlane(b,matrix,2,1,3,-1,false);
    }
    static boolean outsideShadow(double[] bounds,float[] matrix,int offX,int offZ) {return outsideShadow(bounds,matrix,offX,offZ,0,new double[6]);}
    private static boolean outsideShadow(double[] bounds,float[] matrix,int offX,int offZ,double oldMagnitude,double[] b) {
        if(!finite(matrix)||bounds.length!=6)return false;
        double magnitude=0;for(double v:bounds)magnitude=Math.max(magnitude,Math.abs(v));
        double pad=16+128*FLOAT_EPS*(magnitude+Math.abs((double)offX)+Math.abs((double)offZ)+oldMagnitude+1);
        for(int a=0;a<3;a++){b[a]=bounds[a]-pad;b[a+3]=bounds[a+3]+pad;}b[0]+=offX;b[3]+=offX;b[2]+=offZ;b[5]+=offZ;
        for(int row=0;row<3;row++)if(positivePlane(b,matrix,row,1,3,-1,true)||positivePlane(b,matrix,row,-1,3,-1,true))return true;
        return false;
    }
    private static boolean positivePlane(double[] b,float[] m,int rowA,int signA,int rowB,int signB,boolean columnMajor) {
        double ta=m[columnMajor?12+rowA:rowA*4+3],tb=m[columnMajor?12+rowB:rowB*4+3];
        double low=signA*ta+signB*tb,magnitude=Math.abs(ta)+Math.abs(tb);
        for(int axis=0;axis<3;axis++) {
            double ca=m[columnMajor?axis*4+rowA:rowA*4+axis],cb=m[columnMajor?axis*4+rowB:rowB*4+axis];
            double a=signA*ca+signB*cb;low+=Math.min(a*b[axis],a*b[axis+3]);
            magnitude+=(Math.abs(ca)+Math.abs(cb))*Math.max(Math.abs(b[axis]),Math.abs(b[axis+3]));
        }
        return Double.isFinite(low)&&Double.isFinite(magnitude)&&magnitude<1e30&&low>128*FLOAT_EPS*magnitude+1e-5;
    }
    private static boolean bounded(double[] bounds,int offX,int offZ,int vertexX,int vertexZ) {
        for(double value:bounds) {
            if(!Double.isFinite(value)||Math.abs(value)>MAX_COORD)return false;
        }
        return Math.abs((double)offX)<=MAX_COORD&&Math.abs((double)offZ)<=MAX_COORD
            &&Math.abs((double)vertexX)<=MAX_COORD&&Math.abs((double)vertexZ)<=MAX_COORD
            &&bounds[0]<=bounds[3]&&bounds[1]<=bounds[4]&&bounds[2]<=bounds[5];
    }
    private static boolean finite(float[] matrix){if(matrix==null||matrix.length!=16)return false;for(float f:matrix)if(!Float.isFinite(f))return false;return true;}
    private static boolean contract(){try {Field f=OpenGLShaderProgram.class.getDeclaredField("RESIDENT_CHUNK_PARITY_VERTEX_SHADER");f.setAccessible(true);return digest((String)f.get(null)).equals("7af2917e332f05cc614aa50c40fba1509546e820dceb54900f8f0e56bbdfb3df")&&digest(BrowserLightingData.depthVertex()).equals("354aec4d0338ccdf0f988bdf5524ee00443f48e5bbe8d944ee0179571ff315a9");}catch(Exception error){return false;}}
    private static String digest(String s)throws Exception{StringBuilder b=new StringBuilder();for(byte v:MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")))b.append(String.format("%02x",v&255));return b.toString();}
}
