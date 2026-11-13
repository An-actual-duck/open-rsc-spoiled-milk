import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;
import orsc.graphics.three.RSModel;
public final class BrowserFinalModelProfileFixture {
    static void append(MessageDigest d,Object v){if(v!=null&&v.getClass().isArray()){for(int i=0;i<Array.getLength(v);i++)append(d,Array.get(v,i));}else d.update(String.valueOf(v).getBytes(java.nio.charset.StandardCharsets.UTF_8));d.update((byte)0);}
    public static void main(String[] args)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");Method reset=RSModel.class.getDeclaredMethod("resetTransformCache",int.class);reset.setAccessible(true);
        Field[] fields=RSModel.class.getDeclaredFields();Arrays.sort(fields,Comparator.comparing(Field::getName));
        for(int n=0;n<120;n++){
            RSModel model=new RSModel(3,1);model.insertVertex(-32+n,-12,9);model.insertVertex(64,16+n,-9);model.insertVertex(0,-70,88);model.insertFace(3,new int[]{0,1,2},-1,-2,false);
            try{RSModel.class.getMethod("setBrowserProfileOwner",int.class).invoke(model,n%3);}catch(NoSuchMethodException baseline){}
            model.addRotation(0,(n%8)*32,0);model.setDiffuseLightAndColor(-50,-10,-50,48,48,true,117);model.setTranslate(n*128,-n*9,-n*256);reset.invoke(model,7972);model.setTranslate(-n*256,n*9,n*128);reset.invoke(model,7972);
            for(Field f:fields)if(!Modifier.isStatic(f.getModifiers())&&!f.getName().equals("browserProfileOwner")&&(f.getType().isPrimitive()||f.getType().isArray())){f.setAccessible(true);append(digest,f.getName());append(digest,f.get(model));}
        }
        if(Boolean.getBoolean("spoiledmilk.browserFinalModelProfile")){
            Class<?> c=Class.forName("orsc.graphics.three.BrowserFinalModelProfile");double[] v=(double[])c.getMethod("snapshot").invoke(null);if(v[1]!=1)throw new AssertionError("Profile gate");for(int owner=1;owner<4;owner++)for(int phase=1;phase<6;phase++)if(v[3+owner*12+phase*2]<=0)throw new AssertionError("Missing owner/phase "+owner+"/"+phase);
        }
        StringBuilder h=new StringBuilder();for(byte b:digest.digest())h.append(String.format("%02x",b&255));System.out.println(h);
    }
}
