package orsc;
import java.nio.*;
/** Typed destination boundary contract independent of the production field formulas. */
public final class NativeTypedWriterBoundaryFixture {
    static void require(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        ByteBuffer direct=ByteBuffer.allocateDirect(104).order(ByteOrder.nativeOrder()),reference=ByteBuffer.allocateDirect(104).order(ByteOrder.nativeOrder());int checks=0;
        for(int code=0;code<256;code++){
            float[] v=new float[28];v[0]=-0f;v[7]=.25f;v[8]=.75f;v[18]=-.5f;v[19]=1;int color=code<<16|(255-code)<<8|code;int terrain=code<<16|code<<8|(255-code);
            for(int c:new int[]{3,15}){v[c]=code/255f;v[c+1]=(255-code)/255f;v[c+2]=code/255f;}v[6]=1;for(int c=9;c<=12;c++)v[c]=1;v[13]=v[14]=code;v[21]=v[22]=v[23]=code;v[24]=v[25]=code/255f;v[26]=(255-code)/255f;v[27]=code/255f;
            direct.position(52);reference.position(52);NativeResidentGeometry.putTypedMaterialColor(direct,color);NativeResidentGeometry.putUnbakedTextureLight(direct);require(NativeResidentGeometry.putTypedShaderInputs(direct,code,color,code,code,code,terrain,code),"valid code");require(NativeResidentGeometry.putTypedFloats(direct,v)&&NativeResidentGeometry.put(reference,v),"float admission");for(int i=52;i<104;i++){require(direct.get(i)==reference.get(i),"byte parity "+i);checks++;}
        }
        for(int slot=0;slot<5;slot++)for(int bad:new int[]{-1,256,257,65535,Integer.MIN_VALUE,Integer.MAX_VALUE}){
            int[] inputs={255,255,255,255,255};inputs[slot]=bad;direct.position(52);require(!NativeResidentGeometry.putTypedShaderInputs(direct,inputs[0],0,inputs[1],inputs[2],inputs[3],0,inputs[4]),"future field rejected "+slot+"/"+bad);require(direct.position()==52,"position unchanged");checks++;
        }
        for(int c:NativeResidentGeometry.FLOAT_SOURCE)for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){float[] v=new float[28];v[c]=bad;direct.position(52);require(!NativeResidentGeometry.putTypedFloats(direct,v)&&direct.position()==52,"nonfinite rejected");checks++;}
        for(int c:NativeResidentGeometry.FLOAT_SOURCE){float[] v=new float[28];v[c]=-0f;direct.position(52);require(NativeResidentGeometry.putTypedFloats(direct,v),"typed signed zero admitted");int target=0;while(NativeResidentGeometry.FLOAT_SOURCE[target]!=c)target++;require(Float.floatToRawIntBits(direct.getFloat(52+target*4))==0x80000000,"typed signed zero retained");checks++;}
        OpenGLWorldChunkRenderer renderer=new OpenGLWorldChunkRenderer(null,null,null,true,true,false,false,true,true,false);
        java.lang.reflect.Method writer=OpenGLWorldChunkRenderer.class.getDeclaredMethod("writeChunkVertex",float[].class,int.class,orsc.graphics.three.Renderer3DWorldChunkFrame.ChunkMesh.class,orsc.graphics.three.Renderer3DFrame.class,int.class,boolean.class,boolean.class,int[].class,float[].class,boolean.class,ByteBuffer.class);writer.setAccessible(true);
        direct.position(52);for(int i=52;i<104;i++)direct.put(i,(byte)77);
        require(!(Boolean)writer.invoke(renderer,new float[28],0,null,null,0,true,false,null,null,false,direct),"mismatched eligibility rejected before touching source");require(direct.position()==52,"mismatch position");for(int i=52;i<104;i++)require(direct.get(i)==77,"mismatch destination untouched");
        System.clearProperty("spoiledmilk.nativeCompactGeometry");System.clearProperty("spoiledmilk.nativeCompactTypedWriter");require(!NativeResidentGeometry.typedWriterEnabled(),"default off");System.setProperty("spoiledmilk.nativeCompactTypedWriter","true");require(!NativeResidentGeometry.typedWriterEnabled(),"compact gate");System.setProperty("spoiledmilk.nativeCompactGeometry","true");require(NativeResidentGeometry.typedWriterEnabled(),"explicit on");for(String p:new String[]{"spoiledmilk.browserBenchmark","spoiledmilk.browserPublicAlpha"}){System.setProperty(p,"true");require(!NativeResidentGeometry.typedWriterEnabled(),"browser isolation");System.clearProperty(p);}System.out.println("PASS typed byte boundaries/checks="+checks);
    }
}
