package orsc.graphics.three;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Actual producer -> nested-array JNI -> Java reads. No Scene, game or GPU. */
public final class BrowserBatchedModelKernelProbe {
    private static native void report(boolean passed,int models,int values,int[] work,String detail);
    public static void main(String[] args)throws Exception {
        BrowserBatchedModelKernel batch=new BrowserBatchedModelKernel();
        try {
            List<int[][]> actual=new ArrayList<int[][]>(),expected=new ArrayList<int[][]>();
            int total,values=0;int[] previous=null;
            try(InputStream raw=BrowserBatchedModelKernelProbe.class.getResourceAsStream("/batch-reference.bin");DataInputStream in=new DataInputStream(raw)){
                if(in.readInt()!=0x4e4b3031)throw new AssertionError("Reference magic");total=in.readInt();
                for(int model=0;model<total;model++){
                    int count=in.readInt();int[] camera=new int[8];for(int n=0;n<8;n++)camera[n]=in.readInt();for(int n=0;n<6;n++)in.readInt();
                    int[][] a=NativeModelKernelReferenceFixture.readArrays(in),e=NativeModelKernelReferenceFixture.readArrays(in);
                    if(!Arrays.equals(previous,camera)){batch.flush();batch.begin(camera[1],camera[3],camera[0],camera[2],camera[6],camera[5],camera[7],camera[4]);previous=camera;}
                    if(!batch.enqueue(NativeModelKernelReferenceFixture.model(count,a)))throw new AssertionError("Valid independent model rejected: "+model);
                    actual.add(a);expected.add(e);
                }
            }
            batch.flush();
            for(int model=0;model<total;model++)for(int n=0;n<8;n++){
                if(!Arrays.equals(actual.get(model)[n],expected.get(model)[n]))throw new AssertionError("Batched JNI mismatch model "+model+" array "+n);
                values+=actual.get(model)[n].length;
            }
            int[] work=batch.work();
            if(work[3]!=total||work[4]<=0||work[7]!=0||work[11]!=128||work[2]>=total/2)throw new AssertionError("Wrong batch granularity: "+Arrays.toString(work));
            for(String name:new String[]{"rows","models"}){
                Field f=BrowserBatchedModelKernel.class.getDeclaredField(name);f.setAccessible(true);
                for(Object value:(Object[])f.get(batch))if(value!=null)throw new AssertionError("Java packet retained "+name);
            }
            Field roles=BrowserBatchedModelKernel.class.getDeclaredField("roles");roles.setAccessible(true);
            if(!((Map<?,?>)roles.get(batch)).isEmpty())throw new AssertionError("Java role map retained rows");
            report(true,total,values,work,"Actual bounded Java producer -> nested primitive rows -> JS batch -> complete Java arrays match frozen RSModel");
        }catch(Throwable error){report(false,0,0,batch.work(),String.valueOf(error));throw error;}
        finally{batch.discard();}
    }
}
