import orsc.graphics.three.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Actual prepared decoder adversarial corpus; no browser/scene/server setup. */
public final class BrowserWholeRamCodecFixture {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static WholeRamPrepared decode(byte[] bytes)throws Exception{ByteArrayInputStream in=new ByteArrayInputStream(bytes);WholeRamPrepared p=WholeRamPrepared.read(in);if(in.read()!=-1)throw new IOException("Trailing prepared payload");return p;}
    private static void reject(byte[] bytes)throws Exception{checks++;try{decode(bytes);throw new AssertionError("Malformed prepared data accepted");}catch(IOException|RuntimeException expected){}}
    private static byte[] changed(byte[] bytes,int offset,int value){byte[] out=bytes.clone();ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).putInt(offset,value);return out;}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    public static void main(String[] args)throws Exception{
        byte[] bytes=Files.readAllBytes(Paths.get(args[0]));WholeRamPrepared p=decode(bytes);check(p.recipes()==3);check(p.queryCells()==9);check(p.pool.bytes()>0);check(p.modelTemplates()>0);
        for(int length:new int[]{0,1,3,4,7,8,11,12,13,99,bytes.length-1})reject(Arrays.copyOf(bytes,length));
        reject(changed(bytes,0,0));reject(changed(bytes,4,2));reject(changed(bytes,8,-1));reject(changed(bytes,8,WholeRamData.MAX_ENTRIES+1));reject(changed(bytes,12,99));reject(changed(bytes,16,WholeRamData.MAX_ARRAY+1));reject(changed(bytes,16,-1));
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        InputStream hashed=new BufferedInputStream(new java.security.DigestInputStream(new ByteArrayInputStream(bytes),digest),65536);WholeRamPrepared verified=WholeRamPrepared.read(hashed);check(hashed.read()==-1);check(Arrays.equals(digest.digest(),java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));check(verified.recipeKeys().equals(p.recipeKeys()));
        byte[] tail=Arrays.copyOf(bytes,bytes.length+1);tail[tail.length-1]=1;reject(tail);
        ByteArrayOutputStream out=new ByteArrayOutputStream();p.write(out);WholeRamPrepared copy=decode(out.toByteArray());check(p.recipeKeys().equals(copy.recipeKeys()));check(p.queryKeys().equals(copy.queryKeys()));check(p.pool.bytes()==copy.pool.bytes());
        World.PreparedActivation r=p.recipe(2,12);check(r!=null);int[] descriptor=(int[])field(r.getClass(),"d").get(r);int old=descriptor[743];descriptor[743]=Integer.MAX_VALUE;
        ByteArrayOutputStream corrupt=new ByteArrayOutputStream();p.write(corrupt);descriptor[743]=old;reject(corrupt.toByteArray());
        @SuppressWarnings("unchecked") Map<String,Integer> ids=(Map<String,Integer>)field(p.getClass(),"cellIds").get(p);int cellId=ids.get("0:2:12");int[] cd=p.pool.ints(cellId),kinds=p.pool.ints(cd[23]);int oldKind=kinds[0];kinds[0]=Integer.MAX_VALUE;
        corrupt=new ByteArrayOutputStream();p.write(corrupt);kinds[0]=oldKind;reject(corrupt.toByteArray());
        int[] rd=(int[])field(p.recipe(2,13).getClass(),"d").get(p.recipe(2,13));old=rd[15];rd[15]=2;corrupt=new ByteArrayOutputStream();p.write(corrupt);rd[15]=old;reject(corrupt.toByteArray());
        long[] roof=p.recipe(2,12).roofBits();check(roof.length==(143*143+63)/64);Renderer3DWorldChunkFrame frame=p.queryFrame(2,12);check(frame.getChunks().get(0).getRoofCoverageAxis()==143);for(int x=0;x<143;x++)for(int y=0;y<143;y++){int bit=y+x*143;check(frame.getChunks().get(0).isRoofCoveredTile(x,y)==((roof[bit>>>6]&(1L<<(bit&63)))!=0));}
        System.out.println("PASS checks="+checks+" preparedBytes="+bytes.length+" recipes="+p.recipes()+" queryCells="+p.queryCells());
    }
}
