package orsc;
import java.nio.*;import java.util.*;
/** Bounded conversion microkernel only. Does not predict gameplay FPS/latency. */
public final class NativeCompactPackingBenchmark {
    static final int COUNT=131072;
    static boolean bulk(IntBuffer out,int[] words,float[] v){
        for(int i=0;i<8;i++){float f=v[NativeResidentGeometry.FLOAT_SOURCE[i]];if(!Float.isFinite(f))return false;words[i]=Float.floatToRawIntBits(f);}
        Arrays.fill(words,8,13,0);boolean little=ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN;
        for(int i=0;i<NativeResidentGeometry.BYTE_SOURCE.length;i++){
            int c=NativeResidentGeometry.BYTE_SOURCE[i],value=NativeResidentGeometry.exactByte(v[c],NativeResidentGeometry.normalized(c));if(value<0)return false;
            int byteAt=NativeResidentGeometry.BYTE_TARGET[i];int shift=little?(byteAt&3)*8:(3-(byteAt&3))*8;words[byteAt/4]|=value<<shift;
        }
        out.put(words);return true;
    }
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        ByteBuffer scalar=ByteBuffer.allocateDirect(COUNT*52).order(ByteOrder.nativeOrder()),packed=ByteBuffer.allocateDirect(COUNT*52).order(ByteOrder.nativeOrder());IntBuffer ints=packed.asIntBuffer();int[] words=new int[13];float[][] source=new float[256][28];
        for(int i=0;i<256;i++){float[] v=source[i];v[0]=i;v[1]=-i;v[2]=i*128;v[7]=.5f;v[8]=.25f;v[18]=-.5f;v[19]=1;for(int c:NativeResidentGeometry.BYTE_SOURCE)v[c]=NativeResidentGeometry.normalized(c)?i/255f:i;
            scalar.clear();ints.clear();require(NativeResidentGeometry.put(scalar,v)&&bulk(ints,words,v),"admission");for(int b=0;b<52;b++)require(scalar.get(b)==packed.get(b),"exact byte layout");
            for(int c:NativeResidentGeometry.BYTE_SOURCE){float old=v[c];v[c]=Math.nextUp(old);ints.clear();require(!bulk(ints,words,v)&&ints.position()==0,"late lossless fallback");v[c]=old;}
        }
        System.out.println("mode,iteration,vertices,nanos");long check=0;
        for(int iteration=-8;iteration<16;iteration++)for(int mode=0;mode<2;mode++){
            boolean useBulk=((iteration&1)==0?mode:1-mode)==1;scalar.clear();ints.clear();long start=System.nanoTime();
            if(useBulk)for(int i=0;i<COUNT;i++){if(!bulk(ints,words,source[i&255]))throw new AssertionError();}
            else for(int i=0;i<COUNT;i++){if(!NativeResidentGeometry.put(scalar,source[i&255]))throw new AssertionError();}
            long elapsed=System.nanoTime()-start;check+=useBulk?ints.get(COUNT*13-1):scalar.get(COUNT*52-1);
            if(iteration>=0)System.out.println((useBulk?"bulk":"scalar")+","+iteration+","+COUNT+","+elapsed);
        }
        System.out.println("# consumed="+check+" parity=PASS; no gameplay performance claim");
    }
}
