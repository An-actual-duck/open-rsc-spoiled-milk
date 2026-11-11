import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import orsc.graphics.two.SpriteArchive.Entry;
import orsc.graphics.two.SpriteArchive.Frame;
import orsc.graphics.two.SpriteArchive.Subspace;
import orsc.graphics.two.SpriteArchive.Unpacker;
import orsc.graphics.two.SpriteArchive.Workspace;

/** Actual unchanged decoder, two independently decoded inputs; never creates a Scene. */
public final class WholeZoneTextureEquivalence {
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    public static void main(String[] args)throws Exception {
        Workspace original=new Unpacker().unpackArchive(new File(args[0]));
        Workspace derivative=new Unpacker().unpackArchive(new File(args[1]));
        check(original!=null&&derivative!=null,"Actual decoder refused archive");
        Subspace a=original.getSubspaceByName("textures"),b=derivative.getSubspaceByName("textures");
        check(a!=null&&b!=null&&derivative.getSubspaceCount()==1,"Derived subspace membership");
        check(a.getEntryCount()>0&&a.getEntryCount()==b.getEntryCount(),"Actual decoded entry count");
        int frames=0,pixels=0;Set<String> seen=new HashSet<String>();MessageDigest hash=MessageDigest.getInstance("SHA-256");
        for(int i=0;i<a.getEntryCount();i++){
            Entry x=a.getEntryList().get(i),y=b.getEntryList().get(i);
            check(seen.add(x.getID())&&x.getID().equals(y.getID()),"Exact entry identity/order");
            check(x.getType()==y.getType()&&x.getLayer()==y.getLayer(),"Exact entry type/layer");
            check(x.getFrames().length==y.getFrames().length,"Frame count");
            for(int f=0;f<x.getFrames().length;f++){
                Frame p=x.getFrames()[f],q=y.getFrames()[f];check(p!=null&&q!=null,"Actual decoded frames");
                check(p.getWidth()==q.getWidth()&&p.getHeight()==q.getHeight()&&p.getUseShift()==q.getUseShift()
                    &&p.getOffsetX()==q.getOffsetX()&&p.getOffsetY()==q.getOffsetY()
                    &&p.getBoundWidth()==q.getBoundWidth()&&p.getBoundHeight()==q.getBoundHeight(),"Exact dimensions/shift/offset/bounds");
                check(Arrays.equals(p.getPixels(),q.getPixels()),"Exact actual decoded palette pixels");
                for(int value:p.getPixels()){hash.update((byte)(value>>>24));hash.update((byte)(value>>>16));hash.update((byte)(value>>>8));hash.update((byte)value);}
                frames++;pixels+=p.getPixels().length;
            }
        }
        StringBuilder digest=new StringBuilder();for(byte value:hash.digest())digest.append(String.format("%02x",value&255));
        System.out.println("{\"passed\":true,\"actualDecoder\":true,\"entries\":"+a.getEntryCount()+",\"frames\":"+frames+",\"pixelsCompared\":"+pixels+",\"decodedIntPixelsSHA256\":\""+digest+"\",\"gpuExecuted\":false}");
    }
}
