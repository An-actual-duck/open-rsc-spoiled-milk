package orsc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Small reusable circle fans, clipped to the same declared roof tiles as sunlight. */
final class CharacterGroundShadowMesh {
    static final int STRIDE = 7;
    static final int MAX_VERTICES = 131070;
    private static final int SEGMENTS = 16;
    private static final float[] X = new float[SEGMENTS+1], Z = new float[SEGMENTS+1];
    static {
        for(int i=0;i<=SEGMENTS;i++) {
            X[i]=(float)Math.cos(i*Math.PI*2/SEGMENTS);
            Z[i]=(float)Math.sin(i*Math.PI*2/SEGMENTS);
        }
    }
    private FloatBuffer vertices=buffer(1024*STRIDE);
    private final float[] a=new float[32], b=new float[32];
    private float centerX, centerY, centerZ, slopeX, slopeZ;
    int circles, roofSuppressed;

    void clear() { vertices.clear(); circles=roofSuppressed=0; }
    int vertexCount() { return vertices.position()/STRIDE; }
    FloatBuffer data() { FloatBuffer result=vertices.asReadOnlyBuffer();result.flip();return result; }

    void append(float x,float y,float z,float radius,float dx,float dz,float opacity,
                GeometryShadowRoofMask roofs,int worldOffsetX,int worldOffsetZ) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(radius)
            ||!Float.isFinite(dx)||!Float.isFinite(dz)||!Float.isFinite(opacity)
            ||Math.abs(x)>1_000_000_000f||Math.abs(z)>1_000_000_000f
            ||radius<1||radius>64||opacity<=0||vertexCount()>=MAX_VERTICES-256)return;
        if(roofs.covered((int)x+worldOffsetX,(int)z+worldOffsetZ)) {roofSuppressed++;return;}
        centerX=x;centerY=y;centerZ=z;slopeX=dx;slopeZ=dz;
        int minX=(int)Math.floor((x-radius)/128),maxX=(int)Math.floor((x+radius)/128);
        int minZ=(int)Math.floor((z-radius)/128),maxZ=(int)Math.floor((z+radius)/128);
        int before=vertexCount();
        for(int tx=minX;tx<=maxX;tx++)for(int tz=minZ;tz<=maxZ;tz++) {
            if(roofs.covered(tx*128+64+worldOffsetX,tz*128+64+worldOffsetZ))continue;
            for(int i=0;i<SEGMENTS;i++) {
                a[0]=x;a[1]=z;a[2]=Math.min(.32f,opacity);
                a[3]=x+radius*X[i];a[4]=z+radius*Z[i];a[5]=0;
                a[6]=x+radius*X[i+1];a[7]=z+radius*Z[i+1];a[8]=0;
                int n=clip(a,3,b,0,tx*128,true);
                n=clip(b,n,a,0,(tx+1)*128,false);
                n=clip(a,n,b,1,tz*128,true);
                n=clip(b,n,a,1,(tz+1)*128,false);
                for(int j=1;j<n-1;j++) {
                    if(vertexCount()+3>MAX_VERTICES)return;
                    vertex(a,0);vertex(a,j*3);vertex(a,(j+1)*3);
                }
            }
        }
        if(vertexCount()>before)circles++;
    }
    private static int clip(float[] in,int count,float[] out,int axis,float edge,boolean greater) {
        if(count==0)return 0;
        int used=0,previous=(count-1)*3;
        for(int current=0;current<count*3;current+=3) {
            boolean p=greater?in[previous+axis]>=edge:in[previous+axis]<=edge;
            boolean c=greater?in[current+axis]>=edge:in[current+axis]<=edge;
            if(p!=c) {
                float t=(edge-in[previous+axis])/(in[current+axis]-in[previous+axis]);
                for(int k=0;k<3;k++)out[used++]=in[previous+k]+t*(in[current+k]-in[previous+k]);
            }
            if(c)for(int k=0;k<3;k++)out[used++]=in[current+k];
            previous=current;
        }
        return used/3;
    }
    private void vertex(float[] v,int at) {
        if(vertices.remaining()<STRIDE) {
            FloatBuffer next=buffer(Math.min(MAX_VERTICES*STRIDE,vertices.capacity()*2));
            vertices.flip();next.put(vertices);vertices=next;
        }
        float x=v[at],z=v[at+1];
        vertices.put(x).put(centerY+slopeX*(x-centerX)+slopeZ*(z-centerZ)-1.5f).put(z);
        vertices.put(0).put(0).put(0).put(v[at+2]);
    }
    private static FloatBuffer buffer(int floats) {
        return ByteBuffer.allocateDirect(floats*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }
}
